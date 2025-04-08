package com.example.tflitetest.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import kotlin.math.min

class ValTransform(
    private val swap: IntArray = intArrayOf(2, 0, 1)
) {
    private val loggerTag = "ValTransform"

    /**
     * Transforms the input Bitmap:
     * 1. Resizes and pads to target dimensions (e.g., 416x416)
     * 2. Converts to a FloatArray with shape [3, targetH, targetW]
     * 3. Applies optional legacy normalization.
     */
    fun transform(bitmap: Bitmap, inputSize: IntArray): Triple<FloatArray, Int, Int> {
        val targetH = inputSize[0]
        val targetW = inputSize[1]
        val ratio = min(targetH.toFloat() / bitmap.height, targetW.toFloat() / bitmap.width)
        val newH = (bitmap.height * ratio).toInt()
        val newW = (bitmap.width * ratio).toInt()
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newW, newH, true)

        // Create a padded bitmap with a gray background (value 114)
        val paddedBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(paddedBitmap)
        canvas.drawColor(Color.rgb(114, 114, 114))
        canvas.drawBitmap(resizedBitmap, 0f, 0f, null)

        val floatData = FloatArray(3 * targetH * targetW)
        var idx = 0
        for (y in 0 until targetH) {
            for (x in 0 until targetW) {
                val color = paddedBitmap.getPixel(x, y)
                val r = ((color shr 16) and 0xFF).toFloat()
                val g = ((color shr 8) and 0xFF).toFloat()
                val b = (color and 0xFF).toFloat()
                floatData[idx++] = r
                floatData[idx++] = g
                floatData[idx++] = b
            }
        }

        val reordered = reorderChannels(floatData, targetH, targetW, swap)
        Log.i(loggerTag, "[transform] Final floatData shape: (3, $targetH, $targetW)")
        return Triple(reordered, targetH, targetW)
    }

    private fun reorderChannels(
        input: FloatArray,
        height: Int,
        width: Int,
        swap: IntArray
    ): FloatArray {
        val c = 3
        val output = FloatArray(c * height * width)
        for (pixIndex in 0 until (height * width)) {
            val r = input[pixIndex * 3 + 0]
            val g = input[pixIndex * 3 + 1]
            val b = input[pixIndex * 3 + 2]
            val c0 = when (swap[0]) {
                0 -> r
                1 -> g
                2 -> b
                else -> r
            }
            val c1 = when (swap[1]) {
                0 -> r
                1 -> g
                2 -> b
                else -> g
            }
            val c2 = when (swap[2]) {
                0 -> r
                1 -> g
                2 -> b
                else -> b
            }
            val offset0 = 0 * height * width + pixIndex
            val offset1 = 1 * height * width + pixIndex
            val offset2 = 2 * height * width + pixIndex
            output[offset0] = c0
            output[offset1] = c1
            output[offset2] = c2
        }
        return output
    }
}














