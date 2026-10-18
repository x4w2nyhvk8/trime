/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.switches

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.StateListDrawable
import android.icu.text.BreakIterator
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.updateLayoutParams
import com.osfans.trime.data.theme.ItemStyleResolver
import com.osfans.trime.data.theme.OptionPanelItemKind
import com.osfans.trime.data.theme.ResolvedItemStyle
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.core.AutoScaleTextView
import com.osfans.trime.ime.keyboard.GestureFrame
import com.osfans.trime.util.toTruncateAt
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.imageDrawable

/**
 * One option-panel row, styled by the slot of its entry kind (doc/Theme.md §8.4):
 * the row reads a [ResolvedItemStyle] and applies it, so no number or color is
 * borrowed from the keyboard and every one of them is configurable. With no
 * style node the resolved values are today's appearance.
 */
class SwitchOptionEntryUi(
    override val ctx: Context,
    private val scope: ThemeScope,
) : Ui {
    private val theme: Theme get() = scope.theme

    /** The slot this row currently draws; [setEntry] replaces it with the entry's own kind. */
    private var style: ResolvedItemStyle = ItemStyleResolver.optionItemStyle(theme, OptionPanelItemKind.ACTION)
    private var isOn = false

    private val box = frameLayout {}

    val icon =
        imageView {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

    val textIcon =
        view(::AutoScaleTextView) {
            // keep original typeface, apply textStyle only
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // 600 = Semi Bold, 700 = Bold which is too heavy
                typeface = Typeface.create(typeface, 600, false)
            } else {
                setTypeface(typeface, Typeface.BOLD)
            }
        }

    val label =
        textView {
            gravity = gravityCenter
        }

    private val content =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(box, LinearLayout.LayoutParams(ctx.dp(ItemStyleResolver.OPTION_ICON_SIZE), ctx.dp(ItemStyleResolver.OPTION_ICON_SIZE)))
            addView(label, LinearLayout.LayoutParams(wrapContent, wrapContent))
        }

    override val root =
        GestureFrame(ctx).apply {
            isClickable = true
            add(content, lParams(matchParent, matchParent))
            layoutParams = ViewGroup.LayoutParams(matchParent, ctx.dp(ItemStyleResolver.OPTION_ROW_HEIGHT))
        }

    init {
        box.addView(
            icon,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
        )
        box.addView(
            textIcon,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
        )
        applyStyle(style, isOn)
    }

    /** Restyles the row after a scheme switch; the colors and sizes are re-read on every bind. */
    fun refreshColors() {
        applyStyle(style, isOn)
    }

    fun setEntry(entry: SwitchOptionEntry) {
        isOn = entry.isOn
        applyStyle(ItemStyleResolver.optionItemStyle(theme, entry.itemKind), isOn)
        if (entry.icon != 0) {
            icon.visibility = View.VISIBLE
            textIcon.visibility = View.GONE
            icon.imageDrawable = ctx.drawable(entry.icon)!!.apply {
                mutate()
                setTint(scope.color(style.iconColor))
            }
        } else {
            icon.visibility = View.GONE
            textIcon.visibility = View.VISIBLE
            textIcon.text = getFirstCharacter(entry.label)
            textIcon.setTextColor(scope.color(style.iconColor))
        }
        label.text = entry.label
    }

    /** Applies one slot's appearance to this row's views and layout. */
    private fun applyStyle(style: ResolvedItemStyle, on: Boolean) {
        this.style = style
        val borderPx = ctx.dp(style.borderWidth)
        val normal = scope.decorDrawable(style.backgroundFor(on), style.borderColor, borderPx, style.cornerRadius)
        val highlight =
            style.backgroundHighlight
                .takeIf { it.isNotEmpty() }
                ?.let { scope.decorDrawable(it, style.borderColor, borderPx, style.cornerRadius) }
        root.background =
            if (normal != null && highlight != null) {
                // A pressed background is the only stateful slot §8.3 declares
                // besides a switch's on state, which `backgroundFor` selects.
                StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), highlight)
                    addState(intArrayOf(), normal)
                }
            } else {
                normal
            }
        label.setTextColor(scope.color(style.textColor))
        label.textSize = style.labelSize
        label.maxLines = style.maxLines.takeIf { it > 0 } ?: Int.MAX_VALUE
        label.ellipsize = style.ellipsize.toTruncateAt()
        textIcon.setTextSize(TypedValue.COMPLEX_UNIT_DIP, style.iconSize * ItemStyleResolver.OPTION_TEXT_ICON_RATIO)
        updateBox(style)
        updateHeight(style.height)
    }

    /** The icon box carries `icon_size`, and the item's padding its top inset and icon-to-label gap. */
    private fun updateBox(style: ResolvedItemStyle) {
        (box.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.width = ctx.dp(style.iconSize)
            params.height = ctx.dp(style.iconSize)
            params.topMargin = ctx.dp(style.padding.vertical)
            box.layoutParams = params
        }
        (label.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.topMargin = ctx.dp(style.padding.horizontal)
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            label.layoutParams = params
        }
    }

    private fun updateHeight(heightDp: Int) {
        root.updateLayoutParams<ViewGroup.LayoutParams> {
            height = if (heightDp > 0) ctx.dp(heightDp) else ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }

    private fun getFirstCharacter(s: String): String {
        if (s.isEmpty()) return ""
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val iterator = BreakIterator.getCharacterInstance()
            iterator.setText(s)
            s.substring(iterator.first(), iterator.next())
        } else {
            s.substring(0, s.offsetByCodePoints(0, 1))
        }
    }
}
