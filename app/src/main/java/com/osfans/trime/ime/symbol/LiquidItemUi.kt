/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import android.content.Context
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.core.AutoScaleTextView
import com.osfans.trime.ime.keyboard.GestureFrame
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp

class LiquidItemUi(
    override val ctx: Context,
    private val scope: ThemeScope,
) : Ui {
    private val theme: Theme get() = scope.theme

    val mainText = view(::AutoScaleTextView) {
        isClickable = false
        isFocusable = false
        background = null
        textSize = liquid.cellTextSize.takeIf { it > 0f } ?: theme.style.keyTextSize
        typeface = theme.fonts.key
        val padding = liquid.cellPadding
        if (padding.isSet) {
            setPaddingDp(padding.horizontal, padding.vertical, padding.horizontal, padding.vertical)
        } else {
            setPaddingDp(8, 4, 8, 4)
        }
        setTextColor(
            liquid.cellTextColor
                .takeIf { it.isNotEmpty() }
                ?.let { runCatching { ColorManager.getColor(it) }.getOrNull() }
                ?: scope.colors.keyTextColor,
        )
    }

    /** The panel's cell styling, each field falling back to a keyboard key (doc/Theme.md §9.1). */
    private val liquid get() = theme.liquidKeyboard

    private val content = constraintLayout {
        background = normalBackground()
        add(
            mainText,
            lParams(wrapContent, wrapContent) {
                centerInParent()
            },
        )
    }

    override val root = view(::GestureFrame) {
        add(content, lParams(matchParent, matchParent))
        onPress = { content.background = highlightedBackground() }
        onRelease = { _, _ -> content.background = normalBackground() }
        onCancel = { content.background = normalBackground() }
    }

    /** The cell's resting background (doc/Theme.md §9.1). */
    private fun normalBackground() = scope.decorDrawable(
        liquid.cellBackColor.ifEmpty { "key_back_color" },
        liquid.cellBorderColor.ifEmpty { "key_border_color" },
        ctx.dp(liquid.cellBorder.takeIf { it >= 0 } ?: theme.style.keyBorder),
        ctx.dp(liquid.cellCornerRadius.takeIf { it >= 0f } ?: theme.style.roundCorner),
    )

    /** The pressed background; an undeclared token falls back to a key's pressed colour. */
    private fun highlightedBackground() = scope.decorDrawable(
        liquid.cellHighlightColor.ifEmpty { "hilited_key_back_color" },
        liquid.cellBorderColor.ifEmpty { "key_border_color" },
        ctx.dp(liquid.cellBorder.takeIf { it >= 0 } ?: theme.style.keyBorder),
        ctx.dp(liquid.cellCornerRadius.takeIf { it >= 0f } ?: theme.style.roundCorner),
    )

    /** Restyles the item after a scheme switch. */
    fun refreshColors() {
        mainText.setTextColor(
            liquid.cellTextColor
                .takeIf { it.isNotEmpty() }
                ?.let { runCatching { ColorManager.getColor(it) }.getOrNull() }
                ?: scope.colors.keyTextColor,
        )
        content.background = normalBackground()
    }
}
