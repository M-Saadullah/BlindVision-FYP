package com.example.tflitetest.ml

import android.util.Log
import kotlin.math.exp

class Decoder {
    private val loggerTag = "Decoder"
    private var isFirst = true

    /**
     * Decodes the raw model outputs.
     *
     * @param outputs A 3D array with shape [1, numBoxes, numAttributes]. For YOLOX, numBoxes=3549 and numAttributes=85.
     * @return The decoded outputs.
     */
    fun decode(outputs: Array<Array<FloatArray>>): Array<Array<FloatArray>> {
        if (isFirst) {
            isFirst = false
            Log.i(loggerTag, "In Decodeeeeeeeeeeeeeeeeeeeeee")
        }

        // YOLOX decoder settings
        val hwSizes = listOf(Pair(52, 52), Pair(26, 26), Pair(13, 13))
        val stridesVals = listOf(8, 16, 32)

        // Build grid and stride lists for each scale.
        val gridList = mutableListOf<FloatArray>() // Each grid element is [x, y]
        val strideList = mutableListOf<Float>()      // One stride value per grid element

        // For each feature map scale
        for ((index, hw) in hwSizes.withIndex()) {
            val (hsize, wsize) = hw
            val stride = stridesVals[index].toFloat()
            for (i in 0 until hsize) {
                for (j in 0 until wsize) {
                    // With indexing "ij", the grid coordinate is (x, y) = (j, i)
                    gridList.add(floatArrayOf(j.toFloat(), i.toFloat()))
                    strideList.add(stride)
                }
            }
        }

        // At this point, gridList.size should be 3549.
        val numBoxes = gridList.size
        val boxes = outputs[0]  // Assuming the first dimension is 1

        // Decode each box.
        for (i in 0 until numBoxes) {
            val grid = gridList[i]
            val stride = strideList[i]
            val box = boxes[i]

            // Modify the first 4 coordinates:
            //   For (x, y): add grid coordinate and multiply by stride.
            box[0] = (box[0] + grid[0]) * stride
            box[1] = (box[1] + grid[1]) * stride
            //   For (w, h): apply exponential and multiply by stride.
            box[2] = (exp(box[2].toDouble()).toFloat()) * stride
            box[3] = (exp(box[3].toDouble()).toFloat()) * stride
            // The remaining 81 values (confidence and class scores) are left unchanged.
        }

        return outputs
    }
}
