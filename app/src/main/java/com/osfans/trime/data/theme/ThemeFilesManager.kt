/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.util.get
import com.osfans.trime.util.string
import timber.log.Timber
import java.io.File

object ThemeFilesManager {
    /**
     * Lists the theme packages in [dir]: a directory holding a `theme.yaml`.
     * Newest first by the theme file's modification time; when two directories
     * yield the same [ThemeItem.configId] the newer one wins.
     */
    fun listThemes(dir: File): MutableList<ThemeItem> {
        val candidates = themePackages(dir)
        return candidates
            .sortedByDescending { it.lastModified }
            .distinctBy { it.configId }
            .map { ThemeItem(it.configId, it.name) }
            .toMutableList()
    }

    /** A theme candidate and how it was discovered. */
    private data class Candidate(
        val configId: String,
        val name: String,
        val lastModified: Long,
    )

    /**
     * Theme packages: directories holding a `theme.yaml`. The directory name is
     * the id; a recommended `.trime` suffix is dropped from
     * [ThemeItem.configId], and the loader puts it back when looking the package
     * up.
     */
    private fun themePackages(dir: File): List<Candidate> {
        val packages =
            dir.listFiles { file -> file.isDirectory && File(file, ThemeLoader.THEME_FILE).isFile }
                ?: return emptyList()
        return packages.map { packageDir ->
            val configId = packageDir.name.removeSuffix(".trime")
            val themeFile = File(packageDir, ThemeLoader.THEME_FILE)
            Candidate(configId, readPackageName(themeFile) ?: configId, themeFile.lastModified())
        }
    }

    /** Reads the display name straight out of a package's `theme.yaml`. */
    private fun readPackageName(themeFile: File): String? = runCatching {
        ThemeYaml.parser.parseToYamlNode(themeFile.readText())["name"]?.string
    }.getOrElse { e ->
        Timber.w("Failed to decode theme file ${themeFile.absolutePath}: ${e.message}")
        null
    }
}
