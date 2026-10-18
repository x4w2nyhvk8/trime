/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.content.Context
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import com.osfans.trime.R
import com.osfans.trime.data.theme.ClipboardItemKind
import com.osfans.trime.data.theme.ItemStyleResolver
import com.osfans.trime.data.theme.ResolvedItemStyle
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.keyboard.GestureFrame
import com.osfans.trime.util.toTruncateAt
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable

/**
 * One clipboard row, styled by its slot of doc/Theme.md §8.4: an ordinary row
 * uses `item_style`, a pinned one `pinned_item_style`. The row reads a
 * [ResolvedItemStyle], so nothing is borrowed from the keyboard and its height,
 * padding, pin and text are all configurable; without a style node the resolved
 * values are today's appearance.
 */
class ClipboardBeanUi(
    override val ctx: Context,
    private val scope: ThemeScope,
) : Ui {
    private val theme: Theme get() = scope.theme

    /** The slot this row currently draws; [setBean] replaces it with the row's own kind. */
    private var style: ResolvedItemStyle = ItemStyleResolver.clipboardItemStyle(theme, ClipboardItemKind.ITEM)

    val textView =
        textView {
            minLines = 1
            typeface = theme.fonts.key
        }

    val pin =
        imageView {
            imageDrawable =
                drawable(R.drawable.ic_baseline_push_pin_24)!!.apply {
                    setAlpha(0.3f)
                }
        }

    private val layout =
        constraintLayout {
            add(
                textView,
                lParams(matchParent, wrapContent) {
                    centerVertically()
                },
            )
        }

    override val root =
        GestureFrame(ctx).apply {
            isClickable = true
            add(layout, lParams(matchParent, matchParent))
            add(pin, FrameLayout.LayoutParams(ctx.dp(12), ctx.dp(12), Gravity.BOTTOM or Gravity.END))
            layoutParams = ViewGroup.LayoutParams(matchParent, wrapContent)
        }

    init {
        applyStyle(style)
    }

    fun setBean(
        text: String,
        pinned: Boolean,
    ) {
        applyStyle(ItemStyleResolver.clipboardItemStyle(theme, ClipboardItemKind.of(pinned)))
        textView.text = text
        pin.visibility = if (pinned) View.VISIBLE else View.GONE
    }

    /** Restyles the row after a scheme switch; the colors are re-read on every bind. */
    fun refreshColors() {
        applyStyle(style)
    }

    /** Applies one slot's appearance to this row's views and layout. */
    private fun applyStyle(style: ResolvedItemStyle) {
        this.style = style
        val borderPx = ctx.dp(style.borderWidth)
        val normal =
            style.background
                .takeIf { it.isNotEmpty() }
                ?.let { scope.decorDrawable(it, style.borderColor, borderPx, style.cornerRadius) }
        val highlight =
            style.backgroundHighlight
                .takeIf { it.isNotEmpty() }
                ?.let { scope.decorDrawable(it, style.borderColor, borderPx, style.cornerRadius) }
        // The list paints one shared surface, so a row is transparent at rest and
        // only shows the pressed state (doc/Theme.md §8.2).
        root.background =
            if (highlight != null) {
                StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), highlight)
                    addState(intArrayOf(), normal)
                }
            } else {
                normal
            }
        root.minimumHeight = ctx.dp(style.minHeight)
        root.updateLayoutParams<ViewGroup.LayoutParams> {
            height = if (style.height > 0) ctx.dp(style.height) else ViewGroup.LayoutParams.WRAP_CONTENT
        }
        textView.setTextColor(scope.color(style.textColor))
        textView.textSize = style.labelSize
        textView.maxLines = style.maxLines.takeIf { it > 0 } ?: Int.MAX_VALUE
        textView.ellipsize = style.ellipsize.toTruncateAt()
        val horizontal = ctx.dp(style.padding.horizontal)
        val vertical = ctx.dp(style.padding.vertical)
        textView.setPadding(horizontal, vertical, horizontal, vertical)
        pin.drawable?.setTint(scope.color(style.pinColor))
        (pin.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.width = ctx.dp(style.pinSize)
            params.height = ctx.dp(style.pinSize)
            params.rightMargin = ctx.dp(style.pinInset)
            params.bottomMargin = ctx.dp(style.pinInset)
            pin.layoutParams = params
        }
    }
}
