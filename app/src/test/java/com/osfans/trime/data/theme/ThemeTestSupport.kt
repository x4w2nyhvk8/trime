/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.charleskorn.kaml.YamlMap
import com.osfans.trime.util.mapping
import java.io.File

/**
 * Loads themes for the unit tests. Paths are relative to the app module directory
 * (the unit-test working directory).
 *
 * The built-in themes are v3 theme packages (`<id>/theme.yaml` plus
 * `day.yaml`/`night.yaml`), so they are read through [ThemeLoader]'s own package
 * path, diagnostics included: a built-in theme that lints with errors fails the
 * load, exactly like at runtime.
 *
 * The fixtures under `src/test/assets` are plain theme bodies: the old
 * single-file format is gone (doc/Theme.md §6), so they are decoded without any
 * adaptation.
 */
object ThemeTestSupport {
    /** The parser themes are read with in production, so fixtures see the same configuration. */
    val yaml = ThemeYaml.parser

    private const val BUILTIN_DIR = "src/main/assets/shared"

    /** Decodes a plain YAML file as a theme body (test fixtures only). */
    fun decodeThemeFile(relativePath: String): Theme {
        val file = File(relativePath)
        check(file.isFile) { "Theme fixture not found: $relativePath (cwd=${File(".").absolutePath})" }
        val mapping = yaml.parseToYamlNode(file.readText()).mapping ?: error("$relativePath: YAML root is not a mapping")
        return yaml.decodeFromYamlNode<Theme>(mapping)
    }

    /** The package directory of a built-in theme. */
    fun builtinThemeDir(themeId: String): File = File("$BUILTIN_DIR/$themeId")

    /** Loads a built-in theme package, refusing it when its diagnostics have errors. */
    fun decodeBuiltinTheme(themeId: String): Theme {
        val dir = builtinThemeDir(themeId)
        check(dir.isDirectory) { "Theme package not found: $dir (cwd=${File(".").absolutePath})" }
        return when (val result = ThemeLoader.loadThemePackage(themeId, dir)) {
            is ThemeLoader.ThemeLoadResult.Success -> result.theme
            is ThemeLoader.ThemeLoadResult.Failure -> error("$themeId: ${result.error.message}")
        }
    }

    /** The raw `theme.yaml` mapping of a built-in package, for the linter. */
    fun builtinThemeNode(themeId: String): YamlMap {
        val file = File(builtinThemeDir(themeId), ThemeLoader.THEME_FILE)
        check(file.isFile) { "Theme file not found: $file (cwd=${File(".").absolutePath})" }
        return yaml.parseToYamlNode(file.readText()).mapping ?: error("$file: YAML root is not a mapping")
    }
}
