package com.mide.ide.project

import java.io.File

enum class ProjectTemplate {
    EMPTY_JAVA, EMPTY_KOTLIN, HELLO_WORLD_JAVA, HELLO_WORLD_KOTLIN;

    fun generate(rootDir: File, projectName: String, packageName: String) {
        createDirectoryStructure(rootDir, packageName)
        when (this) {
            EMPTY_JAVA, HELLO_WORLD_JAVA -> generateJavaProject(rootDir, projectName, packageName)
            EMPTY_KOTLIN, HELLO_WORLD_KOTLIN -> generateKotlinProject(rootDir, projectName, packageName)
        }
    }

    private fun createDirectoryStructure(rootDir: File, packageName: String) {
        val packagePath = packageName.replace('.', '/')
        listOf(
            "src/main/java/$packagePath",
            "src/main/kotlin/$packagePath",
            "src/main/res/layout",
            "src/main/res/values",
            "src/main/res/values-night",
            "src/main/res/drawable",
            "src/main/res/mipmap-hdpi",
            "libs",
            "build"
        ).forEach { File(rootDir, it).mkdirs() }
    }

    private fun generateJavaProject(rootDir: File, projectName: String, packageName: String) {
        val packagePath = packageName.replace('.', '/')
        val srcDir = File(rootDir, "src/main/java/$packagePath")

        File(srcDir, "MainActivity.java").writeText(
            """package $packageName;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        TextView textView = findViewById(R.id.text_view);
        textView.setText("Hello from $projectName!");
    }
}
"""
        )
        writeCommonResources(rootDir, projectName, packageName)
    }

    private fun generateKotlinProject(rootDir: File, projectName: String, packageName: String) {
        val packagePath = packageName.replace('.', '/')
        val srcDir = File(rootDir, "src/main/kotlin/$packagePath")

        File(srcDir, "MainActivity.kt").writeText(
            """package $packageName

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val textView: TextView = findViewById(R.id.text_view)
        textView.text = "Hello from $projectName!"
    }
}
"""
        )
        writeCommonResources(rootDir, projectName, packageName)
    }

    private fun writeCommonResources(rootDir: File, projectName: String, packageName: String) {
        File(rootDir, "src/main/AndroidManifest.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="$packageName">

    <application
        android:label="$projectName"
        android:theme="@style/Theme.AppCompat.Light.DarkActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
"""
        )

        File(rootDir, "src/main/res/layout/activity_main.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center"
    android:orientation="vertical">

    <TextView
        android:id="@+id/text_view"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello World!"
        android:textSize="24sp" />

</LinearLayout>
"""
        )

        File(rootDir, "src/main/res/values/strings.xml").writeText(
            """<resources>
    <string name="app_name">$projectName</string>
</resources>
"""
        )

        File(rootDir, "src/main/res/values/colors.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="primary">#6200EE</color>
    <color name="primary_variant">#3700B3</color>
    <color name="secondary">#03DAC6</color>
    <color name="background">#FFFFFF</color>
    <color name="surface">#FFFFFF</color>
    <color name="on_primary">#FFFFFF</color>
    <color name="on_secondary">#000000</color>
    <color name="on_background">#000000</color>
    <color name="on_surface">#000000</color>
</resources>
"""
        )

        File(rootDir, "src/main/res/values/styles.xml").writeText(
            """<resources>
    <style name="AppTheme" parent="Theme.AppCompat.Light.DarkActionBar">
        <item name="colorPrimary">@color/primary</item>
        <item name="colorPrimaryDark">@color/primary_variant</item>
        <item name="colorAccent">@color/secondary</item>
    </style>
</resources>
"""
        )
    }
}
