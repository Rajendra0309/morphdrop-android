package com.morphdrop.app.util

import android.content.Context
import android.net.Uri

/**
 * Lifecycle of files MorphDrop copies in for a share.
 *
 * A shared file is copied into `cache/shared_incoming/<unique>/` (MainActivity)
 * or written to `files/shared_text/` (plain-text shares). Copies are app-private
 * and nobody else cleans them, so without pruning they grow forever — and a
 * share that ends in a toast (incompatible multi-file share) used to do the
 * full copy and then leak every file it had just copied.
 *
 * Rules:
 * - A copy is deleted when its share is dismissed or rejected, so a dead
 *   share leaves nothing behind.
 * - Anything older than [MAX_AGE_MS] is pruned at app launch. A week is
 *   generous for a share: the pending share itself lives only in memory, so
 *   nothing still referenced by the UI can be that old, and a conversion
 *   worker that still needs its input will have finished long before.
 */
object SharedFileCleanup {

    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    /** True when [uri] is one of MorphDrop's own cache copies, never a sender's file. */
    fun isOwnCacheCopy(context: Context, uri: Uri): Boolean {
        if (uri.authority != "${context.packageName}.fileprovider") return false
        val path = uri.path ?: return false
        return path.contains("shared_incoming")
    }

    /** Deletes [uri] only when it is one of MorphDrop's own cache copies. */
    fun deleteIfOwnCacheCopy(context: Context, uri: Uri) {
        if (!isOwnCacheCopy(context, uri)) return
        runCatching {
            val file = java.io.File(context.cacheDir, uri.path?.substringAfter("shared_incoming/")?.let {
                "shared_incoming/$it"
            } ?: return)
            if (file.exists()) file.delete()
            // Each copy lives in its own subfolder; remove the folder too
            // when it is left empty, so no orphan dirs accumulate.
            file.parentFile?.takeIf { it.exists() && it.list()?.isEmpty() == true }?.delete()
        }
    }

    /** Deletes the app's own cache copies for a whole share, if any. */
    fun deleteShareCopies(context: Context, uris: List<Uri>) {
        uris.forEach { deleteIfOwnCacheCopy(context, it) }
    }

    /** Prunes stale share copies and plain-text shares. Call off the main thread. */
    fun pruneOldSharedFiles(context: Context) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        runCatching {
            context.cacheDir.resolve("shared_incoming").listFiles()
                ?.forEach { dir ->
                    if (dir.lastModified() < cutoff) dir.deleteRecursively()
                }
        }
        runCatching {
            context.filesDir.resolve("shared_text").listFiles()
                ?.forEach { file ->
                    if (file.lastModified() < cutoff) {
                        if (file.isDirectory) file.deleteRecursively() else file.delete()
                    }
                }
        }
    }
}
