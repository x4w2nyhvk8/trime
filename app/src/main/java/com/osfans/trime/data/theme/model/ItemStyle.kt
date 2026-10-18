/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import kotlinx.serialization.Serializable

/**
 * One `styles:` node of the item family, referenced by the panel/list item slots
 * of doc/Theme.md §8.1.
 *
 * Every field is optional: a field that is absent (or 0 / -1) keeps the
 * resolver's built-in default, which is today's appearance (see
 * [com.osfans.trime.data.theme.PanelStyleResolver]).
 */
@Serializable
data class ItemStyle(
    /** The item's height in dp; 0 = the resolver's default (content-sized where that is the default). */
    val height: Int = 0,
    /** The item's minimum height in dp; 0 = the resolver's default. */
    val minHeight: Int = 0,
    /** The item's corner radius in dp; -1 = unset. */
    val cornerRadius: Float = -1f,
    /** The item's border width in dp; -1 = unset. */
    val borderWidth: Int = -1,
    /** The item's border color (a color token or a literal); "" = unset. */
    val borderColor: String = "",
    /** The normal background (a color token or an image name); "" = unset. */
    val background: String = "",
    /** The on-state background of a Rime switch item; "" = [background]. */
    val backgroundOn: String = "",
    /** The pressed background; "" = [background]. */
    val backgroundHighlight: String = "",
    /** The text color (a color token or a literal); "" = unset. */
    val textColor: String = "",
    /** The label's text size; 0 = unset. */
    val labelSize: Float = 0f,
    /** The label's maximum lines; 0 = the resolver's default. */
    val maxLines: Int = 0,
    /** The label's ellipsis mode; null = the resolver's default. */
    val ellipsize: Ellipsize? = null,
    /** The left icon's box size in dp; 0 = unset. */
    val iconSize: Int = 0,
    /** The left icon's tint (a color token or a literal); "" = unset. */
    val iconColor: String = "",
    /** The pinned marker's size in dp; 0 = unset. */
    val pinSize: Int = 0,
    /** The pinned marker's tint (a color token or a literal); "" = unset. */
    val pinColor: String = "",
    /** The pinned marker's inset from the item's bottom-right corner; -1 = unset. */
    val pinInset: Int = -1,
    /** The item's inner padding; unset = the resolver's default. */
    @Serializable(with = PaddingSerializer::class)
    val padding: Padding = Padding(),
) {
    /** How a label that does not fit is truncated (doc/Theme.md §8.3). */
    enum class Ellipsize {
        NONE,
        START,
        MIDDLE,
        END,
        MARQUEE,
    }
}
