// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.setPadding
import androidx.recyclerview.widget.GridLayoutManager
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.after
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.bottomToTopOf
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.constraintlayout.topToBottomOf
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.recyclerview.recyclerView

@SuppressLint("ViewConstructor")
class LiquidLayout(
    context: Context,
    private val scope: ThemeScope,
    commonKeyboardActionListener: CommonKeyboardActionListener,
) : ConstraintLayout(context) {
    private val theme: Theme get() = scope.theme

    // TODO: 继承一个键盘视图嵌入到这里，而不是自定义一个视图
    // The fixed-key items, kept so their colors can be refreshed on scheme switches.
    private val fixedKeyItems = mutableListOf<LiquidItemUi>()

    private val fixedKeyBar =
        constraintLayout {
            val fixedKeys =
                theme.liquidKeyboard.fixedKeyBar.keys
            if (fixedKeys.isNotEmpty()) {
                val btns =
                    Array(fixedKeys.size) { index ->
                        val presetKeyName = fixedKeys[index]
                        val presetKey = theme.presetKeys[presetKeyName]
                        val ui = LiquidItemUi(context, scope)
                        fixedKeyItems += ui
                        // Only a static label is shown here: a dynamic
                        // `{source: …}` label has no enter label to read in the
                        // fixed key bar (doc/Keyboard.md §2.2).
                        ui.mainText.text = presetKey?.label?.staticText.orEmpty()
                        ui.root.apply {
                            isRepeatable = presetKey?.repeatable ?: false
                            onClick = {
                                commonKeyboardActionListener.listener.onAction(
                                    theme.resolveAction(presetKeyName),
                                )
                            }
                        }
                        return@Array ui.root
                    }
                val marginX = theme.liquidKeyboard.marginX
                when (theme.liquidKeyboard.fixedKeyBar.position) {
                    LiquidKeyboard.KeyBar.Position.LEFT,
                    LiquidKeyboard.KeyBar.Position.RIGHT,
                    -> {
                        btns.forEachIndexed { i, btn ->
                            add(
                                btn,
                                lParams(wrapContent, matchConstraints) {
                                    if (i == 0) {
                                        topOfParent()
                                    } else {
                                        topMargin = dp(marginX).toInt()
                                        below(btns[i - 1])
                                    }
                                    if (i == btns.size - 1) {
                                        bottomOfParent()
                                    } else {
                                        bottomMargin = dp(marginX).toInt()
                                        above(btns[i + 1])
                                    }
                                },
                            )
                        }
                    }

                    LiquidKeyboard.KeyBar.Position.TOP,
                    LiquidKeyboard.KeyBar.Position.BOTTOM,
                    -> {
                        btns.forEachIndexed { i, btn ->
                            add(
                                btn,
                                lParams(wrapContent, matchConstraints) {
                                    if (i == 0) {
                                        startOfParent()
                                    } else {
                                        leftMargin = dp(marginX).toInt()
                                        after(btns[i - 1])
                                    }
                                    if (i == btns.size - 1) {
                                        endOfParent()
                                    } else {
                                        rightMargin = dp(marginX).toInt()
                                        before(btns[i + 1])
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

    /** The category column's width (doc/Theme.md §9); the tab area keeps its own width. */
    private val tabColumnWidth = context.dp(theme.liquidKeyboard.tabColumnWidth.takeIf { it > 0 } ?: 72)

    val recyclerView =
        recyclerView {
            val liquid = theme.liquidKeyboard
            layoutManager = GridLayoutManager(context, LiquidAdapter.GRID_COLUMNS)
            val space = dp(liquid.marginX.toInt())
            val spaceY = dp((liquid.marginY.takeIf { it >= 0f } ?: liquid.marginX).toInt())
            addItemDecoration(SpacesItemDecoration(space, spaceY))
            // A theme may draw hairlines between the cells (doc/Theme.md §9.1).
            if (liquid.cellDivider > 0) {
                val color =
                    runCatching {
                        ColorManager.getColor(
                            liquid.cellDividerColor.ifEmpty {
                                liquid.cellBorderColor.ifEmpty { "key_border_color" }
                            },
                        )
                    }.getOrDefault(Color.GRAY)
                addItemDecoration(CellDividerDecoration(dp(liquid.cellDivider), color, space / 2, LiquidAdapter.GRID_COLUMNS))
            }
            setPadding(space)
            addOnLayoutChangeListener { v, _, _, _, bottom, _, _, _, oldBottom ->
                if (bottom == oldBottom) return@addOnLayoutChangeListener
                val cell = (bottom - paddingTop - paddingBottom - 2 * spaceY * LiquidAdapter.GRID_ROWS) / LiquidAdapter.GRID_ROWS
                (this@recyclerView.adapter as? LiquidAdapter)?.let { adapter ->
                    if (adapter.cellHeight != cell) {
                        adapter.cellHeight = cell
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }

    val tabsUi = LiquidTabsUi(context, scope)

    val root =
        view(::ConstraintLayout) {
            add(
                tabsUi.root,
                lParams(tabColumnWidth, matchConstraints) {
                    startOfParent()
                    topOfParent()
                    bottomOfParent()
                },
            )
            add(
                recyclerView,
                lParams(matchConstraints, matchConstraints) {
                    startToEndOf(tabsUi.root)
                    endOfParent()
                    topOfParent()
                    bottomOfParent()
                },
            )
        }

    init {
        // 搜狗式符号面板：左侧一列分类（可上下滑），右侧 4 列 × 5 行网格，
        // 底部固定键栏（返回在最左），doc/Theme.md §9.
        when (theme.liquidKeyboard.fixedKeyBar.position) {
            LiquidKeyboard.KeyBar.Position.TOP -> {
                add(
                    root,
                    lParams {
                        centerHorizontally()
                        topToBottomOf(fixedKeyBar)
                        bottomOfParent()
                    },
                )
                add(
                    fixedKeyBar,
                    lParams(wrapContent, wrapContent) {
                        centerHorizontally()
                        centerVertically()
                        topOfParent()
                        bottomToTopOf(root)
                    },
                )
            }

            LiquidKeyboard.KeyBar.Position.BOTTOM -> {
                add(
                    root,
                    lParams {
                        centerHorizontally()
                        topOfParent()
                        bottomToTopOf(fixedKeyBar)
                    },
                )
                add(
                    fixedKeyBar,
                    lParams(wrapContent, wrapContent) {
                        centerHorizontally()
                        centerVertically()
                        topToBottomOf(root)
                        bottomOfParent()
                    },
                )
            }

            LiquidKeyboard.KeyBar.Position.LEFT -> {
                add(
                    root,
                    lParams {
                        topOfParent()
                        startToEndOf(fixedKeyBar)
                        endOfParent()
                        bottomOfParent()
                    },
                )
                add(
                    fixedKeyBar,
                    lParams(wrapContent, matchConstraints) {
                        centerVertically()
                        startOfParent()
                        endToStartOf(root)
                    },
                )
            }

            LiquidKeyboard.KeyBar.Position.RIGHT -> {
                add(
                    root,
                    lParams {
                        topOfParent()
                        startOfParent()
                        endToStartOf(fixedKeyBar)
                        bottomOfParent()
                    },
                )
                add(
                    fixedKeyBar,
                    lParams(wrapContent, matchConstraints) {
                        centerVertically()
                        startToEndOf(root)
                        endOfParent()
                    },
                )
            }
        }
    }

    fun refreshColors() {
        fixedKeyItems.forEach { it.refreshColors() }
        tabsUi.refreshColors()
        recyclerView.adapter?.notifyDataSetChanged()
    }
}
