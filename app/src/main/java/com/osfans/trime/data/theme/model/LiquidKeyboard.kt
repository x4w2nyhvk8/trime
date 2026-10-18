/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable(with = LiquidKeyboardSerializer::class)
data class LiquidKeyboard(
    val singleWidth: Int = 0,
    val keyHeight: Int = 0,
    val marginX: Float = 0f,
    val fixedKeyBar: KeyBar = KeyBar(),
    val keyboards: List<Keyboard> = emptyList(),
    // Cell styling, decoupled from the main keyboard's key tokens (doc/Theme.md §9.1).
    // Every field defaults to "same as a key", so an old theme looks unchanged.
    val cellBackColor: String = "",
    val cellBorder: Int = -1,
    val cellBorderColor: String = "",
    val cellCornerRadius: Float = -1f,
    val cellTextColor: String = "",
    /** Hairline between cells, in dp; 0 draws none. */
    val cellDivider: Int = 0,
    /** Divider colour token; empty falls back to the cell border colour. */
    val cellDividerColor: String = "",
    // Category strip styling (doc/Theme.md §9.2); every default keeps the old look.
    /** Gap between category tabs, in dp. */
    val tabSpacing: Int = 0,
    /** Vertical hairline between tabs, in dp; 0 draws none. */
    val tabDivider: Int = 0,
    /** Tab divider colour token; empty falls back to the cell border colour. */
    val tabDividerColor: String = "",
    /** Tab corner radius; -1 falls back to `style.candidate_corner_radius`. */
    val tabCornerRadius: Float = -1f,
    val tabBackColor: String = "",
    val tabActiveBackColor: String = "",
    val tabTextColor: String = "",
    val tabActiveTextColor: String = "",
    /** Cell text size in sp; 0 uses `style.key_text_size`. */
    val cellTextSize: Float = 0f,
    /** Cell text padding; unset keeps the built-in 8/4 dp. */
    val cellPadding: Padding = Padding(),
    /** Vertical cell spacing in dp; -1 uses [marginX]. */
    val marginY: Float = -1f,
    /** Pressed cell background token; empty uses `hilited_key_back_color`. */
    val cellHighlightColor: String = "",
    /** Tab text size in sp; 0 uses `style.candidate_text_size`. */
    val tabTextSize: Float = 0f,
    /** Category strip height in dp; 0 uses `candidate_view_height + comment_height`. */
    val tabHeight: Int = 0,
    /** Tab text padding; unset keeps `style.candidate_padding` on the horizontal axis. */
    val tabPadding: Padding = Padding(),
    /** Tab minimum width in dp; 0 keeps the built-in 40dp. */
    val tabMinWidth: Int = 0,
    /** The category column's width in dp; 0 keeps the built-in 72dp. */
    val tabColumnWidth: Int = 0,
) {

    @Serializable
    data class KeyBar(
        val keys: List<String> = emptyList(),
        val position: Position = Position.BOTTOM,
    ) {
        enum class Position {
            TOP,
            LEFT,
            BOTTOM,
            RIGHT,
        }
    }

    data class Keyboard(
        val id: String,
        val type: DataType,
        val name: String,
        val keys: List<KeyItem>,
    )

    data class KeyItem(
        val text: String,
        val altText: String,
    ) {
        constructor(text: String) : this(text, text)
    }

    enum class DataType {
        SINGLE,
        SYMBOL,
        TABS,
        HISTORY,
    }

    data class Tag(val label: String = "", val type: DataType)

    @Transient
    private val data = lazy {
        keyboards.map {
            Tag(it.name, it.type) to it.keys.toTypedArray()
        }
    }

    fun getTagList() = data.value.map { it.first }

    fun getDataByIndex(index: Int): List<KeyItem> {
        val item = data.value[index]
        val tag = item.first
        return if (tag.type == DataType.TABS) {
            data.value.mapNotNull {
                if (it.first.type != DataType.TABS) KeyItem(it.first.label) else null
            }
        } else {
            item.second.toList()
        }
    }
}
