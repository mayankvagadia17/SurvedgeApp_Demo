package com.nexova.survedge.ui.project.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.nexova.survedge.databinding.FragmentProjectsBinding

import androidx.fragment.app.viewModels
import com.nexova.survedge.ui.project.viewmodel.ProjectsViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexova.survedge.ui.project.adapter.ProjectsAdapter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.constraintlayout.widget.ConstraintLayout

class ProjectsFragment : Fragment() {

    interface ProjectNavigationListener {
        fun onProjectCreated(projectId: Long)
        fun onStartCreateProject()
    }

    private val viewModel: ProjectsViewModel by viewModels()
    private val projectsAdapter by lazy {
        ProjectsAdapter(
            onProjectClicked = { project ->
                navigateToMap(project.id)
            },
            onProjectLongClicked = { project ->
                showProjectOptions(project)
            }
        )
    }

    private fun showProjectOptions(project: com.nexova.survedge.data.db.entity.ProjectEntity) {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(com.nexova.survedge.R.layout.bottom_sheet_project_options, null)
        bottomSheetDialog.setContentView(view)

        view.findViewById<android.widget.TextView>(com.nexova.survedge.R.id.tvTitle).text = project.name
        
        view.findViewById<View>(com.nexova.survedge.R.id.btnImport).setOnClickListener {
            importProjectId = project.id
            getContent.launch("application/json")
            bottomSheetDialog.dismiss()
        }

        view.findViewById<View>(com.nexova.survedge.R.id.btnExport).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val csvContent = viewModel.exportProjectPoints(project.id)
                    val fileName = "${project.name}_${System.currentTimeMillis()}.csv"
                    saveFileToDownloads(fileName, csvContent)
                    android.widget.Toast.makeText(requireContext(), "Exported to Downloads/$fileName", android.widget.Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(requireContext(), "Export Failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
                bottomSheetDialog.dismiss()
            }
        }

        bottomSheetDialog.show()
    }

    private var importProjectId: Long = -1

    private val getContent = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        uri?.let {
            try {
                requireContext().contentResolver.openInputStream(it)?.use { inputStream ->
                    val jsonString = java.io.BufferedReader(java.io.InputStreamReader(inputStream)).readText()
                    if (importProjectId != -1L) {
                         viewLifecycleOwner.lifecycleScope.launch {
                             val count = viewModel.importProjectPoints(importProjectId, jsonString)
                             android.widget.Toast.makeText(requireContext(), "$count points imported", android.widget.Toast.LENGTH_LONG).show()
                         }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(requireContext(), "Import Failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveFileToDownloads(fileName: String, content: String) {
        val resolver = requireContext().contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
        }

        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(content.toByteArray())
            }
        } ?: throw java.io.IOException("Failed to create file via MediaStore")
    }

    private var _binding: FragmentProjectsBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        setupEdgeToEdgeInsets()
        observeProjects()
    }

    private fun setupRecyclerView() {
        binding.rvProjects.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = projectsAdapter
        }
    }

    private fun observeProjects() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allProjects.collect { projects ->
                projectsAdapter.submitList(projects)
                updateEmptyState(projects.isEmpty())
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.rvProjects.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.fabNewProject.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.clEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun setupEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val statusBarHeight = systemBars.top
            val navBarHeight = systemBars.bottom
            val density = resources.displayMetrics.density
            
            // Calculate total offset needed to clear Bottom Nav (56dp + system bars)
            val bnvHeight = 56f * density
            val bottomNavOffset = navBarHeight + bnvHeight

            // Header Top Padding (Status Bar)
            // Original padding is _25sdp. We should keep it and add status bar.
            // Since we can't easily resolve sdp dynamically here without context, 
            // we'll fetch the original padding from styles/dimens or just add to current.
            // Assuming the layout inflation already applied the XML padding.
            // But applyWindowInsetsListener might be called multiple times, so adding to *current* is risky.
            // Better to fetch the dimen resource again.
            val originalTopPadding = resources.getDimensionPixelSize(
                resources.getIdentifier("_25sdp", "dimen", requireContext().packageName)
            )
//            binding.header.updatePadding(top = originalTopPadding + statusBarHeight)

            // FAB Bottom Margin (Nav Bar)
            // Original margin is _24sdp.
            val originalFabMargin = resources.getDimensionPixelSize(
                resources.getIdentifier("_24sdp", "dimen", requireContext().packageName)
            )
            binding.fabNewProject.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = (originalFabMargin + bottomNavOffset).toInt()
            }
            
            // RecyclerView Bottom Padding (Nav Bar)
            // So content scrolls above the nav bar.
            // Original padding is 16dp.
            val originalRvPadding = (16 * density).toInt()
            binding.rvProjects.updatePadding(bottom = (originalRvPadding + bottomNavOffset).toInt())

            insets
        }
    }

    private fun setupClickListeners() {
        binding.btnNewProject.setOnClickListener { 
            (activity as? ProjectNavigationListener)?.onStartCreateProject()
        }
        binding.fabNewProject.setOnClickListener { 
            (activity as? ProjectNavigationListener)?.onStartCreateProject()
        }
    }

    private fun navigateToMap(projectId: Long) {
        (activity as? ProjectNavigationListener)?.onProjectCreated(projectId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = ProjectsFragment()
    }
}
