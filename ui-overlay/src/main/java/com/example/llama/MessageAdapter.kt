package com.example.llama

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Message(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val thinkMs: Long = 0L
)

class MessageAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ASSISTANT = 2

        // Теги собираются из unicode-эскейпов, чтобы исходник не ломался при передаче
        private const val THINK_OPEN = "\u003c" + "think" + "\u003e"
        private const val THINK_CLOSE = "\u003c" + "/think" + "\u003e"
        private const val HEADER_COLLAPSED = "Показать размышления ˅"
        private const val HEADER_EXPANDED = "Скрыть размышления ˄"

        private val DIFF = object : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(oldItem: Message, newItem: Message) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Message, newItem: Message) = oldItem == newItem
        }
    }

    // Пока модель генерирует ответ: рисуем курсор в пустых сообщениях
    var isStreaming: Boolean = false

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isUser) VIEW_TYPE_USER else VIEW_TYPE_ASSISTANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            UserMessageViewHolder(inflater.inflate(R.layout.item_message_user, parent, false))
        } else {
            AssistantMessageViewHolder(inflater.inflate(R.layout.item_message_assistant, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        if (holder is UserMessageViewHolder) {
            holder.textView.text = message.content
            holder.timeTv.text = timeFormat.format(Date(message.timestamp))
        } else if (holder is AssistantMessageViewHolder) {
            bindAssistant(holder, message)
        }
    }

    private fun bindAssistant(holder: AssistantMessageViewHolder, message: Message) {
        holder.timeTv.text = timeFormat.format(Date(message.timestamp))

        var rawText = message.content
        val thinkStart = rawText.indexOf(THINK_OPEN)
        val thinkEnd = rawText.indexOf(THINK_CLOSE)

        if (thinkStart != -1 && thinkEnd != -1) {
            val thought = rawText.substring(thinkStart + THINK_OPEN.length, thinkEnd).trim()
            val answer = rawText.substring(thinkEnd + THINK_CLOSE.length).trim()
            val duration = if (message.thinkMs > 0) {
                " · " + String.format(Locale.US, "%.1f", message.thinkMs / 1000.0) + " c"
            } else ""

            holder.thinkContainer.visibility = View.VISIBLE
            holder.thinkContent.text = thought
            holder.thinkContent.visibility = View.GONE
            holder.thinkHeader.text = HEADER_COLLAPSED + duration
            holder.textView.text = if (answer.isNotBlank()) answer else if (isStreaming) "▍" else ""

            holder.thinkHeader.setOnClickListener {
                if (holder.thinkContent.visibility == View.GONE) {
                    holder.thinkContent.visibility = View.VISIBLE
                    holder.thinkHeader.text = HEADER_EXPANDED + duration
                } else {
                    holder.thinkContent.visibility = View.GONE
                    holder.thinkHeader.text = HEADER_COLLAPSED + duration
                }
            }
        } else if (thinkStart != -1) {
            // Модель всё ещё размышляет: показываем размышления в реальном времени
            holder.thinkContainer.visibility = View.VISIBLE
            holder.thinkContent.visibility = View.VISIBLE
            holder.thinkHeader.text = "⏳ Модель размышляет..."
            holder.thinkContent.text = rawText.substring(thinkStart + THINK_OPEN.length).trim()
            holder.textView.text = if (isStreaming) "▍" else ""
            holder.thinkHeader.setOnClickListener(null)
        } else {
            holder.thinkContainer.visibility = View.GONE
            holder.textView.text = if (rawText.isNotBlank()) rawText else if (isStreaming) "▍" else ""
        }
    }

    class UserMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.msg_content)
        val timeTv: TextView = view.findViewById(R.id.time_tv_user)
    }

    class AssistantMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.msg_content)
        val timeTv: TextView = view.findViewById(R.id.time_tv_assistant)
        val thinkContainer: LinearLayout = view.findViewById(R.id.thinkContainer)
        val thinkHeader: TextView = view.findViewById(R.id.thinkHeader)
        val thinkContent: TextView = view.findViewById(R.id.thinkContent)
    }
}
