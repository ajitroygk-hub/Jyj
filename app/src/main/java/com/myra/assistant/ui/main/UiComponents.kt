package com.myra.assistant.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import kotlin.random.Random

// ==========================================
// 1. WAVEFORM VIEW (20 vertical bars)
// ==========================================
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barCount = 20
    private val barHeights = FloatArray(barCount) { 4f }
    private val targetHeights = FloatArray(barCount) { 4f }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val barRect = RectF()

    private var currentAmplitude = 0f
    private var isAnimating = false
    private var animator: ValueAnimator? = null

    init {
        startAnimation()
    }

    fun startAnimation() {
        if (isAnimating) return
        isAnimating = true
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                updateBars()
                invalidate()
            }
            start()
        }
    }

    fun stopAnimation() {
        isAnimating = false
        animator?.cancel()
        animator = null
        for (i in 0 until barCount) {
            barHeights[i] = 4f
            targetHeights[i] = 4f
        }
        invalidate()
    }

    fun setAmplitude(rms: Float) {
        currentAmplitude = rms.coerceIn(0f, 1f)
    }

    private fun updateBars() {
        val maxH = height.toFloat().coerceAtLeast(30f)
        for (i in 0 until barCount) {
            val centerFactor = 1f - kotlin.math.abs(i - (barCount / 2f)) / (barCount / 2f)
            val jitter = if (currentAmplitude > 0.02f) Random.nextFloat() * 0.4f + 0.6f else 0.1f
            val target = (4f + currentAmplitude * maxH * centerFactor * jitter).coerceIn(4f, maxH)
            targetHeights[i] = target
            barHeights[i] += (targetHeights[i] - barHeights[i]) * 0.3f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 10f || h <= 10f) return

        val totalSpacing = w * 0.4f
        val barWidth = maxOf(1f, (w - totalSpacing) / barCount)
        val spacing = maxOf(1f, totalSpacing / (barCount + 1))
        val centerY = h / 2f

        for (i in 0 until barCount) {
            val left = spacing + i * (barWidth + spacing)
            val right = left + barWidth
            val halfBar = maxOf(1f, barHeights[i] / 2f)
            val top = centerY - halfBar
            val bottom = centerY + halfBar

            barRect.set(left, top, right, bottom)

            val heightRatio = (barHeights[i] / h).coerceIn(0f, 1f)
            val alpha = (150 + heightRatio * 105).toInt().coerceIn(150, 255)
            barPaint.color = Color.argb(alpha, 255, 23, 68)

            val rx = maxOf(0.5f, barWidth / 2f)
            canvas.drawRoundRect(barRect, rx, rx, barPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}

// ==========================================
// 2. CHAT MESSAGE DATA MODEL
// ==========================================
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

// ==========================================
// 3. CHAT RECYCLERVIEW ADAPTER
// ==========================================
class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_MYRA = 2
    }

    private val messages = mutableListOf<ChatMessage>()

    fun addMessage(message: ChatMessage) {
        val trimmed = message.text.trim()
        if (trimmed.isEmpty()) return

        if (!message.isUser && lastMyraText() == trimmed) {
            return
        }

        messages.add(message.copy(text = trimmed))
        notifyItemInserted(messages.size - 1)
    }

    fun lastMyraText(): String? {
        for (i in messages.indices.reversed()) {
            if (!messages[i].isUser) {
                return messages[i].text
            }
        }
        return null
    }

    fun clear() {
        val count = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, count)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_MYRA
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            val view = inflater.inflate(R.layout.item_chat_user, parent, false)
            UserViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_chat_myra, parent, false)
            MyraViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        if (holder is UserViewHolder) {
            holder.text.text = msg.text
        } else if (holder is MyraViewHolder) {
            holder.text.text = msg.text
        }
    }

    override fun getItemCount(): Int = messages.size

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.userMessageText)
    }

    class MyraViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.myraMessageText)
    }
}
