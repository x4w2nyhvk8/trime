/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.data.theme.model.ItemStyle
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.PanelStyle
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.orAbsent
import com.osfans.trime.util.mapping
import com.osfans.trime.util.pairs
import com.osfans.trime.util.string
import timber.log.Timber
import java.io.File

/**
 * Loads a v3 theme package: `theme.yaml` is the body of the theme, and
 * `day.yaml` / `night.yaml` are its day and night color tables.
 *
 * The package is self-contained, so the librime DSL is never expanded: a theme
 * is read as it is written. Everything ends in [decodeAndReport], which decodes
 * `theme.yaml`, fills in the color tables and refuses a theme whose diagnostics
 * have errors instead of loading it half-broken.
 *
 * Failures are reported as [ThemeLoadError] instead of a bare log line, so
 * callers can fall back and surface diagnostics (YAML syntax errors carry
 * line/column info).
 */
object ThemeLoader {
    /** The body of a theme package. */
    const val THEME_FILE = "theme.yaml"

    /** Required day color table of a theme package. */
    const val DAY_FILE = ResolvedColors.DAY_FILE

    /** Optional night color table of a theme package. */
    const val NIGHT_FILE = ResolvedColors.NIGHT_FILE

    /** Suffix a theme package directory may carry (`<id>.trime/`). */
    private const val PACKAGE_SUFFIX = ".trime"

    /** Structured failure of a single theme load. */
    sealed class ThemeLoadError(
        val themeId: String,
        message: String,
        cause: Throwable? = null,
    ) : Exception(message, cause) {
        /** No theme package named [path] exists in the data directories. */
        class FileNotFound(
            themeId: String,
            val path: String,
        ) : ThemeLoadError(themeId, "Theme package not found: $path")

        class FileUnreadable(
            themeId: String,
            val path: String,
            cause: Throwable,
        ) : ThemeLoadError(themeId, "Cannot read theme file: $path", cause)

        /** Not valid YAML; [cause] message usually includes line/column info. */
        class YamlParseError(
            themeId: String,
            cause: Throwable,
        ) : ThemeLoadError(themeId, "Failed to parse theme YAML: ${cause.message}", cause)

        /** Root is not a mapping, or the theme structure is invalid. */
        class InvalidStructure(
            themeId: String,
            detail: String,
            cause: Throwable? = null,
        ) : ThemeLoadError(themeId, "Invalid theme structure: $detail", cause)

        /**
         * A part a theme package must declare is missing, e.g. `theme.yaml` or
         * `day.yaml`. A missing optional part (`night.yaml`) falls back instead.
         */
        class MissingPart(
            themeId: String,
            val part: String,
        ) : ThemeLoadError(themeId, "Theme package is missing '$part'")

        /**
         * The theme declares no day color table, so there is nothing the
         * runtime could render it with.
         */
        class NoDayColors(
            themeId: String,
        ) : ThemeLoadError(themeId, "No day color table is defined")

        /**
         * Diagnostics found error-level problems, so the theme is refused
         * instead of loading half-broken (see `doc/Theme.md` §5: a color table
         * carrying structural keys is a hard error). The findings are carried
         * so the reason survives the refusal.
         */
        class DiagnosticErrors(
            themeId: String,
            val findings: List<ThemeDiagnostics.Finding>,
            detail: String,
        ) : ThemeLoadError(themeId, "Theme has error-level diagnostics ($detail)")
    }

    sealed interface ThemeLoadResult {
        data class Success(
            val themeId: String,
            val theme: Theme,
            /**
             * What static checks found in the theme, or null when the checks
             * could not run at all.
             */
            val findings: List<ThemeDiagnostics.Finding>? = null,
        ) : ThemeLoadResult

        data class Failure(
            val themeId: String,
            val error: ThemeLoadError,
        ) : ThemeLoadResult
    }

    /**
     * Loads [themeId] from its theme package. Never throws: returns
     * [ThemeLoadResult.Success] or [ThemeLoadResult.Failure] with a structured
     * [ThemeLoadError].
     */
    fun loadTheme(themeId: String): ThemeLoadResult {
        val dir = findThemePackageDir(themeId)
        if (dir == null) {
            return ThemeLoadResult.Failure(themeId, ThemeLoadError.FileNotFound(themeId, themeId))
        }
        return loadThemePackage(themeId, dir)
    }

