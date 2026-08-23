package com.titus.aicompose

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

fun ImageProxy.toAiJpeg(maxLongEdge: Int = 176, quality: Int = 32): ByteArray {
    val w = width
    val h = height
    val y = planes[0]
    val u = planes[1]
    val v = planes[2]
    val nv21 = ByteArray(w * h + w * h / 2)
    var out = 0
    val yb = y.buffer.duplicate()
    for (row in 0 until h) {
        val base = row * y.rowStride
        for (col in 0 until w) nv21[out++] = yb.get(base + col * y.pixelStride)
    }
    val ub = u.buffer.duplicate()
    val vb = v.buffer.duplicate()
    for (row in 0 until h / 2) {
        val uBase = row * u.rowStride
        val vBase = row * v.rowStride
        for (col in 0 until w / 2) {
            nv21[out++] = vb.get(vBase + col * v.pixelStride)
            nv21[out++] = ub.get(uBase + col * u.pixelStride)
        }
    }
    val raw = ByteArrayOutputStream()
    YuvImage(nv21, ImageFormat.NV21, w, h, null).compressToJpeg(Rect(0, 0, w, h), 90, raw)
    var bitmap = BitmapFactory.decodeByteArray(raw.toByteArray(), 0, raw.size())
    val rotation = imageInfo.rotationDegrees
    if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        bitmap = rotated
    }
    val longEdge = maxOf(bitmap.width, bitmap.height)
    if (longEdge > maxLongEdge) {
        val scale = maxLongEdge / longEdge.toFloat()
        val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        if (scaled !== bitmap) bitmap.recycle()
        bitmap = scaled
    }
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
    bitmap.recycle()
    return output.toByteArray()
}
