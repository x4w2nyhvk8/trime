/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

/**
 * Canonical theme color keys. The YAML spelling is the lowercased enum name.
 *
 * Keys name a role and an optional state: `<role>_[pressed_]<part>_color`.
 * The default state is not spelled out, so `key_back_color` is the normal
 * state and `key_pressed_back_color` is the pressed one. Candidate highlight
 * is the same `pressed` state: candidates do not distinguish touch and
 * highlight.
 *
 * The keyboard canvas is a role of its own ([KEYBOARD_BACK_COLOR],
 * [KEYBOARD_TEXT_COLOR]); the canvas outline belongs to the independent
 * `border` role ([BORDER_BACK_COLOR]).
 *
 * The enum covers every key the built-in fallback chains can hop through and
 * every key the runtime requests; theme-defined keys that do not appear here
 * are resolved through [ColorTable]'s string path.
 */
enum class ColorKey {
    KEYBOARD_BACK_COLOR,
    KEYBOARD_TEXT_COLOR,
    BORDER_BACK_COLOR,
    CANDIDATE_BACK_COLOR,
    CANDIDATE_BORDER_COLOR,
    CANDIDATE_SEPARATOR_COLOR,
    CANDIDATE_TEXT_COLOR,
    CANDIDATE_PRESSED_BACK_COLOR,
    CANDIDATE_PRESSED_TEXT_COLOR,
    COMMENT_TEXT_COLOR,
    COMMENT_PRESSED_TEXT_COLOR,
    PREEDIT_BACK_COLOR,
    PREEDIT_TEXT_COLOR,
    PREEDIT_PRESSED_BACK_COLOR,
    PREEDIT_PRESSED_TEXT_COLOR,
    KEY_BACK_COLOR,
    KEY_BORDER_COLOR,
    KEY_TEXT_COLOR,
    KEY_SYMBOL_COLOR,
    KEY_PRESSED_BACK_COLOR,
    KEY_PRESSED_BORDER_COLOR,
    KEY_PRESSED_TEXT_COLOR,
    KEY_PRESSED_SYMBOL_COLOR,
    KEY_SHADOW_COLOR,
    FUNCTION_KEY_BACK_COLOR,
    FUNCTION_KEY_BORDER_COLOR,
    FUNCTION_KEY_TEXT_COLOR,
    FUNCTION_KEY_SYMBOL_COLOR,
    FUNCTION_KEY_PRESSED_BACK_COLOR,
    FUNCTION_KEY_PRESSED_BORDER_COLOR,
    FUNCTION_KEY_PRESSED_TEXT_COLOR,
    FUNCTION_KEY_PRESSED_SYMBOL_COLOR,
    MODIFIER_KEY_BACK_COLOR,
    MODIFIER_KEY_BORDER_COLOR,
    MODIFIER_KEY_TEXT_COLOR,
    MODIFIER_KEY_SYMBOL_COLOR,
    MODIFIER_KEY_PRESSED_BACK_COLOR,
    MODIFIER_KEY_PRESSED_BORDER_COLOR,
    MODIFIER_KEY_PRESSED_TEXT_COLOR,
    MODIFIER_KEY_PRESSED_SYMBOL_COLOR,
    POPUP_BACK_COLOR,
    POPUP_TEXT_COLOR,
    POPUP_PRESSED_BACK_COLOR,
    POPUP_PRESSED_TEXT_COLOR,
    LABEL_COLOR,
    LABEL_PRESSED_TEXT_COLOR,
    CANDIDATE_BUTTON_BACK_COLOR,
    CANDIDATE_BUTTON_TEXT_COLOR,
    CANDIDATE_BUTTON_PRESSED_BACK_COLOR,
    CANDIDATE_BUTTON_PRESSED_TEXT_COLOR,
    LIQUID_KEYBOARD_BACK_COLOR,
    ROOT_BACK_COLOR,
    LONG_TEXT_COLOR,
    LONG_TEXT_BACK_COLOR,
    PREVIEW_BACK_COLOR,
    PREVIEW_TEXT_COLOR,
    ;

    /** The color key as it appears in theme YAML files. */
    val key: String = name.lowercase()

