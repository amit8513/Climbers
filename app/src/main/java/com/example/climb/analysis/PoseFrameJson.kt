package com.example.climb.analysis

import com.example.climb.pose.BodyBoundingBox
import com.example.climb.pose.PoseFrame
import com.example.climb.pose.PoseLandmark
import com.example.climb.pose.PoseLandmarkType
import org.json.JSONArray
import org.json.JSONObject

/** Compact JSON encoding for a whole climb's [PoseFrame] list, for the `poseFramesJson` column. */
fun List<PoseFrame>.toJson(): String {
    val array = JSONArray()
    for (frame in this) {
        val frameObj = JSONObject()
        frameObj.put("t", frame.timestampMs)
        frameObj.put("c", frame.averageConfidence.toDouble())
        frameObj.put("r", frame.isReliable)
        frame.bodyBoundingBox?.let { bb ->
            frameObj.put("bb", JSONObject().apply {
                put("l", bb.left.toDouble())
                put("t", bb.top.toDouble())
                put("r", bb.right.toDouble())
                put("b", bb.bottom.toDouble())
            })
        }
        val landmarksArray = JSONArray()
        for (landmark in frame.landmarks) {
            landmarksArray.put(
                JSONObject().apply {
                    put("ty", landmark.type.name)
                    put("x", landmark.normalizedX.toDouble())
                    put("y", landmark.normalizedY.toDouble())
                    put("z", landmark.normalizedZ.toDouble())
                    put("v", landmark.visibility.toDouble())
                    put("p", landmark.presence.toDouble())
                },
            )
        }
        frameObj.put("lm", landmarksArray)
        array.put(frameObj)
    }
    return array.toString()
}

fun String.toPoseFrames(): List<PoseFrame> {
    if (isBlank()) return emptyList()
    val array = JSONArray(this)
    return (0 until array.length()).map { i ->
        val frameObj = array.getJSONObject(i)
        val bbObj = frameObj.optJSONObject("bb")
        val landmarksArray = frameObj.getJSONArray("lm")
        val landmarks = (0 until landmarksArray.length()).map { j ->
            val landmarkObj = landmarksArray.getJSONObject(j)
            PoseLandmark(
                type = PoseLandmarkType.valueOf(landmarkObj.getString("ty")),
                normalizedX = landmarkObj.getDouble("x").toFloat(),
                normalizedY = landmarkObj.getDouble("y").toFloat(),
                normalizedZ = landmarkObj.getDouble("z").toFloat(),
                visibility = landmarkObj.getDouble("v").toFloat(),
                presence = landmarkObj.getDouble("p").toFloat(),
            )
        }
        PoseFrame(
            timestampMs = frameObj.getLong("t"),
            landmarks = landmarks,
            averageConfidence = frameObj.getDouble("c").toFloat(),
            isReliable = frameObj.getBoolean("r"),
            bodyBoundingBox = bbObj?.let {
                BodyBoundingBox(
                    left = it.getDouble("l").toFloat(),
                    top = it.getDouble("t").toFloat(),
                    right = it.getDouble("r").toFloat(),
                    bottom = it.getDouble("b").toFloat(),
                )
            },
        )
    }
}
