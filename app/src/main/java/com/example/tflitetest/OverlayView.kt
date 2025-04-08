package com.example.tflitetest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
//
//class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
//    private var detections: List<Detection> = emptyList()
//
//    private val boxPaint = Paint().apply {
//        color = Color.RED
//        style = Paint.Style.STROKE
//        strokeWidth = 4f
//    }
//
//    private val textPaint = Paint().apply {
//        color = Color.YELLOW
//        textSize = 40f
//        style = Paint.Style.FILL
//    }
//
//    fun setDetections(detections: List<Detection>) {
//        this.detections = detections
//        invalidate() // Refresh the view
//    }
//
//    override fun onDraw(canvas: Canvas) {
//        super.onDraw(canvas)
//
//        if (detections.isEmpty()) {
//            textPaint.color = Color.RED
//            textPaint.textSize = 40f
//            canvas.drawText("No objects detected", 50f, 50f, textPaint)
//            return
//        }
//
//        for (detection in detections) {
//            // Directly use already scaled values
//            val left = detection.x1
//            val top = detection.y1
//            val right = detection.x2
//            val bottom = detection.y2
//            canvas.drawRect(left, top, right, bottom, boxPaint)
//            val label = "${detection.label}"
//            canvas.drawText(label, left, top - 10, textPaint)
//        }
//    }
//
//
//}
