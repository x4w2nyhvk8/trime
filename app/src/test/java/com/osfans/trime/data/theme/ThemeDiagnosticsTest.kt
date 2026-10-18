// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import android.util.Log
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.yamlMap
import com.osfans.trime.data.theme.ThemeDiagnostics.Code
import com.osfans.trime.data.theme.ThemeDiagnostics.Severity
import com.osfans.trime.ime.keyboard.KeyCode
import com.osfans.trime.util.mapping
import com.osfans.trime.util.pairs
import com.osfans.trime.util.string
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import timber.log.Timber
import java.io.File

/**
 * The day table most fixtures use: a valid single-entry table, so a fixture
 * only has to declare what it is actually about.
 */
private const val DEFAULT_DAY = "back_color: \"#000000\""

class ThemeDiagnosticsTest :
    BehaviorSpec({
        // The Android key-name lookup is a platform call that throws when not
        // mocked; the bare letters used by the fixtures resolve without it.
        beforeTest { KeyCode.androidKeyNameToCode = { 0 } }

        /** Hex-only color parser; enough to tell colors from typos. */
        fun parseHex(value: String): Int? {
            val digits = when {
                value.startsWith("#") -> value.substring(1)
                value.startsWith("0x", ignoreCase = true) -> value.substring(2)
                else -> null
            }
            return digits?.let { runCatching { java.lang.Long.parseLong(it, 16).toInt() }.getOrNull() }
        }

        /** The day table most fixtures use, as the loader would resolve it. */
        val defaultDayColors = mapOf("back_color" to "#000000")

        /** The scalar entries of a color-table fixture; the same shape the loader reads. */
        fun colorsOf(yaml: String?): ColorScheme? = yaml?.let {
            ThemeTestSupport.yaml.parseToYamlNode(it).pairs.orEmpty()
                .mapNotNull { (key, value) -> value.string?.let { text -> key to text } }
                .toMap()
        }

        fun nodeOf(yaml: String?): YamlMap? = yaml?.let { ThemeTestSupport.yaml.parseToYamlNode(it).mapping }

        /**
         * Lints a theme body with the day/night tables it would be loaded with.
         * The tables are passed the way the loader passes them, so the path a
         * finding points at matches `day.yaml`/`night.yaml`.
         *
         * @param packageDir the theme package the resources would be read from;
         *   null (a legacy theme) skips the resource check.
         */
        fun lint(
            themeYaml: String,
            dayYaml: String? = DEFAULT_DAY,
            nightYaml: String? = null,
            packageDir: File? = null,
        ): List<ThemeDiagnostics.Finding> {
            val node = ThemeTestSupport.yaml.parseToYamlNode(themeYaml)
            val colors =
                ResolvedColors(
                    day = colorsOf(dayYaml).orEmpty(),
                    night = colorsOf(nightYaml),
                    dayNode = nodeOf(dayYaml),
                    nightNode = nodeOf(nightYaml),
                )
            val theme =
                ThemeTestSupport.yaml.decodeFromYamlNode<Theme>(node)
                    .copy(dayColors = colors.day, nightColors = colors.night, packageDir = packageDir)
            return ThemeDiagnostics.lint(theme, node.yamlMap, colors, ::parseHex)
        }

        fun lintCodes(
            themeYaml: String,
            dayYaml: String? = DEFAULT_DAY,
            nightYaml: String? = null,
            packageDir: File? = null,
        ): List<Code> = lint(themeYaml, dayYaml, nightYaml, packageDir).map(ThemeDiagnostics.Finding::code)

        /** A package directory holding [relativePaths] as (dummy) files. */
        fun packageWith(vararg relativePaths: String): File {
            val dir = File.createTempFile("theme-images", "").apply {
                delete()
                mkdirs()
                deleteOnExit()
            }
            relativePaths.forEach { relative ->
                File(dir, relative).apply { parentFile?.mkdirs() }.writeText("dummy")
            }
            return dir
        }

        Given("a minimal valid theme") {
            val yaml =
                """
                config_version: "3.0"
                name: minimal
                style: {candidate_text_size: 20}
                preset_keys:
                  A: {label: a, send: a}
                preset_keyboards:
                  letter: {name: letter, keys: [{row: [{click: a}]}]}
                """.trimIndent()

            Then("nothing is reported") {
                lint(yaml) shouldBe emptyList()
            }
        }

        Given("unknown keys") {
            Then("top-level keys the runtime ignores are reported once each") {
                val findings =
                    lint(
                        """
                        config_version: "3.0"
                        name: t
                        style: {}
                        height: 5
                        round_corner: 3
                        """.trimIndent(),
                    )
                findings.filter { it.code == Code.UNKNOWN_TOP_LEVEL_KEY } shouldContainExactlyInAnyOrder
                    listOf(
                        ThemeDiagnostics.Finding(
                            Severity.INFO,
                            Code.UNKNOWN_TOP_LEVEL_KEY,
                            "unknown key 'height' in ''; the runtime ignores it",
                            "/height",
                        ),
                        ThemeDiagnostics.Finding(
                            Severity.INFO,
                            Code.UNKNOWN_TOP_LEVEL_KEY,
                            "unknown key 'round_corner' in ''; the runtime ignores it",
                            "/round_corner",
                        ),
                    )
            }

            Then("style typos are warnings pointing at the key") {
                val findings = lint(
                    """
                    config_version: "3.0"
                    name: t
                    style: {candidate_texts_size: 22, candidate_text_size: 20}
                    """.trimIndent(),
                )
                findings.map { it.code to it.path } shouldBe
                    listOf(Code.UNKNOWN_STYLE_KEY to "style/candidate_texts_size")
            }
        }

        Given("config_version") {
            val base =
                """
                name: t
                style: {}
                """.trimIndent()

            Then("a missing version is informational") {
                lintCodes(base) shouldBe listOf(Code.CONFIG_VERSION_MISSING)
            }

            Then("the supported version is silent") {
                lintCodes("config_version: \"${ThemeDiagnostics.SUPPORTED_CONFIG_VERSION}\"\n$base") shouldBe emptyList()
            }

            Then("an older version is silent") {
                lintCodes("config_version: '0.40'\n$base") shouldBe emptyList()
            }

            Then("a newer version warns with a migration hint") {
                lint("config_version: \"99.1\"\n$base").single().let {
                    it.code shouldBe Code.CONFIG_VERSION_UNSUPPORTED
                    it.severity shouldBe Severity.WARNING
                    it.message shouldBe
                        "'config_version' 99.1 is newer than the supported 3.0; " +
                        "settings added by the newer format are ignored"
                }
            }

            Then("a newer minor version warns") {
                lintCodes("config_version: \"3.1\"\n$base") shouldBe listOf(Code.CONFIG_VERSION_UNSUPPORTED)
            }

            Then("a value that is not a version warns") {
                lintCodes("config_version: abc\n$base") shouldBe listOf(Code.CONFIG_VERSION_INVALID)
            }
        }

        Given("color tables") {
            val base =
                """
                config_version: "3.0"
                name: t
                style: {}
                """.trimIndent()

            Then("a theme without a day table is reported") {
                lintCodes(base, dayYaml = null) shouldBe listOf(Code.NO_COLOR_SCHEME)
            }

            Then("a value the color parser rejects is a warning naming the value") {
                val findings = lint(base, dayYaml = "back_color: \"not-a-color\"")
                findings.single().let {
                    it.code shouldBe Code.INVALID_COLOR_VALUE
                    it.path shouldBe "day.yaml/back_color"
                    it.message shouldBe "color table: 'back_color' cannot be parsed as a color (value 'not-a-color')"
                }
            }

            Then("a typo in the night table is reported at that file") {
                val findings =
                    lint(
                        base,
                        dayYaml = "back_color: \"#000000\"\ntext_color: \"#ffffff\"",
                        nightYaml = "back_color: \"#000000\"\ntext_color: \"nope\"",
                    )
                findings.single().let {
                    it.code shouldBe Code.INVALID_COLOR_VALUE
                    it.path shouldBe "night.yaml/text_color"
                }
            }

            Then("a value a custom table key carries is reported at that key") {
                val findings =
                    lint(
                        "$base\nfallback_colors: {key_back_color: my_base}",
                        dayYaml = "back_color: \"#000000\"\nmy_base: \"nope\"",
                    )
                findings.single().let {
                    it.code shouldBe Code.INVALID_COLOR_VALUE
                    it.path shouldBe "day.yaml/my_base"
                    it.message shouldBe "color table: 'my_base' cannot be parsed as a color (value 'nope')"
                }
            }

            Then("a fallback_colors entry pointing at nothing is reported") {
                val findings = lint("$base\nfallback_colors:\n  key_back_color: no_such_key")
                findings.single().let {
                    it.code shouldBe Code.BROKEN_FALLBACK_TARGET
                    it.severity shouldBe Severity.WARNING
                    it.path shouldBe "fallback_colors/key_back_color"
                    it.message shouldBe "fallback_colors: 'key_back_color' points at 'no_such_key', which no color table defines"
                }
            }

            Then("a fallback_colors entry reaching a built-in chain is fine") {
                lintCodes(
                    "$base\nfallback_colors:\n  candidate_text_color: text_color\n  my_panel: candidate_text_color",
                    dayYaml = "back_color: \"#000000\"\ntext_color: \"#ffffff\"",
                ) shouldBe emptyList()
            }

            Then("a fallback_colors chain that cycles is reported") {
                lintCodes(
                    "$base\nfallback_colors:\n  my_panel: my_panel_color\n  my_panel_color: my_panel",
                ) shouldBe listOf(Code.BROKEN_FALLBACK_TARGET, Code.BROKEN_FALLBACK_TARGET)
            }
        }

        Given("a theme package's day.yaml and night.yaml") {
            val base =
                """
                config_version: "3.0"
                name: t
                style: {}
                """.trimIndent()
            val day = "back_color: \"#000000\"\ntext_color: \"#ffffff\""
            val night = "back_color: \"#1c1c1e\"\ntext_color: \"#f0f0f0\""

            Then("equal key sets are silent") {
                lint(base, dayYaml = day, nightYaml = night) shouldBe emptyList()
            }

            Then("a key set difference is a warning naming both sides") {
                val findings =
                    lint(
                        base,
                        dayYaml = day,
                        nightYaml = "back_color: \"#1c1c1e\"\nkey_back_color: \"#333333\"",
                    )
                findings.single().let {
                    it.code shouldBe Code.DAY_NIGHT_KEY_MISMATCH
                    it.severity shouldBe Severity.WARNING
                    it.path shouldBe "night.yaml"
                    it.message shouldBe
                        "'day.yaml' and 'night.yaml' define different keys" +
                        "; only in 'day.yaml': text_color; only in 'night.yaml': key_back_color"
                }
            }

            Then("a structural field in a color table is an error") {
                val findings = lint(base, dayYaml = "back_color: \"#000000\"\nstyle: {}")
                findings.single().let {
                    it.code shouldBe Code.STRUCTURAL_KEY_IN_COLORS
                    it.severity shouldBe Severity.ERROR
                    it.path shouldBe "day.yaml/style"
                    it.message shouldBe
                        "'style' is a structural key; a color table only carries color tokens and image names"
                }
            }

            Then("a value that is not a scalar is reported as a structural field too") {
                lintCodes(base, nightYaml = "back_color: \"#1c1c1e\"\nkey: {}") shouldBe
                    listOf(Code.STRUCTURAL_KEY_IN_COLORS)
            }

            Then("a key written without a value is not structural") {
                lintCodes(base, dayYaml = "back_color: \"#000000\"\ntext_color:") shouldBe emptyList()
            }
        }

        Given("a theme package's images") {
            val base =
                """
                config_version: "3.0"
                name: t
                style: {}
                """.trimIndent()
            // Both fixtures carry the same key set, so the resource check is the
            // only one that can report anything: plainTable names a color where
            // imageTable names an image.
            val plainTable = "back_color: \"#000000\"\nkey_back_color: \"#ffffff\""
            val imageTable = "back_color: \"#000000\"\nkey_back_color: \"splash.9.png\""

            Then("an image in the mode directory satisfies the day table") {
                val dir = packageWith("images/day/splash.9.png")
                lint(base, dayYaml = imageTable, packageDir = dir) shouldBe emptyList()
            }

            Then("an image in the shared images/ satisfies either mode") {
                val dir = packageWith("images/splash.9.png")
                lint(base, dayYaml = imageTable, nightYaml = imageTable, packageDir = dir) shouldBe emptyList()
            }

            Then("a missing image is an error naming the lookups") {
                val findings = lint(base, dayYaml = imageTable, packageDir = packageWith("images/other.png"))
                findings.single().let {
                    it.code shouldBe Code.MISSING_THEME_RESOURCE
                    it.severity shouldBe Severity.ERROR
                    it.path shouldBe "day.yaml/key_back_color"
                    it.message shouldBe
                        "'splash.9.png' is not in the theme package's 'images/' " +
                        "(looked for 'day/splash.9.png' and 'splash.9.png')"
                }
            }

            Then("an image only the other mode carries does not satisfy this one") {
                val dir = packageWith("images/night/splash.9.png")
                lintCodes(base, dayYaml = imageTable, packageDir = dir) shouldBe
                    listOf(Code.MISSING_THEME_RESOURCE)
            }

            Then("the night table resolves its images in the night directory") {
                val dir = packageWith("images/night/splash.9.png")
                lintCodes(base, dayYaml = plainTable, nightYaml = imageTable, packageDir = dir) shouldBe emptyList()
            }

            Then("a legacy theme is not checked, because it has no package images") {
                lintCodes(base, dayYaml = imageTable) shouldBe emptyList()
            }
        }

        Given("preset keys and keyboards") {
            val base =
                """
                config_version: "3.0"
                name: t
                style: {}
                """.trimIndent()

            Then("an unresolvable preset send is a warning") {
                val findings = lint("$base\npreset_keys:\n  BROKEN: {label: x, send: NotAKey}")
                findings.single().let {
                    it.code shouldBe Code.UNRESOLVABLE_PRESET_SEND
                    it.path shouldBe "preset_keys/BROKEN"
                    it.message shouldBe "preset 'BROKEN' has an unrecognized send 'NotAKey'"
                }
            }

            Then("keyboards referencing missing keyboards are reported") {
                val findings =
                    lint(
                        "$base\npreset_keyboards:\n" +
                            "  letter:\n" +
                            "    name: letter\n" +
                            "    import_preset: nope\n" +
                            "    ascii_keyboard: also-nope\n" +
                            "    landscape_keyboard: letter\n" +
                            "    keys: [{row: [{click: a}]}]",
                    )
                findings.map { it.code to it.path } shouldBe
                    listOf(
                        Code.MISSING_KEYBOARD_REFERENCE to "preset_keyboards/letter/import_preset",
                        Code.MISSING_KEYBOARD_REFERENCE to "preset_keyboards/letter/ascii_keyboard",
                    )
            }
            Then("keyboards importing each other in a cycle are reported once") {
                val findings =
                    lint(
                        "$base\npreset_keyboards:\n" +
                            "  letter: {name: letter, import_preset: symbols, keys: [{row: [{click: a}]}]}\n" +
                            "  symbols: {name: symbols, import_preset: letter, keys: [{row: [{click: a}]}]}",
                    )
                findings.single().let {
                    it.code shouldBe Code.CYCLIC_KEYBOARD_REFERENCE
                    it.severity shouldBe Severity.WARNING
                    it.path shouldBe "preset_keyboards/symbols/import_preset"
                    it.message shouldBe
                        "preset keyboards import each other in a cycle ('letter' -> 'symbols') " +
                        "via 'import_preset'; the reference never resolves"
                }
            }
        }

        Given("a theme's styles table") {
            val base =
                """
                config_version: "3.0"
                name: t
                style: {}
                """.trimIndent()

            Then("a styles section is a known top-level key") {
                lintCodes("$base\nstyles:\n  aStyle: {content: text, text: A}") shouldBe emptyList()
            }

            Then("a referenced text style and a referenced geometry style are silent") {
                lintCodes(
                    "$base\n" +
                        "styles:\n" +
                        "  spaceKeyTextStyle: {content: text, text: 空格, text_size: 20}\n" +
                        "  letterButtonBackgroundStyle: {content: geometry, corner_radius: 4}\n" +
                        "preset_keyboards:\n" +
                        "  letter:\n" +
                        "    name: letter\n" +
                        "    keys:\n" +
                        "      - row:\n" +
                        "          - {click: a, style: spaceKeyTextStyle}\n" +
                        "          - {click: b, style: letterButtonBackgroundStyle}",
                ) shouldBe emptyList()
            }

            Then("a style reference that names nothing is an error") {
                val findings =
                    lint(
                        "$base\npreset_keyboards:\n" +
                            "  letter: {name: letter, keys: [{row: [{click: a, style: nope}]}]}",
                    )
                findings.single().let {
                    it.code shouldBe Code.UNKNOWN_STYLE_NAME
                    it.severity shouldBe Severity.ERROR
                    it.path shouldBe "preset_keyboards/letter/keys/0/style"
                    it.message shouldBe "'nope' is not defined in 'styles'; the entry has no style to apply"
                }
            }

            Then("a node that carries the other layer's fields is an error") {
                // 一义一名: this name would be both the foreground (text) and the
                // background (nine_patch) of a key.
                val findings =
                    lint(
                        "$base\nstyles:\n  weirdStyle: {content: text, text: A, nine_patch: key_9p.9.png}",
                    )
                findings.single().let {
                    it.code shouldBe Code.STYLE_LAYER_MISMATCH
                    it.severity shouldBe Severity.ERROR
                    it.path shouldBe "styles/weirdStyle"
                    it.message shouldBe
                        "style 'weirdStyle' declares 'foreground' content ('text') but also carries " +
                        "'nine_patch'; a style node fills one layer"
                }
            }

            Then("the other direction is an error too") {
                val findings =
                    lint(
                        "$base\nstyles:\n  weirdStyle: {content: nine_patch, nine_patch: key_9p.9.png, text: A}",
                    )
                findings.single().let {
                    it.code shouldBe Code.STYLE_LAYER_MISMATCH
                    it.message shouldBe
                        "style 'weirdStyle' declares 'background' content ('nine_patch') but also carries " +
                        "'text'; a style node fills one layer"
                }
            }

            Then("a background field on a nine_patch node is not a mismatch") {
                lintCodes(
                    "$base\nstyles:\n  patchStyle: {content: nine_patch, nine_patch: key_9p.9.png, border_width: 2}",
                ) shouldBe emptyList()
            }
        }

        Given("a theme package's style resources") {
            val base =
                """
                config_version: "3.0"
                name: t
                style: {}
                """.trimIndent()
            val styles =
                "styles:\n" +
                    "  iconStyle: {content: asset_icon, asset_icon: voice_icon.png}\n" +
                    "  patchStyle: {content: nine_patch, nine_patch: key_9p.9.png}"

            Then("an image the package carries for both modes is fine") {
                // Shared images resolve for either mode.
                lintCodes(
                    "$base\n$styles",
                    packageDir = packageWith("images/voice_icon.png", "images/key_9p.9.png"),
                ) shouldBe emptyList()
            }

            Then("a per-mode image is fine when every mode directory has it") {
                lintCodes(
                    "$base\n$styles",
                    packageDir =
                    packageWith(
                        "images/day/voice_icon.png",
                        "images/night/voice_icon.png",
                        "images/day/key_9p.9.png",
                        "images/night/key_9p.9.png",
                    ),
                ) shouldBe emptyList()
            }

            Then("an image only one mode carries is an error naming that mode") {
                // doc/Theme.md §4.1: a style node is not per-mode, so a resource
                // that only exists at night would be missing during the day.
                val findings =
                    lint(
                        "$base\n$styles",
                        packageDir = packageWith("images/day/voice_icon.png", "images/key_9p.9.png"),
                    )
                findings.single().let {
                    it.code shouldBe Code.MISSING_THEME_RESOURCE
                    it.severity shouldBe Severity.ERROR
                    it.path shouldBe "styles/iconStyle"
                    it.message shouldBe
                        "'voice_icon.png' is not in the theme package's 'images/' " +
                        "(looked for 'night/voice_icon.png' and 'voice_icon.png')"
                }
            }

            Then("an image the package does not carry at all is an error too") {
                val findings = lint("$base\n$styles", packageDir = packageWith("images/voice_icon.png"))
                findings.single().let {
                    it.code shouldBe Code.MISSING_THEME_RESOURCE
                    it.path shouldBe "styles/patchStyle"
                    it.message shouldBe
                        "'key_9p.9.png' is not in the theme package's 'images/' " +
                        "(looked for 'day/key_9p.9.png' and 'key_9p.9.png')"
                }
            }

            Then("a legacy theme is not checked, because it has no package images") {
                lintCodes("$base\n$styles") shouldBe emptyList()
            }
        }

        Given("the one styles table's three families") {
            val base =
                """
                config_version: "3.0"
                name: t
                style: {}
                """.trimIndent()
            fun withKeyboard(styleName: String) = "$base\n" +
                "preset_keyboards:\n" +
                "  letter:\n" +
                "    name: letter\n" +
                "    keys:\n" +
                "      - row:\n" +
                "          - {click: a, style: $styleName}"

            Then("a panel slot that names no style is an error") {
                val findings = lint("$base\nstyles:\n  aStyle: {content: text}\noption_panel:\n  panel_style: missing")
                findings.single().let {
                    it.code shouldBe Code.UNKNOWN_STYLE_NAME
                    it.path shouldBe "option_panel/panel_style"
                    it.message shouldBe "'missing' is not defined in 'styles'; the section has no style to apply"
                }
            }

            Then("a clipboard item slot that names no style is an error") {
                val findings = lint("$base\nstyles:\n  aStyle: {content: text}\nclipboard:\n  pinned_item_style: missing")
                findings.single().let {
                    it.code shouldBe Code.UNKNOWN_STYLE_NAME
                    it.path shouldBe "clipboard/pinned_item_style"
                }
            }

            Then("a name two families claim is an error") {
                val findings =
                    lint(
                        withKeyboard("shared") +
                            "\nstyles:\n" +
                            "  shared: {content: text, text: A}\n" +
                            "option_panel:\n  action_item_style: shared",
                    )
                findings.single().let {
                    it.code shouldBe Code.STYLE_FAMILY_CONFLICT
                    it.path shouldBe "styles/shared"
                    it.message shouldBe
                        "style 'shared' is referenced by a key entry, a panel or list item; " +
                        "a style name belongs to one family"
                }
            }

            Then("a node may carry its family's fields, and only its own") {
                // A panel style's fields are not key fields: the key-family
                // layer rule must not judge it (corner_radius is a background
                // field there).
                lintCodes(
                    "$base\nstyles:\n  p: {background: panel.png, corner_radius: 8, padding: [2, 3]}\n" +
                        "option_panel:\n  panel_style: p",
                ) shouldBe emptyList()
                lintCodes(
                    "$base\nstyles:\n  i: {min_height: 30, pin_size: 12, ellipsize: end}\n" +
                        "clipboard:\n  item_style: i",
                ) shouldBe emptyList()
            }

            Then("a field no family knows is an error") {
                val findings =
                    lint(
                        "$base\nstyles:\n  aStyle: {content: text, text_sizee: 20}\n" +
                            "preset_keyboards:\n  letter:\n    name: letter\n    keys:\n      - row:\n          - {click: a, style: aStyle}",
                    )
                findings.single().let {
                    it.code shouldBe Code.UNKNOWN_STYLE_FIELD
                    it.path shouldBe "styles/aStyle/text_sizee"
                    it.message shouldBe "style 'aStyle' carries 'text_sizee', which no style family knows"
                }
            }

            Then("a key field on a panel-referenced node is an error too") {
                val findings = lint("$base\nstyles:\n  p: {content: text}\noption_panel:\n  panel_style: p")
                findings.single().let {
                    it.code shouldBe Code.UNKNOWN_STYLE_FIELD
                    it.path shouldBe "styles/p/content"
                }
            }
        }

        Given("reporting") {
            /** Collects the lines [ThemeDiagnostics.report] logs, one per finding. */
            class CollectingTree(private val lines: MutableList<String>) : Timber.Tree() {
                override fun log(
                    priority: Int,
                    tag: String?,
                    message: String,
                    t: Throwable?,
                ) {
                    val level =
                        when (priority) {
                            Log.INFO -> "I"
                            Log.WARN -> "W"
                            Log.ERROR -> "E"
                            else -> "?"
                        }
                    lines += "$level $message"
                }
            }

            /** Runs [block] and returns what it logged. */
            fun captured(block: () -> Unit): List<String> {
                val lines = mutableListOf<String>()
                val tree = CollectingTree(lines)
                Timber.plant(tree)
                try {
                    block()
                } finally {
                    Timber.uproot(tree)
                }
                return lines
            }

            Then("each finding is logged once, at its severity") {
                val node =
                    ThemeTestSupport.yaml.parseToYamlNode(
                        """
                        config_version: "3.0"
                        name: t
                        style: {candidate_texts_size: 12}
                        height: 5
                        """.trimIndent(),
                    )
                val theme = ThemeTestSupport.yaml.decodeFromYamlNode<Theme>(node)
                val colors =
                    ResolvedColors(day = mapOf("back_color" to "#000000", "hilited_back_color" to "nope"))
                val findings = ThemeDiagnostics.lint(theme.copy(dayColors = colors.day), node.yamlMap, colors, ::parseHex)
                val lines = captured { ThemeDiagnostics.log("fixture", findings) }
                lines shouldBe
                    listOf(
                        "I Theme 'fixture': unknown key 'height' in ''; the runtime ignores it",
                        "W Theme 'fixture': unknown key 'candidate_texts_size' in 'style'; the runtime ignores it",
                        "W Theme 'fixture': color table: 'hilited_back_color' cannot be parsed as a color (value 'nope')",
                    )
            }

            Then("an error finding is logged as an error") {
                val node =
                    ThemeTestSupport.yaml.parseToYamlNode(
                        """
                        config_version: "3.0"
                        name: t
                        style: {}
                        """.trimIndent(),
                    )
                val theme = ThemeTestSupport.yaml.decodeFromYamlNode<Theme>(node)
                val colors = ResolvedColors(day = defaultDayColors, dayNode = nodeOf("style: {}"))
                val findings = ThemeDiagnostics.lint(theme.copy(dayColors = colors.day), node.yamlMap, colors, ::parseHex)
                captured { ThemeDiagnostics.log("fixture", findings) } shouldBe
                    listOf(
                        "E Theme 'fixture': 'style' is a structural key; " +
                            "a color table only carries color tokens and image names",
                    )
            }

            Then("a theme the runtime can read is silent") {
                val node =
                    ThemeTestSupport.yaml.parseToYamlNode(
                        """
                        config_version: "3.0"
                        name: t
                        style: {candidate_text_size: 12}
                        """.trimIndent(),
                    )
                val theme = ThemeTestSupport.yaml.decodeFromYamlNode<Theme>(node)
                val colors = ResolvedColors(day = defaultDayColors, dayNode = nodeOf(DEFAULT_DAY))
                val findings = ThemeDiagnostics.lint(theme.copy(dayColors = colors.day), node.yamlMap, colors, ::parseHex)
                captured { ThemeDiagnostics.log("clean", findings) } shouldBe emptyList()
                findings shouldBe emptyList()
            }
        }

        Given("the report of a theme") {
            val findings =
                lint(
                    """
                    config_version: "3.0"
                    name: t
                    height: 5
                    style: {candidate_texts_size: 12}
                    """.trimIndent(),
                )

            Then("the findings of a checked theme are rendered with their code") {
                val text = ThemeDiagnostics.format("mytheme", "My Theme", findings)
                text shouldContain "Theme: My Theme (mytheme)\n"
                text shouldContain "Findings: 2 (0 errors, 1 warnings, 1 info)\n"
                text shouldContain "[INFO] /height: unknown key 'height'"
                text shouldContain "(UNKNOWN_TOP_LEVEL_KEY)"
                text shouldContain "(UNKNOWN_STYLE_KEY)"
            }

            Then("a theme without findings says so") {
                ThemeDiagnostics.format("mytheme", "My Theme", emptyList()) shouldBe
                    "Theme: My Theme (mytheme)\nNo findings.\n"
            }

            Then("a theme whose checks could not run says so") {
                ThemeDiagnostics.format("mytheme", "My Theme", null) shouldBe
                    "Theme: My Theme (mytheme)\n" +
                    "Static checks could not run for this theme.\n"
            }
        }

        Given("the shipped theme") {
            // Read the way the runtime reads it: through the package loader, so
            // the linter sees the tables and the package directory it sees.
            fun lintBuiltin(themeId: String): List<ThemeDiagnostics.Finding> {
                val theme = ThemeTestSupport.decodeBuiltinTheme(themeId)
                return ThemeDiagnostics.lint(theme, ThemeTestSupport.builtinThemeNode(themeId), parseColor = ::parseHex)
            }

            Then("it is refused-worthy for nothing: no finding is an error") {
                lintBuiltin("trime").filter { it.severity == Severity.ERROR } shouldBe emptyList()
            }

            Then("the sends it cannot resolve off-device are platform key names only") {
                // Send values are platform key names: a device resolves them, the
                // JVM cannot, so they are summarised instead of listed.
                val sends = lintBuiltin("trime").filter { it.code == Code.UNRESOLVABLE_PRESET_SEND }
                sends.forEach { finding ->
                    val send = finding.message.substringBeforeLast('\'').substringAfterLast('\'')
                    // The message names the send, so an empty extraction means the
                    // format changed and this summary checks nothing.
                    send.isEmpty() shouldBe false
                    send shouldBe send.uppercase()
                }
            }
        }
    })
