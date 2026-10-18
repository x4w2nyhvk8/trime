/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.symbol

import android.view.View
import com.osfans.trime.data.SymbolHistory
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.ResidentWindow
import org.kodein.di.DI
import org.kodein.di.instance

class LiquidWindow(di: DI) :
    BoardWindow.BarBoardWindow(di),
    ResidentWindow {
    override val showTitle = false

    private val service: TrimeInputMethodService by instance()
    private val scope: ThemeScope by instance()
    private val commonKeyboardActionListener: CommonKeyboardActionListener by instance()

    private lateinit var liquidLayout: LiquidLayout
    private val symbolHistory = SymbolHistory(180)
    var currentDataType: LiquidKeyboard.DataType = LiquidKeyboard.DataType.SINGLE
        private set

    private val adapter by lazy {
        LiquidAdapter(scope) {
            when (currentDataType) {
                // Every cell commits its text verbatim: the panel no longer goes
                // through the engine (and no longer flips ascii mode) just to
                // type a kaomoji (doc/Theme.md §9).
                LiquidKeyboard.DataType.TABS -> {
                    val index = scope.theme.liquidKeyboard.getTagList()
                        .indexOfFirst { it.label == this.text }
                    if (index >= 0) setDataByIndex(index)
                }

                else -> {
                    service.commitText(this.text)
                    if (currentDataType != LiquidKeyboard.DataType.HISTORY) {
                        symbolHistory.insert(this.text)
                        symbolHistory.save()
                    }
                }
            }
        }
    }

    companion object : ResidentWindow.Key

    override val key: ResidentWindow.Key
        get() = LiquidWindow

    override fun onCreateView(): View = LiquidLayout(context, scope, commonKeyboardActionListener).apply {
        liquidLayout = this
        tabsUi.apply {
            setTags(scope.theme.liquidKeyboard.getTagList())
            setOnTabClickListener { i ->
                setDataByIndex(i)
            }
        }
        recyclerView.apply {
            this.adapter = this@LiquidWindow.adapter
        }
    }

    // The category column lives inside the layout (left of the grid), so the
    // window has no bar of its own.
    override fun onCreateBarView(): View? = null

    override fun onAttached() {}

    override fun onDetached() {}

    override fun refreshColors() {
        if (::liquidLayout.isInitialized) liquidLayout.refreshColors()
    }

    fun setDataByIndex(i: Int) {
        val tag = scope.theme.liquidKeyboard.getTagList()[i]
        currentDataType = tag.type
        liquidLayout.tabsUi.activateTab(i)
        when (tag.type) {
            LiquidKeyboard.DataType.HISTORY -> {
                symbolHistory.load()
                submitData(symbolHistory.toOrderedList().map { LiquidKeyboard.KeyItem(it) })
            }

            else -> {
                val data = scope.theme.liquidKeyboard.getDataByIndex(i)
                submitData(data)
            }
        }
    }

    private fun submitData(data: List<LiquidKeyboard.KeyItem>) {
        adapter.submitList(data)
    }
}
