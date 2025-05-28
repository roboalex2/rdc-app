package at.roboalex2.rdc.service

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class PhotoCaptureActivity : ComponentActivity() {
    companion object {
        const val EXTRA_RECIPIENT = "extra_recipient"
    }

    private lateinit var recipient: String
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var imageCapture: ImageCapture
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val outputFiles = mutableListOf<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recipient = intent.getStringExtra(EXTRA_RECIPIENT) ?: run { finish(); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // check runtime permissions
        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.SEND_SMS)
            .filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()

        if (needed.isNotEmpty()) {
            SmsSenderService.sendReply(
                this, recipient,
                "Target app has missing permissions: ${needed.joinToString(", ")}"
            )
            finish()
        } else {
            initPhotoSequence()
        }
    }

    private fun initPhotoSequence() {
        ProcessCameraProvider.getInstance(this)
            .addListener({
                cameraProvider = ProcessCameraProvider.getInstance(this@PhotoCaptureActivity).get()
                imageCapture = ImageCapture.Builder().build()

                captureSequential(CameraSelector.LENS_FACING_FRONT, "front") {
                    captureSequential(CameraSelector.LENS_FACING_BACK, "back") {
                        sendAllAndFinish()
                    }
                }
            }, ContextCompat.getMainExecutor(this))
    }

    private fun captureSequential(lensFacing: Int, tag: String, onDone: () -> Unit) {
        // swap lens
        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(this@PhotoCaptureActivity, selector, imageCapture)

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(cacheDir, "photo_${tag}_$ts.jpg")

        val opts = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(opts, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                runOnUiThread {
                    SmsSenderService.sendReply(
                        this@PhotoCaptureActivity, recipient,
                        "Photo Capture Error '${tag}': ${exc.message}"
                    )
                    finish()
                }
            }

            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                results.savedUri?.let {
                    outputFiles += it
                }
                runOnUiThread { onDone() }
            }
        })
    }

    private fun sendAllAndFinish() {
        outputFiles.forEach { uri ->
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra("address", recipient)
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    "sms_body",
                    "Photo from ${if (uri.path?.contains("front") == true) "front" else "back"} camera"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.android.mms")
            }.also { runOnUiThread { startActivity(it) } }
        }
        runOnUiThread {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}