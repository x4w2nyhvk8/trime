/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import android.content.Context
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.core.AutoScaleTextView
import splitties.dimensions.dp
import splitties.views.gravityCenter

class LiquidAdapter(
    private val scope: ThemeScope,
    private val onItemClick: LiquidKeyboard.KeyItem.(Int) -> Unit,
) : BaseQuickAdapter<LiquidKeyboard.KeyItem, LiquidAdapter.ViewHolder>() {
    inner class ViewHolder(
        val ui: LiquidItemUi,
    ) : RecyclerView.ViewHolder(ui.root)

    /** The grid's cell height in px: the panel's height divided by [GRID_ROWS]. */
    var cellHeight: Int = 0

    companion object {
        /** The symbol grid is 4 columns wide and 5 rows tall (doc/Theme.md §9). */
        const val GRID_COLUMNS = 4
        const val GRID_ROWS = 5
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val ui = LiquidItemUi(context, scope)
        ui.mainText.apply {
            scaleMode = AutoScaleTextView.Mode.Proportional
            gravity = gravityCenter
            updateLayoutParams {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
        }
        ui.root.layoutParams =
            RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                cellHeight.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT,
            )
        return ViewHolder(ui)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        item: LiquidKeyboard.KeyItem?,
    ) {
        item ?: return
        // The grid's cell height is known only after the panel is measured, and a
        // recycled holder keeps the height it was created with — re-apply it here.
        holder.ui.root.updateLayoutParams<RecyclerView.LayoutParams> {
            height = cellHeight.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT
        }
        holder.ui.refreshColors()
        holder.ui.mainText.text = item.text
        holder.ui.root.setOnClickListener {
            onItemClick.invoke(item, position)
        }
    }
}
