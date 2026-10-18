/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyLabel
import com.osfans.trime.data.theme.model.KeyStyle
import com.osfans.trime.data.theme.model.TextKeyboard

/**
 * What one key draws, after the placement > style > built-in default resolution
 * (doc/Theme.md §4.1, doc/Keyboard.md §2.1): a field the layout entry sets
 * explicitly wins over the style node it references, which wins over the key's
 * built-in default.
 */
data class ResolvedKeyStyle(
    /** The `styles:` name the placement references; "" when it references none. */
    val styleName: String = "",
    /** The referenced style's presentation type; [KeyStyle.Content.TEXT] without a style. */
    val content: KeyStyle.Content = KeyStyle.Content.TEXT,
    /** The layer [content] fills. */
    val layer: KeyStyle.Layer = KeyStyle.Layer.FOREGROUND,
    /**
     * The resolved foreground label: the placement's own `label` when it
     * declares one, else the style's `text` as a static label, else
     * [KeyLabel.None] (doc/Keyboard.md §2.2). A dynamic source is resolved when
     * the key is drawn, through [KeyLabelResolver] and a [LabelContext].
     */
    val label: KeyLabel = KeyLabel.None,
    /**
     * The static text of [label]: the text itself for [KeyLabel.Text], "" for a
     * dynamic label and for [KeyLabel.None].
     */
    val text: String = "",
    /** Foreground text size: the placement's when it sets one (> 0), else the style's. */
    val textSize: Float = 0f,
    /**
     * `content: system_icon`'s icon name, when the style's icon is the
     * foreground. "" when the content is not a system icon, or when the
     * placement's own label takes the foreground slot (placement > style).
     *
     * The renderer turns it into the `ic@…` label the existing Iconics channel
     * reads (see [com.osfans.trime.ime.keyboard.isIconFont]).
     */
    val systemIcon: String = "",
    /** `content: asset_icon`'s package image value, under the same rule as [systemIcon]. */
    val assetIcon: String = "",
    /** `content: system_icon` / `asset_icon`: the icon's size; 0 = the key's text size, else the keyboard default. */
    val iconSize: Int = 0,
    /** Corner radius: the placement's when set (>= 0), else the style's, else -1. */
    val cornerRadius: Float = -1f,
    /** Border width: the placement's when set (>= 0), else the style's, else -1. */
    val borderWidth: Int = -1,
    /** Border color: the placement's when set, else the style's, else "". */
    val borderColor: String = "",
    /** Where the background comes from. */
    val background: KeyBackground = KeyBackground.Scheme,
)

/**
 * The source of a key's background, after the placement > style > default
 * resolution. A `content: geometry` style is not listed: it does not replace the
 * background, it shapes it (the resolved [ResolvedKeyStyle.cornerRadius] /
 * [ResolvedKeyStyle.borderWidth] / [ResolvedKeyStyle.borderColor] are applied on
 * top of the scheme's background, exactly as a placement's own shape fields are).
 */
sealed interface KeyBackground {
    /** The built-in flow: the scheme's color keys, the placement's own value first. */
    data object Scheme : KeyBackground

    /** `content: nine_patch`'s package image, which stretches with the key. */
    data class NinePatch(val value: String) : KeyBackground
}

/**
 * Resolves a layout entry against the theme's `styles:` table.
 *
 * This is the whole priority matrix (doc/Keyboard.md §2.1), pure and free of
 * Android so it is unit-tested rather than spread over the views:
 *
 * - foreground content: a placement label (or `key_text_size`) wins over the
 *   style's `text`/`text_size`, and a placement label also wins over an icon the
 *   style declares — both want the same slot;
 * - shape: a placement's `round_corner`/`key_border`/`key_border_color` win over
 *   the style's `corner_radius`/`border_width`/`border_color`;
 * - background: a placement's `key_back_color` takes the slot, so a nine-patch
 *   style yields to it; otherwise a `content: nine_patch` style supplies it.
 *
 * An unknown [TextKeyboard.TextKey.style] name resolves to the defaults, the
 * same as no style: the linter reports the dangling reference, the renderer
 * does not crash on it.
 */
object KeyStyleResolver {
    fun resolve(
        placement: TextKeyboard.TextKey?,
        styles: Map<String, KeyStyle>,
    ): ResolvedKeyStyle {
        val styleName = placement?.style.orEmpty()
        val style = styles[styleName]
        val content = style?.content ?: KeyStyle.Content.TEXT
        // The entry's own label takes the foreground slot; the style's `text`
        // only applies when the entry declares no label of its own. An empty
        // static text declares nothing, exactly like before this became
        // polymorphic.
        val placementLabel = placement?.label ?: KeyLabel.None
        val placementDeclares =
            placementLabel !is KeyLabel.None &&
                !(placementLabel is KeyLabel.Text && placementLabel.text.isEmpty())
        val label =
            if (placementDeclares) {
                placementLabel
            } else {
                style?.text?.takeIf { it.isNotEmpty() }?.let { KeyLabel.Text(it) } ?: KeyLabel.None
            }
        return ResolvedKeyStyle(
            styleName = styleName,
            content = content,
            layer = style?.layer ?: KeyStyle.Layer.FOREGROUND,
            label = label,
            text = label.staticText,
            textSize = placement?.keyTextSize?.takeIf { it > 0f } ?: style?.textSize?.takeIf { it > 0f } ?: 0f,
            systemIcon = if (content == KeyStyle.Content.SYSTEM_ICON && !placementDeclares) style?.systemIcon.orEmpty() else "",
            assetIcon = if (content == KeyStyle.Content.ASSET_ICON && !placementDeclares) style?.assetIcon.orEmpty() else "",
            iconSize = style?.iconSize?.takeIf { it > 0 } ?: 0,
            cornerRadius = placement?.roundCorner?.takeIf { it >= 0f } ?: style?.cornerRadius?.takeIf { it >= 0f } ?: -1f,
            borderWidth = placement?.keyBorder?.takeIf { it >= 0 } ?: style?.borderWidth?.takeIf { it >= 0 } ?: -1,
            borderColor = placement?.keyBorderColor?.takeIf { it.isNotEmpty() } ?: style?.borderColor.orEmpty(),
            background = when {
                // The placement's own value takes the background slot too.
                !placement?.keyBackColor.isNullOrEmpty() -> KeyBackground.Scheme

                content == KeyStyle.Content.NINE_PATCH && !style?.ninePatch.isNullOrEmpty() -> KeyBackground.NinePatch(style.ninePatch)

                else -> KeyBackground.Scheme
            },
        )
    }
}
