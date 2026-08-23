package com.titus.aicompose

import android.util.Base64
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class CompositionApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    fun analyze(jpeg: ByteArray, currentZoom: Float, callback: (Result<CompositionResult>) -> Unit) {
        val image = Base64.encodeToString(jpeg, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("image", image)
            .addQueryParameter("zoom", currentZoom.toString())
            .build()
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(Result.failure(e))
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val text = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback(Result.failure(IOException("HTTP ${it.code}: $text")))
                        return
                    }
                    try {
                        if (text.trimStart().startsWith("<!DOCTYPE", true) || text.trimStart().startsWith("<html", true)) {
                            callback(Result.failure(IOException("Backend AI chưa định tuyến đúng cho ứng dụng Android")))
                            return
                        }
                        val root = JSONObject(text)
                        if (!root.optBoolean("ok")) {
                            callback(Result.failure(IOException(root.optString("message", "AI không khả dụng"))))
                            return
                        }
                        val c = root.getJSONObject("composition")
                        val s = root.getJSONObject("subject")
                        val m = root.getJSONObject("movement")
                        callback(Result.success(CompositionResult(
                            composition = NormalizedRect(c.getDouble("x").toFloat(), c.getDouble("y").toFloat(), c.getDouble("width").toFloat(), c.getDouble("height").toFloat()),
                            subject = NormalizedPoint(s.getDouble("x").toFloat(), s.getDouble("y").toFloat()),
                            recommendedZoom = root.getDouble("recommendedZoom").toFloat(),
                            movement = Movement(m.getDouble("horizontal").toFloat(), m.getDouble("vertical").toFloat(), m.getDouble("rotation").toFloat()),
                            compositionType = root.optString("compositionType", "other"),
                            confidence = root.optDouble("confidence", .5).toFloat(),
                            instruction = root.optString("instruction", "Căn máy theo khung gợi ý."),
                            reason = root.optString("reason", "")
                        )))
                    } catch (e: Exception) {
                        callback(Result.failure(IOException("Không đọc được phản hồi AI: ${e.message}")))
                    }
                }
            }
        })
    }

    companion object {
        const val ENDPOINT = "https://long-525p3v.v2.appdeploy.ai/api/composition/v1"
    }
}
