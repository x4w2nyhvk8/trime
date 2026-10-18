/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.graphics.Canvas
import android.graphics.Paint
import androidx.recyclerview.widget.RecyclerView

/**
 * A hairline under every clipboard row (doc/Theme.md §8.2), drawn in the middle
 * of the list's item spacing. The spacing itself comes from
 * [com.osfans.trime.ime.symbol.SpacesItemDecoration].
 */
class ClipboardDividerDecoration(
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
        // One row needs no divider; the last row gets none, so the list ends cleanly.
        if (state.itemCount < 2) return
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (parent.getChildAdapterPosition(child) >= state.itemCount - 1) continue
            val y = child.bottom + spacePx / 2f
            canvas.drawRect(
                child.left.toFloat(),
                y - dividerPx / 2f,
                child.right.toFloat(),
                y + dividerPx / 2f,
                paint,
            )
        }
    }
}
