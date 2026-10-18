/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.Padding

/**
 * The two pages that carry a panel style (doc/Theme.md §8.1). The enum owns the
 * page's default background token, so no caller spells a color key itself.
 */
enum class PanelPage(
    /** The color token a panel falls back to when its `background` is not declared. */
    val backColorKey: ColorKey,
) {
    OPTION_PANEL(ColorKey.OPTION_PANEL_BACK_COLOR),
    CLIPBOARD(ColorKey.CLIPBOARD_BACK_COLOR),
}

/**
 * One page's panel appearance, after the theme's style node and the built-in
 * defaults are merged (doc/Theme.md §8.2).
 */
data class ResolvedPanelStyle(
    /** A color token or an image name; never "" — it falls back to the page's token. */
    val background: String,
    val cornerRadius: Float,
    val padding: Padding,
    /** `height`: null when the theme declares none, so the host's own params stay (today's behaviour). */
    val height: PanelSize?,
    /** `width`: null when the theme declares none, like [height]. */
    val width: PanelSize?,
    val maxHeight: Int,
    val sideMargin: Int,
    val itemSpacing: Int,
    /** The chrome action button's size in dp, today's candidate bar plus its comment line by default. */
    val actionButtonSize: Int,
    /** Hairline between list items in dp; 0 draws none. */
    val divider: Int,
    /** The divider's colour token; empty means the item's border colour. */
    val dividerColor: String,
)

/**
 * What a declared panel `height`/`width` asks the host layout to do
 * (doc/Theme.md §8.2), so no view interprets the numbers itself.
 */
sealed interface PanelSize {
    /** `0`: size the panel to its content. */
    data object Auto : PanelSize

    /** `-1`: fill the space the host offers. */
    data object Fill : PanelSize

    /** A positive value: the panel's size in dp. */
    data class Fixed(val dp: Int) : PanelSize {
        init {
            require(dp > 0) { "A fixed panel size must be positive, got $dp" }
        }
    }

    /** A layout-params value: a dp number, or [AUTO] / [FILL]. */
    val layoutValue: Int
        get() = when (this) {
            Auto -> AUTO
            Fill -> FILL
            is Fixed -> dp
        }

    companion object {
        /** The theme value, and the layout params constant, for "fill the available space". */
        const val FILL = -1

        /** The layout params constant for "size to the content". */
        const val AUTO = -2

        /** [value] as a panel size: -1 fills, a positive value is dp, anything else sizes to content. */
        fun of(value: Int): PanelSize = when {
            value == FILL -> Fill
            value > 0 -> Fixed(value)
            else -> Auto
        }
    }
}

/**
 * Resolves a page's panel style (doc/Theme.md §8.2): read the node
 * `option_panel.panel_style` / `clipboard.panel_style` names, apply its fields,
 * and keep today's appearance for everything it does not declare. Pure, so the
 * fallbacks are unit-tested instead of hiding in the views.
 */
object PanelStyleResolver {
    /** Today's panel defaults: no panel chrome, the host's own size, and the clipboard's 4dp item spacing. */
    const val DEFAULT_CORNER_RADIUS = 0f
    const val DEFAULT_PADDING = 0
    const val DEFAULT_MAX_HEIGHT = 0
    const val DEFAULT_SIDE_MARGIN = 0
    const val OPTION_PANEL_ITEM_SPACING = 0
    const val CLIPBOARD_ITEM_SPACING = 4

    /** An undeclared dimension keeps the host's layout params, which is today's behaviour. */
    val DEFAULT_SIZE: PanelSize? = null

    /** An undeclared action button keeps today's candidate bar plus its comment line. */
    const val DEFAULT_ACTION_BUTTON_SIZE = 0

    /** The `styles:` name [page]'s panel resolves through; "" when it names none. */
    fun slotName(
        theme: Theme,
        page: PanelPage,
    ): String = when (page) {
        PanelPage.OPTION_PANEL -> theme.optionPanel.panelStyle
        PanelPage.CLIPBOARD -> theme.clipboard.panelStyle
    }

    /** The appearance of [page]'s panel. */
    fun panelStyle(
        theme: Theme,
        page: PanelPage,
    ): ResolvedPanelStyle {
        val style = theme.panelStyles[slotName(theme, page)]
        return ResolvedPanelStyle(
            background = style?.background?.takeIf { it.isNotEmpty() } ?: page.backColorKey.key,
            cornerRadius = style?.cornerRadius?.takeIf { it >= 0f } ?: DEFAULT_CORNER_RADIUS,
            padding = style?.padding?.takeIf { it.isSet } ?: Padding(DEFAULT_PADDING, DEFAULT_PADDING),
            height = style?.height?.let(PanelSize::of) ?: DEFAULT_SIZE,
            width = style?.width?.let(PanelSize::of) ?: DEFAULT_SIZE,
            maxHeight = style?.maxHeight?.takeIf { it > 0 } ?: DEFAULT_MAX_HEIGHT,
            sideMargin = style?.sideMargin?.takeIf { it > 0 } ?: DEFAULT_SIDE_MARGIN,
            itemSpacing = style?.itemSpacing?.takeIf { it >= 0 } ?: defaultItemSpacing(page),
            actionButtonSize = style?.actionButtonSize?.takeIf { it > 0 } ?: defaultActionButtonSize(theme),
            divider = style?.divider?.takeIf { it > 0 } ?: 0,
            dividerColor = style?.dividerColor.orEmpty(),
        )
    }

    /** Today's chrome button size: the candidate bar plus its comment line. */
    fun defaultActionButtonSize(theme: Theme): Int = theme.style.candidateViewHeight + theme.style.commentHeight

    /** Today's item spacing: the clipboard list already separates its rows, the option grid does not. */
    private fun defaultItemSpacing(page: PanelPage): Int = when (page) {
        PanelPage.OPTION_PANEL -> OPTION_PANEL_ITEM_SPACING
        PanelPage.CLIPBOARD -> CLIPBOARD_ITEM_SPACING
    }
}
