/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import kotlinx.serialization.Serializable

/**
 * A named style node from a theme's `styles:` table, referenced by a layout
 * entry's `style:` (doc/Theme.md §4.1).
 *
 * `content` declares what the node *is*, and with it which of a key's two
 * layers the node fills:
 *
 * - [Content.TEXT], [Content.SYSTEM_ICON] and [Content.ASSET_ICON] are
 *   **foreground** contents: they replace the key's label with text or with an
 *   icon, and leave the background to the color scheme;
 * - [Content.NINE_PATCH] and [Content.GEOMETRY] are **background** contents:
 *   they paint (or shape) the key's background and leave the label alone.
 *
 * A node fills exactly one layer (doc/Theme.md §4: 一义一名). Declaring one
 * layer while carrying the other layer's fields is an authoring mistake, so
 * [foregroundFields] and [backgroundFields] expose what the node sets and the
 * theme linter reports the overlap instead of silently ignoring half of the
 * node.
 *
 * Field names are snake_case in YAML like every other theme model; the parser
 * applies the same naming strategy, and `content` is read through
 * [KeyStyleContentSerializer].
 */
@Serializable
data class KeyStyle(
    /** The node's presentation type; see [Content]. */
    @Serializable(with = KeyStyleContentSerializer::class)
    val content: Content = Content.TEXT,
    /** `content: text`: the text to show. A placement's own label still wins. */
    val text: String = "",
    /** `content: text`: the text size; 0 = the key's or keyboard's default. */
    val textSize: Float = 0f,
    /**
     * `content: text`: the font face name. Accepted because doc/Theme.md §4.1's
     * example declares it; the renderer still draws with the theme's key font, so
     * a value here is not applied yet.
     */
    val fontFace: String = "",
    /** `content: system_icon`: the Iconics icon name, drawn by the existing `ic@` channel. */
    val systemIcon: String = "",
    /** `content: asset_icon`: the image in the theme package's `images/`. */
    val assetIcon: String = "",
    /**
     * `content: system_icon` / `content: asset_icon`: the icon's size; 0 = the
     * key's own text size, else the keyboard default.
     */
    val iconSize: Int = 0,
    /** `content: nine_patch`: the nine-patch image in the theme package's `images/`. */
    val ninePatch: String = "",
    /** `content: geometry`: the corner radius; -1 = unset. */
    val cornerRadius: Float = -1f,
    /** `content: geometry`: the border width in dp; -1 = unset. */
    val borderWidth: Int = -1,
    /** `content: geometry`: the border color value (a color token or a literal); "" = unset. */
    val borderColor: String = "",
) {
    /** The layer [content] fills. */
    val layer: Layer
        get() = when (content) {
            Content.TEXT, Content.SYSTEM_ICON, Content.ASSET_ICON -> Layer.FOREGROUND
            Content.NINE_PATCH, Content.GEOMETRY -> Layer.BACKGROUND
        }

    /** The foreground field names this node sets, for the one-layer check. */
    val foregroundFields: List<String>
        get() = buildList {
            if (text.isNotEmpty()) add("text")
            if (textSize > 0f) add("text_size")
            if (fontFace.isNotEmpty()) add("font_face")
            if (systemIcon.isNotEmpty()) add("system_icon")
            if (assetIcon.isNotEmpty()) add("asset_icon")
            if (iconSize > 0) add("icon_size")
        }

    /** The background field names this node sets, for the one-layer check. */
    val backgroundFields: List<String>
        get() = buildList {
            if (ninePatch.isNotEmpty()) add("nine_patch")
            if (cornerRadius >= 0f) add("corner_radius")
            if (borderWidth >= 0) add("border_width")
            if (borderColor.isNotEmpty()) add("border_color")
        }

    /** Which of a key's two layers a style node fills. */
    enum class Layer {
        FOREGROUND,
        BACKGROUND,
    }

    /** The presentation type a key style node declares, and the layer it fills. */
    enum class Content(
        /** The snake_case spelling the theme contract uses. */
        val yamlName: String,
    ) {
        TEXT("text"),
        SYSTEM_ICON("system_icon"),
        ASSET_ICON("asset_icon"),
        NINE_PATCH("nine_patch"),
        GEOMETRY("geometry"),
        ;

        companion object {
            /**
             * The entry [raw] names, matching case-insensitively, so both the
             * contract's spelling (`system_icon`) and the Kotlin one
             * (`SYSTEM_ICON`) are accepted.
             */
            fun of(raw: String): Content? = entries.firstOrNull { it.yamlName.equals(raw.trim(), ignoreCase = true) }
        }
    }
}
