/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.charleskorn.kaml.YamlMap
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.KeyStyle
import com.osfans.trime.data.theme.model.KeyboardCollection
import com.osfans.trime.data.theme.model.KeyboardNode
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.data.theme.model.orAbsent
import com.osfans.trime.ime.keyboard.KeyCode
import com.osfans.trime.util.ColorUtils
import com.osfans.trime.util.isNull
import com.osfans.trime.util.mapping
import com.osfans.trime.util.pairs
import com.osfans.trime.util.scalar
import com.osfans.trime.util.string
import timber.log.Timber

/**
 * Static checks over a loaded theme. Findings never affect how a theme is
 * loaded: they tell a theme author which settings the runtime ignores, which
 * values it cannot resolve, and which references point at nothing.
 *
 * The checks reuse the validators of the runtime itself — the color tables
 * ([ColorTable]) and the preset checks ([presetDiagnostics]) —
 * so the linter cannot disagree with what the keyboard actually does. A finding
 * can therefore depend on the platform: a preset send is checked against the key
 * names the runtime resolves, and part of them only exist on a device.
 */
object ThemeDiagnostics {
    /** Theme format version this build understands (theme `config_version`). */
    const val SUPPORTED_CONFIG_VERSION = "3.0"

    /**
     * Keys that belong to the theme body ([Theme]), never to a color table:
     * finding one in `day.yaml`/`night.yaml` means it was written in the wrong
     * file.
     */
    private val STRUCTURAL_COLOR_KEYS =
        setOf(
            "layout",
            "key",
            "keys",
            "preset_keys",
            "preset_keyboards",
            "style",
            "action",
            "actions",
        )

    /** Where a finding comes from; also its stable identity in tests. */
    enum class Code {
        UNKNOWN_TOP_LEVEL_KEY,
        UNKNOWN_STYLE_KEY,
        CONFIG_VERSION_MISSING,
        CONFIG_VERSION_INVALID,
        CONFIG_VERSION_UNSUPPORTED,
        NO_COLOR_SCHEME,
        DAY_NIGHT_KEY_MISMATCH,
        STRUCTURAL_KEY_IN_COLORS,
        INVALID_COLOR_VALUE,
        MISSING_THEME_RESOURCE,
        BROKEN_FALLBACK_TARGET,
        UNRESOLVABLE_PRESET_SEND,
        UNKNOWN_STYLE_NAME,
        STYLE_LAYER_MISMATCH,
        STYLE_FAMILY_CONFLICT,
        UNKNOWN_STYLE_FIELD,
        UNKNOWN_KEY_NAME,
        MISSING_KEYBOARD_REFERENCE,
        CYCLIC_KEYBOARD_REFERENCE,
        MISSING_COLLECTION_SOURCE,
        EMPTY_COLLECTION,
    }

    enum class Severity {
        /** The theme works, but the runtime ignores something. */
        INFO,

        /** Likely an authoring mistake. */
        WARNING,

        /** A hard error: a theme the runtime cannot use faithfully (doc/Theme.md §5). */
        ERROR,
    }

    /**
     * @param path location inside the theme, e.g. `style/candidate_text_size`
     *   or `day.yaml/key_back_color`, when the finding points at one node.
     */
    data class Finding(
        val severity: Severity,
        val code: Code,
        val message: String,
        val path: String? = null,
    )

    /**
     * Lints a decoded [theme] together with the [node] it was decoded from,
     * which still holds keys the decoder ignores, and its color tables
     * ([colors], defaulting to the ones the theme carries).
     *
     * @param parseColor parses a color string, null when it is not a color
     *   (dependency-injected for JVM testability).
     */
    fun lint(
        theme: Theme,
        node: YamlMap,
        colors: ResolvedColors = ResolvedColors(theme.dayColors, theme.nightColors),
        parseColor: (String) -> Int? = ::parseColor,
    ): List<Finding> = buildList {
        lintTopLevelKeys(node)
        lintStyleKeys(node)
        lintConfigVersion(node)
        lintColors(theme, colors, parseColor)
        lintThemeResources(theme, colors)
        lintDayNightKeys(colors)
        lintColorTableStructure(colors)
        lintPresetSends(theme)
        lintKeyboardReferences(theme)
        lintCollectionSources(theme)
        lintStyles(theme, node)
    }

