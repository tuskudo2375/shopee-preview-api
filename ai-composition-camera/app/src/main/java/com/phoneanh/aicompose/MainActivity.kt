package com.phoneanh.aicompose

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.phoneanh.aicompose.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private val tag = "AICompose"
    private lateinit var binding: ActivityMainBinding
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val api = CompositionApi()
    private val requestInFlight = AtomicBoolean(false)
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var aiEnabled = false
    private var lastAiAt = 0L
    private var lastAutoZoomAt = 0L

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else Toast.makeText(this, "Cần quyền Camera để chạy AI Bố Cục", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top + dp(6), view.paddingRight, bars.bottom)
            insets
        }
        Log.i(tag, "app_start version=0.1.2 device=${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL} sdk=${android.os.Build.VERSION.SDK_INT}")
        binding.previewView.implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
        binding.previewView.scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER

        binding.aiButton.alpha = .82f
        binding.aiTip.text = "Bật Bố cục AI để được gợi ý khung đẹp"
        binding.aiButton.setOnClickListener { toggleAi() }
        binding.shutterButton.setOnClickListener { takePhoto() }
        bindZoom(binding.zoom06, .6f)
        bindZoom(binding.zoom1, 1f)
        bindZoom(binding.zoom2, 2f)
        bindZoom(binding.zoom3, 3f)
        updateZoomUi(1f)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun bindZoom(view: TextView, zoom: Float) {
        view.setOnClickListener { setZoom(zoom, haptic = true) }
    }

    private fun updateZoomUi(current: Float) {
        val views = listOf(binding.zoom06 to .6f, binding.zoom1 to 1f, binding.zoom2 to 2f, binding.zoom3 to 3f)
        val nearest = views.minByOrNull { abs(it.second - current) }?.second ?: 1f
        views.forEach { (view, zoom) -> view.isSelected = abs(zoom - nearest) < .05f }
    }

    private fun toggleAi() {
        aiEnabled = !aiEnabled
        Log.i(tag, "ai_toggle enabled=$aiEnabled")
        binding.aiButton.isSelected = aiEnabled
        binding.aiButton.alpha = if (aiEnabled) 1f else .82f
        if (aiEnabled) {
            binding.aiTip.text = "Đang phân tích bố cục…"
            binding.compositionOverlay.setAnalyzing(true)
            lastAiAt = 0L
            binding.aiButton.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            binding.aiTip.text = "Bật Bố cục AI để được gợi ý khung đẹp"
            binding.compositionOverlay.clear()
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            Log.i(tag, "camera_provider_ready")
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(cameraExecutor) { image ->
                try {
                    val now = SystemClock.elapsedRealtime()
                    if (aiEnabled && !requestInFlight.get() && now - lastAiAt >= 2000L) {
                        lastAiAt = now
                        val jpeg = image.toAiJpeg()
                        submitAi(jpeg)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "frame_read_failed", e)
                    runOnUiThread {
                        binding.aiTip.text = "Không đọc được frame camera"
                        binding.compositionOverlay.setError("Không đọc được frame camera")
                    }
                } finally {
                    image.close()
                }
            }
            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis)
                camera?.cameraInfo?.zoomState?.observe(this) { z -> updateZoomUi(z.zoomRatio) }
                camera?.cameraInfo?.zoomState?.value?.let { z ->
                    Log.i(tag, "camera_bound zoom=${z.zoomRatio} min=${z.minZoomRatio} max=${z.maxZoomRatio} linear=${z.linearZoom}")
                }
            } catch (e: Exception) {
                Log.e(tag, "camera_bind_failed", e)
                Toast.makeText(this, "Không mở được camera: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun submitAi(jpeg: ByteArray) {
        if (!requestInFlight.compareAndSet(false, true)) return
        runOnUiThread {
            binding.aiTip.text = "Đang phân tích bố cục…"
            binding.compositionOverlay.setAnalyzing(true)
        }
        val zoom = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
        Log.i(tag, "ai_request bytes=${jpeg.size} zoom=$zoom endpoint=${CompositionApi.ENDPOINT}")
        api.analyze(jpeg, zoom) { result ->
            requestInFlight.set(false)
            runOnUiThread {
                result.onSuccess {
                    Log.i(tag, "ai_success type=${it.compositionType} conf=${it.confidence} crop=${it.composition.x},${it.composition.y},${it.composition.width},${it.composition.height} subject=${it.subject.x},${it.subject.y} move=${it.movement.horizontal},${it.movement.vertical},${it.movement.rotation} zoom=${it.recommendedZoom} instruction=${it.instruction}")
                    binding.aiTip.text = it.instruction
                    binding.compositionOverlay.setResult(it)
                    maybeAutoZoom(it.recommendedZoom)
                }.onFailure {
                    Log.e(tag, "ai_failed ${it.message}", it)
                    val message = it.message ?: "AI tạm thời không khả dụng"
                    binding.aiTip.text = if (message.length > 80) message.take(77) + "…" else message
                    binding.compositionOverlay.setError(message)
                }
            }
        }
    }

    private fun maybeAutoZoom(target: Float) {
        val state = camera?.cameraInfo?.zoomState?.value ?: return
        val clamped = target.coerceIn(state.minZoomRatio, state.maxZoomRatio)
        val now = SystemClock.elapsedRealtime()
        if (abs(clamped - state.zoomRatio) < .10f || now - lastAutoZoomAt < 2800L) return
        lastAutoZoomAt = now
        animateZoom(state.zoomRatio, clamped)
    }

    private fun animateZoom(from: Float, to: Float) {
        val cam = camera ?: return
        Log.i(tag, "zoom_auto from=$from to=$to")
        val steps = 14
        var step = 0
        val runnable = object : Runnable {
            override fun run() {
                step++
                val t = (step / steps.toFloat()).coerceIn(0f, 1f)
                val smooth = t * t * (3f - 2f * t)
                cam.cameraControl.setZoomRatio(from + (to - from) * smooth)
                if (step < steps) binding.root.postDelayed(this, 28L)
            }
        }
        binding.root.post(runnable)
    }

    private fun setZoom(zoom: Float, haptic: Boolean) {
        val state = camera?.cameraInfo?.zoomState?.value ?: return
        val applied = zoom.coerceIn(state.minZoomRatio, state.maxZoomRatio)
        Log.i(tag, "zoom_tap requested=$zoom applied=$applied min=${state.minZoomRatio} max=${state.maxZoomRatio}")
        camera?.cameraControl?.setZoomRatio(applied)
        if (haptic) binding.zoomBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "AICompose_$name")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AIComposition")
        }
        val options = ImageCapture.OutputFileOptions.Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                binding.shutterButton.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                Log.i(tag, "photo_saved uri=${output.savedUri}")
                Toast.makeText(this@MainActivity, "Đã lưu ảnh", Toast.LENGTH_SHORT).show()
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e(tag, "photo_failed", exception)
                Toast.makeText(this@MainActivity, "Chụp thất bại: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
