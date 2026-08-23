package com.titus.aicompose

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.IOException

class CompositionWebBridge(
    private val activity: Activity,
    parent: ViewGroup
) {
    private val tag = "AICompose"
    private val main = Handler(Looper.getMainLooper())
    private val webView = WebView(activity)
    private var ready = false
    private var pending: Pending? = null
    private var timeoutRunnable: Runnable? = null

    private data class Pending(
        val jpeg: ByteArray,
        val zoom: Float,
        val callback: (Result<CompositionResult>) -> Unit
    )

    init {
        configure(parent)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(parent: ViewGroup) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.safeBrowsingEnabled = true
        webView.isClickable = false
        webView.isFocusable = false
        webView.alpha = 0f
        webView.layoutParams = ViewGroup.LayoutParams(1, 1)
        webView.addJavascriptInterface(NativeCallbacks(), "TitusNative")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                Log.i(tag, "ai_web_page_finished url=$url")
                view.evaluateJavascript(
                    "if(window.titusAnalyze&&window.TitusNative){window.TitusNative.onBridgeReady();}",
                    null
                )
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    Log.e(tag, "ai_web_load_failed code=${error.errorCode} desc=${error.description}")
                    failPending("Không tải được cầu nối AI: ${error.description}")
                }
            }
        }
        parent.addView(webView)
        Log.i(tag, "ai_web_bridge_load url=$BRIDGE_URL")
        webView.loadUrl(BRIDGE_URL)
    }

    fun analyze(
        jpeg: ByteArray,
        currentZoom: Float,
        callback: (Result<CompositionResult>) -> Unit
    ) {
        main.post {
            if (pending != null) {
                callback(Result.failure(IOException("AI đang xử lý frame trước")))
                return@post
            }
            pending = Pending(jpeg, currentZoom, callback)
            if (ready) {
                sendPending()
            } else {
                Log.i(tag, "ai_web_wait_ready bytes=${jpeg.size}")
                scheduleTimeout(12_000L, "Cầu nối AI chưa sẵn sàng")
            }
        }
    }

    private fun sendPending() {
        val p = pending ?: return
        if (!ready) return
        val payload = JSONObject().apply {
            put("image_base64", Base64.encodeToString(p.jpeg, Base64.NO_WRAP))
            put("mime_type", "image/jpeg")
            put("current_zoom", p.zoom)
        }.toString()
        val quoted = JSONObject.quote(payload)
        Log.i(tag, "ai_web_send bytes=${p.jpeg.size} zoom=${p.zoom}")
        scheduleTimeout(60_000L, "Gemini phản hồi quá lâu")
        webView.evaluateJavascript(
            "window.titusAnalyze ? window.titusAnalyze($quoted) : window.TitusNative.onAiResult(JSON.stringify({ok:false,message:'Web bridge chưa sẵn sàng'}));",
            null
        )
    }

    private fun scheduleTimeout(delayMs: Long, message: String) {
        timeoutRunnable?.let(main::removeCallbacks)
        val runnable = Runnable {
            if (pending != null) failPending(message)
        }
        timeoutRunnable = runnable
        main.postDelayed(runnable, delayMs)
    }

    private fun failPending(message: String) {
        val p = pending ?: return
        pending = null
        timeoutRunnable?.let(main::removeCallbacks)
        timeoutRunnable = null
        p.callback(Result.failure(IOException(message)))
    }

    private inner class NativeCallbacks {
        @JavascriptInterface
        fun onBridgeReady() {
            main.post {
                if (!ready) Log.i(tag, "ai_web_bridge_ready")
                ready = true
                if (pending != null) sendPending()
            }
        }

        @JavascriptInterface
        fun onAiResult(json: String) {
            main.post {
                val p = pending ?: return@post
                pending = null
                timeoutRunnable?.let(main::removeCallbacks)
                timeoutRunnable = null
                try {
                    val root = JSONObject(json)
                    if (!root.optBoolean("ok")) {
                        p.callback(Result.failure(IOException(root.optString("message", "AI không khả dụng"))))
                        return@post
                    }
                    val c = root.getJSONObject("composition")
                    val s = root.getJSONObject("subject")
                    val m = root.getJSONObject("movement")
                    val result = CompositionResult(
                        composition = NormalizedRect(
                            c.getDouble("x").toFloat(),
                            c.getDouble("y").toFloat(),
                            c.getDouble("width").toFloat(),
                            c.getDouble("height").toFloat()
                        ),
                        subject = NormalizedPoint(
                            s.getDouble("x").toFloat(),
                            s.getDouble("y").toFloat()
                        ),
                        recommendedZoom = root.getDouble("recommendedZoom").toFloat(),
                        movement = Movement(
                            m.getDouble("horizontal").toFloat(),
                            m.getDouble("vertical").toFloat(),
                            m.getDouble("rotation").toFloat()
                        ),
                        compositionType = root.optString("compositionType", "other"),
                        confidence = root.optDouble("confidence", .5).toFloat(),
                        instruction = root.optString("instruction", "Căn máy theo khung gợi ý."),
                        reason = root.optString("reason", "")
                    )
                    Log.i(tag, "ai_web_result ok=true type=${result.compositionType} conf=${result.confidence}")
                    p.callback(Result.success(result))
                } catch (e: Exception) {
                    Log.e(tag, "ai_web_result_parse_failed body=${json.take(180)}", e)
                    p.callback(Result.failure(IOException("Không đọc được kết quả AI: ${e.message}")))
                }
            }
        }
    }

    fun destroy() {
        main.post {
            timeoutRunnable?.let(main::removeCallbacks)
            timeoutRunnable = null
            pending = null
            webView.stopLoading()
            webView.removeJavascriptInterface("TitusNative")
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    companion object {
        const val BRIDGE_URL = "https://long-525p3v.v2.appdeploy.ai/"
    }
}
