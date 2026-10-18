/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import java.io.File

/**
 * Resolves the image files a theme's color values name (a value ending in
 * `.png`/`.webp`/`.jpg`/`.gif`, `.9.png` included; see [ColorTable.isImageValue]).
 *
 * A v3 theme package is self-contained: a name resolves to
 * `images/<mode>/<name>` first and to `images/<name>` second, and never
 * outside the package (doc/Theme.md §3).
 *
 * Pure file lookups: no Android and no data-dir knowledge, so the same code
 * backs the runtime drawable lookup and the linter, and both are JVM-testable.
 */
internal object ThemeResources {
    /** The package directory images live in. */
    const val IMAGES_DIR = "images"

    /**
     * [packageDir]'s image for [value] in [mode]: `images/<mode>/<value>` when
     * it exists, else `images/<value>`, else null. Null means the package does
     * not carry the image, which doc/Theme.md §3 makes a hard error; the caller
     * reports it instead of handing a non-existent path to the decoder.
     */
    fun resolveInPackage(
        packageDir: File,
        mode: ThemeMode,
        value: String,
    ): File? {
        val images = File(packageDir, IMAGES_DIR)
        val modeFile = File(images, "${mode.dirName}/$value")
        if (modeFile.isFile) return modeFile
        return File(images, value).takeIf { it.isFile }
    }
}
