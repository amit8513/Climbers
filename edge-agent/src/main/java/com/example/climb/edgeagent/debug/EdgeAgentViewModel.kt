package com.example.climb.edgeagent.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.climb.edge.DeviceHeartbeat
import com.example.climb.edge.EdgeDeviceIdentity
import com.example.climb.edge.HeartbeatReporter
import com.example.climb.edge.HeartbeatStatus
import com.example.climb.edge.CapturedFrame
import com.example.climb.edge.DeviceRegistry
import com.example.climb.edge.ReferenceFrameUploader
import com.example.climb.edgeagent.camera.CameraSourceAdapter
import com.example.climb.edgeagent.config.DeviceConfigStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EdgeAgentUiState(
    val organizationId: String = "",
    val wallId: String = "",
    val cameraDeviceId: String = "",
    val identitySaved: Boolean = false,
    val lastCapturedFrame: CapturedFrame? = null,
    val lastUploadReference: String? = null,
    val lastError: String? = null,
    val heartbeatStatusText: String = "not sent yet",
)

class EdgeAgentViewModel(
    private val configStore: DeviceConfigStore,
    private val deviceRegistry: DeviceRegistry,
    private val heartbeatReporter: HeartbeatReporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EdgeAgentUiState())
    val uiState: StateFlow<EdgeAgentUiState> = _uiState.asStateFlow()

    init {
        configStore.load()?.let { identity ->
            _uiState.update {
                it.copy(
                    organizationId = identity.organizationId,
                    wallId = identity.wallId,
                    cameraDeviceId = identity.cameraDeviceId,
                    identitySaved = true,
                )
            }
        }
    }

    fun onOrganizationIdChanged(value: String) = _uiState.update { it.copy(organizationId = value, identitySaved = false) }
    fun onWallIdChanged(value: String) = _uiState.update { it.copy(wallId = value, identitySaved = false) }
    fun onCameraDeviceIdChanged(value: String) = _uiState.update { it.copy(cameraDeviceId = value, identitySaved = false) }

    fun currentIdentityOrNull(): EdgeDeviceIdentity? {
        val s = _uiState.value
        return runCatching { EdgeDeviceIdentity(s.organizationId, s.wallId, s.cameraDeviceId) }.getOrNull()
    }

    fun saveIdentity() {
        val identity = currentIdentityOrNull()
        if (identity == null) {
            _uiState.update { it.copy(lastError = "organizationId/wallId/cameraDeviceId must all be non-blank") }
            return
        }
        configStore.save(identity)
        _uiState.update { it.copy(identitySaved = true, lastError = null) }
        viewModelScope.launch { deviceRegistry.registerDevice(identity) }
    }

    fun captureReferenceFrame(adapter: CameraSourceAdapter, uploader: ReferenceFrameUploader) {
        viewModelScope.launch {
            try {
                val frame = adapter.captureStillReferenceFrame()
                val uploadResult = uploader.upload(frame)
                _uiState.update {
                    it.copy(
                        lastCapturedFrame = frame,
                        lastUploadReference = uploadResult.remoteReference,
                        lastError = if (!uploadResult.success) uploadResult.errorMessage else null,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(lastError = e.message ?: "capture failed") }
            }
        }
    }

    fun sendHeartbeat() {
        val identity = currentIdentityOrNull() ?: run {
            _uiState.update { it.copy(lastError = "save a device identity before sending a heartbeat") }
            return
        }
        viewModelScope.launch {
            val heartbeat = DeviceHeartbeat(
                cameraDeviceId = identity.cameraDeviceId,
                status = HeartbeatStatus.ONLINE,
                timestampEpochMs = System.currentTimeMillis(),
                firmwareOrAppVersion = APP_VERSION,
            )
            heartbeatReporter.reportHeartbeat(heartbeat)
            _uiState.update { it.copy(heartbeatStatusText = "sent ${heartbeat.status} at ${heartbeat.timestampEpochMs}") }
        }
    }

    companion object {
        const val APP_VERSION = "0.1.0-phase1.5a"

        fun factory(
            configStore: DeviceConfigStore,
            deviceRegistry: DeviceRegistry,
            heartbeatReporter: HeartbeatReporter,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EdgeAgentViewModel(configStore, deviceRegistry, heartbeatReporter) as T
        }
    }
}
