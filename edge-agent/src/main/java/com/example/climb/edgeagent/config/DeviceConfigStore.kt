package com.example.climb.edgeagent.config

import com.example.climb.edge.EdgeDeviceIdentity
import java.io.File

interface DeviceConfigStore {
    fun load(): EdgeDeviceIdentity?
    fun save(identity: EdgeDeviceIdentity)
}

/**
 * Line-based `key=value` persistence — deliberately not JSON, since Android's built-in
 * `org.json` classes are stubs on a plain unit-test classpath (see :app's build.gradle.kts
 * comment on the same issue) and this module has no other JSON dependency. Takes a plain
 * [File] rather than deriving one from a `Context` internally, so it's testable with a temp
 * directory and no Android dependency at all.
 */
class FileDeviceConfigStore(private val file: File) : DeviceConfigStore {

    override fun load(): EdgeDeviceIdentity? {
        if (!file.exists()) return null
        val values = file.readLines()
            .mapNotNull { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()
        val organizationId = values["organizationId"] ?: return null
        val wallId = values["wallId"] ?: return null
        val cameraDeviceId = values["cameraDeviceId"] ?: return null
        return runCatching { EdgeDeviceIdentity(organizationId, wallId, cameraDeviceId) }.getOrNull()
    }

    override fun save(identity: EdgeDeviceIdentity) {
        file.parentFile?.mkdirs()
        file.writeText(
            "organizationId=${identity.organizationId}\n" +
                "wallId=${identity.wallId}\n" +
                "cameraDeviceId=${identity.cameraDeviceId}\n",
        )
    }
}
