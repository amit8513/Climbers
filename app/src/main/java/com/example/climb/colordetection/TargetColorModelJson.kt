package com.example.climb.colordetection

import com.example.climb.data.RouteColor
import org.json.JSONObject

/** Persists a successful calibration ([DetectionBonusState.Active]'s [TargetColorModel]) so
 * reopening a climb restores it instead of requiring the user to tap-to-calibrate every time —
 * see `ClimbEntity.calibratedColorModelJson`. */
fun TargetColorModel.toJson(): String = JSONObject().apply {
    put("selectedColor", selectedColor.name)
    put("labL", labCenter.l)
    put("labA", labCenter.a)
    put("labB", labCenter.b)
    put("hsvH", hsvCenter.h.toDouble())
    put("hsvS", hsvCenter.s.toDouble())
    put("hsvV", hsvCenter.v.toDouble())
    put("hueToleranceDegrees", hueToleranceDegrees.toDouble())
    put("deltaEThreshold", deltaEThreshold)
    put("saturationRangeStart", saturationRange.start.toDouble())
    put("saturationRangeEnd", saturationRange.endInclusive.toDouble())
    put("luminanceTolerance", luminanceTolerance.toDouble())
    put("calibrationSource", calibrationSource.name)
}.toString()

/** A corrupt or shape-mismatched stored value (e.g. from a future format change) falls back to
 * "no saved calibration" rather than crashing reopening the climb. */
fun String.toTargetColorModel(): TargetColorModel? {
    if (isBlank()) return null
    return runCatching {
        val o = JSONObject(this)
        TargetColorModel(
            selectedColor = RouteColor.valueOf(o.getString("selectedColor")),
            labCenter = LabColor(o.getDouble("labL"), o.getDouble("labA"), o.getDouble("labB")),
            hsvCenter = HsvColor(o.getDouble("hsvH").toFloat(), o.getDouble("hsvS").toFloat(), o.getDouble("hsvV").toFloat()),
            hueToleranceDegrees = o.getDouble("hueToleranceDegrees").toFloat(),
            deltaEThreshold = o.getDouble("deltaEThreshold"),
            saturationRange = o.getDouble("saturationRangeStart").toFloat()..o.getDouble("saturationRangeEnd").toFloat(),
            luminanceTolerance = o.getDouble("luminanceTolerance").toFloat(),
            calibrationSource = ColorCalibrationSource.valueOf(o.getString("calibrationSource")),
        )
    }.getOrNull()
}
