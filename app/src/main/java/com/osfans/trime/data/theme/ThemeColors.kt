/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import androidx.annotation.ColorInt

/**
 * Typed, read-only view over the resolved colors of the active color scheme.
 *
 * Each property is one [ColorKey]; reading it is an O(1) lookup into the
 * pre-compiled [ColorTable]. Keys whose value is an image or that no chain
 * resolves throw [IllegalArgumentException], exactly like
 * `ColorManager.getColor(key)` with the same key.
 *
 * The color access root for UI code: reach it through [ThemeScope.colors] and
 * re-read values on every bind or draw — the instance is replaced whenever
 * the scheme switches.
 */
class ThemeColors internal constructor(
    private val table: ColorTable,
) {
    val keyboardBackColor: Int get() = color(ColorKey.KEYBOARD_BACK_COLOR)
    val keyboardTextColor: Int get() = color(ColorKey.KEYBOARD_TEXT_COLOR)
    val borderBackColor: Int get() = color(ColorKey.BORDER_BACK_COLOR)
    val candidateBackground: Int get() = color(ColorKey.CANDIDATE_BACK_COLOR)
    val candidateBorderColor: Int get() = color(ColorKey.CANDIDATE_BORDER_COLOR)
    val candidateSeparatorColor: Int get() = color(ColorKey.CANDIDATE_SEPARATOR_COLOR)
    val candidateTextColor: Int get() = color(ColorKey.CANDIDATE_TEXT_COLOR)
    val commentTextColor: Int get() = color(ColorKey.COMMENT_TEXT_COLOR)
    val candidatePressedBackColor: Int get() = color(ColorKey.CANDIDATE_PRESSED_BACK_COLOR)
    val candidatePressedTextColor: Int get() = color(ColorKey.CANDIDATE_PRESSED_TEXT_COLOR)
    val commentPressedTextColor: Int get() = color(ColorKey.COMMENT_PRESSED_TEXT_COLOR)
    val preeditBackColor: Int get() = color(ColorKey.PREEDIT_BACK_COLOR)
    val preeditTextColor: Int get() = color(ColorKey.PREEDIT_TEXT_COLOR)
    val preeditPressedBackColor: Int get() = color(ColorKey.PREEDIT_PRESSED_BACK_COLOR)
    val preeditPressedTextColor: Int get() = color(ColorKey.PREEDIT_PRESSED_TEXT_COLOR)
    val candidateButtonPressedBackColor: Int get() = color(ColorKey.CANDIDATE_BUTTON_PRESSED_BACK_COLOR)
    val candidateButtonPressedTextColor: Int get() = color(ColorKey.CANDIDATE_BUTTON_PRESSED_TEXT_COLOR)
    val keyBackColor: Int get() = color(ColorKey.KEY_BACK_COLOR)
    val keyBorderColor: Int get() = color(ColorKey.KEY_BORDER_COLOR)
    val keyTextColor: Int get() = color(ColorKey.KEY_TEXT_COLOR)
    val keySymbolColor: Int get() = color(ColorKey.KEY_SYMBOL_COLOR)
    val keyPressedBackColor: Int get() = color(ColorKey.KEY_PRESSED_BACK_COLOR)
    val keyPressedBorderColor: Int get() = color(ColorKey.KEY_PRESSED_BORDER_COLOR)
    val keyPressedTextColor: Int get() = color(ColorKey.KEY_PRESSED_TEXT_COLOR)
    val keyPressedSymbolColor: Int get() = color(ColorKey.KEY_PRESSED_SYMBOL_COLOR)
    val labelColor: Int get() = color(ColorKey.LABEL_COLOR)
    val labelPressedTextColor: Int get() = color(ColorKey.LABEL_PRESSED_TEXT_COLOR)
    val functionKeyBackColor: Int get() = color(ColorKey.FUNCTION_KEY_BACK_COLOR)
    val functionKeyBorderColor: Int get() = color(ColorKey.FUNCTION_KEY_BORDER_COLOR)
    val functionKeyTextColor: Int get() = color(ColorKey.FUNCTION_KEY_TEXT_COLOR)
    val functionKeySymbolColor: Int get() = color(ColorKey.FUNCTION_KEY_SYMBOL_COLOR)
    val functionKeyPressedBackColor: Int get() = color(ColorKey.FUNCTION_KEY_PRESSED_BACK_COLOR)
    val functionKeyPressedBorderColor: Int get() = color(ColorKey.FUNCTION_KEY_PRESSED_BORDER_COLOR)
    val functionKeyPressedTextColor: Int get() = color(ColorKey.FUNCTION_KEY_PRESSED_TEXT_COLOR)
    val functionKeyPressedSymbolColor: Int get() = color(ColorKey.FUNCTION_KEY_PRESSED_SYMBOL_COLOR)
    val modifierKeyBackColor: Int get() = color(ColorKey.MODIFIER_KEY_BACK_COLOR)
    val modifierKeyBorderColor: Int get() = color(ColorKey.MODIFIER_KEY_BORDER_COLOR)
    val modifierKeyTextColor: Int get() = color(ColorKey.MODIFIER_KEY_TEXT_COLOR)
    val modifierKeySymbolColor: Int get() = color(ColorKey.MODIFIER_KEY_SYMBOL_COLOR)
    val modifierKeyPressedBackColor: Int get() = color(ColorKey.MODIFIER_KEY_PRESSED_BACK_COLOR)
    val modifierKeyPressedBorderColor: Int get() = color(ColorKey.MODIFIER_KEY_PRESSED_BORDER_COLOR)
    val modifierKeyPressedTextColor: Int get() = color(ColorKey.MODIFIER_KEY_PRESSED_TEXT_COLOR)
    val modifierKeyPressedSymbolColor: Int get() = color(ColorKey.MODIFIER_KEY_PRESSED_SYMBOL_COLOR)
    val popupBackColor: Int get() = color(ColorKey.POPUP_BACK_COLOR)
    val popupTextColor: Int get() = color(ColorKey.POPUP_TEXT_COLOR)
    val popupPressedBackColor: Int get() = color(ColorKey.POPUP_PRESSED_BACK_COLOR)
    val popupPressedTextColor: Int get() = color(ColorKey.POPUP_PRESSED_TEXT_COLOR)
    val liquidKeyboardBackground: Int get() = color(ColorKey.LIQUID_KEYBOARD_BACK_COLOR)
    val longTextBackColor: Int get() = color(ColorKey.LONG_TEXT_BACK_COLOR)
    val longTextColor: Int get() = color(ColorKey.LONG_TEXT_COLOR)
    val rootBackground: Int get() = color(ColorKey.ROOT_BACK_COLOR)
    val shadowColor: Int get() = color(ColorKey.KEY_SHADOW_COLOR)

    @ColorInt
    private fun color(key: ColorKey): Int = when (val value = table[key]) {
        is ColorTable.Value.Color -> value.argb
        else -> throw IllegalArgumentException("'${key.key}' is not a color")
    }
}
