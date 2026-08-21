package com.example.climb.validation

import com.example.climb.analysis.contact.ContactLandmarkType
import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmark
import com.example.climb.pose.PoseLandmarkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactPoseFrameAdapterTest {

    @Test
    fun `maps each relevant landmark to its identically-named ContactLandmarkType`() {
        val frame = PoseFrame(
            timestampMs = 500L,
            landmarks = listOf(
                PoseLandmark(PoseLandmarkType.LEFT_WRIST, 0.1f, 0.2f, 0f, 0.9f, 0.8f),
                PoseLandmark(PoseLandmarkType.RIGHT_FOOT_INDEX, 0.3f, 0.4f, 0f, 0.7f, 0.6f),
            ),
            averageConfidence = 0.8f,
            isReliable = true,
            bodyBoundingBox = null,
        )

        val contactFrame = frame.toContactPoseFrame()

        assertEquals(500L, contactFrame.timestampMs)
        val wrist = contactFrame.landmark(ContactLandmarkType.LEFT_WRIST)
        assertEquals(0.1f, wrist?.position?.x)
        assertEquals(0.2f, wrist?.position?.y)
        val footIndex = contactFrame.landmark(ContactLandmarkType.RIGHT_FOOT_INDEX)
        assertEquals(0.3f, footIndex?.position?.x)
    }

    @Test
    fun `confidence is the weaker of visibility and presence`() {
        val frame = PoseFrame(
            timestampMs = 0L,
            landmarks = listOf(
                PoseLandmark(PoseLandmarkType.LEFT_WRIST, 0.1f, 0.2f, 0f, visibility = 0.9f, presence = 0.3f),
                PoseLandmark(PoseLandmarkType.RIGHT_WRIST, 0.1f, 0.2f, 0f, visibility = 0.2f, presence = 0.95f),
            ),
            averageConfidence = 0.5f,
            isReliable = true,
            bodyBoundingBox = null,
        )

        val contactFrame = frame.toContactPoseFrame()

        assertEquals(0.3f, contactFrame.landmark(ContactLandmarkType.LEFT_WRIST)?.confidence)
        assertEquals(0.2f, contactFrame.landmark(ContactLandmarkType.RIGHT_WRIST)?.confidence)
    }

    @Test
    fun `a landmark absent from the source PoseFrame is absent from the ContactPoseFrame too`() {
        val frame = PoseFrame(
            timestampMs = 0L,
            landmarks = listOf(PoseLandmark(PoseLandmarkType.LEFT_WRIST, 0.1f, 0.2f, 0f, 0.9f, 0.9f)),
            averageConfidence = 0.9f,
            isReliable = true,
            bodyBoundingBox = null,
        )

        val contactFrame = frame.toContactPoseFrame()

        assertNull(contactFrame.landmark(ContactLandmarkType.LEFT_ANKLE))
        assertEquals(1, contactFrame.landmarks.size)
    }

    @Test
    fun `an entirely unreliable PoseFrame with no landmarks converts to an entirely empty ContactPoseFrame`() {
        val frame = PoseFrame(
            timestampMs = 100L,
            landmarks = emptyList(),
            averageConfidence = 0f,
            isReliable = false,
            bodyBoundingBox = null,
        )

        val contactFrame = frame.toContactPoseFrame()

        assertTrue(contactFrame.landmarks.isEmpty())
        assertEquals(100L, contactFrame.timestampMs)
    }
}
