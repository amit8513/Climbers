package com.example.climb.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.example.climb.data.RouteColor
import com.google.common.collect.ImmutableList
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Keeps pixels whose hue matches [targetColor] (shifted by [hueOffsetDegrees]) in color, plus
 * anything ML Kit detects as a person, and desaturates everything else — so a climb's tagged
 * route color (and the climber) stands out from the rest of the wall during playback.
 */
@UnstableApi
class ColorIsolationEffect(
    private val targetColor: RouteColor,
    private val hueToleranceDegrees: Float = DEFAULT_HUE_TOLERANCE_DEGREES,
    private val hueOffsetDegrees: Float = 0f,
    private val detectPerson: Boolean = true,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        ColorIsolationShaderProgram(useHdr, targetColor, hueToleranceDegrees, hueOffsetDegrees, detectPerson)

    companion object {
        const val DEFAULT_HUE_TOLERANCE_DEGREES = 12.6f
        const val MIN_HUE_TOLERANCE_DEGREES = 4f
        const val MAX_HUE_TOLERANCE_DEGREES = 45f
        const val MIN_HUE_OFFSET_DEGREES = -60f
        const val MAX_HUE_OFFSET_DEGREES = 60f
    }
}

@UnstableApi
private class ColorIsolationShaderProgram(
    useHdr: Boolean,
    targetColor: RouteColor,
    hueToleranceDegrees: Float,
    hueOffsetDegrees: Float,
    private val detectPerson: Boolean,
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val glProgram: GlProgram
    private val targetHue: Float
    private val hueThreshold: Float

    private val segmenter: Segmenter? = if (detectPerson) {
        Segmentation.getClient(
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
                .setStreamModeSmoothingRatio(STREAM_SMOOTHING_RATIO)
                .build(),
        )
    } else {
        null
    }

    private var frameWidth = 0
    private var frameHeight = 0
    private var pixelReadBuffer: ByteBuffer? = null
    private var frameBitmap: Bitmap? = null
    private var maskBitmap: Bitmap? = null
    private var maskPixels: IntArray? = null
    private var maskTexId = 0
    private var maskWidth = 0
    private var maskHeight = 0
    private var placeholderMaskTexId = 0

    // Segmentation runs on ML Kit's own background thread and must never block the GL/frame
    // thread — otherwise playback stalls to inference speed. These fields hand results back
    // across threads; the GL thread only ever reads/uploads latestMask, never blocks on it.
    @Volatile private var segmentationInFlight = false
    @Volatile private var latestMask: SegmentationMask? = null
    private var lastSegmentationAttemptUs: Long? = null

    init {
        glProgram = try {
            GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
        glProgram.setBufferAttribute(
            "aFramePosition",
            GlUtil.createVertexBuffer(NDC_SQUARE),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )

        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(targetColor.hex.toInt(), hsv)
        val clampedOffset = hueOffsetDegrees.coerceIn(
            ColorIsolationEffect.MIN_HUE_OFFSET_DEGREES,
            ColorIsolationEffect.MAX_HUE_OFFSET_DEGREES,
        )
        targetHue = (((hsv[0] + clampedOffset) % 360f + 360f) % 360f) / 360f
        val clampedToleranceDegrees = hueToleranceDegrees.coerceIn(
            ColorIsolationEffect.MIN_HUE_TOLERANCE_DEGREES,
            ColorIsolationEffect.MAX_HUE_TOLERANCE_DEGREES,
        )
        // Achromatic tape (black/white) has no meaningful hue to isolate — leave the frame untouched.
        hueThreshold = if (hsv[1] < 0.2f) 1f else clampedToleranceDegrees / 360f
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        frameWidth = inputWidth
        frameHeight = inputHeight
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        updatePersonMask(inputTexId, presentationTimeUs)
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            glProgram.setIntUniform("uHasMask", if (maskTexId != 0) 1 else 0)
            // uMaskSampler is referenced in the shader unconditionally (the `if (uHasMask == 1)`
            // branch doesn't stop GlProgram from treating it as an active uniform), so it must
            // always be bound to *something* or bindAttributesAndUniforms() throws — even before
            // the first real mask exists.
            glProgram.setSamplerTexIdUniform(
                "uMaskSampler",
                if (maskTexId != 0) maskTexId else ensurePlaceholderMaskTexture(),
                /* texUnitIndex= */ 1,
            )
            glProgram.setFloatUniform("uTargetHue", targetHue)
            glProgram.setFloatUniform("uHueThreshold", hueThreshold)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException.from(e, presentationTimeUs)
        }
    }

    /**
     * Kicks off ML Kit's selfie segmenter asynchronously and, if a previous request has already
     * finished, uploads its mask texture. Never blocks the GL/frame thread on inference — the
     * mask always trails the video by a frame or few rather than freezing playback to wait for it.
     *
     * Throttled to at most once every [SEGMENTATION_INTERVAL_US]: `glReadPixels` is a hard
     * GPU/CPU sync point regardless of how async the ML call is, so doing it every single frame
     * was itself enough to stall playback even with inference off the GL thread.
     */
    private fun updatePersonMask(inputTexId: Int, presentationTimeUs: Long) {
        val activeSegmenter = segmenter ?: return
        if (frameWidth <= 0 || frameHeight <= 0) return

        latestMask?.let { mask ->
            latestMask = null
            try {
                uploadMaskTexture(mask)
            } catch (e: Exception) {
                // Keep whatever mask texture is already uploaded.
            }
        }

        if (segmentationInFlight) return
        val lastAttempt = lastSegmentationAttemptUs
        if (lastAttempt != null && presentationTimeUs - lastAttempt < SEGMENTATION_INTERVAL_US) return
        lastSegmentationAttemptUs = presentationTimeUs
        try {
            val bitmap = readTextureToBitmap(inputTexId, frameWidth, frameHeight)
            segmentationInFlight = true
            activeSegmenter.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { mask ->
                    latestMask = mask
                    segmentationInFlight = false
                }
                .addOnFailureListener {
                    segmentationInFlight = false
                }
        } catch (e: Exception) {
            segmentationInFlight = false
        }
    }

    private fun readTextureToBitmap(texId: Int, width: Int, height: Int): Bitmap {
        val previousFbo = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, previousFbo, 0)

        val readFbo = GlUtil.createFboForTexture(texId)
        try {
            GlUtil.focusFramebufferUsingCurrentContext(readFbo, width, height)

            val buffer = pixelReadBuffer?.takeIf { it.capacity() == width * height * 4 }
                ?: ByteBuffer.allocateDirect(width * height * 4)
                    .order(ByteOrder.nativeOrder())
                    .also { pixelReadBuffer = it }
            buffer.clear()
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
            buffer.rewind()

            val bitmap = frameBitmap?.takeIf { it.width == width && it.height == height }
                ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { frameBitmap = it }
            bitmap.copyPixelsFromBuffer(buffer)
            return bitmap
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, previousFbo[0])
            GlUtil.deleteFbo(readFbo)
        }
    }

    private fun uploadMaskTexture(mask: SegmentationMask) {
        val width = mask.width
        val height = mask.height
        val confidences = mask.buffer.order(ByteOrder.nativeOrder()).asFloatBuffer()

        val pixels = maskPixels?.takeIf { it.size == width * height }
            ?: IntArray(width * height).also { maskPixels = it }
        for (i in 0 until width * height) {
            val gray = (confidences.get(i).coerceIn(0f, 1f) * 255f).toInt()
            pixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }

        val bitmap = maskBitmap?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { maskBitmap = it }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        if (maskTexId != 0 && maskWidth == width && maskHeight == height) {
            GlUtil.setTexture(maskTexId, bitmap)
        } else {
            if (maskTexId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(maskTexId), 0)
            }
            maskTexId = GlUtil.createTexture(bitmap)
            maskWidth = width
            maskHeight = height
        }
    }

    private fun ensurePlaceholderMaskTexture(): Int {
        if (placeholderMaskTexId == 0) {
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            bitmap.setPixel(0, 0, AndroidColor.BLACK)
            placeholderMaskTexId = GlUtil.createTexture(bitmap)
        }
        return placeholderMaskTexId
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException.from(e)
        } finally {
            segmenter?.close()
            if (maskTexId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(maskTexId), 0)
                maskTexId = 0
            }
            if (placeholderMaskTexId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(placeholderMaskTexId), 0)
                placeholderMaskTexId = 0
            }
        }
    }

    companion object {
        // Blends this frame's mask with the previous one to cut down on frame-to-frame flicker.
        private const val STREAM_SMOOTHING_RATIO = 0.8f
        // Only attempt a new person-detection pass this often — each attempt costs a hard
        // GPU/CPU sync (glReadPixels), so running it every frame stalls playback.
        private const val SEGMENTATION_INTERVAL_US = 250_000L

        private val NDC_SQUARE: ImmutableList<FloatArray> = ImmutableList.of(
            floatArrayOf(-1f, -1f, 0f, 1f),
            floatArrayOf(-1f, 1f, 0f, 1f),
            floatArrayOf(1f, 1f, 0f, 1f),
            floatArrayOf(1f, -1f, 0f, 1f),
        )

        private const val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
              gl_Position = aFramePosition;
              vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform sampler2D uMaskSampler;
            uniform int uHasMask;
            uniform float uTargetHue;
            uniform float uHueThreshold;
            varying vec2 vTexSamplingCoord;

            vec3 rgb2hsv(vec3 c) {
              vec4 k = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
              vec4 p = mix(vec4(c.bg, k.wz), vec4(c.gb, k.xy), step(c.b, c.g));
              vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
              float d = q.x - min(q.w, q.y);
              float e = 1.0e-10;
              return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
            }

            void main() {
              vec4 sampled = texture2D(uTexSampler, vTexSamplingCoord);
              vec3 hsv = rgb2hsv(sampled.rgb);
              float hueDiff = abs(hsv.x - uTargetHue);
              hueDiff = min(hueDiff, 1.0 - hueDiff);
              bool isColorMatch = hueDiff < uHueThreshold && hsv.y > 0.28 && hsv.z > 0.15;

              float personWeight = 0.0;
              if (uHasMask == 1) {
                float personConfidence = texture2D(uMaskSampler, vTexSamplingCoord).r;
                // Soft-edged instead of a hard cutoff, so mask edges (hair, fingertips) don't
                // flicker between fully colored and fully gray from one frame to the next.
                personWeight = smoothstep(0.3, 0.6, personConfidence);
              }
              float colorWeight = isColorMatch ? 1.0 : 0.0;
              float weight = max(colorWeight, personWeight);

              float luminance = dot(sampled.rgb, vec3(0.299, 0.587, 0.114));
              vec3 outColor = mix(vec3(luminance), sampled.rgb, weight);
              gl_FragColor = vec4(outColor, sampled.a);
            }
        """
    }
}
