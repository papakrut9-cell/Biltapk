package com.example.llama

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class Message(val id: String, val content: String, val isUser: Boolean)

class MessageAdapter(private val messages: List<Message>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ASSISTANT = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_ASSISTANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            UserMessageViewHolder(inflater.inflate(R.layout.item_message_user, parent, false))
        } else {
            AssistantMessageViewHolder(inflater.inflate(R.layout.item_message_assistant, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is UserMessageViewHolder) {
            holder.textView.text = message.content
        } else if (holder is AssistantMessageViewHolder) {
            val rawText = message.content
            val thinkStart = rawText.indexOf("<think>")
            val thinkEnd = rawText.indexOf("</think>")

            if (thinkStart != -1 && thinkEnd != -1) {
                val thought = rawText.substring(thinkStart + 7, thinkEnd).trim()
                val answer = rawText.substring(thinkEnd + 8).trim()
                holder.thinkContainer.visibility = View.VISIBLE
                holder.thinkContent.text = thought
                holder.thinkHeader.text = "Показать размышления"
                holder.textView.text = answer
                holder.thinkHeader.setOnClickListener {
                    if (holder.thinkContent.visibility == View.GONE) {
                        holder.thinkContent.visibility = View.VISIBLE
                        holder.thinkHeader.text = "Скрыть размышления"
                    } else {
                        holder.thinkContent.visibility = View.GONE
                        holder.thinkHeader.text = "Показать размышления"
                    }
                }
            } else if (thinkStart != -1) {
                holder.thinkContainer.visibility = View.VISIBLE
                holder.thinkContent.visibility = View.VISIBLE
                holder.thinkHeader.text = "Модель размышляет..."
                holder.thinkContent.text = rawText.substring(thinkStart + 7).trim()
                holder.textView.text = ""
            } else {
                holder.thinkContainer.visibility = View.GONE
                holder.textView.text = rawText
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    class UserMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.msg_content)
    }

    class AssistantMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.msg_content)
        val thinkContainer: LinearLayout = view.findViewById(R.id.thinkContainer)
        val thinkHeader: TextView = view.findViewById(R.id.thinkHeader)
        val thinkContent: TextView = view.findViewById(R.id.thinkContent)
    }
}
