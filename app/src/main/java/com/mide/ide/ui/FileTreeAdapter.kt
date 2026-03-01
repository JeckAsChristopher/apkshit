package com.mide.ide.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mide.ide.project.FileNode

class FileTreeAdapter(
    private val onNodeClick: (FileNode) -> Unit
) : ListAdapter<FileNode, FileTreeAdapter.ViewHolder>(FileNodeDiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconView: ImageView = view.findViewById(android.R.id.icon)
        val nameView: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        val iconView = ImageView(parent.context).apply {
            id = android.R.id.icon
        }
        val container = android.widget.LinearLayout(parent.context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            addView(iconView)
            addView(view.findViewById(android.R.id.text1))
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val node = getItem(position)
        val indent = node.depth * 32
        holder.itemView.setPadding(indent, 0, 0, 0)
        holder.nameView.text = when {
            node.isDirectory && node.isExpanded -> "▼ ${node.name}"
            node.isDirectory -> "▶ ${node.name}"
            else -> "  ${node.name}"
        }
        holder.itemView.setOnClickListener { onNodeClick(node) }
        holder.itemView.setOnLongClickListener {
            holder.itemView.showContextMenu()
            true
        }
    }

    class FileNodeDiffCallback : DiffUtil.ItemCallback<FileNode>() {
        override fun areItemsTheSame(a: FileNode, b: FileNode) = a.file.absolutePath == b.file.absolutePath
        override fun areContentsTheSame(a: FileNode, b: FileNode) = a == b
    }
}
