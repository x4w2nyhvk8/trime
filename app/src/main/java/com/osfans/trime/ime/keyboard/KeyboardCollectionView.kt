/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.ScrollDirection
import splitties.dimensions.dp
import kotlin.math.max

/**
 * One embedded collection of a keyboard layout (doc/Keyboard.md §2.6): a
 * scrollable grid of symbols read from the theme's `collections:`, drawn in the
 * rectangle the layout engine reserved for it.
 *
 * The cells reuse the keyboard's own color tokens (`key_back_color`,
 * `key_text_color`, `key_border_color`, `key_border`) and its `key_text_size`
 * and corner radius, so a theme styles them without new settings. A tap commits
 * the cell through [KeyboardActionListener.onCollectionItem]. A missing or empty
 * collection shows an empty grid — the load-time diagnostics report the
 * reference instead of failing the theme.
 */
@SuppressLint("ViewConstructor")
class KeyboardCollectionView(
    context: Context,
    private val theme: Theme,
    private val placement: Keyboard.CollectionPlacement,
    private val keyboardActionListener: KeyboardActionListener,
) : FrameLayout(context) {

    private val spec get() = placement.spec

    /** Cell height in px: `cell_height`, or the keyboard's default row height. */
    private val cellHeight: Int =
        if (spec.cellHeight > 0f) context.dp(spec.cellHeight).toInt() else context.dp(theme.style.keyHeight)

    /** Cell width in px: `cell_width`, or a square cell. */
    private val cellWidth: Int =
        if (spec.cellWidth > 0f) context.dp(spec.cellWidth).toInt() else cellHeight

    private val adapter = CellAdapter()

    init {
        addView(
            RecyclerView(context).apply {
                layoutManager = when (spec.scroll) {
                    ScrollDirection.HORIZONTAL -> LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                    ScrollDirection.VERTICAL -> GridLayoutManager(context, columns())
                }
                adapter = this@KeyboardCollectionView.adapter
                overScrollMode = RecyclerView.OVER_SCROLL_NEVER
                // The cells answer taps; the grid itself never takes input focus.
                isFocusable = false
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    /** Cells per line for a vertical collection: `columns`, or as many square cells as fit. */
    private fun columns(): Int {
        if (spec.columns > 0) return spec.columns
        val available = if (placement.width > 0) placement.width else cellWidth
        return max(1, available / max(1, cellWidth))
    }

    /** Rebinds the cells, so a day/night switch is picked up on the next bind. */
    fun refreshColors() {
        adapter.notifyDataSetChanged()
    }

    private inner class CellHolder(
        val text: TextView,
    ) : RecyclerView.ViewHolder(text)

    private inner class CellAdapter : RecyclerView.Adapter<CellHolder>() {
        override fun getItemCount(): Int = placement.items.size

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): CellHolder {
            val text = TextView(context).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.style.keyTextSize)
                typeface = theme.fonts.key
                maxLines = 1
                isClickable = true
                isFocusable = false
                layoutParams = RecyclerView.LayoutParams(
                    if (spec.scroll == ScrollDirection.HORIZONTAL) cellWidth else ViewGroup.LayoutParams.MATCH_PARENT,
                    if (spec.scroll == ScrollDirection.HORIZONTAL) ViewGroup.LayoutParams.MATCH_PARENT else cellHeight,
                )
            }
            return CellHolder(text)
        }

        override fun onBindViewHolder(
            holder: CellHolder,
            position: Int,
        ) {
            val item = placement.items[position]
            holder.text.apply {
                text = item
                setTextColor(runCatching { ColorManager.getColor("key_text_color") }.getOrDefault(Color.BLACK))
                background = cellBackground()
                setOnClickListener {
                    keyboardActionListener.onCollectionItem(item)
                }
            }
        }
    }

    /**
     * The cell background, built from the same tokens a key uses. A theme may
     * paint `key_back_color` with an image, which cannot be turned into a rounded
     * block, so it is used as the cell's drawable as-is.
     */
    private fun cellBackground(): Drawable {
        val color = runCatching { ColorManager.getColor("key_back_color") }.getOrNull()
            ?: return ColorManager.getDrawable("key_back_color") ?: GradientDrawable()
        val cornerRatio = theme.style.keyCornerRatio
        val radius =
            if (cornerRatio > 0f) cellHeight * cornerRatio else context.dp(theme.style.roundCorner)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
            theme.style.keyBorder.takeIf { it > 0 }?.let {
                setStroke(context.dp(it), ColorManager.getColor("key_border_color"))
            }
        }
    }
}
