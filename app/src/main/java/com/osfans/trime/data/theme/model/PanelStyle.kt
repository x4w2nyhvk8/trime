/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import kotlinx.serialization.Serializable

/**
 * One `styles:` node of the panel family, referenced by
 * `option_panel.panel_style` / `clipboard.panel_style` (doc/Theme.md §8.2).
 *
 * Every field is optional: a field that is absent (or 0 / -1) keeps the
 * resolver's built-in default, which is today's appearance.
 */
@Serializable
data class PanelStyle(
    /** A color token or an image name in the theme package's `images/`. */
    val background: String = "",
    /** The panel's corner radius in dp; -1 = unset. */
    val cornerRadius: Float = -1f,
    /** The panel's inner padding; unset = the resolver's default. */
    @Serializable(with = PaddingSerializer::class)
    val padding: Padding = Padding(),
    /**
     * The panel's height (doc/Theme.md §8.2): 0 sizes it to its content, -1
     * fills the space the host offers, a positive value is that many dp. A
     * missing value leaves the host's own layout params alone, which is today's
     * behaviour.
     */
    val height: Int? = null,
    /** The panel's width, with the same values as [height]. */
    val width: Int? = null,
    /** The panel's maximum height in dp; 0 = unlimited. */
    val maxHeight: Int = 0,
    /** The list's left and right margin in dp. */
    val sideMargin: Int = 0,
    /** The vertical spacing between items in dp; -1 = unset. */
    val itemSpacing: Int = -1,
    /**
     * The panel chrome's action button — the option panel's settings button and
     * the clipboard title bar's delete-all button — in dp; 0 = the built-in
     * default, today's `candidate_view_height + comment_height`.
     */
    val actionButtonSize: Int = 0,
    /** Hairline between the panel's list items, in dp; 0 draws none (doc/Theme.md §8.2). */
    val divider: Int = 0,
    /** The divider's colour token; empty falls back to the item's border colour. */
    val dividerColor: String = "",
)
