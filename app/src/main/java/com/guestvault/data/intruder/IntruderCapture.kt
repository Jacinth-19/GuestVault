package com.guestvault.data.intruder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat
import com.guestvault.domain.policy.IntruderPhoto
import com.guestvault.domain.policy.IntruderPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Takes one photo after a failed owner unlock.
 *
 * Written against `camera2` directly rather than a camera library: the app has
 * no dependency on CameraX, and adding one for a single front-camera still is a
 * lot of bytes for a feature most owners leave off.
 *
 * The design constraint that shapes everything here is that this code runs on
 * the owner-lock screen, at the exact moment a stranger is holding the phone.
 * It therefore fails silently and completely: no camera, no permission, a busy
 * sensor or an OEM that refuses `camera2` all end in a log line, never in a
 * dialog, a crash, or a delay the owner can see. The photo is a bonus; getting
 * back into the phone is the job.
 */
@Singleton
class IntruderCapture @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { if (!exists()) mkdirs() }

    fun cameraAvailable(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)

    fun permissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /** Why the feature cannot run right now, or null. Shown in Settings. */
    fun unavailableReason(): String? =
        IntruderPolicy.unavailableReason(cameraAvailable(), permissionGranted())

    /**
     * Stores one photo and prunes the oldest.
     *
     * Returns the file that was written, or null when nothing could be taken —
     * which is a normal outcome, not an error.
     */
    fun capture(now: Long = System.currentTimeMillis()): File? {
        if (!cameraAvailable() || !permissionGranted()) return null
        val file = File(directory, FILE_PREFIX + STAMP.format(Date(now)) + ".jpg")
        val written = runCatching { takeStill(file) }.getOrDefault(false)
        if (!written) {
            runCatching { file.delete() }
            return null
        }
        prune()
        return file
    }

    /** Everything on disk, newest first. */
    fun photos(): List<IntruderPhoto> = runCatching {
        directory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".jpg") }
            ?.map { IntruderPhoto(it.name, it.lastModified()) }
            ?.sortedByDescending { it.takenAt }
            .orEmpty()
    }.getOrDefault(emptyList())

    /** Deletes the oldest files beyond [IntruderPolicy.MAX_PHOTOS]. */
    fun prune() {
        val doomed = IntruderPolicy.toDelete(photos())
        doomed.forEach { photo -> runCatching { File(directory, photo.name).delete() } }
    }

    /** Deletes every stored photo. Called by the guest-data wipe and from Settings. */
    fun clear() {
        runCatching { directory.listFiles()?.forEach { it.delete() } }
    }

    // ------------------------------------------------------------------ camera

    /**
     * Opens the front camera, takes one JPEG and closes everything again.
     *
     * Blocking, and always called from a worker thread. The [latch] has a hard
     * timeout because a wedged camera driver must not leave a thread parked
     * forever on a device that is about to be handed back to its owner.
     */
    private fun takeStill(target: File): Boolean {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        val cameraId = frontCameraId(manager) ?: return false

        val thread = HandlerThread("intruder-camera").apply { start() }
        val handler = Handler(thread.looper)
        val latch = CountDownLatch(1)
        var reader: ImageReader? = null
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var preview: SurfaceTexture? = null
        var wrote = false

        try {
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val stream = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val size = stream?.getOutputSizes(ImageFormat.JPEG)
                ?.filter { it.width <= CAPTURE_WIDTH }
                ?.maxByOrNull { it.width * it.height }
                ?: stream?.getOutputSizes(ImageFormat.JPEG)?.lastOrNull()
                ?: return false

            val imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
            reader = imageReader

            // A preview surface is still required by most HALs even when the
            // frame is never shown: the sensor will not stream to a reader on
            // its own. A 1×1 texture costs nothing and is never drawn.
            val texture = SurfaceTexture(0)
            texture.setDefaultBufferSize(1, 1)
            preview = texture
            val junk = Surface(texture)

            imageReader.setOnImageAvailableListener({ source ->
                runCatching {
                    source.acquireLatestImage()?.use { image ->
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        target.writeBytes(bytes)
                        wrote = bytes.isNotEmpty()
                    }
                }.onFailure { Log.w(TAG, "Could not read the captured frame", it) }
                latch.countDown()
            }, handler)

            val opened = CountDownLatch(1)
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    device = camera
                    opened.countDown()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    runCatching { camera.close() }
                    opened.countDown()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.w(TAG, "Camera error $error")
                    runCatching { camera.close() }
                    opened.countDown()
                }
            }, handler)

            if (!opened.await(OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) return false
            val camera = device ?: return false
            val created = CountDownLatch(1)

            camera.createCaptureSession(
                listOf(imageReader.surface, junk),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(configured: CameraCaptureSession) {
                        session = configured
                        runCatching {
                            val request = camera.createCaptureRequest(
                                CameraDevice.TEMPLATE_STILL_CAPTURE
                            ).apply {
                                addTarget(imageReader.surface)
                                set(
                                    CaptureRequest.JPEG_ORIENTATION,
                                    jpegOrientation(characteristics)
                                )
                                set(
                                    CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                )
                            }.build()
                            configured.capture(request, null, handler)
                        }.onFailure { Log.w(TAG, "Capture request failed", it) }
                        created.countDown()
                    }

                    override fun onConfigureFailed(configured: CameraCaptureSession) {
                        Log.w(TAG, "Capture session could not be configured")
                        created.countDown()
                    }
                },
                handler
            )

            if (!created.await(OPEN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) return false
            latch.await(CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            return wrote
        } catch (t: Throwable) {
            // CameraAccessException, SecurityException, a vendor HAL that
            // disagrees about surface formats — all the same to the caller.
            Log.w(TAG, "Could not take an intruder photo", t)
            return false
        } finally {
            runCatching { session?.close() }
            runCatching { device?.close() }
            runCatching { reader?.close() }
            runCatching { preview?.release() }
            thread.quitSafely()
        }
    }

    private fun frontCameraId(manager: CameraManager): String? = try {
        manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        } ?: manager.cameraIdList.firstOrNull()
    } catch (e: CameraAccessException) {
        Log.w(TAG, "Camera list unavailable", e)
        null
    }

    /**
     * Rotation that makes the stored image upright.
     *
     * The sensor is mounted at a fixed angle and the phone can be held any way
     * up, so the two are combined; the formula is the one the platform
     * documentation gives for a front-facing camera.
     */
    private fun jpegOrientation(characteristics: CameraCharacteristics): Int {
        val sensor = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val rotation = displayRotationDegrees()
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        return if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            (sensor + rotation) % 360
        } else {
            (sensor - rotation + 360) % 360
        }
    }

    @Suppress("DEPRECATION")
    private fun displayRotationDegrees(): Int {
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation ?: Surface.ROTATION_0
        } else {
            runCatching {
                (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                    .defaultDisplay.rotation
            }.getOrDefault(Surface.ROTATION_0)
        }
        return when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    private companion object {
        const val TAG = "IntruderCapture"
        const val DIRECTORY = "intruder"
        const val FILE_PREFIX = "intruder-"
        const val CAPTURE_WIDTH = 640
        const val OPEN_TIMEOUT_MS = 2_500L
        const val CAPTURE_TIMEOUT_MS = 4_000L
        val STAMP = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
}
