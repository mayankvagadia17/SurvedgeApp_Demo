package com.nexova.survedge.ui.project.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nexova.survedge.data.db.entity.ProjectEntity
import com.nexova.survedge.databinding.ItemProjectBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProjectsAdapter(
    val onProjectClicked: (ProjectEntity) -> Unit,
    val onProjectLongClicked: (ProjectEntity) -> Unit
) : ListAdapter<ProjectEntity, ProjectsAdapter.ProjectViewHolder>(ProjectDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val binding = ItemProjectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProjectViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProjectViewHolder(private val binding: ItemProjectBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(project: ProjectEntity) {
            binding.tvProjectName.text = project.name
            
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            val dateStr = dateFormat.format(Date(project.lastModified))
            binding.tvDate.text = "Modified: $dateStr"

            binding.root.setOnClickListener {
                onProjectClicked(project)
            }

            binding.root.setOnLongClickListener {
                onProjectLongClicked(project)
                true
            }
        }
    }

    class ProjectDiffCallback : DiffUtil.ItemCallback<ProjectEntity>() {
        override fun areItemsTheSame(oldItem: ProjectEntity, newItem: ProjectEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ProjectEntity, newItem: ProjectEntity): Boolean {
            return oldItem == newItem
        }
    }
}
