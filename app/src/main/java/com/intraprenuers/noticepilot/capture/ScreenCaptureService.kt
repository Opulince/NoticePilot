package com.intraprenuers.noticepilot.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import com.intraprenuers.noticepilot.MainActivity
import com.intraprenuers.noticepilot.R
import com.intraprenuers.noticepilot.io.CacheFiles
import java.io.File
import java.io.FileOutputStream

/**
 * Captures ONE frame of the screen with MediaProjection, saves it as a JPEG in cache and hands it to
 * MainActivity via [CaptureHandoff]. Runs as a foreground service of type mediaProjection, which
 * Android 14+ requires before getMediaProjection() is called. Every consent token is used exactly once:
 * one projection, one virtual display, then everything is stopped.
 *
 * Frame timing: we wait for real frames from ImageReader (never a blind sleep), keep only the newest,
 * and take it once [ScreenFrames.SETTLE_MS] has passed. A static screen produces no new frames, so the
 * newest frame IS the current screen. No frame by [ScreenFrames.TIMEOUT_MS] -> clean failure.
 */
class ScreenCaptureService : Service() {

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var latest: Image? = null
    private var startedAt = 0L
    private var finished = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (projection != null || finished) return START_NOT_STICKY // one session per service run
        createChannels()
        val notification = Notification.Builder(this, CHANNEL_CAPTURE)
            .setSmallIcon(R.drawable.ic_qs_scan)
            .setContentTitle("NoticePilot")
            .setContentText("Capturing your screen…")
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            finish(null, "Screen capture couldn't start on this phone (${e.javaClass.simpleName}).")
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            finish(null, "Screen capture wasn't allowed.")
            return START_NOT_STICKY
        }
        try {
            begin(resultCode, data)
        } catch (e: Exception) {
            finish(null, "Screen capture couldn't start (${e.javaClass.simpleName}).")
        }
        return START_NOT_STICKY
    }

    private fun begin(resultCode: Int, data: Intent) {
        val manager = getSystemService(MediaProjectionManager::class.java) ?: throw IllegalStateException("no MediaProjectionManager")
        val p = manager.getMediaProjection(resultCode, data) ?: throw IllegalStateException("getMediaProjection returned null")
        projection = p
        val t = HandlerThread("NoticePilotCapture").also { it.start() }
        thread = t
        val h = Handler(t.looper)
        handler = h

        // Must be registered before createVirtualDisplay (Android 14+ enforces this).
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                h.post { if (!finished) finish(null, "Screen capture was stopped.") }
            }
        }, h)

        val metrics = screenMetrics()
        val r = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        reader = r
        r.setOnImageAvailableListener({ onFrame(it) }, h)
        startedAt = SystemClock.uptimeMillis()
        display = p.createVirtualDisplay(
            "NoticePilotCapture",
            metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, h
        )
        h.postDelayed({ tryCapture() }, ScreenFrames.SETTLE_MS)
        h.postDelayed({ if (!finished) finish(null, "No screen frame arrived in time. Please try again.") }, ScreenFrames.TIMEOUT_MS)
    }

    /** Keep only the newest frame; capture it straight away if the settle time has passed. */
    private fun onFrame(r: ImageReader) {
        if (finished) return
        val img = try { r.acquireLatestImage() } catch (e: Exception) { null } ?: return
        latest?.close()
        latest = img
        if (SystemClock.uptimeMillis() - startedAt >= ScreenFrames.SETTLE_MS) tryCapture()
    }

    private fun tryCapture() {
        if (finished) return
        if (SystemClock.uptimeMillis() - startedAt < ScreenFrames.SETTLE_MS) return
        val img = latest ?: return // no frame yet: onFrame() will call us when one arrives
        latest = null
        val file = try {
            save(img)
        } catch (e: Exception) {
            finish(null, "Couldn't save the screen capture.")
            return
        } catch (e: OutOfMemoryError) {
            finish(null, "Not enough memory to save the screen capture.")
            return
        } finally {
            img.close()
        }
        finish(file, null)
    }

    private fun save(img: Image): File {
        val plane = img.planes[0]
        val width = img.width
        val height = img.height
        val padded = ScreenFrames.bufferWidth(width, plane.rowStride, plane.pixelStride)
        val raw = Bitmap.createBitmap(padded, height, Bitmap.Config.ARGB_8888)
        try {
            raw.copyPixelsFromBuffer(plane.buffer)
            val cropped = if (padded == width) raw else Bitmap.createBitmap(raw, 0, 0, width, height)
            try {
                val file = CacheFiles.newFile(this, "screen", "jpg")
                FileOutputStream(file).use { out -> cropped.compress(Bitmap.CompressFormat.JPEG, 92, out) }
                return file
            } finally {
                if (cropped !== raw) cropped.recycle()
            }
        } finally {
            raw.recycle()
        }
    }

    /** Stops everything exactly once, hands the outcome to the app and tries to bring it forward. */
    private fun finish(file: File?, error: String?) {
        if (finished) return
        finished = true
        try { latest?.close() } catch (e: Exception) { }
        latest = null
        try { display?.release() } catch (e: Exception) { }
        display = null
        try { reader?.close() } catch (e: Exception) { }
        reader = null
        try { projection?.stop() } catch (e: Exception) { }
        projection = null
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()

        CaptureHandoff.deliver(
            if (file != null) CaptureHandoff.Outcome.Captured(file)
            else CaptureHandoff.Outcome.Failed(error ?: "Screen capture failed.")
        )
        CaptureHandoff.inProgress = false

        val open = Intent(this, MainActivity::class.java)
            .setAction(CaptureActions.ACTION_CAPTURE_DONE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // May be blocked by Android's background-activity-launch rules; the notification and the
        // onResume pickup in MainActivity cover that case.
        try { startActivity(open) } catch (e: Exception) { }
        if (file != null) notifyDone(open)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyDone(open: Intent) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (!nm.areNotificationsEnabled()) return
        val pi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_qs_scan)
            .setContentTitle("Screen captured")
            .setContentText("Tap to read the notice in NoticePilot.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { nm.notify(DONE_NOTIFICATION_ID, n) } catch (e: SecurityException) { }
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL_CAPTURE, "Screen capture", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Capture ready", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun screenMetrics(): DisplayMetrics {
        val dm = DisplayMetrics()
        val wm = getSystemService(WindowManager::class.java)
        if (wm == null) {
            val fallback = resources.displayMetrics
            dm.widthPixels = fallback.widthPixels
            dm.heightPixels = fallback.heightPixels
            dm.densityDpi = fallback.densityDpi
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            dm.widthPixels = bounds.width()
            dm.heightPixels = bounds.height()
            dm.densityDpi = resources.displayMetrics.densityDpi
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
        }
        return dm
    }

    override fun onDestroy() {
        if (!finished) finish(null, "Screen capture ended.")
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "result_data"
        private const val CHANNEL_CAPTURE = "screen_capture"
        private const val CHANNEL_DONE = "capture_done"
        private const val NOTIFICATION_ID = 4101
        private const val DONE_NOTIFICATION_ID = 4102

        /** Call from the foreground activity right after the user granted capture consent. */
        fun start(context: Context, resultCode: Int, data: Intent) {
            CaptureHandoff.inProgress = true
            val intent = Intent(context, ScreenCaptureService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            context.startForegroundService(intent)
        }
    }
}
