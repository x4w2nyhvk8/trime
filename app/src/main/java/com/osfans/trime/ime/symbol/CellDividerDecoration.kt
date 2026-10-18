/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import android.graphics.Canvas
import android.graphics.Paint
import androidx.recyclerview.widget.RecyclerView

/**
 * Draws the liquid keyboard's cell dividers (doc/Theme.md §9.1): one hairline on
 * the right and one under every cell, in the middle of the cell spacing. The
 * item spacing itself is still handled by [SpacesItemDecoration].
 */
class CellDividerDecoration(
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
            val right = child.right + spacePx / 2f
            val bottom = child.bottom + spacePx / 2f
            canvas.drawRect(right - dividerPx / 2f, child.top.toFloat(), right + dividerPx / 2f, bottom, paint)
            canvas.drawRect(child.left.toFloat(), bottom - dividerPx / 2f, right, bottom + dividerPx / 2f, paint)
        }
    }
}
