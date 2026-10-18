/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.Clipboard
import com.osfans.trime.data.theme.model.ItemStyle
import com.osfans.trime.data.theme.model.OptionPanel
import com.osfans.trime.data.theme.model.Padding

/** The option panel's engine-known item kinds (doc/Theme.md §8.4). */
enum class OptionPanelItemKind {
    /** A `SwitchOptionEntry.Static` row: theme / schema / config / keyboard. */
    ACTION,

    /** A `SwitchOptionEntry.Custom` row: a Rime switch with an on and an off state. */
    SWITCH,

    /** A group or title row. */
    TITLE,
}

/** The clipboard page's engine-known item kinds (doc/Theme.md §8.4). */
enum class ClipboardItemKind {
    /** An ordinary text row. */
    ITEM,

    /** A pinned row, which shows the pin marker. */
    PINNED,

    /** The title row, including the delete-all button. */
    TITLE,
    ;

    companion object {
        /** The kind a clipboard row binds to, so no view decides it. */
        fun of(pinned: Boolean): ClipboardItemKind = if (pinned) PINNED else ITEM
    }
}

/** One item's appearance, after the theme's style node and the built-in defaults are merged (doc/Theme.md §8.3). */
data class ResolvedItemStyle(
    /** The item's height in dp; 0 = wrap the content (and honour [minHeight]). */
    val height: Int,
    val minHeight: Int,
    val cornerRadius: Float,
    val borderWidth: Int,
    /** A color token or a literal; "" = no border. */
    val borderColor: String,
    /** A color token or an image name; "" = no background. */
    val background: String,
    /** The switch's on-state background; "" = [background]. */
    val backgroundOn: String,
    /** The pressed background; "" = none. */
    val backgroundHighlight: String,
    val textColor: String,
    val labelSize: Float,
    val maxLines: Int,
    val ellipsize: ItemStyle.Ellipsize?,
    val iconSize: Int,
    val iconColor: String,
    val pinSize: Int,
    val pinColor: String,
    val pinInset: Int,
    val padding: Padding,
) {
    /** The background of a switch item in [on] state, falling back to the normal one. */
    fun backgroundFor(on: Boolean): String = if (on) backgroundOn.ifEmpty { background } else background
}

/**
 * Resolves an item style (doc/Theme.md §8.3) for one slot of doc/Theme.md §8.4.
 * Pure: it reads the theme's item-style nodes, applies the slot's built-in
 * default — which is exactly what the views used before this slice — and hands
 * the views one flat value object, so no view judges a priority itself.
 *
 * A slot whose style name is empty or unresolved keeps the default, so a theme
 * that declares nothing renders exactly like today.
 */
object ItemStyleResolver {
    /** Today's option-panel row: a 96dp cell with a 48dp icon box and a 12sp label. */
    const val OPTION_ROW_HEIGHT = 96
    const val OPTION_ICON_SIZE = 48
    const val OPTION_LABEL_SIZE = 12f

    /**
     * The option panel stacks its icon box above its label, and today leaves 4dp
     * above the box and 6dp between the box and the label. doc/Theme.md §8.3 has
     * no field for those two gaps, so the item's `padding` carries them:
     * `vertical` is the top inset, `horizontal` the icon-to-label gap.
     */
    const val OPTION_BOX_TOP_INSET = 4
    const val OPTION_LABEL_GAP = 6

    /** The letter drawn in place of an entry's icon is scaled from the icon box, as today (20dp of 48dp). */
    const val OPTION_TEXT_ICON_RATIO = 20f / 48f

    /** Today's clipboard row: a 30dp-minimum text row with a 12dp pin and 14sp text. */
    const val CLIPBOARD_MIN_HEIGHT = 30
    const val CLIPBOARD_LABEL_SIZE = 14f
    const val CLIPBOARD_MAX_LINES = 4
    const val CLIPBOARD_PIN_SIZE = 12
    const val CLIPBOARD_PIN_INSET = 2
    const val CLIPBOARD_PADDING_VERTICAL = 4
    const val CLIPBOARD_PADDING_HORIZONTAL = 8

    /** The `styles:` name the option panel's [kind] slot resolves through; "" when it names none. */
    fun slotName(
        panel: OptionPanel,
        kind: OptionPanelItemKind,
    ): String = when (kind) {
        OptionPanelItemKind.ACTION -> panel.actionItemStyle
        OptionPanelItemKind.SWITCH -> panel.switchItemStyle
        OptionPanelItemKind.TITLE -> panel.titleItemStyle
    }

    /** The `styles:` name the clipboard's [kind] slot resolves through; "" when it names none. */
    fun slotName(
        clipboard: Clipboard,
        kind: ClipboardItemKind,
    ): String = when (kind) {
        ClipboardItemKind.ITEM -> clipboard.itemStyle
        ClipboardItemKind.PINNED -> clipboard.pinnedItemStyle
        ClipboardItemKind.TITLE -> clipboard.titleItemStyle
    }