    /**
     * Logs [findings] of the theme [themeId], one line each, at their severity.
     * A theme is checked when it is read instead of on first use; the same
     * findings are what [format] renders for a report and what the theme
     * diagnostics screen lists.
     */
    fun log(
        themeId: String,
        findings: List<Finding>,
    ) {
        findings.forEach { finding ->
            val message = "Theme '$themeId': ${finding.message}"
            when (finding.severity) {
                Severity.INFO -> Timber.i(message)
                Severity.WARNING -> Timber.w(message)
                Severity.ERROR -> Timber.e(message)
            }
        }
    }

    /**
     * Renders [findings] as the text of a bug report: which theme was checked
     * and what the checks found. [findings] is null when the checks themselves
     * failed to run, which the report says instead of pretending all is well.
     */
    fun format(
        themeId: String,
        themeName: String,
        findings: List<Finding>?,
    ): String = buildString {
        appendLine("Theme: $themeName ($themeId)")
        when {
            findings == null ->
                appendLine("Static checks could not run for this theme.")

            findings.isEmpty() -> appendLine("No findings.")

            else -> {
                val errors = findings.count { it.severity == Severity.ERROR }
                val warnings = findings.count { it.severity == Severity.WARNING }
                val infos = findings.size - errors - warnings
                appendLine("Findings: ${findings.size} ($errors errors, $warnings warnings, $infos info)")
                findings.forEach { finding ->
                    val path = finding.path?.let { "$it: " } ?: ""
                    appendLine("[${finding.severity}] $path${finding.message} (${finding.code})")
                }
            }
        }
    }

    private fun MutableList<Finding>.reportUnknownKeys(
        node: YamlMap?,
        known: Set<String>,
        path: String,
        code: Code,
        severity: Severity,
    ) {
        node?.pairs?.keys?.forEach { name ->
            if (name in known) return@forEach
            add(
                Finding(
                    severity,
                    code,
                    "unknown key '$name' in '$path'; the runtime ignores it",
                    "$path/$name",
                ),
            )
        }
    }

    private fun MutableList<Finding>.lintTopLevelKeys(node: YamlMap) {
        // `__include`/`__patch` are expansion directives: they never survive
        // expansion, but a theme may still carry them for the librime backend.
        reportUnknownKeys(
            node,
            Theme.TOP_LEVEL_KEYS + "__include" + "__patch",
            path = "",
            code = Code.UNKNOWN_TOP_LEVEL_KEY,
            severity = Severity.INFO,
        )
    }

    private fun MutableList<Finding>.lintStyleKeys(node: YamlMap) {
        reportUnknownKeys(
            node.pairs["style"]?.mapping,
            GeneralStyle.KNOWN_KEYS,
            path = "style",
            code = Code.UNKNOWN_STYLE_KEY,
            severity = Severity.WARNING,
        )
    }

    /**
     * Reads `config_version`, the theme format version. The librime backend
     * only uses it as a deploy cache key, so this is where it is checked
     * against the version this build can read.
     */
    private fun MutableList<Finding>.lintConfigVersion(node: YamlMap) {
        val raw = node.pairs["config_version"]?.string
        if (raw == null) {
            add(
                Finding(
                    Severity.INFO,
                    Code.CONFIG_VERSION_MISSING,
                    "no 'config_version'; the theme is read as version $SUPPORTED_CONFIG_VERSION",
                    "config_version",
                ),
            )
            return
        }
        val version = parseVersion(raw)
        if (version == null) {
            add(
                Finding(
                    Severity.WARNING,
                    Code.CONFIG_VERSION_INVALID,
                    "cannot read 'config_version' value '$raw'",
                    "config_version",
                ),
            )
            return
        }
        val supported = parseVersion(SUPPORTED_CONFIG_VERSION)!!
        val newer =
            version.first > supported.first ||
                (version.first == supported.first && version.second > supported.second)
        if (newer) {
            add(
                Finding(
                    Severity.WARNING,
                    Code.CONFIG_VERSION_UNSUPPORTED,
                    "'config_version' $raw is newer than the supported $SUPPORTED_CONFIG_VERSION; " +
                        "settings added by the newer format are ignored",
                    "config_version",
                ),
            )
        }
    }

