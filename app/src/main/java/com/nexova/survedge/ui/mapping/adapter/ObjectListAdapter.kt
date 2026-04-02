package com.nexova.survedge.ui.mapping.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nexova.survedge.R

data class ObjectListItem(
    val id: String,
    val codeId: String,
    val dateTime: String,
    val indicatorType: IndicatorType,
    val pointCount: Int = 1,
    val distance: Double = 0.0,
    var isExpanded: Boolean = false,
    var nestedPoints: List<ObjectListItem>? = null
)

class ObjectListAdapter(
    private var objects: MutableList<ObjectListItem>,
    private val isOrderable: Boolean = false,
    private val onDelete: (ObjectListItem) -> Unit = {},
    private val onItemClick: (ObjectListItem) -> Unit = {},
    private val onArrowClick: (ObjectListItem) -> Unit = {},
    private val onDragStart: (RecyclerView.ViewHolder) -> Unit = {}
) : RecyclerView.Adapter<ObjectListAdapter.ObjectViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ObjectViewHolder {
        val layoutId = if (isOrderable) R.layout.item_edit_point else R.layout.item_object_list
        val view = LayoutInflater.from(parent.context)
            .inflate(layoutId, parent, false)
        return ObjectViewHolder(view, isOrderable)
    }

    override fun onBindViewHolder(holder: ObjectViewHolder, position: Int) {
        val item = objects[position]
        holder.bind(item, isOrderable, onDelete, onItemClick, onDragStart)
        
        val clickTarget = holder.mainItemLayout ?: holder.itemView
        clickTarget.setOnClickListener { onItemClick(item) }
        
        holder.arrowView?.setOnClickListener {
            if (item.indicatorType == IndicatorType.LINE) {
                item.isExpanded = !item.isExpanded
                notifyItemChanged(position)
                onArrowClick(item)
            } else {
                onArrowClick(item)
            }
        }
    }

    override fun getItemCount(): Int = objects.size
    
    fun updateItems(newItems: List<ObjectListItem>) {
        if (this.objects !== newItems) {
            this.objects.clear()
            this.objects.addAll(newItems)
        }
        notifyDataSetChanged()
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition !in objects.indices || toPosition !in objects.indices || fromPosition == toPosition) {
            return
        }

        val movedItem = objects.removeAt(fromPosition)
        objects.add(toPosition, movedItem)
        notifyItemMoved(fromPosition, toPosition)
    }

    class ObjectViewHolder(itemView: View, private val isOrderable: Boolean) : RecyclerView.ViewHolder(itemView) {
        // Views for item_object_list
        private val iconView: ImageView? = itemView.findViewById(R.id.iv_object_icon)
        private val nameText: TextView? = itemView.findViewById(R.id.tv_object_name)
        private val dateText: TextView? = itemView.findViewById(R.id.tv_object_date)
        val arrowView: ImageView? = itemView.findViewById(R.id.iv_object_arrow)
        val rvNestedPoints: androidx.recyclerview.widget.RecyclerView? = itemView.findViewById(R.id.rv_nested_points)
        val mainItemLayout: View? = itemView.findViewById(R.id.ll_main_item)
        
        // Views for item_edit_point
        private val removeView: ImageView? = itemView.findViewById(R.id.iv_remove)
        private val pointIdText: TextView? = itemView.findViewById(R.id.tv_point_id)
        private val dragHandleView: ImageView? = itemView.findViewById(R.id.iv_drag_handle)
        private val typeDotView: ImageView? = itemView.findViewById(R.id.view_type_dot)

        fun bind(
            item: ObjectListItem,
            isOrderable: Boolean,
            onDelete: (ObjectListItem) -> Unit,
            onItemClick: (ObjectListItem) -> Unit,
            onDragStart: (RecyclerView.ViewHolder) -> Unit = {}
        ) {
            val displayName = item.id.ifEmpty { item.codeId.ifEmpty { "No code" } }
            
            if (isOrderable) {
                // Binding for item_edit_point
                pointIdText?.text = displayName
                removeView?.setOnClickListener { onDelete(item) }
                
                dragHandleView?.setOnLongClickListener {
                    onDragStart(this)
                    true
                }
                
                // Add short click to handle to allow startDrag immediately if desired by the ItemTouchHelper
                dragHandleView?.setOnTouchListener { _, event ->
                    if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                        onDragStart(this)
                    }
                    false
                }
            } else {
                // Binding for item_object_list
                nameText?.text = displayName
                
                // Set icon based on type
                when (item.indicatorType) {
                    IndicatorType.POINT -> {
                        iconView?.setImageResource(R.drawable.point_type_dot)
                        dateText?.text = item.dateTime
                        arrowView?.visibility = View.GONE
                        rvNestedPoints?.visibility = View.GONE
                    }
                    IndicatorType.LINE -> {
                        iconView?.setImageResource(R.drawable.point_type_line)
                        // For lines, show "X Points - Y M"
                        val distanceText = if (item.distance > 0) {
                            String.format("%.1f M", item.distance)
                        } else {
                            ""
                        }
                        val lineInfo = if (item.pointCount > 1) {
                            "${item.pointCount} Points${if (distanceText.isNotEmpty()) " - $distanceText" else ""}"
                        } else {
                            item.dateTime
                        }
                        dateText?.text = lineInfo
                        arrowView?.visibility = View.VISIBLE
                        arrowView?.rotation = if (item.isExpanded) 90f else 0f
                        
                        if (item.isExpanded && item.nestedPoints != null) {
                            rvNestedPoints?.visibility = View.VISIBLE
                            rvNestedPoints?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(itemView.context)
                            rvNestedPoints?.adapter = ObjectListAdapter(item.nestedPoints!!.toMutableList(), onItemClick = onItemClick)
                        } else {
                            rvNestedPoints?.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }
}

