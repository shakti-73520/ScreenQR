package app.scanner.screenscan

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ScreenCaptureResult {
    data class Success(val bitmap: Bitmap, val capturedAtMs: Long = System.currentTimeMillis()) : ScreenCaptureResult
    data class Failure(val reason: String) : ScreenCaptureResult
}

object ScreenCaptureHolder {
    private val _result = MutableStateFlow<ScreenCaptureResult?>(null)
    val result: StateFlow<ScreenCaptureResult?> = _result.asStateFlow()

    fun publish(capture: ScreenCaptureResult) {
        _result.value = capture
    }

    fun clear() {
        _result.value = null
    }
}
