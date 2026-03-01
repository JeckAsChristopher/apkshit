package com.mide.ide.ui

import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.mide.ide.project.ProjectTemplate

class NewProjectDialog(
    private val onProjectCreate: (name: String, packageName: String, template: ProjectTemplate) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }

        val nameInput = EditText(ctx).apply { hint = "Project name (e.g. MyApp)" }
        val packageInput = EditText(ctx).apply { hint = "Package name (e.g. com.example.myapp)" }
        
        val templateSpinner = Spinner(ctx)
        val templates = arrayOf("Empty Java", "Empty Kotlin", "Hello World Java", "Hello World Kotlin")
        templateSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, templates).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        layout.addView(TextView(ctx).apply { text = "Project Name" })
        layout.addView(nameInput)
        layout.addView(TextView(ctx).apply { text = "Package Name"; setPadding(0, 16, 0, 0) })
        layout.addView(packageInput)
        layout.addView(TextView(ctx).apply { text = "Template"; setPadding(0, 16, 0, 0) })
        layout.addView(templateSpinner)

        return AlertDialog.Builder(ctx)
            .setTitle("New Project")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val name = nameInput.text.toString().trim()
                val pkg = packageInput.text.toString().trim().ifEmpty {
                    "com.example.${name.lowercase().replace(Regex("[^a-z0-9]"), "")}"
                }
                val template = when (templateSpinner.selectedItemPosition) {
                    0 -> ProjectTemplate.EMPTY_JAVA
                    1 -> ProjectTemplate.EMPTY_KOTLIN
                    2 -> ProjectTemplate.HELLO_WORLD_JAVA
                    3 -> ProjectTemplate.HELLO_WORLD_KOTLIN
                    else -> ProjectTemplate.EMPTY_JAVA
                }
                if (name.isNotEmpty()) onProjectCreate(name, pkg, template)
            }
            .setNegativeButton("Cancel", null)
            .create()
    }
}
