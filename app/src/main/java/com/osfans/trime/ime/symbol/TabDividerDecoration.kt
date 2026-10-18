/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import android.graphics.Canvas
import android.graphics.Paint
import androidx.recyclerview.widget.RecyclerView

/**
 * Draws a vertical hairline between the liquid keyboard's category tabs
 * (doc/Theme.md §9.2), in the middle of the tab spacing.
 */
class TabDividerDecoration(
    private val dividerPx: Int,
    color: Int,
    private val spacePx: Int,
) : RecyclerView.ItemDecoration() {
    private val paint = Paint().apply { this.color = color }

    override fun onDraw(
        canvas: Canvas,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val x = child.right + spacePx / 2f
            canvas.drawRect(x - dividerPx / 2f, child.top.toFloat(), x + dividerPx / 2f, child.bottom.toFloat(), paint)
        }
    }
}
