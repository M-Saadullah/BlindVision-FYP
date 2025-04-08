package  com.example.myapplication

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val wavePaint = Paint().apply {
        color = Color.CYAN
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val glowPaint = Paint().apply {
        color = Color.CYAN
        strokeWidth = 40f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        alpha = 50
    }

    private val path = Path()
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
        duration = 2000
        repeatMode = ValueAnimator.RESTART
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        path.reset()

        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2
        val amplitude = height / 4
        val frequency = 2f

        for (x in 0..width.toInt() step 5) {
            val xRatio = x.toFloat() / width
            val y = centerY + amplitude * sin(frequency * (xRatio * 2f * Math.PI.toFloat() + phase))

            if (x == 0) {
                path.moveTo(x.toFloat(), y)
            } else {
                path.lineTo(x.toFloat(), y)
            }
        }

        canvas.drawPath(path, glowPaint) // Glow effect
        canvas.drawPath(path, wavePaint) // Main wave line
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
