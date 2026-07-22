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

actual suspend fun pickSaveFilePath(
    title: String,
    suggestedFileName: String,
    extensionFilterLabel: String,
    extension: String,
): String? = withContext(Dispatchers.IO) {
    suspendCancellableCoroutine { cont ->
        val show = Runnable {
            val owner = Frame()
            try {
                owner.isUndecorated = true
                owner.isVisible = false
                val dialog = FileDialog(owner, title, FileDialog.SAVE)
                dialog.file = suggestedFileName
                dialog.setFilenameFilter { _, name ->
                    name.endsWith(".$extension", ignoreCase = true) || !name.contains('.')
                }
                dialog.isVisible = true
                val dir = dialog.directory
                val file = dialog.file
                if (dir == null || file == null) {
                    cont.resume(null)
                } else {
                    var path = File(dir, file).absolutePath
                    val ext = ".$extension"
                    if (!path.endsWith(ext, ignoreCase = true)) {
                        path += ext
                    }
                    cont.resume(path)
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

actual suspend fun writeTextFileUtf8(
    path: String,
    content: String,
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(content, StandardCharsets.UTF_8)
    }
}
