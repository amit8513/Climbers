package com.example.climb.playback

import android.content.Context
import android.graphics.Color as AndroidColor
import android.opengl.GLES11Ext
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.ExternalShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.example.climb.data.RouteColor
import com.google.common.collect.ImmutableList

/**
 * Keeps pixels whose hue matches [targetColor] (shifted by [hueOffsetDegrees]) in color and
 * desaturates everything else — so a climb's tagged route color stands out from the rest of the
 * wall during playback.
 */
@UnstableApi
class ColorIsolationEffect(
    private val targetColor: RouteColor,
    private val hueToleranceDegrees: Float = DEFAULT_HUE_TOLERANCE_DEGREES,
    private val hueOffsetDegrees: Float = 0f,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        ColorIsolationShaderProgram(useHdr, targetColor, hueToleranceDegrees, hueOffsetDegrees)

    companion object {
        const val DEFAULT_HUE_TOLERANCE_DEGREES = 12.6f
        const val MIN_HUE_TOLERANCE_DEGREES = 4f
        const val MAX_HUE_TOLERANCE_DEGREES = 45f
        const val MIN_HUE_OFFSET_DEGREES = -60f
        const val MAX_HUE_OFFSET_DEGREES = 60f
    }
}

/**
 * Implements [ExternalShaderProgram] so Media3 hands us the decoder's texture directly
 * (`samplerExternalOES`) instead of auto-inserting an internal OES->2D conversion stage before us
 * — that automatic conversion stage is the prime suspect for corrupting frames on this device.
 */
@UnstableApi
private class ColorIsolationShaderProgram(
    useHdr: Boolean,
    targetColor: RouteColor,
    hueToleranceDegrees: Float,
    hueOffsetDegrees: Float,
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1), ExternalShaderProgram {

    private val glProgram: GlProgram
    private val targetHue: Float
    private val hueThreshold: Float

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
        glProgram.setFloatsUniform("uTexTransform", GlUtil.create4x4IdentityMatrix())

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

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun setTextureTransformMatrix(textureTransformMatrix: FloatArray) {
        glProgram.setFloatsUniform("uTexTransform", textureTransformMatrix)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform(
                "uTexSampler",
                inputTexId,
                /* texUnitIndex= */ 0,
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            )
            glProgram.setFloatUniform("uTargetHue", targetHue)
            glProgram.setFloatUniform("uHueThreshold", hueThreshold)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException.from(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException.from(e)
        }
    }

    companion object {
        private val NDC_SQUARE: ImmutableList<FloatArray> = ImmutableList.of(
            floatArrayOf(-1f, -1f, 0f, 1f),
            floatArrayOf(-1f, 1f, 0f, 1f),
            floatArrayOf(1f, 1f, 0f, 1f),
            floatArrayOf(1f, -1f, 0f, 1f),
        )

        private const val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            uniform mat4 uTexTransform;
            varying vec2 vTexSamplingCoord;
            void main() {
              gl_Position = aFramePosition;
              vec2 uv = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
              vTexSamplingCoord = (uTexTransform * vec4(uv, 0.0, 1.0)).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexSampler;
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
              bool isMatch = hueDiff < uHueThreshold && hsv.y > 0.28 && hsv.z > 0.15;
              float luminance = dot(sampled.rgb, vec3(0.299, 0.587, 0.114));
              vec3 outColor = isMatch ? sampled.rgb : vec3(luminance);
              gl_FragColor = vec4(outColor, sampled.a);
            }
        """
    }
}