    /**
     * Reads a theme package: `theme.yaml` is required, `day.yaml` is required
     * and `night.yaml` is optional (a missing one falls back to `day.yaml` with
     * a warning). The package is self-contained, so the librime DSL is not
     * expanded.
     */
    internal fun loadThemePackage(
        themeId: String,
        dir: File,
    ): ThemeLoadResult {
        val themeMapping = when (val part = readPart(themeId, File(dir, THEME_FILE), THEME_FILE)) {
            is PartRead.Err -> return part.failure

            is PartRead.Ok ->
                part.node.mapping
                    ?: return invalidStructure(themeId, "$THEME_FILE root is not a mapping")
        }
        val dayNode = when (val part = readPart(themeId, File(dir, DAY_FILE), DAY_FILE)) {
            is PartRead.Err -> return part.failure

            is PartRead.Ok ->
                part.node.mapping
                    ?: return invalidStructure(themeId, "$DAY_FILE root is not a mapping")
        }

        val nightFile = File(dir, NIGHT_FILE)
        val nightNode =
            if (nightFile.isFile) {
                when (val nightPart = readPart(themeId, nightFile, NIGHT_FILE)) {
                    is PartRead.Err -> return nightPart.failure

                    is PartRead.Ok ->
                        nightPart.node.mapping
                            ?: return invalidStructure(themeId, "$NIGHT_FILE root is not a mapping")
                }
            } else {
                Timber.w("Theme '%s' has no '$NIGHT_FILE', falling back to '$DAY_FILE'", themeId)
                null
            }

        return decodeAndReport(
            themeId,
            themeMapping,
            ResolvedColors(
                day = dayNode.pairs.flatColors(),
                night = nightNode?.pairs?.flatColors(),
                dayNode = dayNode,
                nightNode = nightNode,
            ),
            packageDir = dir,
        )
    }

    /**
     * Locates the package directory of [themeId], trying the plain directory
     * name first and then its `.trime`-suffixed spelling, in [roots] order
     * (user data before shared data). A name that resolves outside its root is
     * refused.
     */
    internal fun findThemePackageDir(
        themeId: String,
        roots: List<File> = listOf(DataManager.userDataDir, DataManager.sharedDataDir),
    ): File? {
        val names = listOf(themeId, themeId + PACKAGE_SUFFIX)
        return roots.firstNotNullOfOrNull { root ->
            names.firstNotNullOfOrNull { name ->
                // A directory that only has `day.yaml` is a broken package, not
                // a miss: it is reported as a missing `theme.yaml`.
                root.resolve(name).takeIf { dir ->
                    (File(dir, THEME_FILE).isFile || File(dir, DAY_FILE).isFile) && dir.isInside(root)
                }
            }
        }
    }

    /**
     * Resolves every layout entry's `key:` identity into the action it stands for
     * (doc/Keyboard.md §2.1): a `preset_keys` name becomes `click: <name>`, a
     * built-in character key name (doc/Keyboard.md §1.3) becomes
     * `click: <character>`.
     *
     * An explicit `click` always wins, so an entry that already carries one keeps
     * it, and a name that resolves to neither is left for
     * [ThemeDiagnostics] to report (it is also allowed when the entry carries an
     * explicit `click`, since the name then only names the key's identity).
     *
     * The normalization happens once, at load time, so the runtime (Key,
     * KeyView) never sees a `key:` that still needs resolving.
     */
    internal fun resolveKeyIdentities(theme: Theme): Map<String, TextKeyboard> = theme.presetKeyboards.mapValues { (_, keyboard) ->
        // The leaves carry the identity; resolving them in the tree keeps the
        // flat view (and every other consumer) in sync (doc/Keyboard.md §2.6).
        keyboard.copy(keys = keyboard.keys.mapLeaves { key -> key.resolvedIdentity(theme.presetKeys) })
    }

    private fun TextKeyboard.TextKey.resolvedIdentity(presetKeys: Map<String, PresetKey>): TextKeyboard.TextKey {
        if (key.isEmpty() || click.orAbsent != null) return this
        val action = if (key in presetKeys) key else CharKeyNames.of(key) ?: return this
        return copy(click = KeyActionToken.Plain(action))
    }

    /**
     * The `styles:` table decoded for one family (doc/Theme.md §8.1). The table
     * is shared by three families and each reads its own fields out of the same
     * nodes, so the loader decodes it once per family beside the key-family
     * decode. A node this family cannot read is skipped rather than failing the
     * whole load: it either belongs to another family — which the diagnostics
     * reports as a field/family mismatch — or carries a bad value, and either
     * way the panel falls back to its built-in default.
     */
    private inline fun <reified T> decodeStyleFamily(mapping: YamlMap): Map<String, T> {
        val styles = mapping.pairs["styles"]?.mapping ?: return emptyMap()
        return styles.pairs
            .mapNotNull { (name, node) ->
                runCatching { name to ThemeYaml.parser.decodeFromYamlNode<T>(node) }.getOrNull()
            }.toMap()
    }