    /** `major[.minor[.patch]]`, ignoring non-numeric parts. */
    private fun parseVersion(raw: String): Pair<Int, Int>? {
        val parts = raw.trim().split('.').map { it.takeWhile(Char::isDigit) }
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
        return major to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    private fun MutableList<Finding>.lintColors(
        theme: Theme,
        colors: ResolvedColors,
        parseColor: (String) -> Int?,
    ) {
        if (colors.day.isEmpty()) {
            add(
                Finding(
                    Severity.WARNING,
                    Code.NO_COLOR_SCHEME,
                    "no day color table in '${colors.dayPath}'; the keyboard cannot resolve any color",
                    colors.dayPath,
                ),
            )
            return
        }

        // A value that cannot be parsed is a typo in the file: it is reported
        // at the key that carries it, which may be a theme fallback entry rather
        // than the key the runtime asks for. The keys that inherit the value are
        // left out, so it is reported once.
        //
        // A key the table does not define is not reported on its own: the
        // built-in fallback chains cover only part of the vocabulary, and the
        // runtime already reports missing keys on demand (see ThemeScope).
        tables(colors).forEach { (table, path) ->
            ColorTable.resolve(table, theme.fallbackColors, parseColor)
                .invalidValues
                .mapNotNull { key -> ColorTable.resolveRawSource(key.key, table, theme.fallbackColors) }
                .distinct()
                .forEach { (source, raw) ->
                    val definedByTable = table[source]?.isNotEmpty() == true
                    val where = if (definedByTable) "color table" else "fallback_colors"
                    val findingPath = if (definedByTable) "$path/$source" else "fallback_colors/$source"
                    add(
                        Finding(
                            Severity.WARNING,
                            Code.INVALID_COLOR_VALUE,
                            "$where: '$source' cannot be parsed as a color (value '$raw')",
                            findingPath,
                        ),
                    )
                }
        }

        lintFallbackTargets(theme, tables(colors).map { it.first })
    }

    /** The day and night tables with the path each was read from. */
    private fun tables(colors: ResolvedColors): List<Pair<ColorScheme, String>> = listOfNotNull(
        colors.day to colors.dayPath,
        colors.night?.let { it to colors.nightPath },
    )

    /** The day and night tables with the path each was read from and its mode. */
    private fun tablesByMode(colors: ResolvedColors): List<Triple<ColorScheme, String, ThemeMode>> = listOfNotNull(
        Triple(colors.day, colors.dayPath, ThemeMode.DAY),
        colors.night?.let { Triple(it, colors.nightPath, ThemeMode.NIGHT) },
    )

    /**
     * An image-valued entry of a color table must exist where the runtime looks
     * for it: `images/<mode>/<name>` then `images/<name>`, inside the theme
     * package (doc/Theme.md §3). Both missing is a hard error there, so it is
     * reported as one.
     *
     * The day table is resolved as [ThemeMode.DAY] and the night table as
     * [ThemeMode.NIGHT] — exactly the lookup each table feeds — so the check
     * agrees with the runtime instead of approximating it. Only a package has
     * its own `images/`, so a theme decoded without one is skipped.
     */
    private fun MutableList<Finding>.lintThemeResources(
        theme: Theme,
        colors: ResolvedColors,
    ) {
        val packageDir = theme.packageDir ?: return
        val reported = mutableSetOf<String>()
        for ((table, path, mode) in tablesByMode(colors)) {
            for ((key, value) in table) {
                if (!ColorTable.isImageValue(value)) continue
                // One missing image is one finding even when several keys name
                // it; the path names the first key that does.
                if (!reported.add("${mode.dirName}/$value")) continue
                if (ThemeResources.resolveInPackage(packageDir, mode, value) != null) continue
                add(
                    Finding(
                        Severity.ERROR,
                        Code.MISSING_THEME_RESOURCE,
                        "'$value' is not in the theme package's '${ThemeResources.IMAGES_DIR}/' " +
                            "(looked for '${mode.dirName}/$value' and '$value')",
                        "$path/$key",
                    ),
                )
            }
        }
    }

    /**
     * The v3 package format keeps the day and night key sets equal
     * (doc/Theme.md §2.1): a difference is a drift warning. The check compares
     * two read files, so it only runs when both were.
     */
    private fun MutableList<Finding>.lintDayNightKeys(colors: ResolvedColors) {
        if (colors.dayNode == null || colors.nightNode == null) return
        val night = colors.night ?: return
        val dayOnly = colors.day.keys - night.keys
        val nightOnly = night.keys - colors.day.keys
        if (dayOnly.isEmpty() && nightOnly.isEmpty()) return
        add(
            Finding(
                Severity.WARNING,
                Code.DAY_NIGHT_KEY_MISMATCH,
                buildString {
                    append("'${colors.dayPath}' and '${colors.nightPath}' define different keys")
                    if (dayOnly.isNotEmpty()) {
                        append("; only in '${colors.dayPath}': ${dayOnly.sorted().joinToString()}")
                    }
                    if (nightOnly.isNotEmpty()) {
                        append("; only in '${colors.nightPath}': ${nightOnly.sorted().joinToString()}")
                    }
                },
                colors.nightPath,
            ),
        )
    }

    /**
     * A color table only carries color tokens and image names; a structural
     * field (or a value that is not a scalar at all) means theme body content
     * was written into `day.yaml`/`night.yaml`, where the runtime never looks
     * for it.
     */
    private fun MutableList<Finding>.lintColorTableStructure(colors: ResolvedColors) {
        listOfNotNull(
            colors.dayNode?.let { it to colors.dayPath },
            colors.nightNode?.let { it to colors.nightPath },
        ).forEach { (node, path) ->
            node.pairs.forEach { (name, value) ->
                val structural = name in STRUCTURAL_COLOR_KEYS || (!value.isNull && value.scalar == null)
                if (!structural) return@forEach
                add(
                    Finding(
                        Severity.ERROR,
                        Code.STRUCTURAL_KEY_IN_COLORS,
                        "'$name' is a structural key; a color table only carries color tokens and image names",
                        "$path/$name",
                    ),
                )
            }
        }
    }

    /**
     * A `fallback_colors` entry whose target no table defines is dead: the key
     * it feeds stays unresolved even though the theme asked for something.
     */
    private fun MutableList<Finding>.lintFallbackTargets(
        theme: Theme,
        colorTables: List<ColorScheme>,
    ) {
        val fallbacks = theme.fallbackColors
        fallbacks.forEach { (from, target) ->
            if (target.isEmpty()) return@forEach
            // `target` is fine when walking it reaches a value the way the
            // runtime does: a key a table defines, another entry of the chain,
            // a built-in chain from there, or an image. Walking also means a
            // chain that ends nowhere, or in a cycle, is reported at every
            // entry pointing into it.
            val resolvable =
                ColorTable.isImageValue(target) ||
                    colorTables.any { table ->
                        ColorTable.resolveRaw(target, table, fallbacks) != null
                    }
            if (resolvable) return@forEach
            add(
                Finding(
                    Severity.WARNING,
                    Code.BROKEN_FALLBACK_TARGET,
                    "fallback_colors: '$from' points at '$target', which no color table defines",
                    "fallback_colors/$from",
                ),
            )
        }
    }

    /**
     * Lists presets whose send value can never resolve to a key, so that a
     * theme is checked once at activation time instead of on first use.
     */
    fun presetDiagnostics(presetKeys: Map<String, PresetKey>): List<String> = presetKeys.mapNotNull { (name, preset) ->
        val (keycode, modifiers) = KeyCode.parse(preset.send)
        if (preset.send.isNotEmpty() && keycode == 0 && modifiers == 0) {
            "preset '$name' has an unrecognized send '${preset.send}'"
        } else {
            null
        }
    }

    private fun MutableList<Finding>.lintPresetSends(theme: Theme) {
        presetDiagnostics(theme.presetKeys).forEach { message ->
            add(
                Finding(
                    Severity.WARNING,
                    Code.UNRESOLVABLE_PRESET_SEND,
                    message,
                    "preset_keys/${message.substringAfter('\'').substringBefore('\'')}",
                ),
            )
        }
    }

    private fun MutableList<Finding>.lintKeyboardReferences(theme: Theme) {
        val keyboards = theme.presetKeyboards
        keyboards.forEach { (id, keyboard) ->
            listOf(
                "import_preset" to keyboard.importPreset,
                "ascii_keyboard" to keyboard.asciiKeyboard,
                "landscape_keyboard" to keyboard.landscapeKeyboard,
            ).forEach { (key, target) ->
                if (target.isEmpty() || target in keyboards) return@forEach
                add(
                    Finding(
                        Severity.WARNING,
                        Code.MISSING_KEYBOARD_REFERENCE,
                        "keyboard '$id' references missing keyboard '$target' via '$key'",
                        "preset_keyboards/$id/$key",
                    ),
                )
            }
        }
        lintKeyboardCycles(theme)
    }

    /**
     * An embedded collection names an entry of the theme's `collections:` map
     * (doc/Keyboard.md §2.6.3). A name the theme does not declare renders as an empty collection:
     * the keyboard still works, so it is a warning, and the layout path points at
     * the collection that names it.
     */
    private fun MutableList<Finding>.lintCollectionSources(theme: Theme) {
        val collections = theme.collections
        for ((keyboardId, keyboard) in theme.presetKeyboards) {
            for ((path, spec) in collectionPaths(keyboard.keys.tree)) {
                val findingPath = "preset_keyboards/$keyboardId/keys/$path/collection/source"
                when {
                    spec.source.isEmpty() -> add(
                        Finding(
                            Severity.WARNING,
                            Code.MISSING_COLLECTION_SOURCE,
                            "collection of keyboard '$keyboardId' names no 'collections' entry; " +
                                "it renders empty",
                            findingPath,
                        ),
                    )

                    spec.source !in collections -> add(
                        Finding(
                            Severity.WARNING,
                            Code.MISSING_COLLECTION_SOURCE,
                            "collection of keyboard '$keyboardId' names missing 'collections' " +
                                "entry '${spec.source}'; it renders empty",
                            findingPath,
                        ),
                    )

                    collections.getValue(spec.source).items.isEmpty() -> add(
                        Finding(
                            Severity.WARNING,
                            Code.EMPTY_COLLECTION,
                            "collection of keyboard '$keyboardId' reads 'collections: ${spec.source}', " +
                                "which has no cells; it renders empty",
                            findingPath,
                        ),
                    )
                }
            }
        }
    }

    /** Every collection of a layout tree, with the index path that leads to it. */
    private fun collectionPaths(
        nodes: List<KeyboardNode>,
        prefix: String = "",
    ): List<Pair<String, KeyboardCollection>> = buildList {
        nodes.forEachIndexed { index, node ->
            val path = if (prefix.isEmpty()) "$index" else "$prefix/$index"
            when (node) {
                is KeyboardNode.Collection -> add(path to node.spec)
                is KeyboardNode.Row -> addAll(collectionPaths(node.children, path))
                is KeyboardNode.Column -> addAll(collectionPaths(node.children, path))
                is KeyboardNode.Flow -> addAll(collectionPaths(node.children, path))
                is KeyboardNode.Key -> Unit
            }
        }
    }

    /**
     * `import_preset` is followed until the chain ends, so a keyboard that
     * imports itself never ends. Reported once per cycle, at the entry that
     * closes it; the runtime has no guard either.
     */
    private fun MutableList<Finding>.lintKeyboardCycles(theme: Theme) {
        val keyboards = theme.presetKeyboards
        val reported = mutableSetOf<Set<String>>()
        keyboards.keys.forEach { start ->
            val chain = linkedSetOf<String>()
            var current: String? = start
            var last: String? = null
            while (current != null && current in keyboards && chain.add(current)) {
                last = current
                current = keyboards[current]?.importPreset?.takeIf { it.isNotEmpty() }
            }
            if (current == null || current !in chain) return@forEach
            val cycle = chain.dropWhile { it != current }
            if (!reported.add(cycle.toSet())) return@forEach
            add(
                Finding(
                    Severity.WARNING,
                    Code.CYCLIC_KEYBOARD_REFERENCE,
                    "preset keyboards import each other in a cycle " +
                        cycle.joinToString(" -> ", prefix = "(", postfix = ")") { "'$it'" } +
                        " via 'import_preset'; the reference never resolves",
                    "preset_keyboards/$last/import_preset",
                ),
            )
        }
    }

    /**
     * The one `styles:` table and everything that references it — key entries
     * (doc/Theme.md §4) and the panel/item sections (doc/Theme.md §8). A name
     * must be defined, must belong to exactly one family (一义一名, §8.5), a key
     * node must fill exactly one layer, a node may only carry its family's
     * fields, and its payload must exist in the package. All of them are hard
     * errors there, so the theme is refused instead of silently dropping a
     * reference, half of a node, or an image.
     */
    private fun MutableList<Finding>.lintStyles(
        theme: Theme,
        node: YamlMap,
    ) {
        val references = StyleFamilyResolver.references(theme)
        lintKeyNames(theme)
        lintStyleReferences(theme)
        lintStyleFamilies(references)
        lintStyleLayers(theme, node, references)
        lintStyleFields(node, references)
        lintStyleResources(theme)
    }

    /**
     * A layout entry's `key:` must name either a `preset_keys` entry or a built-in
     * character key (doc/Keyboard.md §2.1), because it is what gives the entry its
     * action. An entry that carries an explicit `click` needs no default, so the
     * name there is only the key's identity and is not reported.
     */
    private fun MutableList<Finding>.lintKeyNames(theme: Theme) {
        theme.presetKeyboards.forEach { (keyboardId, keyboard) ->
            keyboard.keys.forEachIndexed { index, key ->
                val name = key.key
                if (name.isEmpty() || key.click.orAbsent != null) return@forEachIndexed
                if (name in theme.presetKeys || CharKeyNames.of(name) != null) return@forEachIndexed
                add(
                    Finding(
                        Severity.ERROR,
                        Code.UNKNOWN_KEY_NAME,
                        "'$name' is neither a preset key name nor a built-in character key",
                        "preset_keyboards/$keyboardId/keys/$index/key",
                    ),
                )
            }
        }
    }

    /**
     * A `style:` — a layout entry's, or one of the two panel sections' slots —
     * must name a node of the theme's `styles:` table (doc/Theme.md §8.5).
     */
    private fun MutableList<Finding>.lintStyleReferences(theme: Theme) {
        theme.presetKeyboards.forEach { (keyboardId, keyboard) ->
            keyboard.keys.forEachIndexed { index, key ->
                val name = key.style
                if (name.isEmpty() || name in theme.styles) return@forEachIndexed
                add(
                    Finding(
                        Severity.ERROR,
                        Code.UNKNOWN_STYLE_NAME,
                        "'$name' is not defined in 'styles'; the entry has no style to apply",
                        "preset_keyboards/$keyboardId/keys/$index/style",
                    ),
                )
            }
        }
        theme.optionPanel.run {
            lintStyleSlot("option_panel", panelStyle, "panel_style", theme)
            lintStyleSlot("option_panel", actionItemStyle, "action_item_style", theme)
            lintStyleSlot("option_panel", switchItemStyle, "switch_item_style", theme)
            lintStyleSlot("option_panel", titleItemStyle, "title_item_style", theme)
        }
        theme.clipboard.run {
            lintStyleSlot("clipboard", panelStyle, "panel_style", theme)
            lintStyleSlot("clipboard", itemStyle, "item_style", theme)
            lintStyleSlot("clipboard", pinnedItemStyle, "pinned_item_style", theme)
            lintStyleSlot("clipboard", titleItemStyle, "title_item_style", theme)
        }
    }

    /** One panel/item style slot of a section must name a `styles:` node too. */
    private fun MutableList<Finding>.lintStyleSlot(
        section: String,
        name: String,
        field: String,
        theme: Theme,
    ) {
        if (name.isEmpty() || name in theme.styles) return
        add(
            Finding(
                Severity.ERROR,
                Code.UNKNOWN_STYLE_NAME,
                "'$name' is not defined in 'styles'; the section has no style to apply",
                "$section/$field",
            ),
        )
    }

    /**
     * A style name belongs to one family — key style, panel style or item style
     * (doc/Theme.md §8.5). The family is not a field of the node, so a name that
     * two kinds of reference site claim at once is ambiguous and refused.
     */
    private fun MutableList<Finding>.lintStyleFamilies(references: Map<String, Set<StyleFamily>>) {
        references.forEach { (name, families) ->
            if (families.size <= 1) return@forEach
            val named =
                families.sortedBy { it.ordinal }.joinToString { family ->
                    when (family) {
                        StyleFamily.KEY -> "a key entry"
                        StyleFamily.PANEL -> "a panel"
                        StyleFamily.ITEM -> "a panel or list item"
                    }
                }
            add(
                Finding(
                    Severity.ERROR,
                    Code.STYLE_FAMILY_CONFLICT,
                    "style '$name' is referenced by $named; a style name belongs to one family",
                    "styles/$name",
                ),
            )
        }
    }

    /**
     * A key style node fills one layer (doc/Theme.md §4: 一义一名). Its `content`
     * declares which, so a node that also carries the other layer's fields would
     * be asked to serve both — an authoring mistake rather than something to
     * silently ignore.
     *
     * Panel and item nodes have no `content` and are left alone: they share
     * fields such as `corner_radius` with the key family. A node is treated as a
     * key node when a key references it, or when its own fields say so (it sets
     * `content` or a field no other family knows).
     */
    private fun MutableList<Finding>.lintStyleLayers(
        theme: Theme,
        node: YamlMap,
        references: Map<String, Set<StyleFamily>>,
    ) {
        theme.styles.forEach { (name, style) ->
            val fields = styleNodeFields(node, name)
            val keyNode = references[name] == setOf(StyleFamily.KEY) || StyleFamilyResolver.declaresKeyFamily(fields)
            if (!keyNode) return@forEach
            val (declared, offending) = when (style.layer) {
                KeyStyle.Layer.FOREGROUND -> "foreground" to style.backgroundFields
                KeyStyle.Layer.BACKGROUND -> "background" to style.foregroundFields
            }
            if (offending.isEmpty()) return@forEach
            add(
                Finding(
                    Severity.ERROR,
                    Code.STYLE_LAYER_MISMATCH,
                    "style '$name' declares '$declared' content " +
                        "('${style.content.yamlName}') but also carries " +
                        offending.joinToString { "'$it'" } +
                        "; a style node fills one layer",
                    "styles/$name",
                ),
            )
        }
    }

    /**
     * A style node's payload must exist in the theme package for **both** modes.
     * A node is not per-mode, and a key may use it in either one, so an image
     * that only exists under one `images/<mode>/` would be missing whenever the
     * other mode is active (doc/Theme.md §4.1, §3). Only a package has its own
     * `images/`, so a theme decoded without one is skipped.
     */
    private fun MutableList<Finding>.lintStyleResources(theme: Theme) {
        val packageDir = theme.packageDir ?: return
        val reported = mutableSetOf<String>()
        for ((name, style) in theme.styles) {
            for (value in listOf(style.assetIcon, style.ninePatch)) {
                if (value.isEmpty()) continue
                // One missing image is one finding even when a node names it in
                // both fields; the path names the style that does.
                if (!reported.add("$name/$value")) continue
                val missing =
                    ThemeMode.entries.firstOrNull {
                        ThemeResources.resolveInPackage(packageDir, it, value) == null
                    } ?: continue
                add(
                    Finding(
                        Severity.ERROR,
                        Code.MISSING_THEME_RESOURCE,
                        "'$value' is not in the theme package's '${ThemeResources.IMAGES_DIR}/' " +
                            "(looked for '${missing.dirName}/$value' and '$value')",
                        "styles/$name",
                    ),
                )
            }
        }
    }

    /**
     * A style node may only carry fields its family knows (doc/Theme.md §8.5):
     * the runtime ignores anything else, so a typo would otherwise be silent. An
     * unreferenced node may carry any family's fields, since nothing says which
     * one it is; a name two families claim is already [Code.STYLE_FAMILY_CONFLICT],
     * and its fields are then checked against both.
     */
    private fun MutableList<Finding>.lintStyleFields(
        node: YamlMap,
        references: Map<String, Set<StyleFamily>>,
    ) {
        styleNodes(node).forEach { (name, fields) ->
            val known = StyleFamilyResolver.allowedFields(references[name].orEmpty())
            fields.forEach { field ->
                if (field in known) return@forEach
                add(
                    Finding(
                        Severity.ERROR,
                        Code.UNKNOWN_STYLE_FIELD,
                        "style '$name' carries '$field', which no style family knows",
                        "styles/$name/$field",
                    ),
                )
            }
        }
    }

    /** Every `styles:` node's raw field names, keyed by style name. */
    private fun styleNodes(node: YamlMap): Map<String, Set<String>> {
        val styles = node.pairs["styles"]?.mapping ?: return emptyMap()
        return styles.pairs.mapValues { (_, entry) -> entry.mapping?.pairs?.keys.orEmpty() }
    }

    /** One `styles:` node's raw field names; empty when it is not a mapping. */
    private fun styleNodeFields(
        node: YamlMap,
        name: String,
    ): Set<String> = styleNodes(node)[name].orEmpty()

    private fun parseColor(value: String): Int? = runCatching { ColorUtils.parseColor(value) }.getOrNull()
}
