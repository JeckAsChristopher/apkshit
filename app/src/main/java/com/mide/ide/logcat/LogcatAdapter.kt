package com.mide.ide.logcat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mide.ide.databinding.ItemLogcatBinding

class LogcatAdapter : ListAdapter<LogcatEntry, LogcatAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<LogcatEntry>() {
            override fun areItemsTheSame(oldItem: LogcatEntry, newItem: LogcatEntry) =
                oldItem.timestamp == newItem.timestamp && oldItem.message == newItem.message
            override fun areContentsTheSame(oldItem: LogcatEntry, newItem: LogcatEntry) =
                oldItem == newItem
        }
    }

    inner class ViewHolder(private val binding: ItemLogcatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: LogcatEntry) {
            binding.textLevel.text = entry.level.char.toString()
            binding.textLevel.setTextColor(entry.level.color)
            binding.textTag.text = entry.tag
            binding.textMessage.text = entry.message
            binding.textTimestamp.text = entry.timestamp
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLogcatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))
}
