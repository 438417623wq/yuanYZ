package com.goodzh.converter.domain

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

class ModelManager(private val context: Context) {
    val modelDir: File = File(context.filesDir, "vosk-model")
    private val bundledAssetRoot = "vosk-model/vosk-model-small-cn-0.22"

    fun hasModel(): Boolean =
        File(modelDir, "am/final.mdl").exists() ||
            File(modelDir, "final.mdl").exists() ||
            File(modelDir, "graph/HCLG.fst").exists()

    suspend fun ensureBundledModelInstalled(): Boolean = withContext(Dispatchers.IO) {
        if (hasModel()) return@withContext true

        val entries = context.assets.list(bundledAssetRoot).orEmpty()
        if (entries.isEmpty()) return@withContext false

        if (modelDir.exists()) modelDir.deleteRecursively()
        modelDir.mkdirs()
        copyAssetDirectory(bundledAssetRoot, modelDir)
        hasModel()
    }

    suspend fun importZip(uri: Uri): File = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "vosk-model-import")
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取模型压缩包" }
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val outFile = File(staging, entry.name).canonicalFile
                    require(outFile.path.startsWith(staging.canonicalPath)) { "模型压缩包路径不安全" }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { output -> zip.copyTo(output) }
                    }
                    zip.closeEntry()
                }
            }
        }

        val root = staging.findModelRoot() ?: error("压缩包内没有找到 Vosk 模型文件")
        if (modelDir.exists()) modelDir.deleteRecursively()
        root.copyRecursively(modelDir, overwrite = true)
        staging.deleteRecursively()
        modelDir
    }

    private fun copyAssetDirectory(assetPath: String, targetDir: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            targetDir.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                targetDir.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }

        targetDir.mkdirs()
        children.forEach { child ->
            copyAssetDirectory("$assetPath/$child", File(targetDir, child))
        }
    }
}

private fun File.findModelRoot(): File? =
    walkTopDown()
        .filter { it.isDirectory }
        .firstOrNull { dir ->
            File(dir, "am/final.mdl").exists() ||
                File(dir, "final.mdl").exists() ||
                File(dir, "graph/HCLG.fst").exists()
        }
