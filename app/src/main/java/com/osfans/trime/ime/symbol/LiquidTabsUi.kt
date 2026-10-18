// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.keyboard.GestureFrame
import com.osfans.trime.util.roundedRippleDrawable
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.gravityCenter
import splitties.views.horizontalPadding

class LiquidTabsUi(
    override val ctx: Context,
    private val scope: ThemeScope,
) : Ui {
    private val theme: Theme get() = scope.theme

    private val liquid get() = theme.liquidKeyboard

    /** The tab chip's corner radius, falling back to the candidate pill (doc/Theme.md §9.2). */
    private val cornerRadiusPx: Float =
        ctx.dp(liquid.tabCornerRadius.takeIf { it >= 0f } ?: theme.style.candidateCornerRadius)

    private fun colorOf(
        token: String,
        fallback: Int,
    ): Int = token.takeIf { it.isNotEmpty() }
        ?.let { runCatching { ColorManager.getColor(it) }.getOrNull() }
        ?: fallback

    inner class TabUi : Ui {
        override val ctx = this@LiquidTabsUi.ctx

        val text =
            textView {
                textSize = liquid.tabTextSize.takeIf { it > 0f } ?: theme.style.candidateTextSize
                typeface = theme.fonts.candidate
                setTextColor(scope.colors.candidateTextColor)
            }

        override val root =
            view(::GestureFrame) {
                minimumWidth = dp(liquid.tabMinWidth.takeIf { it > 0 } ?: 40)
                val tabPadding = liquid.tabPadding
                val tabPadPx =
                    if (tabPadding.isSet) dp(tabPadding.horizontal) else dp(theme.style.candidatePadding)
                add(
                    text,
                    lParams {
                        gravity = gravityCenter
                        horizontalPadding = tabPadPx
                    },
                )
                background =
                    roundedRippleDrawable(
                        colorOf(liquid.tabActiveBackColor, scope.colors.hilitedCandidateBackColor),
                        cornerRadiusPx,
                    )
            }

        fun setText(str: String) {
            text.text = str
        }

        fun setActive(active: Boolean) {
            val activeBack = colorOf(liquid.tabActiveBackColor, scope.colors.hilitedCandidateBackColor)
            val color =
                if (active) {
                    colorOf(liquid.tabActiveTextColor, scope.colors.hilitedCandidateTextColor)
                } else {
                    colorOf(liquid.tabTextColor, scope.colors.candidateTextColor)
                }
            val contentColor =
                if (active) activeBack else colorOf(liquid.tabBackColor, Color.TRANSPARENT)
            text.setTextColor(color)
            root.background = roundedRippleDrawable(activeBack, cornerRadiusPx, contentColor)
        }
    }

    private var onTabClick: ((Int) -> Unit)? = null

    private class TabUiHolder(
        val ui: TabUi,
    ) : RecyclerView.ViewHolder(ui.root)

    private val adapter by lazy {
        object : BaseQuickAdapter<LiquidKeyboard.Tag, TabUiHolder>() {
            private var selected = -1

            override fun onCreateViewHolder(
                context: Context,
                parent: ViewGroup,
                viewType: Int,
            ) = TabUiHolder(TabUi())

            override fun onBindViewHolder(
                holder: TabUiHolder,
                position: Int,
                item: LiquidKeyboard.Tag?,
            ) {
                holder.ui.apply {
                    setText(item!!.label)
                    setActive(position == selected)
                    root.run {
                        val spacing = ctx.dp(liquid.tabSpacing)
                        layoutParams =
                            (parent as? RecyclerView)?.let {
                                RecyclerView.LayoutParams(wrapContent, matchParent).apply {
                                    marginEnd = spacing
                                }
                            } ?: ViewGroup.LayoutParams(wrapContent, matchParent)
                    }
                }
            }

            override fun submitList(list: List<LiquidKeyboard.Tag>?, commitCallback: Runnable?) {
                selected = -1
                super.submitList(list, commitCallback)
            }

            fun activateTab(position: Int) {
                if (position == selected) return
                notifyItemChanged(selected)
                selected = position
                notifyItemChanged(position)
            }
        }.apply {
            setOnItemClickListener { _, _, position ->
                onTabClick?.invoke(position)
            }
        }
    }

    override val root =
        recyclerView {
            // The categories stand in a column to the grid's left (doc/Theme.md §9).
            layoutManager = LinearLayoutManager(ctx, RecyclerView.VERTICAL, false)
            adapter = this@LiquidTabsUi.adapter
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            if (liquid.tabDivider > 0) {
                addItemDecoration(
                    TabDividerDecoration(
                        ctx.dp(liquid.tabDivider),
                        colorOf(liquid.tabDividerColor, scope.colors.candidateBorderColor),
                        ctx.dp(liquid.tabSpacing),
                    ),
                )
            }
        }

    fun setTags(tags: List<LiquidKeyboard.Tag>) {
        adapter.submitList(tags)
    }

    fun activateTab(index: Int) {
        adapter.activateTab(index)
        root.post { root.scrollToPosition(index) }
    }

    fun setOnTabClickListener(listener: ((Int) -> Unit)? = null) {
        onTabClick = listener
    }

    /** Restyles the tabs after a scheme switch; rows re-apply colors on rebind. */
    fun refreshColors() {
        adapter.notifyDataSetChanged()
    }
}
