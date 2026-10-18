/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.children
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.broadcast.EnterKeyDisplayDelegate
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.popup.PopupDelegate

// TODO: move layout calculation responsibilities from Keyboard to KeyboardView using ConstraintLayout
@SuppressLint("ViewConstructor")
class KeyboardView(
    context: Context,
    private val theme: Theme,
    private val keyboard: Keyboard,
    val popup: PopupDelegate,
    val service: TrimeInputMethodService,
    private val keyboardActionListener: KeyboardActionListener,
    private val enterKeyDisplay: EnterKeyDisplayDelegate,
) : FrameLayout(context) {

    private val keys get() = keyboard.keys

    internal val labelEnter: String
        get() = enterKeyDisplay.keyLabel
    internal val keyTextSize = theme.style.keyTextSize
    internal val keyLongTextSize = theme.style.keyLongTextSize.takeIf { it > 0 } ?: keyTextSize
    internal val symbolTextSize = theme.style.symbolTextSize.takeIf { it > 0 } ?: keyTextSize

    /** Key corner radius as a fraction of the key height; 0 keeps the dp radius. */
    internal val keyCornerRatio = theme.style.keyCornerRatio
    internal val popupOnKeyPress by AppPrefs.defaultInstance().keyboard.popupOnKeyPress
    internal val hookShiftArrow: Boolean by AppPrefs.defaultInstance().keyboard.hookShiftArrow
    internal val hookShiftLock: Boolean by AppPrefs.defaultInstance().keyboard.hookShiftLock
    internal val hideKeySymbol: Boolean by AppPrefs.defaultInstance().keyboard.hideKeySymbol
    internal val hideKeyHint: Boolean by AppPrefs.defaultInstance().keyboard.hideKeyHint

    init {
        setWillNotDraw(false)
        buildKeyViews()
    }

    private fun buildKeyViews() {
        removeAllViews()

        keys.forEachIndexed { index, key ->
            val keyView = createKeyView(index, key)
            addView(keyView)
        }

        // Embedded collections are placed after the keys, so a key's index stays
        // its child index (see invalidateKeyByIndex, doc/Keyboard.md §2.6).
        keyboard.collections.forEach { collection ->
            addView(createCollectionView(collection))
        }
    }

    private fun createCollectionView(collection: Keyboard.CollectionPlacement): View = KeyboardCollectionView(context, theme, collection, keyboardActionListener).apply {
        layoutParams = LayoutParams(collection.width, collection.height)
        translationX = collection.x.toFloat()
        translationY = collection.y.toFloat()
    }

    private fun createKeyView(index: Int, key: Key): KeyView = KeyView(context, theme, key, keyboard, this, keyboardActionListener).apply {
        id = index

        val totalWidth = key.width + key.extraWidthLeft + key.extraWidthRight
        layoutParams = LayoutParams(totalWidth, key.height)

        translationX = (key.x - key.extraWidthLeft).toFloat()
        translationY = key.y.toFloat()

        setPadding(
            keyboard.horizontalGap / 2 + key.extraWidthLeft,
            keyboard.verticalGap / 2,
            keyboard.horizontalGap / 2 + key.extraWidthRight,
            keyboard.verticalGap / 2,
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fullWidth = keyboard.minWidth + paddingLeft + paddingRight
        val fullHeight = keyboard.height + paddingTop + paddingBottom

        val measuredWidth = minOf(
            MeasureSpec.getSize(widthMeasureSpec),
            fullWidth,
        )

        measureChildren(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, fullHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

    fun invalidateAllKeys() {
        children.forEach { child ->
            if (child is KeyboardCollectionView) child.refreshColors() else child.invalidate()
        }
    }

    fun invalidateKeyByIndex(index: Int) {
        getChildAt(index)?.invalidate()
    }

    val isCapsOn: Boolean
        get() = keyboard.mShiftKey?.isOn == true

    fun onDetach() {
        popup.dismissAll()
    }
}
