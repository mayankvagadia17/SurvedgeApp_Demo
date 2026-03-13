package com.nexova.survedge.ui.project.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nexova.survedge.databinding.BottomSheetNewProjectBinding

class NewProjectBottomSheet(
    private val onProjectCreated: (String, String?) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetNewProjectBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetNewProjectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCreate.setOnClickListener {
            val name = binding.etProjectName.text.toString().trim()
            val operator = binding.etOperator.text.toString().trim().takeIf { it.isNotEmpty() }

            if (name.isEmpty()) {
                binding.tilProjectName.error = "Name is required"
                return@setOnClickListener
            }

            onProjectCreated(name, operator)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "NewProjectBottomSheet"
    }
}
