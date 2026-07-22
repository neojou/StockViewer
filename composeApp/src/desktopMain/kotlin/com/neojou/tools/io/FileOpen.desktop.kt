package com.neojou.tools.io

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.charset.StandardCharsets
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

actual suspend fun pickOpenFilePath(
    title: String,
    extensionFilterLabel: String,
    allowedExtensions: List<String>,
): String? = withContext(Dispatchers.IO) {
    suspendCancellableCoroutine { cont ->
        val exts = allowedExtensions.map { it.lowercase() }
        val show = Runnable {
            val owner = Frame()
            try {
                owner.isUndecorated = true
                owner.isVisible = false
                val dialog = FileDialog(owner, title, FileDialog.LOAD)
                dialog.setFilenameFilter { _, name ->
                    val lower = name.lowercase()
                    exts.any { lower.endsWith(".$it") }
                }
                dialog.isVisible = true
                val dir = dialog.directory
                val file = dialog.file
                if (dir == null || file == null) {
                    cont.resume(null)
                } else {
                    cont.resume(File(dir, file).absolutePath)
                }
            } catch (_: Throwable) {
                cont.resume(null)
            } finally {
                owner.dispose()
            }
        }
        if (SwingUtilities.isEventDispatchThread()) {
            show.run()
        } else {
            SwingUtilities.invokeLater(show)
        }
    }
}

actual suspend fun readTextFileUtf8(path: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        File(path).readText(StandardCharsets.UTF_8)
    }
}
