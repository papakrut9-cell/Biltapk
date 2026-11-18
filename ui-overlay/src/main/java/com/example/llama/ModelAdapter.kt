package com.example.llama

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class ModelAdapter(private val onDelete: (LocalModel) -> Unit) :
    ListAdapter<LocalModel, ModelAdapter.ModelVH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<LocalModel>() {
            override fun areItemsTheSame(oldItem: LocalModel, newItem: LocalModel) = oldItem.name == newItem.name
            override fun areContentsTheSame(oldItem: LocalModel, newItem: LocalModel) = oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelVH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_model, parent, false)
        return ModelVH(view)
    }

    override fun onBindViewHolder(holder: ModelVH, position: Int) {
        val model = getItem(position)
        holder.nameTv.text = model.name
        holder.sizeTv.text = String.format(Locale.US, "%.2f ГБ", model.size / 1e9)
        holder.deleteBtn.setOnClickListener { onDelete(model) }
    }

    class ModelVH(view: View) : RecyclerView.ViewHolder(view) {
        val nameTv: TextView = view.findViewById(R.id.model_name_tv)
        val sizeTv: TextView = view.findViewById(R.id.model_size_tv)
        val deleteBtn: TextView = view.findViewById(R.id.model_delete_tv)
    }
}
