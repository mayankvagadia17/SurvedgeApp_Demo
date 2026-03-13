package com.nexova.survedge.ui.mapping.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nexova.survedge.R
import com.nexova.survedge.ui.mapping.overlay.LabeledPoint

class EditPointAdapter(
    private var points: MutableList<LabeledPoint>,
    private val onRemoveClick: (Int) -> Unit,
    private val onDragStart: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<EditPointAdapter.PointViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PointViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_edit_point, parent, false)
        return PointViewHolder(view)
    }

    override fun onBindViewHolder(holder: PointViewHolder, position: Int) {
        val point = points[position]
        holder.bind(point, position)
    }

    override fun getItemCount(): Int = points.size

    fun updatePoints(newPoints: List<LabeledPoint>) {
        points.clear()
        points.addAll(newPoints)
        notifyDataSetChanged()
    }

    fun removePoint(position: Int) {
        if (position in 0 until points.size) {
            points.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, points.size - position)
        }
    }

    fun addPoint(point: LabeledPoint) {
        points.add(point)
        notifyItemInserted(points.size - 1)
    }

    fun getPoints(): List<LabeledPoint> = points.toList()

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                points[i] = points[i + 1].also { points[i + 1] = points[i] }
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                points[i] = points[i - 1].also { points[i - 1] = points[i] }
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    inner class PointViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val pointIdText: TextView = itemView.findViewById(R.id.tv_point_id)
        private val removeButton: ImageView = itemView.findViewById(R.id.iv_remove)
        private val dragHandle: ImageView = itemView.findViewById(R.id.iv_drag_handle)

        fun bind(point: LabeledPoint, position: Int) {
            pointIdText.text = point.id

            removeButton.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onRemoveClick(adapterPosition)
                }
            }

            dragHandle.setOnLongClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onDragStart(this)
                    true
                } else {
                    false
                }
            }
        }
    }
}

