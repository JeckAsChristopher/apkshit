package com.mide.ide

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.commit
import com.google.android.material.navigation.NavigationView
import com.mide.ide.compiler.BuildManager
import com.mide.ide.databinding.ActivityMainBinding
import com.mide.ide.editor.EditorManager
import com.mide.ide.logcat.LogcatFragment
import com.mide.ide.terminal.TerminalFragment
import com.mide.ide.ui.BuildOutputFragment
import com.mide.ide.ui.FileTreeFragment
import com.mide.ide.ui.SettingsActivity

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    lateinit var editorManager: EditorManager
        private set

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) viewModel.onStoragePermissionGranted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        editorManager = EditorManager(this, binding.editorContainer, binding.tabLayout)

        setupDrawer()
        setupBottomNavigation()
        setupBuildButton()
        observeViewModel()
        requestStoragePermissionsIfNeeded()

        if (savedInstanceState == null) {
            showFileTree()
        }
    }

    private fun setupDrawer() {
        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_editor -> {
                    showEditor()
                    true
                }
                R.id.nav_terminal -> {
                    showTerminal()
                    true
                }
                R.id.nav_logcat -> {
                    showLogcat()
                    true
                }
                R.id.nav_build_output -> {
                    showBuildOutput()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupBuildButton() {
        binding.fabBuild.setOnClickListener {
            val project = viewModel.currentProject.value ?: return@setOnClickListener
            val buildManager = BuildManager(this)
            viewModel.startBuild(buildManager, project)
        }
    }

    private fun observeViewModel() {
        viewModel.currentProject.observe(this) { project ->
            supportActionBar?.title = project?.name ?: getString(R.string.app_name)
            binding.fabBuild.isEnabled = project != null
        }
        viewModel.buildProgress.observe(this) { progress ->
            binding.buildProgressBar.progress = progress
            binding.buildProgressBar.visibility = if (progress in 1..99)
                android.view.View.VISIBLE else android.view.View.GONE
        }
        viewModel.buildStatus.observe(this) { status ->
            binding.statusBar.text = status
        }
    }

    private fun showFileTree() {
        supportFragmentManager.commit {
            replace(R.id.file_tree_container, FileTreeFragment())
        }
    }

    private fun showEditor() {
        binding.editorContainer.visibility = android.view.View.VISIBLE
    }

    private fun showTerminal() {
        val existing = supportFragmentManager.findFragmentByTag("terminal")
        if (existing == null) {
            supportFragmentManager.commit {
                replace(R.id.bottom_panel_container, TerminalFragment(), "terminal")
            }
        }
        binding.bottomPanelContainer.visibility = android.view.View.VISIBLE
    }

    private fun showLogcat() {
        val existing = supportFragmentManager.findFragmentByTag("logcat")
        if (existing == null) {
            supportFragmentManager.commit {
                replace(R.id.bottom_panel_container, LogcatFragment(), "logcat")
            }
        }
        binding.bottomPanelContainer.visibility = android.view.View.VISIBLE
    }

    private fun showBuildOutput() {
        val existing = supportFragmentManager.findFragmentByTag("build_output")
        if (existing == null) {
            supportFragmentManager.commit {
                replace(R.id.bottom_panel_container, BuildOutputFragment(), "build_output")
            }
        }
        binding.bottomPanelContainer.visibility = android.view.View.VISIBLE
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_new_project -> viewModel.showNewProjectDialog(this)
            R.id.menu_open_project -> viewModel.showOpenProjectDialog(this)
            R.id.menu_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.menu_plugins -> viewModel.showPluginStore(supportFragmentManager)
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun requestStoragePermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        } else {
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val allGranted = permissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
            if (!allGranted) storagePermissionLauncher.launch(permissions)
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
