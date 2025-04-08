package com.example.tflitetest.ml

import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint


data class Experiment(
    val numClasses: Int,
    val testConf: Float,
    val nmsthre: Float,
    val testSize: IntArray // e.g., [416, 416]
)


class Predictor(
    private val tflite: Interpreter,
    private val exp: Experiment,
    public val clsNames: List<String>? = null,
    private val decoder: Decoder? = null,
    private val device: String = "cpu",
    private val fp16: Boolean = false
) {
    private val numClasses = exp.numClasses
    public val confThre = exp.testConf
    private val nmsThre = exp.nmsthre
    private val testSize = exp.testSize
    private val loggerTag = "Predictor"
    private val preproc = ValTransform()









    fun inference(bitmap: Bitmap): Pair<Any?, Map<String, Any>> {
        Log.i(loggerTag, "=== Starting inference ===")
        Log.i(loggerTag, "[inference] Input type: Bitmap")
        val originalBitmap = bitmap
        val width = originalBitmap.width
        val height = originalBitmap.height
        Log.i(loggerTag, "[inference] Original image shape: ($height, $width)")
        val imgInfo = mutableMapOf<String, Any>(
            "id" to 0,
            "file_name" to "live_frame",
            "height" to height,
            "width" to width,
            "raw_img" to originalBitmap
        )
        val ratio = min(testSize[0].toFloat() / height, testSize[1].toFloat() / width)
        imgInfo["ratio"] = ratio

        val startPreprocessTime = System.nanoTime()
        val processedData = preproc.transform(originalBitmap, testSize)
        val endTransformTime = System.nanoTime()
        val transformMs = (endTransformTime - startPreprocessTime) / 1e6
        Log.i(loggerTag, "[inference] ValTransform took $transformMs ms")

        // Create ByteBuffer directly from ByteArray
        val startBufferTime = System.nanoTime()
        val inputBuffer = ByteBuffer.allocateDirect(processedData.size).order(ByteOrder.nativeOrder())
        inputBuffer.put(processedData)
        inputBuffer.rewind()
        val endBufferTime = System.nanoTime()
        val bufferMs = (endBufferTime - startBufferTime) / 1e6
        Log.i(loggerTag, "[inference] ByteBuffer creation took $bufferMs ms")
        Log.i(loggerTag, "[inference] Created ByteBuffer for TFLite, capacity: ${inputBuffer.capacity()}")



        // Measure TFLite inference time
        val startInferenceTime = System.nanoTime()
        val outputShape = arrayOf(1, 3549, 85)
        val numOutputElements = outputShape[0] * outputShape[1] * outputShape[2]
        val outputBuffer = ByteBuffer.allocateDirect(numOutputElements).order(ByteOrder.nativeOrder())
        Log.i(loggerTag, "[inference] Running TFLite forward pass...")
        tflite.run(inputBuffer, outputBuffer)
        val endInferenceTime = System.nanoTime()
        val inferenceMs = (endInferenceTime - startInferenceTime) / 1e6
        Log.i(loggerTag, "[inference] TFLite forward pass took $inferenceMs ms")
        Log.i(loggerTag, "[inference] Model output shape: [${outputShape.joinToString(", ")}]")

        // Measure postprocessing time
        val startPostprocessTime = System.nanoTime()
        outputBuffer.rewind()
        val outputBytes = ByteArray(numOutputElements)
        outputBuffer.get(outputBytes)
        val dequantizedOutput = dequantizeOutput(outputBytes, arrayOf(1, 3549, 85))
        var decodedOutputs: Any? = dequantizedOutput
        if (decoder != null) {
            Log.i(loggerTag, "[inference] Decoding model outputs with decoder...")
            decodedOutputs = decoder.decode(dequantizedOutput)
        }
        val finalOutputs = postprocess(decodedOutputs, numClasses, confThre, nmsThre)
        val endPostprocessTime = System.nanoTime()
        val postprocessMs = (endPostprocessTime - startPostprocessTime) / 1e6
        Log.i(loggerTag, "[inference] Postprocessing took $postprocessMs ms")
        Log.i(loggerTag, "[inference] Finished postprocess. Final outputs obtained.")

        return Pair(finalOutputs, imgInfo)
    }



    fun visual(output: Any?, imgInfo: Map<String, Any>, clsConf: Float = 0.35f): Bitmap {
        val ratio = imgInfo["ratio"] as Float
        val rawImg = imgInfo["raw_img"] as Bitmap
        if (output == null) return rawImg
        // [x1, y1, x2, y2, object_conf, class_conf, class_id, overall_score]
        val detections = output as List<FloatArray>
        val mutableBitmap = rawImg.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.RED
        }
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 30f
            style = Paint.Style.FILL
        }
        for (det in detections) {
            if (det[7] < clsConf) continue
            val x1 = (det[0] / ratio).toInt()
            val y1 = (det[1] / ratio).toInt()
            val x2 = (det[2] / ratio).toInt()
            val y2 = (det[3] / ratio).toInt()

            // Draw the bounding box.
            canvas.drawRect(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), boxPaint)

            val clsId = det[6].toInt()
            val score = det[7]

            val className = if (clsNames != null && clsId in clsNames.indices) {
                clsNames[clsId]
            } else {
                clsId.toString()
            }
            val text = "$className: ${(score * 100).toInt()}%"


            // Draw the text near the top-left corner of the bounding box.
            canvas.drawText(text, x1.toFloat(), (y1 + textPaint.textSize), textPaint)
        }

        return mutableBitmap
    }



    private fun dequantizeOutput(
        outputBytes: ByteArray,
        shape: Array<Int>
    ): Array<Array<FloatArray>> {
        // Expecting shape [1, 3549, 85]
        val batch = shape[0]
        val numBoxes = shape[1]
        val numAttributes = shape[2]
        val scale = 0.019965287297964096f
        val zeroPoint = 107f
        var index = 0

        val result = Array(batch) {
            Array(numBoxes) {
                FloatArray(numAttributes)
            }
        }
        for (b in 0 until batch) {
            for (i in 0 until numBoxes) {
                for (j in 0 until numAttributes) {
                    // Interpret the byte as unsigned.
                    val value = outputBytes[index].toInt() and 0xFF
                    result[b][i][j] = scale * (value - zeroPoint)
                    index++
                }
            }
        }
        return result
    }


    private fun convertToByteBuffer(
        data: FloatArray,
        batchSize: Int,
        channels: Int,
        height: Int,
        width: Int
    ): ByteBuffer {
        // Allocate 1 byte per element
        val bufferSize = batchSize * channels * height * width
        val byteBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())

        // Convert each float value to a byte (0-255)
        for (value in data) {
            // Clamp the value to 0-255, just to be safe
            val intVal = value.toInt().coerceIn(0, 255)
            byteBuffer.put(intVal.toByte())
        }
        byteBuffer.rewind()
        return byteBuffer
    }


    private fun postprocess(
        outputs: Any?,
        numClasses: Int,
        confThre: Float,
        nmsThre: Float
    ): Any? {
        Log.i(loggerTag, "[postprocess] Running postprocess with conf=$confThre, nms=$nmsThre")
        // Expect outputs as a float[][][] with shape [1, 3549, 85]
        val predictions = outputs as Array<Array<FloatArray>>
        val batchSize = predictions.size  // should be 1
        val finalOutput = mutableListOf<List<FloatArray>>()

        // Process each image in the batch (usually only one)
        for (b in 0 until batchSize) {
            val imagePred = predictions[b]

            // --- Step 1: Convert center format to corner format ---
            // For each detection: [cx, cy, w, h] -> [x1, y1, x2, y2]
            for (detection in imagePred) {
                val cx = detection[0]
                val cy = detection[1]
                val w = detection[2]
                val h = detection[3]
                detection[0] = cx - w / 2f  // x1
                detection[1] = cy - h / 2f  // y1
                detection[2] = cx + w / 2f  // x2
                detection[3] = cy + h / 2f  // y2
            }

            // --- Step 2: Filter detections by confidence ---
            // For each detection, find the maximum class score and its index.
            // Compute overall score = object_conf * max_class_conf.
            val detections = mutableListOf<FloatArray>()
            for (detection in imagePred) {
                var maxClassConf = 0f
                var classPred = -1
                // Loop over class scores; indices 5 to (5+numClasses-1)
                for (i in 0 until numClasses) {
                    val score = detection[5 + i]
                    if (score > maxClassConf) {
                        maxClassConf = score
                        classPred = i
                    }
                }
                val overallScore = detection[4] * maxClassConf
                if (overallScore >= confThre) {
                    // Build a detection record:
                    // [x1, y1, x2, y2, object_conf, class_conf, class_pred, overall_score]
                    val det = FloatArray(8)
                    det[0] = detection[0]
                    det[1] = detection[1]
                    det[2] = detection[2]
                    det[3] = detection[3]
                    det[4] = detection[4]
                    det[5] = maxClassConf
                    det[6] = classPred.toFloat()
                    det[7] = overallScore
                    detections.add(det)
                }
            }

            // If no detections remain for this image, add an empty list and continue.
            if (detections.isEmpty()) {
                finalOutput.add(emptyList())
                continue
            }

            // --- Step 3: Apply NMS (class-agnostic) ---
            val keepIndices = nms(detections, nmsThre)
            val finalDetections = keepIndices.map { detections[it] }
            finalOutput.add(finalDetections)
        }

        // For batch size 1, return detections for the first image.
        return finalOutput[0]
    }


    private fun nms(detections: List<FloatArray>, iouThreshold: Float): List<Int> {
        if (detections.isEmpty()) return emptyList()

        // Pre-compute scores once instead of accessing them repeatedly
        val scores = detections.mapIndexed { index, detection -> Pair(index, detection[7]) }

        // Sort indices by score in descending order
        val sortedIndices = scores.sortedByDescending { it.second }.map { it.first }

        val keep = ArrayList<Int>(sortedIndices.size / 2) // Preallocate with estimated capacity
        val remaining = BooleanArray(sortedIndices.size) { true }

        for (i in sortedIndices.indices) {
            val idx = sortedIndices[i]

            // If this box was already removed, skip it
            if (!remaining[i]) continue

            // Add index to keep list
            keep.add(idx)

            val boxI = detections[idx]

            // For all remaining boxes, check IoU and mark for removal if needed
            for (j in i + 1 until sortedIndices.size) {
                if (!remaining[j]) continue

                val jdx = sortedIndices[j]
                if (iou(boxI, detections[jdx]) > iouThreshold) {
                    remaining[j] = false
                }
            }
        }

        return keep
    }

// Assuming your iou function is implemented elsewhere
    /**
     * Computes Intersection over Union (IoU) between two boxes.
     * Each box is a FloatArray with format [x1, y1, x2, y2, ...].
     */
    private inline fun iou(box1: FloatArray, box2: FloatArray): Float {
        val x1 = maxOf(box1[0], box2[0])
        val y1 = maxOf(box1[1], box2[1])
        val x2 = minOf(box1[2], box2[2])
        val y2 = minOf(box1[3], box2[3])
        val interWidth = maxOf(0f, x2 - x1)
        val interHeight = maxOf(0f, y2 - y1)
        val interArea = interWidth * interHeight
        val area1 = (box1[2] - box1[0]).coerceAtLeast(0f) * (box1[3] - box1[1]).coerceAtLeast(0f)
        val area2 = (box2[2] - box2[0]).coerceAtLeast(0f) * (box2[3] - box2[1]).coerceAtLeast(0f)
        val unionArea = area1 + area2 - interArea
        return if (unionArea <= 0f) 0f else interArea / unionArea
    }




}



