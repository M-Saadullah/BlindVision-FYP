package com.example.tflitetest

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.util.Log
import androidx.camera.core.ImageProxy
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.graphics.ImageFormat

class YuvToRgbConverter(private val context: Context) {
    private val rs: RenderScript = RenderScript.create(context)

    fun yuvToRgb(image: Image): Bitmap {

        if (image.format != ImageFormat.YUV_420_888) {
            throw IllegalArgumentException("Expected YUV_420_888 format, but got ${image.format}")
        }
        // Convert YUV_420_888 to NV21
        val nv21 = yuv420ToNv21(image)
        Log.d("YuvToRgbConverter", "NV21 size: ${nv21.size}")
        Log.d("YuvToRgbConverter", "First 10 NV21 bytes: ${nv21.take(10)}")

        // Create the output Bitmap
        val output = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)

        // Create an Allocation from the NV21 bytes
        val input = Allocation.createSized(rs, Element.U8(rs), nv21.size)
        input.copyFrom(nv21)

        // Create output allocation from the Bitmap
        val outputAllocation = Allocation.createFromBitmap(rs, output)
        val script = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))
        script.setInput(input)
        script.forEach(outputAllocation)
        outputAllocation.copyTo(output)

        // Log a sample pixel
        val samplePixel = output.getPixel(0, 0)
        Log.d("YuvToRgbConverter", "Output bitmap sample pixel (0,0): 0x${Integer.toHexString(samplePixel)}")

        return output
    }


    private fun yuv420ToNv21(image: Image): ByteArray {
        // Get image dimensions.
        val width = image.width
        val height = image.height

        // Y plane
        val yBuffer = image.planes[0].buffer
        val ySize = yBuffer.remaining()
        // UV planes: dimensions are width/2 and height/2.
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val chromaWidth = width / 2
        val chromaHeight = height / 2
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        // Calculate expected NV21 size: Y + interleaved VU.
        val nv21Size = ySize + chromaWidth * chromaHeight * 2
        val nv21 = ByteArray(nv21Size)

        // Copy Y data.
        yBuffer.get(nv21, 0, ySize)

        // Interleave V and U.
        var offset = ySize
        // We'll read row by row from the chroma planes.
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        // Backup original positions.
        val uPos = uBuffer.position()
        val vPos = vBuffer.position()

        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                // Compute the index for each plane.
                val uIndex = row * uRowStride + col * uPixelStride
                val vIndex = row * vRowStride + col * vPixelStride
                // NV21 format expects V first then U.
                nv21[offset++] = vBuffer.get(vIndex)
                nv21[offset++] = uBuffer.get(uIndex)
            }
        }

        // Restore buffer positions (if needed elsewhere).
        uBuffer.position(uPos)
        vBuffer.position(vPos)

        return nv21
    }
}
