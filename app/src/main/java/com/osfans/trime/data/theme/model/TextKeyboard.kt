/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import com.osfans.trime.util.enum
import kotlinx.serialization.Serializable

@Serializable
data class TextKeyboard(
    val name: String = "",
    val author: String = "",
    val width: Float = 0f,
    val height: Float = 0f,
    val keyboardHeight: Int = 0,
    val keyboardHeightLand: Int = 0,
    val autoHeightIndex: Int = -1,
    val horizontalGap: Int = 0,
    val verticalGap: Int = 0,
    val roundCorner: Float = -1f,
    val keyBorder: Int = -1,
    val columns: Int = 30,
    val asciiMode: Int = 1,
    val resetAsciiMode: Boolean = false,
    val labelTransform: LabelTransform = LabelTransform.NONE,
    val lock: Boolean = false,
    val asciiKeyboard: String = "",
    val landscapeKeyboard: String = "",
    val landscapeSplitPercent: Int = 0,
    val keyTextOffsetX: Float = 0f,
    val keyTextOffsetY: Float = 0f,
    val keySymbolOffsetX: Float = 0f,
    val keySymbolOffsetY: Float = 0f,
    val keyHintOffsetX: Float = 0f,
    val keyHintOffsetY: Float = 0f,
    val keyPressOffsetX: Float = 0f,
    val keyPressOffsetY: Float = 0f,
    val importPreset: String = "",
    /**
     * The keyboard's layout as a container tree (doc/Keyboard.md §2.6). It reads
     * as a plain list of [TextKey] — the flattened view every consumer uses —
     * while [TextKeys.tree] keeps the nesting.
     */
    @Serializable(with = TextKeysSerializer::class)
    val keys: TextKeys = TextKeys.EMPTY,
) {
    /**
     * A layout built in code (tests, fixtures) may pass a flat key list: it is
     * read as one wrapping [KeyboardNode.Flow], the shape the pre-container
     * `keys:` had. Themes always decode through [TextKeysSerializer].
     */
    constructor(keys: List<TextKey>) : this(keys = TextKeys.of(keys))

    enum class LabelTransform {
        NONE,
        UPPERCASE,
    }

    @Serializable
    data class TextKey(
        val width: Float = 0f,
        val height: Float = 0f,
        /**
         * Starts a new line before this entry: written explicitly with
         * `row_break: true` inside a `flow`, and set on each container's first
         * leaf when the flat view is derived from the tree (doc/Keyboard.md §2.6).
         */
        val rowBreak: Boolean = false,
        val roundCorner: Float = -1f,
        val keyBorder: Int = -1,
        /** The entry's own label: a static text, a dynamic source or none (doc/Keyboard.md §2.2). */
        val label: KeyLabel = KeyLabel.None,
        val labelSymbol: String = "",
        /**
         * A spare character the key shows in its lower-left corner (fcitx5's
         * `alt`, doc/Keyboard.md §2.5): display only — pair it with a `swipe_*`
         * action to also make the swipe type it.
         */
        val altLabel: String = "",
        val hint: String = "",
        /**
         * The entry's key name (doc/Keyboard.md §2.1): it declares the key's
         * identity, not its action. A `preset_keys` name or a built-in character
         * key name (doc/Keyboard.md §1.3) becomes the entry's `click` at load
         * time; an explicit [click] always wins, and an unrecognized name is a
         * diagnostic error.
         */
        val key: String = "",
        /**
         * The `styles:` node this entry's appearance comes from, by name
         * (doc/Keyboard.md §2.3). An explicit field on this entry still wins
         * over the style, which wins over the key's built-in default.
         */
        val style: String = "",
        val sendBindings: Boolean = true,
        val keyTextSize: Float = 0f,
        val symbolTextSize: Float = 0f,
        val keyTextOffsetX: Float = 0f,
        val keyTextOffsetY: Float = 0f,
        val keySymbolOffsetX: Float = 0f,
        val keySymbolOffsetY: Float = 0f,
        val keyHintOffsetX: Float = 0f,
        val keyHintOffsetY: Float = 0f,
        val keyPressOffsetX: Float = 0f,
        val keyPressOffsetY: Float = 0f,
        val keyTextColor: String = "",
        val keyBackColor: String = "",
        val keyBorderColor: String = "",
        val keySymbolColor: String = "",
        val hilitedKeyTextColor: String = "",
        val hilitedKeyBackColor: String = "",
        val hilitedKeyBorderColor: String = "",
        val hilitedKeySymbolColor: String = "",
        @Serializable(with = LenientStringListSerializer::class)
        val popup: List<String> = emptyList(),
        // behaviors
        val click: KeyActionToken? = null,
        val doubleClick: KeyActionToken? = null,
        val lazyDoubleClick: KeyActionToken? = null,
        val longClick: KeyActionToken? = null,
        val swipeUp: KeyActionToken? = null,
        val swipeDown: KeyActionToken? = null,
        val swipeLeft: KeyActionToken? = null,
        val swipeRight: KeyActionToken? = null,
        val composing: KeyActionToken? = null,
        val hasMenu: KeyActionToken? = null,
        val paging: KeyActionToken? = null,
        val combo: KeyActionToken? = null,
        val ascii: KeyActionToken? = null,
    ) {
        companion object {
            val DEFAULTS = TextKey()
        }
    }

    companion object {
        val DEFAULTS = TextKeyboard()
    }
}