    companion object {
        private val byKey = entries.associateBy { it.key }

        /**
         * The typed [ColorKey] for a YAML color key, or null for keys that
         * only a theme defines.
         */
        fun from(key: String): ColorKey? = byKey[key]

        /**
         * Built-in fallback chains previously hardcoded in ColorManager:
         * when a scheme does not define a key, its chain entry points at the
         * key to try next. The scheme itself is always consulted first.
         */
        internal val builtinFallbackColors: Map<ColorKey, ColorKey> =
            mapOf(
                BORDER_BACK_COLOR to KEYBOARD_BACK_COLOR,
                CANDIDATE_TEXT_COLOR to KEYBOARD_TEXT_COLOR,
                COMMENT_TEXT_COLOR to CANDIDATE_TEXT_COLOR,
                CANDIDATE_SEPARATOR_COLOR to BORDER_BACK_COLOR,
                CANDIDATE_BORDER_COLOR to BORDER_BACK_COLOR,
                PREEDIT_TEXT_COLOR to KEYBOARD_TEXT_COLOR,
                PREEDIT_BACK_COLOR to KEYBOARD_BACK_COLOR,
                PREEDIT_PRESSED_TEXT_COLOR to KEYBOARD_TEXT_COLOR,
                PREEDIT_PRESSED_BACK_COLOR to KEYBOARD_BACK_COLOR,
                CANDIDATE_PRESSED_TEXT_COLOR to PREEDIT_PRESSED_TEXT_COLOR,
                CANDIDATE_PRESSED_BACK_COLOR to PREEDIT_PRESSED_BACK_COLOR,
                CANDIDATE_BUTTON_BACK_COLOR to CANDIDATE_BACK_COLOR,
                CANDIDATE_BUTTON_TEXT_COLOR to CANDIDATE_TEXT_COLOR,
                CANDIDATE_BUTTON_PRESSED_BACK_COLOR to CANDIDATE_PRESSED_BACK_COLOR,
                CANDIDATE_BUTTON_PRESSED_TEXT_COLOR to CANDIDATE_PRESSED_TEXT_COLOR,
                LABEL_PRESSED_TEXT_COLOR to CANDIDATE_PRESSED_TEXT_COLOR,
                COMMENT_PRESSED_TEXT_COLOR to COMMENT_TEXT_COLOR,
                KEY_PRESSED_BACK_COLOR to CANDIDATE_PRESSED_BACK_COLOR,
                KEY_PRESSED_BORDER_COLOR to KEY_BORDER_COLOR,
                KEY_PRESSED_TEXT_COLOR to CANDIDATE_PRESSED_TEXT_COLOR,
                KEY_PRESSED_SYMBOL_COLOR to COMMENT_PRESSED_TEXT_COLOR,
                KEY_BACK_COLOR to KEYBOARD_BACK_COLOR,
                KEY_BORDER_COLOR to BORDER_BACK_COLOR,
                KEY_TEXT_COLOR to CANDIDATE_TEXT_COLOR,
                KEY_SYMBOL_COLOR to COMMENT_TEXT_COLOR,
                KEY_SHADOW_COLOR to BORDER_BACK_COLOR,
                LABEL_COLOR to CANDIDATE_TEXT_COLOR,
                FUNCTION_KEY_BACK_COLOR to KEY_BACK_COLOR,
                FUNCTION_KEY_BORDER_COLOR to KEY_BORDER_COLOR,
                FUNCTION_KEY_TEXT_COLOR to KEY_TEXT_COLOR,
                FUNCTION_KEY_SYMBOL_COLOR to KEY_SYMBOL_COLOR,
                FUNCTION_KEY_PRESSED_BACK_COLOR to KEY_PRESSED_BACK_COLOR,
                FUNCTION_KEY_PRESSED_BORDER_COLOR to KEY_PRESSED_BORDER_COLOR,
                FUNCTION_KEY_PRESSED_TEXT_COLOR to KEY_PRESSED_TEXT_COLOR,
                FUNCTION_KEY_PRESSED_SYMBOL_COLOR to KEY_PRESSED_SYMBOL_COLOR,
                MODIFIER_KEY_BACK_COLOR to KEY_BACK_COLOR,
                MODIFIER_KEY_BORDER_COLOR to KEY_BORDER_COLOR,
                MODIFIER_KEY_TEXT_COLOR to KEY_TEXT_COLOR,
                MODIFIER_KEY_SYMBOL_COLOR to KEY_SYMBOL_COLOR,
                MODIFIER_KEY_PRESSED_BACK_COLOR to KEY_PRESSED_BACK_COLOR,
                MODIFIER_KEY_PRESSED_BORDER_COLOR to KEY_PRESSED_BORDER_COLOR,
                MODIFIER_KEY_PRESSED_TEXT_COLOR to KEY_PRESSED_TEXT_COLOR,
                MODIFIER_KEY_PRESSED_SYMBOL_COLOR to KEY_PRESSED_SYMBOL_COLOR,
                POPUP_BACK_COLOR to KEY_BACK_COLOR,
                POPUP_TEXT_COLOR to KEY_TEXT_COLOR,
                POPUP_PRESSED_BACK_COLOR to KEY_PRESSED_BACK_COLOR,
                POPUP_PRESSED_TEXT_COLOR to KEY_PRESSED_TEXT_COLOR,
                ROOT_BACK_COLOR to KEYBOARD_BACK_COLOR,
                CANDIDATE_BACK_COLOR to KEYBOARD_BACK_COLOR,
                LIQUID_KEYBOARD_BACK_COLOR to KEYBOARD_BACK_COLOR,
                LONG_TEXT_COLOR to KEY_TEXT_COLOR,
                LONG_TEXT_BACK_COLOR to KEY_BACK_COLOR,
                PREVIEW_BACK_COLOR to KEY_BACK_COLOR,
                PREVIEW_TEXT_COLOR to KEY_TEXT_COLOR,
            )

        /** The fallback hop for a key, or null when the key is a chain terminal. */
        fun fallbackOf(key: ColorKey): ColorKey? = builtinFallbackColors[key]
    }
}
