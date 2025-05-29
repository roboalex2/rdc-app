package at.roboalex2.rdc.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.sqrt

class PhotoCaptureActivity : ComponentActivity() {
    companion object {
        const val EXTRA_RECIPIENT = "extra_recipient"
    }

    private lateinit var recipient: String
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var imageCapture: ImageCapture
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val outputFiles = mutableListOf<File>()
    private val previewBitmap = mutableStateOf<Bitmap?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recipient = intent.getStringExtra(EXTRA_RECIPIENT) ?: run { finish(); return }
        Log.i(this.javaClass.name, "Starting PhotoCaptureActivity for $recipient")
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

        enableEdgeToEdge()
        setContent {
            PhotoCaptureUI(previewBitmap)
        }
    }

    @Composable
    fun PhotoCaptureUI(previewBitmap: State<Bitmap?>) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color.LightGray)) {
            previewBitmap.value?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Captured Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } ?: run {
                Text(
                    "Capturing photo...",
                    style = TextStyle(fontSize = 31.sp),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
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
                outputFiles += file
                runOnUiThread { onDone() }
            }
        })
    }

    private fun sendAllAndFinish() {
        lifecycleScope.launch {
            for (file in outputFiles) {
                try {
                    val resized = try {
                        resizeImageToFitMmsLimit(this@PhotoCaptureActivity, file).apply {
                            deleteOnExit()
                        }
                    } catch (e: Exception) {
                        Log.w("PhotoCapture", "Resize failed, using original", e)
                        file
                    }
                    previewBitmap.value = BitmapFactory.decodeFile(resized.absolutePath)
                    val url = withContext(Dispatchers.IO) {
                        ImageUploadService.uploadToImgur(this@PhotoCaptureActivity, resized)
                    }

                    SmsSenderService.sendReply(
                        this@PhotoCaptureActivity,
                        recipient,
                        "📸 $url"
                    )
                    file.delete()
                    resized.delete()
                } catch (e: Exception) {
                    Log.e("PhotoCapture", "Image upload failed", e)
                    SmsSenderService.sendReply(
                        this@PhotoCaptureActivity,
                        recipient,
                        "Upload failed (${file.name}): ${e.message?.take(160) ?: "Unknown error"}"
                    )
                } finally {
                    file.delete()
                }
            }
            finish()
        }
    }

    suspend fun resizeImageToFitMmsLimit(
        context: Context,
        inputFile: File,
        maxBytes: Int = 300_000
    ): File = withContext(Dispatchers.IO) {
        var bitmap = BitmapFactory.decodeFile(inputFile.absolutePath)
            ?: throw IllegalArgumentException("Cannot decode image file: $inputFile")

        var quality = 90
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)

        while (stream.size() > maxBytes && quality > 10) {
            stream.reset()
            quality -= 10
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        }

        if (stream.size() > maxBytes) {
            val ratio = sqrt(maxBytes.toDouble() / stream.size())
            val newW = (bitmap.width * ratio).toInt()
            val newH = (bitmap.height * ratio).toInt()
            bitmap = Bitmap.createScaledBitmap(bitmap, newW, newH, true)

            quality = 90
            stream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            while (stream.size() > maxBytes && quality > 10) {
                stream.reset()
                quality -= 10
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            }
        }

        val outFile = File(context.cacheDir, "resized_${inputFile.name}")
        FileOutputStream(outFile).use { fos ->
            fos.write(stream.toByteArray())
        }

        stream.close()
        return@withContext outFile
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}