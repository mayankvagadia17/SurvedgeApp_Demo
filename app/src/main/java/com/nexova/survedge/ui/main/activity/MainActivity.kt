package com.nexova.survedge.ui.main.activity

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.nexova.survedge.R
import com.nexova.survedge.databinding.ActivityMainBinding
import com.nexova.survedge.ui.base.activity.BaseActivity
import com.nexova.survedge.ui.device.fragment.DeviceFragment
import com.nexova.survedge.ui.mapping.fragment.MappingFragment
import com.nexova.survedge.ui.project.fragment.ProjectsFragment

import com.nexova.survedge.ui.project.fragment.ProjectsFragment.ProjectNavigationListener

class MainActivity : BaseActivity(), ProjectNavigationListener {

    internal lateinit var binding: ActivityMainBinding
    private var deviceFragment: DeviceFragment? = null
    private var projectsFragment: ProjectsFragment? = null
    private var mappingFragment: MappingFragment? = null
    private var activeFragment: Fragment? = null

    private var isNavHiddenByFragment = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge layout (transparent bars are configured in theme)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            if (isImeVisible) {
                binding.bottomNavigationView.visibility = View.GONE
                // When keyboard is open, apply IME bottom inset as padding to root
                v.setPadding(systemBars.left, 0, systemBars.right, ime.bottom)
            } else {
                // Only show BNV if the fragment hasn't requested it to be hidden
                if (!isNavHiddenByFragment) {
                    binding.bottomNavigationView.visibility = View.VISIBLE
                    binding.bottomNavigationView.translationY = 0f // Ensure it's not hidden by animation
                    // When keyboard is closed, apply systemBars bottom (navigation bar) as padding to BNV
                    binding.bottomNavigationView.setPadding(0, 0, 0, systemBars.bottom)
                    // Reset root padding (or handle regular inset)
                    v.setPadding(systemBars.left, 0, systemBars.right, 0)
                } else {
                    // Fragment wants it hidden (e.g. Bottom Sheet is open)
                    binding.bottomNavigationView.visibility = View.GONE
                    // Reset root padding to 0 (remove IME space)
                    v.setPadding(systemBars.left, 0, systemBars.right, 0)
                }
            }
            insets
        }

        setupBottomNavigation()
        if (savedInstanceState == null) {
            deviceFragment = DeviceFragment()
            projectsFragment = ProjectsFragment()
//            mappingFragment = MappingFragment()

            supportFragmentManager.beginTransaction()
                .add(R.id.flFragment, projectsFragment!!, "project")
                .hide(projectsFragment!!)
                .add(R.id.flFragment, deviceFragment!!, "device")
                .commit()
            
            activeFragment = deviceFragment
            binding.bottomNavigationView.selectedItemId = R.id.device
        } else {
            deviceFragment = supportFragmentManager.findFragmentByTag("device") as? DeviceFragment
            projectsFragment = supportFragmentManager.findFragmentByTag("project") as? ProjectsFragment

            activeFragment = when {
                projectsFragment?.isHidden == false -> projectsFragment
                else -> deviceFragment
            }
        }

    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.device -> {
                    showFragment(deviceFragment)
                    true
                }

                R.id.vector -> {
                    val currentMappingFrag = supportFragmentManager.findFragmentByTag("mapping")
                    if (currentMappingFrag != null && currentMappingFrag.isAdded) {
                        showFragment(currentMappingFrag)
                    } else {
                        showFragment(projectsFragment)
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun showFragment(fragment: Fragment?) {
        fragment ?: return
        if (activeFragment === fragment) return
        
        supportFragmentManager.beginTransaction().apply {
            activeFragment?.let { hide(it) }
            if (fragment.isAdded) {
                show(fragment)
            } else {
                add(R.id.flFragment, fragment, fragment.tag)
            }
            commit()
        }
        activeFragment = fragment
    }

    fun setActiveFragment(fragment: Fragment) {
        activeFragment = fragment
    }

    override fun onProjectCreated(projectId: Long) {
        // Create or Update MappingFragment
        
        val bundle = Bundle().apply {
            putLong("project_id", projectId)
        }
        
        val newMappingFragment = MappingFragment()
        newMappingFragment.arguments = bundle
        mappingFragment = newMappingFragment
        
        supportFragmentManager.beginTransaction().apply {
            activeFragment?.let { hide(it) }
            add(R.id.flFragment, newMappingFragment, "mapping")
            addToBackStack(null) // allow back
            commit()
        }
        activeFragment = newMappingFragment
    }

    override fun onStartCreateProject() {
        val createProjectFragment = com.nexova.survedge.ui.project.fragment.CreateProjectFragment.newInstance()
        createProjectFragment.onProjectCreatedListener = { projectId ->
            supportFragmentManager.popBackStack() // Remove create fragment
            onProjectCreated(projectId) // Navigate to map
        }
        
        supportFragmentManager.beginTransaction().apply {
            activeFragment?.let { hide(it) }
            add(R.id.flFragment, createProjectFragment, com.nexova.survedge.ui.project.fragment.CreateProjectFragment.TAG)
            addToBackStack(null)
            commit()
        }
        // activeFragment = createProjectFragment // Don't track as main tab fragment, it's transient
    }
    
    fun hideBottomNavigation() {
        isNavHiddenByFragment = true
        binding.bottomNavigationView.visibility = View.GONE
    }
    
    fun showBottomNavigation() {
        isNavHiddenByFragment = false
        binding.bottomNavigationView.visibility = View.VISIBLE
    }

    fun setNavHiddenState(hidden: Boolean) {
        isNavHiddenByFragment = hidden
    }
}
