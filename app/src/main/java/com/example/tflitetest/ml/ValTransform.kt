package com.example.tflitetest.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import kotlin.math.min

class ValTransform {
    private val loggerTag = "ValTransform"

    // Reusable objects to reduce allocations
    private var paddedBitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private val paint = Paint()
    private val srcRect = Rect()
    private val dstRect = Rect()
    private val backgroundColor = Color.rgb(114, 114, 114)

    // Direct pixel access buffers
    private var pixelBuffer: IntArray? = null
    private var outputArray: ByteArray? = null

    fun transform(bitmap: Bitmap, inputSize: IntArray): Triple<ByteArray, Int, Int> {
        val targetH = inputSize[0]
        val targetW = inputSize[1]

        // Calculate dimensions for aspect ratio preservation
        val ratio = min(targetH.toFloat() / bitmap.height, targetW.toFloat() / bitmap.width)
        val newH = (bitmap.height * ratio).toInt()
        val newW = (bitmap.width * ratio).toInt()

        // Create or reuse the padded bitmap
        if (paddedBitmap == null || paddedBitmap?.width != targetW || paddedBitmap?.height != targetH) {
            paddedBitmap?.recycle()
            paddedBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            canvas = Canvas(paddedBitmap!!)
        }

        // Clear canvas with background color
        canvas!!.drawColor(backgroundColor)

        // Set up rectangles for drawing
        srcRect.set(0, 0, bitmap.width, bitmap.height)
        dstRect.set(0, 0, newW, newH)

        // Draw resized bitmap directly
        canvas!!.drawBitmap(bitmap, srcRect, dstRect, paint)

        // Initialize or reuse pixel buffer
        if (pixelBuffer == null || pixelBuffer!!.size != targetW * targetH) {
            pixelBuffer = IntArray(targetW * targetH)
        }

        // Extract all pixels efficiently
        paddedBitmap!!.getPixels(pixelBuffer!!, 0, targetW, 0, 0, targetW, targetH)

        // Allocate or reuse output array as ByteArray
        if (outputArray == null || outputArray!!.size != 3 * targetH * targetW) {
            outputArray = ByteArray(3 * targetH * targetW)
        }

        // Process pixels with hardcoded BGR order
        processPixels(pixelBuffer!!, outputArray!!, targetH, targetW)

        Log.i(loggerTag, "[transform] Final byteData shape: (3, $targetH, $targetW)")
        return Triple(outputArray!!, targetH, targetW)
    }

    private fun processPixels(pixels: IntArray, output: ByteArray, height: Int, width: Int) {
        val size = height * width
        for (i in 0 until size) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF).toByte()
            val g = ((pixel shr 8) and 0xFF).toByte()
            val b = (pixel and 0xFF).toByte()
            output[0 * size + i] = b  // B
            output[1 * size + i] = r  // R
            output[2 * size + i] = g  // G
        }
    }

    fun cleanup() {
        paddedBitmap?.recycle()
        paddedBitmap = null
        canvas = null
        pixelBuffer = null
        outputArray = null
    }
}