/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import kotlinx.serialization.Serializable

@Serializable
data class ToolBar(
    val primaryButton: Button? = null,
    val buttons: List<Button> = emptyList(),
    /** Toolbar height in dp; 0 keeps `candidate_view_height + comment_height` (doc/Theme.md §8). */
    val height: Int = 0,
    /** Default toolbar button size in dp; 0 follows [height]. */
    val buttonSize: Int = 0,
    val buttonSpacing: Int = 18,
    val buttonFont: MaybeStringList = MaybeStringList.Empty,
    val backIcon: String = "ic@arrow-left",
) {

    @Serializable
    data class Button(
        val background: Background = Background(),
        val foreground: Foreground = Foreground(),
        val action: String = "",
        val longPressAction: String = "",
        @Serializable(with = LenientIntListSerializer::class)
        val size: List<Int> = emptyList(),
    ) {

        @Serializable
        data class Background(
            val type: Type = Type.RECTANGLE,
            val cornerRadius: Float = 10f,
            val normal: String = "",
            val highlight: String = "",
            val verticalInset: Int = 4,
            val horizontalInset: Int = 4,
        ) {
            enum class Type {
                RECTANGLE,
                CIRCLE,
            }
        }

        @Serializable
        data class Foreground(
            val icon: String = "",
            @Serializable(with = LenientStringListSerializer::class)
            val optionIcons: List<String> = emptyList(),
            val normal: String = "",
            val highlight: String = "",
            val fontSize: Float = 18f,
            val padding: Int = 4,
        )
    }
}
