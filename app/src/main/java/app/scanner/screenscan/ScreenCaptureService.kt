package app.scanner.screenscan

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import app.scanner.R
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private val finished = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_screen_qr)
            .setContentTitle(getString(R.string.screen_capture_notif_title))
            .setContentText(getString(R.string.screen_capture_notif_body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, notification, fgsType)

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_RESULT_DATA, Intent::class.java) }

        if (resultCode != Activity.RESULT_OK || data == null) {
            failAndStop("Screen capture permission was not granted.")
            return START_NOT_STICKY
        }

        val thread = HandlerThread("PrismScreenCapture").apply { start() }
        handlerThread = thread
        val handler = Handler(thread.looper)
        workerHandler = handler

        // Brief delay allows consent dialog & invisible overlay window to settle cleanly
        handler.postDelayed({
            startCapture(resultCode, data, handler)
        }, 280L)

        // Timeout safety guard in case virtual display produces no frame
        handler.postDelayed({
            if (!finished.get()) {
                failAndStop("Timed out capturing screen content.")
            }
        }, 3500L)

        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent, handler: Handler) {
        try {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mgr.getMediaProjection(resultCode, data)
            if (projection == null) {
                failAndStop("Unable to obtain MediaProjection session.")
                return
            }
            mediaProjection = projection

            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    if (!finished.get()) {
                        failAndStop("Screen capture session stopped.")
                    } else {
                        releaseProjectionResources()
                    }
                }
            }, handler)

            val (width, height, densityDpi) = resolveDisplaySize()
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            reader.setOnImageAvailableListener({ r ->
                if (finished.get()) return@setOnImageAvailableListener
                val image = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                try {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val tempBitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    tempBitmap.copyPixelsFromBuffer(buffer)
                    val cropped = if (tempBitmap.width != width || tempBitmap.height != height) {
                        Bitmap.createBitmap(tempBitmap, 0, 0, width, height).also {
                            tempBitmap.recycle()
                        }
                    } else {
                        tempBitmap
                    }
                    if (finished.compareAndSet(false, true)) {
                        ScreenCaptureHolder.publish(ScreenCaptureResult.Success(cropped))
                        cleanupAndStop()
                    } else {
                        cropped.recycle()
                    }
                } catch (e: Exception) {
                    failAndStop(e.localizedMessage ?: "Failed reading screen image.")
                } finally {
                    image.close()
                }
            }, handler)

            virtualDisplay = projection.createVirtualDisplay(
                "PrismQRScreenCapture",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            )
        } catch (e: Exception) {
            failAndStop(e.localizedMessage ?: "Screen capture failed.")
        }
    }

    private fun resolveDisplaySize(): Triple<Int, Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            val density = resources.configuration.densityDpi
            Triple(bounds.width().coerceAtLeast(1), bounds.height().coerceAtLeast(1), density)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            Triple(metrics.widthPixels.coerceAtLeast(1), metrics.heightPixels.coerceAtLeast(1), metrics.densityDpi)
        }
    }

    private fun failAndStop(reason: String) {
        if (finished.compareAndSet(false, true)) {
            ScreenCaptureHolder.publish(ScreenCaptureResult.Failure(reason))
            cleanupAndStop()
        }
    }

    private fun cleanupAndStop() {
        releaseProjectionResources()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseProjectionResources() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        runCatching { handlerThread?.quitSafely() }
        handlerThread = null
        workerHandler = null
    }

    override fun onDestroy() {
        releaseProjectionResources()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.screen_capture_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.screen_capture_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "screen_qr_capture"
        const val NOTIF_ID = 7041
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            context.startForegroundService(intent)
        }
    }
}