    /** The option panel's [kind] slot. */
    fun optionItemStyle(
        theme: Theme,
        kind: OptionPanelItemKind,
    ): ResolvedItemStyle {
        val style = theme.itemStyles[slotName(theme.optionPanel, kind)]
        return ResolvedItemStyle(
            height = style?.height?.takeIf { it > 0 } ?: OPTION_ROW_HEIGHT,
            minHeight = style?.minHeight?.takeIf { it > 0 } ?: 0,
            cornerRadius = style?.cornerRadius?.takeIf { it >= 0f } ?: theme.style.roundCorner,
            borderWidth = style?.borderWidth?.takeIf { it >= 0 } ?: theme.style.keyBorder,
            borderColor = style?.borderColor?.takeIf { it.isNotEmpty() } ?: ColorKey.KEY_BORDER_COLOR.key,
            background = style?.background?.takeIf { it.isNotEmpty() } ?: ColorKey.KEY_BACK_COLOR.key,
            backgroundOn = style?.backgroundOn.orEmpty(),
            backgroundHighlight = style?.backgroundHighlight.orEmpty(),
            textColor = style?.textColor?.takeIf { it.isNotEmpty() } ?: ColorKey.KEY_TEXT_COLOR.key,
            labelSize = style?.labelSize?.takeIf { it > 0f } ?: OPTION_LABEL_SIZE,
            maxLines = style?.maxLines?.takeIf { it > 0 } ?: 0,
            ellipsize = style?.ellipsize,
            iconSize = style?.iconSize?.takeIf { it > 0 } ?: OPTION_ICON_SIZE,
            iconColor = style?.iconColor?.takeIf { it.isNotEmpty() } ?: ColorKey.KEY_TEXT_COLOR.key,
            pinSize = style?.pinSize?.takeIf { it > 0 } ?: 0,
            pinColor = style?.pinColor?.takeIf { it.isNotEmpty() } ?: ColorKey.KEY_SYMBOL_COLOR.key,
            pinInset = style?.pinInset?.takeIf { it >= 0 } ?: 0,
            padding = style?.padding?.takeIf { it.isSet } ?: Padding(OPTION_BOX_TOP_INSET, OPTION_LABEL_GAP),
        )
    }

    /** The clipboard's [kind] slot. */
    fun clipboardItemStyle(
        theme: Theme,
        kind: ClipboardItemKind,
    ): ResolvedItemStyle {
        val style = theme.itemStyles[slotName(theme.clipboard, kind)]
        val isTitle = kind == ClipboardItemKind.TITLE
        return ResolvedItemStyle(
            // Today's title bar borrows the candidate view's height; an explicit
            // height replaces that (doc/Theme.md §8.4).
            height = style?.height?.takeIf { it > 0 } ?: if (isTitle) titleBarHeight(theme) else 0,
            minHeight = style?.minHeight?.takeIf { it > 0 } ?: if (isTitle) 0 else CLIPBOARD_MIN_HEIGHT,
            cornerRadius = style?.cornerRadius?.takeIf { it >= 0f } ?: if (isTitle) 0f else theme.style.roundCorner,
            borderWidth = style?.borderWidth?.takeIf { it >= 0 } ?: if (isTitle) 0 else theme.style.keyBorder,
            borderColor = style?.borderColor?.takeIf { it.isNotEmpty() } ?: ColorKey.KEY_BORDER_COLOR.key,
            // The list paints one rounded surface; a row only shows a pressed state.
            background = style?.background.orEmpty(),
            backgroundOn = style?.backgroundOn.orEmpty(),
            backgroundHighlight = style?.backgroundHighlight.orEmpty(),
            textColor = style?.textColor?.takeIf { it.isNotEmpty() } ?: ColorKey.KEY_TEXT_COLOR.key,
            labelSize = style?.labelSize?.takeIf { it > 0f } ?: if (isTitle) theme.style.candidateTextSize else CLIPBOARD_LABEL_SIZE,
            maxLines = style?.maxLines?.takeIf { it > 0 } ?: if (isTitle) 0 else CLIPBOARD_MAX_LINES,
            ellipsize = style?.ellipsize ?: if (isTitle) null else ItemStyle.Ellipsize.END,
            iconSize = style?.iconSize?.takeIf { it > 0 } ?: 0,
            iconColor = style?.iconColor?.takeIf { it.isNotEmpty() } ?: ColorKey.KEY_TEXT_COLOR.key,
            pinSize = style?.pinSize?.takeIf { it > 0 } ?: CLIPBOARD_PIN_SIZE,
            pinColor = style?.pinColor?.takeIf { it.isNotEmpty() } ?: ColorKey.KEY_SYMBOL_COLOR.key,
            pinInset = style?.pinInset?.takeIf { it >= 0 } ?: CLIPBOARD_PIN_INSET,
            padding = style?.padding?.takeIf { it.isSet } ?: if (isTitle) Padding(0, 0) else Padding(CLIPBOARD_PADDING_VERTICAL, CLIPBOARD_PADDING_HORIZONTAL),
        )
    }

    /** Today's title-row height: the candidate bar plus its comment line. */
    private fun titleBarHeight(theme: Theme): Int = theme.style.candidateViewHeight + theme.style.commentHeight
}
