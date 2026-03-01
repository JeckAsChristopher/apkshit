# MIDE — Mobile Android IDE

A full-featured Android IDE that runs directly on Android devices. Write, compile, build, and install Android apps entirely from your phone or tablet.

---

## Features

- **Code Editor** — Sora Editor with Java/Kotlin syntax highlighting, LSP autocomplete, line numbers, and bracket matching
- **Project Manager** — Create, open, and manage Android projects with template generation
- **Full Compiler Pipeline** — Java (ECJ) → DEX (D8) → Resources (AAPT2) → APK → Signing
- **Incremental Builds** — MD5 checksum tracking to only recompile changed files
- **Terminal Emulator** — Full shell terminal using Termux library with ANSI color support
- **Logcat Viewer** — Real-time filtered Android logs with per-package filtering
- **Build Output Panel** — Color-coded build logs with error highlighting
- **Plugin System** — ZIP-based plugin installation with permission system and security scanning
- **On-Demand SDK Downloads** — Downloads build tools (D8, AAPT2, android.jar) from Lzhiyong's GitHub releases on first launch
- **Dark Theme** — Catppuccin Mocha color scheme throughout

---

## SDK Tools Source

Build tools are downloaded from **lzhiyong/sdk-tools** on GitHub:
- `https://github.com/lzhiyong/sdk-tools/releases/download/2.0/sdk-tools-{abi}.zip`
- `https://github.com/lzhiyong/sdk-tools/releases/download/2.0/android.jar`

Supported ABIs: `aarch64` (ARM64), `x86_64`, `arm` (ARM32)

---

## Build Requirements

- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 34
- Gradle 8.4

---

## Project Structure

```
MIDE/
├── app/src/main/java/com/mide/ide/
│   ├── MIDEApplication.kt          # App initialization, global managers
│   ├── MainActivity.kt             # Main UI with drawer, bottom nav, FAB
│   ├── MainViewModel.kt            # UI state, build orchestration
│   ├── compiler/
│   │   ├── BuildManager.kt         # Build orchestrator — calls all pipeline steps
│   │   ├── JavaCompiler.kt         # ECJ-based Java compiler
│   │   ├── DexCompiler.kt          # D8 DEX compiler wrapper
│   │   ├── ResourceCompiler.kt     # AAPT2 resource compiler and linker
│   │   ├── ApkPackager.kt          # ZIP-based APK assembly
│   │   ├── ApkSigner.kt            # V1 APK signing, debug keystore generation
│   │   ├── IncrementalCache.kt     # MD5 file checksum cache for incremental builds
│   │   └── BuildResult.kt          # Sealed result types
│   ├── editor/
│   │   ├── EditorManager.kt        # Multi-tab editor manager
│   │   ├── EditorTab.kt            # Per-tab state (cursor, scroll, modified)
│   │   └── CodeEditorFragment.kt   # Editor fragment wrapper
│   ├── project/
│   │   ├── ProjectManager.kt       # Create/load/list/delete projects
│   │   ├── MIDEProject.kt          # Project data model + BuildConfig
│   │   ├── ProjectTemplate.kt      # Java/Kotlin project template generation
│   │   └── FileNode.kt             # File tree node model
│   ├── downloader/
│   │   ├── ToolDownloadManager.kt  # Downloads SDK tools from Lzhiyong's GitHub
│   │   └── ToolDownloadService.kt  # Foreground service for background downloading
│   ├── terminal/
│   │   └── TerminalFragment.kt     # Full terminal using Termux library
│   ├── logcat/
│   │   └── LogcatFragment.kt       # Real-time logcat with package filtering
│   ├── plugin/
│   │   ├── PluginManager.kt        # Install/uninstall plugins with security scan
│   │   ├── PluginManifest.kt       # Plugin metadata data class
│   │   ├── PluginPermission.kt     # Permission enum with descriptions
│   │   └── PluginStoreFragment.kt  # In-app plugin store UI
│   ├── ui/
│   │   ├── FileTreeFragment.kt     # File tree with expand/collapse
│   │   ├── FileTreeAdapter.kt      # RecyclerView adapter for file tree
│   │   ├── BuildOutputFragment.kt  # Color-coded build log panel
│   │   ├── SettingsActivity.kt     # Settings UI (font, tabs, theme, etc.)
│   │   ├── NewProjectDialog.kt     # Project creation dialog with template picker
│   │   └── ToolsStatusDialog.kt    # SDK tools download status dialog
│   └── utils/
│       ├── ChecksumUtils.kt        # MD5 hashing for incremental builds
│       ├── FileUtils.kt            # File type detection, safe read/write
│       └── PreferencesManager.kt  # DataStore-backed preferences
└── res/
    ├── layout/activity_main.xml    # Main layout with drawer + editor + panels
    ├── values/colors.xml           # Catppuccin Mocha color scheme
    ├── values/themes.xml           # Material3 dark theme
    └── menu/                       # Navigation and bottom bar menus
```

---

## Build Pipeline

```
Source Files (.java / .kt)
        ↓
1. Resource Compilation (AAPT2 compile)
        ↓
2. Resource Linking (AAPT2 link → resources.ap_ + R.java)
        ↓
3. Java Compilation (ECJ → .class files)
        ↓
4. DEX Compilation (D8 → classes.dex)
        ↓
5. APK Packaging (ZIP: classes.dex + resources.ap_ + native libs)
        ↓
6. APK Signing (V1 signature, debug or release keystore)
        ↓
Signed .apk (ready for installation)
```

---

## Key Dependencies

| Library | Purpose |
|---------|---------|
| `io.github.Rosemoe.sora-editor` | Code editor with syntax highlighting |
| `org.eclipse.jdt:ecj` | Java compiler (runs on Android, no JDK needed) |
| `com.squareup.okhttp3:okhttp` | HTTP client for SDK tool downloads |
| `com.github.termux.termux-app` | Terminal emulator |
| `org.apache.commons:commons-compress` | ZIP handling for plugins |
| `androidx.datastore:datastore-preferences` | Persistent settings storage |
| `com.google.code.gson:gson` | JSON parsing for project configs and plugin manifests |

---

## First Launch

On first launch, MIDE automatically downloads the required build tools in the background:
1. `sdk-tools-{abi}.zip` from lzhiyong/sdk-tools (contains D8, AAPT2)
2. `android.jar` from lzhiyong/sdk-tools (~70MB, needed for compilation)

A notification shows download progress. The app is usable for browsing and editing during download. Build attempts before download completes show a clear error message.

---

## Plugin System

Plugins are ZIP files containing:
```
my-plugin.zip
├── manifest.json       # id, name, version, permissions, entryPoint
├── index.js            # Plugin JavaScript code
└── icon.png            # Optional icon
```

Security scanning checks for dangerous patterns before installation. Unverified plugins require explicit user opt-in in Settings.

---

## Notes on Kotlin Support

The Kotlin compiler embeddable is not bundled by default due to size (~50MB). To add Kotlin compilation support, add `org.jetbrains.kotlin:kotlin-compiler-embeddable` to dependencies and integrate it in `BuildManager.kt` after the Java compilation step.
