/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.Clipboard
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.ItemStyle
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.KeyStyle
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.data.theme.model.OptionPanel
import com.osfans.trime.data.theme.model.PanelStyle
import com.osfans.trime.data.theme.model.Preedit
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.ThemeCollection
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.data.theme.model.Window
import com.osfans.trime.ime.keyboard.KeyAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File

/** 主题和样式配置  */
@Serializable
data class Theme(
    val name: String,
    val style: GeneralStyle = GeneralStyle(),
    val preedit: Preedit = Preedit(),
    val window: Window = Window(),
    val liquidKeyboard: LiquidKeyboard = LiquidKeyboard(),
    val presetKeys: Map<String, PresetKey> = emptyMap(),
    val presetKeyboards: Map<String, TextKeyboard> = emptyMap(),
    /**
     * The theme's named style nodes, referenced by layout entries' `style:` and
     * by the panel/item sections (doc/Theme.md §4, §8.5). One table holds three
     * families — key, panel and item styles — and a name belongs to exactly one
     * of them: [StyleFamilyResolver] derives which from the reference sites, and
     * the loader fills [panelStyles] / [itemStyles] with the same nodes decoded
     * for their own family.
     */
    val styles: Map<String, KeyStyle> = emptyMap(),
    val fallbackColors: Map<String, String> = emptyMap(),
    val toolBar: ToolBar = ToolBar(),
    /** The option/switch panel's style slots (doc/Theme.md §8.1). */
    val optionPanel: OptionPanel = OptionPanel(),
    /** The clipboard page's style slots (doc/Theme.md §8.1). */
    val clipboard: Clipboard = Clipboard(),

    /** Named cell sets an embedded collection can read (doc/Keyboard.md §2.6.3). */
    val collections: Map<String, ThemeCollection> = emptyMap(),
    /**
     * The day color table. It is not part of `theme.yaml`: the loader reads it
     * from the theme package's `day.yaml` and fills it in, so the decoder never
     * sees it. Empty only for a theme [ThemeLoader] refuses to load.
     */
    @Transient
    val dayColors: ColorScheme = emptyMap(),
    /**
     * The night color table, or null when the theme declares none and night
     * falls back to [dayColors]. Filled the same way as [dayColors].
     */
    @Transient
    val nightColors: ColorScheme? = null,
    /**
     * The directory of the theme package this theme was read from, or null when
     * it was decoded without one (a test fixture). Filled by [ThemeLoader] like
     * the color tables, and read by the resource lookup: a theme resolves its
     * own `images/` (doc/Theme.md §3).
     */
    @Transient
    val packageDir: File? = null,
    /**
     * The `styles:` nodes decoded for the panel family (doc/Theme.md §8.2). The
     * table is one, but the three families read different fields out of the same
     * nodes, so the loader decodes it once per family; a node that is not a valid
     * panel style is simply absent here.
     */
    @Transient
    val panelStyles: Map<String, PanelStyle> = emptyMap(),
    /** The `styles:` nodes decoded for the item family (doc/Theme.md §8.3). */
    @Transient
    val itemStyles: Map<String, ItemStyle> = emptyMap(),
) {
    val fonts by lazy { ThemeFonts(this) }

    @Transient
    private val actionCache = lazy {
        mutableMapOf<KeyActionToken, KeyAction>()
    }

    fun resolveAction(token: KeyActionToken) = actionCache.value.getOrPut(token) {
        KeyAction(token, presetKeys)
    }

    fun resolveAction(tokenString: String) = resolveAction(KeyActionToken.Plain(tokenString))

    companion object {
        /**
         * Top-level keys a theme may declare: the sections reads plus
         * the metadata librime and the theme picker use. The theme linter
         * reports anything else, since the runtime ignores it.
         *
         * `preset_color_schemes` is deliberately absent: color tables live in
         * the package's `day.yaml`/`night.yaml`, so a theme that still carries
         * the key is reported as carrying an unknown key like any other.
         */
        internal val TOP_LEVEL_KEYS: Set<String> =
            setOf(
                "config_version",
                "name",
                "author",
                "description",
                "version",
                "style",
                "preedit",
                "window",
                "liquid_keyboard",
                "collections",
                "tool_bar",
                "preset_keys",
                "preset_keyboards",
                "styles",
                "option_panel",
                "clipboard",
                "fallback_colors",
            )
    }
}
