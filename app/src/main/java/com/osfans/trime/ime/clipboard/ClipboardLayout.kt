/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.annotation.SuppressLint
import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.viewpager2.widget.ViewPager2
import com.osfans.trime.data.theme.PanelPage
import com.osfans.trime.data.theme.PanelStyleResolver
import com.osfans.trime.data.theme.ThemeScope
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.add
import splitties.views.dsl.core.view

@SuppressLint("ViewConstructor")
class ClipboardLayout(context: Context, private val scope: ThemeScope) : ConstraintLayout(context) {

    val viewPager = view(::ViewPager2) {}

    val titleUi = ClipboardTitleUi(context, scope)

    init {
        // The panel's own style: background, corner and padding (doc/Theme.md §8.2).
        val panelStyle = PanelStyleResolver.panelStyle(scope.theme, PanelPage.CLIPBOARD)
        background = scope.decorDrawable(panelStyle.background, cornerRadius = panelStyle.cornerRadius)
        val horizontal = dp(panelStyle.padding.horizontal)
        val vertical = dp(panelStyle.padding.vertical)
        setPadding(horizontal, vertical, horizontal, vertical)
        add(
            viewPager,
            lParams {
                centerInParent()
            },
        )
    }

    /** Restyles the panel after a scheme switch; its background may be a color token. */
    fun refreshColors() {
        val panelStyle = PanelStyleResolver.panelStyle(scope.theme, PanelPage.CLIPBOARD)
        background = scope.decorDrawable(panelStyle.background, cornerRadius = panelStyle.cornerRadius)
    }
}
