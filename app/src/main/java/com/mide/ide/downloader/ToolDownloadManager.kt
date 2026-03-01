package com.mide.ide.downloader

import android.content.Context
import android.os.Build
import com.mide.ide.MIDEApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

class ToolDownloadManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    private val toolsDir get() = MIDEApplication.get().toolsDir

    private val abi = when {
        Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "aarch64"
        Build.SUPPORTED_ABIS.contains("x86_64") -> "x86_64"
        Build.SUPPORTED_ABIS.contains("armeabi-v7a") -> "arm"
        else -> "aarch64"
    }

    private val sdkToolsZipUrl = "https://github.com/lzhiyong/sdk-tools/releases/download/2.0/sdk-tools-${"$"}abi.zip"
    private val androidJarUrl = "https://github.com/lzhiyong/sdk-tools/releases/download/2.0/android.jar"

    data class DownloadStatus(
        val toolName: String,
        val status: Status,
        val progress: Int = 0,
        val error: String? = null
    )

    enum class Status { PENDING, DOWNLOADING, DONE, FAILED, ALREADY_EXISTS }

    suspend fun checkAndDownloadRequiredTools(
        onProgress: ((DownloadStatus) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        downloadTool(name = "sdk-tools-${"$"}abi.zip", url = sdkToolsZipUrl, isZip = true, onProgress = onProgress)
        downloadTool(name = "android.jar", url = androidJarUrl, isZip = false, onProgress = onProgress)
    }

    private suspend fun downloadTool(
        name: String,
        url: String,
        isZip: Boolean,
        onProgress: ((DownloadStatus) -> Unit)?
    ) {
        if (isZip) {
            if (File(toolsDir, "d8").exists() && File(toolsDir, "aapt2").exists()) {
                onProgress?.invoke(DownloadStatus(name, Status.ALREADY_EXISTS)); return
            }
        } else {
            val target = File(toolsDir, name)
            if (target.exists() && target.length() > 1024) {
                onProgress?.invoke(DownloadStatus(name, Status.ALREADY_EXISTS)); return
            }
        }

        onProgress?.invoke(DownloadStatus(name, Status.DOWNLOADING, 0))
        val request = Request.Builder().url(url).build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            onProgress?.invoke(DownloadStatus(name, Status.FAILED, error = e.message)); return
        }

        if (!response.isSuccessful) {
            onProgress?.invoke(DownloadStatus(name, Status.FAILED, error = "HTTP ${"$"}{response.code}")); return
        }

        val body = response.body ?: run {
            onProgress?.invoke(DownloadStatus(name, Status.FAILED, error = "Empty response")); return
        }

        val totalBytes = body.contentLength()
        var downloadedBytes = 0L
        val tempFile = File(toolsDir, "${"$"}name.tmp")
        toolsDir.mkdirs()

        body.byteStream().use { input ->
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloadedBytes += read
                    if (totalBytes > 0) {
                        onProgress?.invoke(DownloadStatus(name, Status.DOWNLOADING, ((downloadedBytes * 100) / totalBytes).toInt()))
                    }
                }
            }
        }

        if (isZip) { extractZip(tempFile, toolsDir); tempFile.delete() }
        else tempFile.renameTo(File(toolsDir, name))

        onProgress?.invoke(DownloadStatus(name, Status.DONE, 100))
    }

    private fun extractZip(zipFile: File, targetDir: File) {
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (!entry.isDirectory) {
                    val outFile = File(targetDir, entry.name.substringAfterLast("/"))
                    zip.getInputStream(entry).use { it.copyTo(outFile.outputStream()) }
                    if (setOf("aapt2", "d8", "dx", "zipalign", "apksigner").contains(outFile.name)) {
                        outFile.setExecutable(true, false)
                    }
                }
            }
        }
    }

    fun isToolsReady(): Boolean = File(toolsDir, "d8").exists() &&
        File(toolsDir, "aapt2").exists() &&
        File(toolsDir, "android.jar").let { it.exists() && it.length() > 1024 }

    fun getToolStatus(): Map<String, Boolean> = mapOf(
        "d8" to File(toolsDir, "d8").exists(),
        "aapt2" to File(toolsDir, "aapt2").exists(),
        "android.jar" to File(toolsDir, "android.jar").let { it.exists() && it.length() > 1024 }
    )
}
