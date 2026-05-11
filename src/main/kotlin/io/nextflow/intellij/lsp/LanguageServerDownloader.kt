package io.nextflow.intellij.lsp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.util.io.HttpRequests
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object LanguageServerDownloader {
    private val LOG = Logger.getInstance(LanguageServerDownloader::class.java)

    private const val GITHUB_RELEASES_URL = "https://api.github.com/repos/nextflow-io/language-server/releases"
    private const val DOWNLOAD_BASE_URL = "https://github.com/nextflow-io/language-server/releases/download"
    private const val JAR_NAME = "language-server-all.jar"
    private const val CACHE_ROOT_PROPERTY = "nextflow.lsp.cacheRoot"
    const val DEFAULT_VERSION_PREFIX = "v26.04"

    /**
     * Get the cache directory: ~/.nextflow/lsp/{versionPrefix}/
     */
    internal fun getCacheDir(versionPrefix: String): Path {
        val configuredRoot = System.getProperty(CACHE_ROOT_PROPERTY)
        val root = if (configuredRoot.isNullOrBlank()) {
            Path.of(System.getProperty("user.home"), ".nextflow", "lsp")
        } else {
            Path.of(configuredRoot)
        }
        return root.resolve(versionPrefix)
    }

    /**
     * Find the latest cached JAR for the given version prefix.
     */
    fun findCachedJar(versionPrefix: String = DEFAULT_VERSION_PREFIX): Path? {
        val cacheDir = getCacheDir(versionPrefix)
        if (!Files.isDirectory(cacheDir)) return null

        return Files.list(cacheDir).use { stream ->
            stream
                .filter { it.fileName.toString().endsWith(".jar") }
                .sorted { left, right -> compareJarVersions(right, left) }
                .findFirst()
                .orElse(null)
        }
    }

    internal fun compareJarVersions(left: Path, right: Path): Int {
        return compareVersionTags(left.fileName.toString().removeSuffix(".jar"), right.fileName.toString().removeSuffix(".jar"))
    }

    internal fun compareVersionTags(left: String, right: String): Int {
        val leftParts = left.trimStart('v').split('.', '-').map { it.toIntOrNull() ?: 0 }
        val rightParts = right.trimStart('v').split('.', '-').map { it.toIntOrNull() ?: 0 }
        val size = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until size) {
            val leftPart = leftParts.getOrElse(index) { 0 }
            val rightPart = rightParts.getOrElse(index) { 0 }
            if (leftPart != rightPart) return leftPart.compareTo(rightPart)
        }
        return left.compareTo(right)
    }

    /**
     * Resolve the latest release tag from GitHub for the given version prefix.
     * Returns null if the network request fails.
     */
    fun resolveLatestVersion(versionPrefix: String = DEFAULT_VERSION_PREFIX): String? {
        return try {
            val responseText = HttpRequests.request("$GITHUB_RELEASES_URL")
                .accept("application/vnd.github.v3+json")
                .readString()

            // Simple JSON parsing — find tags matching our prefix
            val tagPattern = Regex(""""tag_name"\s*:\s*"(${Regex.escape(versionPrefix)}\.[^"]+)"""")
            val tags = tagPattern.findAll(responseText)
                .map { it.groupValues[1] }
                .filter { !it.endsWith("PREVIEW") }
                .sortedDescending()
                .toList()

            tags.firstOrNull()
        } catch (e: Exception) {
            LOG.info("Failed to query GitHub releases: ${e.message}")
            null
        }
    }

    /**
     * Get the language server JAR path, downloading if necessary.
     * Falls back to cached version if download fails.
     */
    fun getOrDownload(versionPrefix: String = DEFAULT_VERSION_PREFIX): Path? {
        val resolvedVersion = resolveLatestVersion(versionPrefix)
        if (resolvedVersion != null) {
            val cacheDir = getCacheDir(versionPrefix)
            val cachedJar = cacheDir.resolve("$resolvedVersion.jar")
            if (Files.isRegularFile(cachedJar)) {
                LOG.info("Using cached language server: $cachedJar")
                return cachedJar
            }

            // Download
            val downloaded = downloadJar(resolvedVersion, cachedJar)
            if (downloaded != null) return downloaded
        }

        // Fallback to any cached version
        val cached = findCachedJar(versionPrefix)
        if (cached != null) {
            LOG.info("Using cached language server (fallback): $cached")
        } else {
            LOG.warn("No language server JAR available (download failed and no cache)")
        }
        return cached
    }

    /**
     * Download the language server JAR to the given path.
     */
    private fun downloadJar(version: String, targetPath: Path): Path? {
        val url = "$DOWNLOAD_BASE_URL/$version/$JAR_NAME"
        LOG.info("Downloading language server from $url")

        return try {
            Files.createDirectories(targetPath.parent)
            val tempFile = Files.createTempFile(targetPath.parent, "download-", ".tmp")
            try {
                HttpRequests.request(url)
                    .saveToFile(tempFile.toFile(), null)
                Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING)
                LOG.info("Downloaded language server to $targetPath")
                targetPath
            } catch (e: Exception) {
                Files.deleteIfExists(tempFile)
                throw e
            }
        } catch (e: IOException) {
            LOG.warn("Failed to download language server: ${e.message}")
            null
        }
    }

    /**
     * Download the language server with a progress dialog (runs on a background thread).
     */
    fun ensureDownloaded(versionPrefix: String = DEFAULT_VERSION_PREFIX, onComplete: (Path?) -> Unit) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(null, "Downloading Nextflow Language Server...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val result = getOrDownload(versionPrefix)
                onComplete(result)
            }
        })
    }
}
