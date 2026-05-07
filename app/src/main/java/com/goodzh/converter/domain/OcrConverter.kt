package com.goodzh.converter.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.ceil
import kotlin.math.max

class OcrConverter(private val context: Context) {
    suspend fun recognize(uri: Uri, profile: PerformanceProfile): String {
        val bitmap = loadScaledBitmap(uri, profile.ocrMaxImageSide)
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        return try {
            recognizeBitmap(bitmap, profile.ocrTileHeight, recognizer::process)
        } finally {
            bitmap.recycle()
            recognizer.close()
        }
    }

    private suspend fun recognizeBitmap(
        bitmap: Bitmap,
        tileHeight: Int,
        process: (InputImage) -> com.google.android.gms.tasks.Task<com.google.mlkit.vision.text.Text>
    ): String {
        val safeTileHeight = tileHeight.coerceAtLeast(600)
        if (bitmap.height <= safeTileHeight) {
            return process(InputImage.fromBitmap(bitmap, 0)).await().text.trim()
        }

        val parts = ArrayList<String>()
        var y = 0
        while (y < bitmap.height) {
            val height = safeTileHeight.coerceAtMost(bitmap.height - y)
            val tile = Bitmap.createBitmap(bitmap, 0, y, bitmap.width, height)
            try {
                val text = process(InputImage.fromBitmap(tile, 0)).await().text.trim()
                if (text.isNotBlank()) parts.add(text)
            } finally {
                tile.recycle()
            }
            y += height
        }
        return parts.joinToString("\n").trim()
    }

    private fun loadScaledBitmap(uri: Uri, maxImageSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val sourceWidth = bounds.outWidth
        val sourceHeight = bounds.outHeight
        if (sourceWidth <= 0 || sourceHeight <= 0) error("无法读取图片尺寸")

        val sampleSize = calculateSampleSize(sourceWidth, sourceHeight, maxImageSide)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: error("无法读取图片")

        val largestSide = max(decoded.width, decoded.height)
        if (largestSide <= maxImageSide) return decoded

        val scale = maxImageSide.toFloat() / largestSide
        val targetWidth = ceil(decoded.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = ceil(decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
        decoded.recycle()
        return scaled
    }

    private fun calculateSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sampleSize = 1
        var sampledWidth = width
        var sampledHeight = height
        while (max(sampledWidth, sampledHeight) / 2 >= maxSide) {
            sampleSize *= 2
            sampledWidth /= 2
            sampledHeight /= 2
        }
        return sampleSize.coerceAtLeast(1)
    }
}