    /**
     * Decodes [mapping] and reports what the runtime ignores or cannot resolve
     * in it, so a theme is checked when it is read instead of on first use.
     * Diagnostics never affect the result of a load.
     *
     * A theme whose day color table is empty is refused: it decodes, but there
     * is nothing for the runtime to render, so loading it would only fail
     * later.
     *
     * @param packageDir the theme package the tables came from, so the runtime
     *   can resolve this theme's own `images/` (doc/Theme.md §3); null when the
     *   caller has no package directory.
     */
    internal fun decodeAndReport(
        themeId: String,
        mapping: YamlMap,
        colors: ResolvedColors,
        packageDir: File? = null,
    ): ThemeLoadResult {
        val decoded = ThemeYaml.parser.decodeFromYamlNode<Theme>(mapping)
        val theme =
            decoded.copy(
                dayColors = colors.day,
                nightColors = colors.night,
                packageDir = packageDir,
                panelStyles = decodeStyleFamily<PanelStyle>(mapping),
                itemStyles = decodeStyleFamily<ItemStyle>(mapping),
                presetKeyboards = resolveKeyIdentities(decoded),
            )
        if (theme.dayColors.isEmpty()) {
            return ThemeLoadResult.Failure(themeId, ThemeLoadError.NoDayColors(themeId))
        }
        val findings =
            runCatching { ThemeDiagnostics.lint(theme, mapping, colors) }
                .onFailure { Timber.w(it, "Theme '%s': diagnostics failed", themeId) }
                .getOrNull()
        findings?.let { ThemeDiagnostics.log(themeId, it) }
        // Error-level findings mean the theme cannot be rendered faithfully
        // (doc/Theme.md §5), so it is refused rather than loaded half-broken.
        val errors = findings.orEmpty().filter { it.severity == ThemeDiagnostics.Severity.ERROR }
        if (errors.isNotEmpty()) {
            return ThemeLoadResult.Failure(
                themeId,
                ThemeLoadError.DiagnosticErrors(
                    themeId,
                    errors,
                    errors.joinToString("; ") { "${it.path ?: "theme"}: ${it.message}" },
                ),
            )
        }
        return ThemeLoadResult.Success(themeId, theme, findings)
    }

    /** Whether this file really lives in [root]: `..` and absolute ids are escapes. */
    private fun File.isInside(root: File): Boolean = runCatching {
        canonicalFile.toPath().startsWith(root.canonicalFile.toPath())
    }.getOrDefault(false)

    private fun invalidStructure(
        themeId: String,
        detail: String,
    ): ThemeLoadResult = ThemeLoadResult.Failure(themeId, ThemeLoadError.InvalidStructure(themeId, detail))

    /** A parsed theme part, or the failure that replaces the whole load. */
    private sealed interface PartRead {
        data class Ok(val node: YamlNode) : PartRead

        data class Err(val failure: ThemeLoadResult.Failure) : PartRead
    }

    /** Reads and parses one part of a theme package, or says why it could not. */
    private fun readPart(
        themeId: String,
        file: File,
        part: String,
    ): PartRead {
        if (!file.isFile) {
            return PartRead.Err(ThemeLoadResult.Failure(themeId, ThemeLoadError.MissingPart(themeId, part)))
        }
        val content =
            try {
                file.readText()
            } catch (e: Exception) {
                return PartRead.Err(
                    ThemeLoadResult.Failure(themeId, ThemeLoadError.FileUnreadable(themeId, file.path, e)),
                )
            }
        return try {
            PartRead.Ok(ThemeYaml.parser.parseToYamlNode(content))
        } catch (e: Exception) {
            PartRead.Err(ThemeLoadResult.Failure(themeId, ThemeLoadError.YamlParseError(themeId, e)))
        }
    }
}

/**
 * The scalar entries of a node mapping, dropping non-scalar (structural) values:
 * a value that is a mapping or a list is a structural entry, not a color, so it
 * never reaches a color table.
 */
private fun Map<String, YamlNode>.flatColors(): ColorScheme = mapNotNull { (key, node) ->
    node.string?.let { key to it }
}.toMap()
