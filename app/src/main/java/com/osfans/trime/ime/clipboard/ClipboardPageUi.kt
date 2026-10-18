/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.PanelPage
import com.osfans.trime.data.theme.PanelStyleResolver
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.symbol.SpacesItemDecoration
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.recyclerview.recyclerView

class ClipboardPageUi(override val ctx: Context, private val scope: ThemeScope) : Ui {
    /** The list's own spacing: today 4dp on every side, now the panel's margin and spacing. */
    private val panelStyle = PanelStyleResolver.panelStyle(scope.theme, PanelPage.CLIPBOARD)

    val recyclerView = recyclerView {
        // The panel paints one rounded surface (ClipboardLayout); the list only
        // clips itself to that same shape, so an image background still rounds
        // (doc/Theme.md §8.2).
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(
                    view: View,
                    outline: Outline,
                ) {
                    outline.setRoundRect(0, 0, view.width, view.height, ctx.dp(panelStyle.cornerRadius))
                }
            }
        clipToOutline = true
        // Half the spacing on either side of a row: the gap between two rows is
        // `item_spacing`, and the divider sits in its middle.
        addItemDecoration(SpacesItemDecoration(dp(panelStyle.sideMargin), dp(panelStyle.itemSpacing) / 2))
        // A theme may separate the rows with a hairline (doc/Theme.md §8.2).
        if (panelStyle.divider > 0) {
            val color =
                runCatching {
                    ColorManager.getColor(
                        panelStyle.dividerColor.ifEmpty { "key_border_color" },
                    )
                }.getOrDefault(Color.GRAY)
            addItemDecoration(
                ClipboardDividerDecoration(
                    dp(panelStyle.divider),
                    color,
                    dp(panelStyle.itemSpacing),
                ),
            )
        }
    }

    /** Re-applies the clip shape and repaints after a scheme switch. */
    fun refreshColors() {
        recyclerView.invalidateOutline()
        recyclerView.invalidate()
    }

    override val root = view(::FrameLayout) {
        add(recyclerView, lParams(matchParent, matchParent))
    }
}
