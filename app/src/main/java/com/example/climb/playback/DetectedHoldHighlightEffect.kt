package com.example.climb.playback

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.google.common.collect.ImmutableList

/**
 * Phase 6 ("Final Highlight/Render") of the route-color-detection redesign: renders real
 * per-object detection results instead of this project's old full-frame per-pixel hue test (its
 * `ColorIsolationEffect` class has been removed — this effect replaces it everywhere). Brightens/
 * emphasizes the ORIGINAL pixel
 * data (texture, chalk, shadow — never a flat solid overlay) wherever [maskBitmap] marks a
 * validated hold, and desaturates everything else, same "isolate vs. desaturate" duality the old
 * effect used, just driven by a real detected mask instead of a live per-pixel hue guess.
 *
 * [maskBitmap] comes from running [com.example.climb.colordetection.RouteColorDetector] once on a
 * single reference frame (see [HoldHighlightPipeline]) and is reused for every frame of the whole
 * clip — this assumes a fixed/static camera during recording, matching this project's "review
 * after recording" delivery mode. If the camera pans, the mask will visibly misalign with the
 * moving footage; that is a known, accepted scope limitation of this phase (live/tracking
 * detection is separately deferred future work), not a bug.
 *
 * [maskBitmap] need not exactly match the input video frame's own pixel dimensions: both texture
 * samplers below are sampled with the same normalized `[0,1]` coordinate, so GL transparently
 * stretches the mask to fit even if the extracted reference frame's decode size differs slightly
 * from the actual video frame size.
 */
@UnstableApi
class DetectedHoldHighlightEffect(private val maskBitmap: Bitmap) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        DetectedHoldHighlightShaderProgram(useHdr, maskBitmap)

    companion object {
        const val DEFAULT_HUE_TOLERANCE_DEGREES = 12.6f
        const val MIN_HUE_TOLERANCE_DEGREES = 4f
        const val MAX_HUE_TOLERANCE_DEGREES = 45f
        const val MIN_HUE_OFFSET_DEGREES = -60f
        const val MAX_HUE_OFFSET_DEGREES = 60f
    }
}

@UnstableApi
private class DetectedHoldHighlightShaderProgram(
    useHdr: Boolean,
    maskBitmap: Bitmap,
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val glProgram: GlProgram
    private val maskTexId: Int

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
        // Uploaded once here (mask is fixed for this effect instance's whole lifetime — one
        // reference-frame detection reused for every drawFrame call), not per frame.
        maskTexId = try {
            GlUtil.createTexture(maskBitmap)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            glProgram.setSamplerTexIdUniform("uMaskSampler", maskTexId, /* texUnitIndex= */ 1)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException.from(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
            GlUtil.deleteTexture(maskTexId)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException.from(e)
        }
    }

    companion object {
        // Two explicit triangles (not a strip) — matches this project's original hue-isolation
        // shader's own precedent (some Mali drivers have had bugs specifically with
        // GL_TRIANGLE_STRIP vertex ordering).
        private val NDC_SQUARE: ImmutableList<FloatArray> = ImmutableList.of(
            floatArrayOf(-1f, -1f, 0f, 1f),
            floatArrayOf(-1f, 1f, 0f, 1f),
            floatArrayOf(1f, 1f, 0f, 1f),
            floatArrayOf(-1f, -1f, 0f, 1f),
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
            varying vec2 vTexSamplingCoord;

            vec3 rgb2hsv(vec3 c) {
              vec4 k = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
              vec4 p = mix(vec4(c.bg, k.wz), vec4(c.gb, k.xy), step(c.b, c.g));
              vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
              float d = q.x - min(q.w, q.y);
              float e = 1.0e-10;
              return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
            }

            vec3 hsv2rgb(vec3 c) {
              vec4 k = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
              vec3 p = abs(fract(c.xxx + k.xyz) * 6.0 - k.www);
              return c.z * mix(k.xxx, clamp(p - k.xxx, 0.0, 1.0), c.y);
            }

            void main() {
              vec4 sampled = texture2D(uTexSampler, vTexSamplingCoord);
              float maskValue = texture2D(uMaskSampler, vTexSamplingCoord).a;

              // Emphasize the ORIGINAL pixel — real texture/chalk/shadow detail stays visible,
              // this is not a flat overlay — by nudging saturation/value up a bit.
              vec3 hsv = rgb2hsv(sampled.rgb);
              hsv.y = clamp(hsv.y * 1.35, 0.0, 1.0);
              hsv.z = clamp(hsv.z * 1.15 + 0.05, 0.0, 1.0);
              vec3 emphasized = hsv2rgb(hsv);

              float luminance = dot(sampled.rgb, vec3(0.299, 0.587, 0.114));
              vec3 desaturated = vec3(luminance);

              vec3 outColor = mix(desaturated, emphasized, maskValue);
              gl_FragColor = vec4(outColor, sampled.a);
            }
        """
    }
}
