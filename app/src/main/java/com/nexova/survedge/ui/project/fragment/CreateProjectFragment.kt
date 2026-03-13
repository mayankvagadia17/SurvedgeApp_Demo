package com.nexova.survedge.ui.project.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.nexova.survedge.databinding.FragmentCreateProjectBinding
import com.nexova.survedge.ui.project.viewmodel.ProjectsViewModel
import kotlinx.coroutines.launch
import androidx.core.view.updatePadding
import androidx.core.view.updateLayoutParams
import androidx.constraintlayout.widget.ConstraintLayout

class CreateProjectFragment : Fragment() {

    private var _binding: FragmentCreateProjectBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProjectsViewModel by viewModels()

    // Callback to notify creation success, if needed by parent
    var onProjectCreatedListener: ((Long) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateProjectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        setupEdgeToEdgeInsets()
        // setupKeyboardVisibilityListener() // Removed
    }
    
    
    // Manual keyboard handling removed in favor of MainActivity global handling
    /*
    private fun setupKeyboardVisibilityListener() {
         ... removed ...
    }
    */

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
            // Original padding is _25sdp.
            val originalTopPadding = resources.getDimensionPixelSize(
                resources.getIdentifier("_25sdp", "dimen", requireContext().packageName)
            )
//            binding.topBar.updatePadding(top = originalTopPadding + statusBarHeight)

            // Button Bottom Margin (Nav Bar)
            // Original margin is _20sdp.
            val originalButtonMargin = resources.getDimensionPixelSize(
                resources.getIdentifier("_20sdp", "dimen", requireContext().packageName)
            )
            binding.btnCreateProject.updateLayoutParams<ConstraintLayout.LayoutParams> {
                val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                bottomMargin = if (imeVisible) {
                    (8 * density).toInt()
                } else {
                    (originalButtonMargin + bottomNavOffset).toInt()
                }
            }
            
            // ScrollView Bottom Padding (Nav Bar + Button)
            // To ensure content scrolls above the fixed button area.
            // Button height (56dp) + Button Bottom Margin + Nav Bar Offset is handled by constraint?
            // Wait, ScrollView is constrained "toTopOf" btnCreateProject.
            // So resizing btnCreateProject (via margin) will automatically push the ScrollView up.
            // We don't need to pad the ScrollView manually if the constraints are correct.
            // XML: app:layout_constraintBottom_toTopOf="@+id/btnCreateProject"
            // So when we increase btnCreateProject's bottom margin, it moves up, pushing ScrollView bottom up.
            // Effectively shrinking ScrollView. This is correct edge-to-edge behavior.

            insets
        }
    }

    private fun setupClickListeners() {
        binding.tvCancel.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnCreateProject.setOnClickListener {
            createProject()
        }

        binding.rowCS.setOnClickListener {
            Toast.makeText(requireContext(), "Select Coordinate System: Defaulting to WGS84", Toast.LENGTH_SHORT).show()
        }

        binding.rowVerticalDatum.setOnClickListener {
            Toast.makeText(requireContext(), "Select Vertical Datum: Defaulting to Ellipsoidal", Toast.LENGTH_SHORT).show()
        }

        binding.rowUnit.setOnClickListener {
            Toast.makeText(requireContext(), "Select Unit: Defaulting to Meters", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createProject() {
        // Hide keyboard first
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)

        val name = binding.etProjectName.text.toString().trim()
        val author = binding.etAuthor.text.toString().trim()
        val cs = binding.tvCSValue.text.toString() // Or use stored value if logic added
        val verticalDatum = binding.tvVDValue.text.toString()
        val unit = binding.tvUnit.text.toString()

        if (name.isEmpty()) {
            binding.etProjectName.error = "Project name is required"
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val projectId = viewModel.createProject(
                name = name,
                operator = author.ifEmpty { null },
                crs = "WGS84", // Hardcoded default for now as implied by UI placeholders
                verticalDatum = verticalDatum,
                distanceUnit = unit
            )
            
            // Notify listener or handle navigation
            // As per MainActivity logic, finding this fragment and setting callbacks might be tricky if not standardized.
            // But usually we just pop back.
            // If we need to open the map immediately:
            onProjectCreatedListener?.invoke(projectId)
            
            // Or if we just want to go back to list (which observes DB):
            // parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CreateProjectFragment"
        fun newInstance() = CreateProjectFragment()
    }
}
