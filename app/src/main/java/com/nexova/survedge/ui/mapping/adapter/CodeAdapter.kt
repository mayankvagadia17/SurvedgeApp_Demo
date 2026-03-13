package com.nexova.survedge.ui.mapping.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.nexova.survedge.R

data class CodeItem(
    val abbreviation: String,
    val description: String,
    val indicatorType: IndicatorType
)

enum class IndicatorType {
    POINT,
    LINE
}

class CodeAdapter(
    private var codes: List<CodeItem>,
    private val selectedCodeId: String? = null,
    private val onItemClick: (CodeItem) -> Unit
) : RecyclerView.Adapter<CodeAdapter.CodeViewHolder>() {

    fun updateList(newCodes: List<CodeItem>) {
        codes = newCodes
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CodeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_code, parent, false)
        return CodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: CodeViewHolder, position: Int) {
        val code = codes[position]
        val isSelected = selectedCodeId != null && code.abbreviation == selectedCodeId
        holder.bind(code, isSelected)
        holder.itemView.setOnClickListener { onItemClick(code) }
    }

    override fun getItemCount(): Int = codes.size

    class CodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val indicatorView: View = itemView.findViewById(R.id.view_indicator)
        private val abbreviationText: TextView = itemView.findViewById(R.id.tv_code_abbreviation)
        private val descriptionText: TextView = itemView.findViewById(R.id.tv_code_description)

        fun bind(code: CodeItem, isSelected: Boolean) {
            abbreviationText.text = code.abbreviation
            descriptionText.text = code.description

            // Highlight selected item with a subtle background
            if (isSelected) {
                itemView.setBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.selected_code_background)
                )
            } else {
                val outValue = android.util.TypedValue()
                itemView.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                itemView.setBackgroundResource(outValue.resourceId)
            }

            when (code.indicatorType) {
                IndicatorType.POINT -> {
                    indicatorView.setBackgroundResource(R.drawable.point_type_dot)
                }

                IndicatorType.LINE -> {
                    indicatorView.setBackgroundResource(R.drawable.point_type_line)
                }
            }
        }
    }
}
