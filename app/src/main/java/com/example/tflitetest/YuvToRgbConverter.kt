package com.example.tflitetest

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.util.Log
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.graphics.ImageFormat

class YuvToRgbConverter(private val context: Context) {
    private val rs: RenderScript = RenderScript.create(context)
    private val script: ScriptIntrinsicYuvToRGB = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))
    private var nv21Array: ByteArray? = null
    private var inputAllocation: Allocation? = null
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0

    fun yuvToRgb(image: Image): Bitmap {
        if (image.format != ImageFormat.YUV_420_888) {
            throw IllegalArgumentException("Expected YUV_420_888 format, but got ${image.format}")
        }
        val width = image.width
        val height = image.height

        // Initialize or reinitialize resources if size has changed or not yet allocated
        if (nv21Array == null || width != lastWidth || height != lastHeight) {
            lastWidth = width
            lastHeight = height
            val ySize = width * height
            val chromaSize = (width / 2) * (height / 2)
            val nv21Size = ySize + chromaSize * 2
            nv21Array = ByteArray(nv21Size)
            inputAllocation = Allocation.createSized(rs, Element.U8(rs), nv21Size)
        }

        // Convert YUV_420_888 to NV21 using the preallocated array
        yuv420ToNv21(image, nv21Array!!)
        Log.d("YuvToRgbConverter", "NV21 size: ${nv21Array!!.size}")
        Log.d("YuvToRgbConverter", "First 10 NV21 bytes: ${nv21Array!!.take(10)}")

        // Update input allocation with new NV21 data
        inputAllocation!!.copyFrom(nv21Array)

        // Create the output Bitmap
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Create output allocation from the Bitmap
        val outputAllocation = Allocation.createFromBitmap(rs, output)

        // Perform conversion using the reused script
        script.setInput(inputAllocation)
        script.forEach(outputAllocation)
        outputAllocation.copyTo(output)

        // Log a sample pixel
        val samplePixel = output.getPixel(0, 0)
        Log.d("YuvToRgbConverter", "Output bitmap sample pixel (0,0): 0x${Integer.toHexString(samplePixel)}")

        return output
    }

    private fun yuv420ToNv21(image: Image, nv21: ByteArray) {
        // Get image dimensions
        val width = image.width
        val height = image.height

        // Y plane
        val yBuffer = image.planes[0].buffer
        val ySize = yBuffer.remaining()
        yBuffer.get(nv21, 0, ySize)

        // UV planes
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val chromaWidth = width / 2
        val chromaHeight = height / 2
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        // Interleave V and U into the NV21 array
        var offset = ySize
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val uPos = uBuffer.position()
        val vPos = vBuffer.position()

        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val uIndex = row * uRowStride + col * uPixelStride
                val vIndex = row * vRowStride + col * vPixelStride
                // NV21 format: V followed by U
                nv21[offset++] = vBuffer.get(vIndex)
                nv21[offset++] = uBuffer.get(uIndex)
            }
        }

        // Restore buffer positions
        uBuffer.position(uPos)
        vBuffer.position(vPos)
    }
}