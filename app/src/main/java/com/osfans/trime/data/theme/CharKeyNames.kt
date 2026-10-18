/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

/**
 * The built-in character key names of doc/Keyboard.md §1.3, the table a layout
 * entry's `key:` resolves against (doc/Keyboard.md §2.1).
 *
 * A name is the character's English name plus `Button`: the letters and digits
 * are regular, the punctuation names are listed explicitly. A symbol that is not
 * in this table has no name yet — the table is extended first, then the name is
 * used (doc/Keyboard.md §1.3: 表外符号先补表再命名).
 */
object CharKeyNames {
    /** `aButton` .. `zButton` → `a` .. `z`. */
    val LETTERS: Map<String, String> =
        ('a'..'z').associate { letter -> "${letter}Button" to letter.toString() }

    /** `digit0Button` .. `digit9Button` → `0` .. `9`. */
    val DIGITS: Map<String, String> =
        ('0'..'9').associate { digit -> "digit${digit}Button" to digit.toString() }

    /** The punctuation names of doc/Keyboard.md §1.3, in the order the doc lists them. */
    private val PUNCTUATION_NAMES: List<Pair<String, String>> =
        listOf(
            "period" to ".",
            "comma" to ",",
            "question" to "?",
            "exclaim" to "!",
            "colon" to ":",
            "semicolon" to ";",
            "apostrophe" to "'",
            "quote" to "\"",
            "hyphen" to "-",
            "underscore" to "_",
            "slash" to "/",
            "backslash" to "\\",
            "at" to "@",
            "numberSign" to "#",
            "dollar" to "\$",
            "percent" to "%",
            "ampersand" to "&",
            "asterisk" to "*",
            "plus" to "+",
            "equal" to "=",
            "tilde" to "~",
            "caret" to "^",
            "pipe" to "|",
            "parenLeft" to "(",
            "parenRight" to ")",
            "bracketLeft" to "[",
            "bracketRight" to "]",
            "braceLeft" to "{",
            "braceRight" to "}",
            "less" to "<",
            "greater" to ">",
        )

    /** The punctuation names as they appear in a theme: `periodButton` → `.`. */
    val PUNCTUATION: Map<String, String> =
        PUNCTUATION_NAMES.associate { (name, symbol) -> "${name}Button" to symbol }

    /** Every built-in character key name. */
    val ALL: Map<String, String> = LETTERS + DIGITS + PUNCTUATION

    /** The character [name] stands for, or null when it is not a built-in character key. */
    fun of(name: String): String? = ALL[name]
}
