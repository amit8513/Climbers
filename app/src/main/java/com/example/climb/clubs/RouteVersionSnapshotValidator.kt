package com.example.climb.clubs

/** A newly-registered fixed-camera route (i.e. one with a non-null wallId — it went through real
 * wall-camera route registration) must snapshot every field a route-version needs to be usable by
 * later attribution/verification phases. A legacy, metadata-only route (wallId == null) is exempt
 * — it predates the wall concept entirely and staying incomplete is its normal, valid state, not
 * an error. This validator draws that exact line: "wallId present" is what triggers the complete-
 * snapshot requirement, nothing else. */
object RouteVersionSnapshotValidator {

    /** Every field a wall-camera route version must have populated once it's actually usable for
     * attribution, beyond the plain-metadata fields every RouteVersionEntity already always has
     * (id/organizationId/routeId/setterUserId/versionNumber/createdAt).
     *
     * [RouteVersionEntity.retiredAt] is deliberately NOT required: a route can legitimately be
     * retired with or without ever having been fully registered, and requiring it would wrongly
     * demand every active route claim to be "retired". */
    fun validate(routeVersion: RouteVersionEntity): RouteVersionSnapshotValidationResult {
        if (routeVersion.wallId == null) {
            // Legacy/metadata-only route — predates the wall concept, exempt from the complete-
            // snapshot requirement regardless of which other fields happen to be null.
            return RouteVersionSnapshotValidationResult(isValid = true, missingFields = emptyList())
        }

        val missingFields = mutableListOf<String>()
        if (routeVersion.venueId == null) missingFields += "venueId"
        if (routeVersion.zoneId == null) missingFields += "zoneId"
        if (routeVersion.colorHex == null) missingFields += "colorHex"
        if (routeVersion.grade == null) missingFields += "grade"
        if (routeVersion.gradeSystem == null) missingFields += "gradeSystem"
        if (routeVersion.publicNumberOrName == null) missingFields += "publicNumberOrName"
        if (routeVersion.setAt == null) missingFields += "setAt"
        if (routeVersion.wallCalibrationId == null) missingFields += "wallCalibrationId"
        if (routeVersion.visionProfileId == null) missingFields += "visionProfileId"
        if (routeVersion.startPolicy == null) missingFields += "startPolicy"
        if (routeVersion.finishPolicy == null) missingFields += "finishPolicy"

        return RouteVersionSnapshotValidationResult(
            isValid = missingFields.isEmpty(),
            missingFields = missingFields,
        )
    }
}

data class RouteVersionSnapshotValidationResult(val isValid: Boolean, val missingFields: List<String>)
