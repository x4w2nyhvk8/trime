/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.data.theme.model.TextKeyboard
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * The `key:` identity of doc/Keyboard.md §2.1 and the character key name table of
 * doc/Keyboard.md §1.3 it resolves against.
 */
class CharKeyNamesTest :
    BehaviorSpec({
        Given("the built-in character key name table") {
            Then("the letters are aButton..zButton") {
                CharKeyNames.LETTERS.size shouldBe 26
                CharKeyNames.of("aButton") shouldBe "a"
                CharKeyNames.of("qButton") shouldBe "q"
                CharKeyNames.of("zButton") shouldBe "z"
            }

            Then("the digits are digit0Button..digit9Button") {
                CharKeyNames.DIGITS.size shouldBe 10
                CharKeyNames.of("digit0Button") shouldBe "0"
                CharKeyNames.of("digit9Button") shouldBe "9"
            }

            Then("the punctuation table matches doc/Keyboard.md §1.3") {
                CharKeyNames.PUNCTUATION shouldBe
                    mapOf(
                        "periodButton" to ".",
                        "commaButton" to ",",
                        "questionButton" to "?",
                        "exclaimButton" to "!",
                        "colonButton" to ":",
                        "semicolonButton" to ";",
                        "apostropheButton" to "'",
                        "quoteButton" to "\"",
                        "hyphenButton" to "-",
                        "underscoreButton" to "_",
                        "slashButton" to "/",
                        "backslashButton" to "\\",
                        "atButton" to "@",
                        "numberSignButton" to "#",
                        "dollarButton" to "\$",
                        "percentButton" to "%",
                        "ampersandButton" to "&",
                        "asteriskButton" to "*",
                        "plusButton" to "+",
                        "equalButton" to "=",
                        "tildeButton" to "~",
                        "caretButton" to "^",
                        "pipeButton" to "|",
                        "parenLeftButton" to "(",
                        "parenRightButton" to ")",
                        "bracketLeftButton" to "[",
                        "bracketRightButton" to "]",
                        "braceLeftButton" to "{",
                        "braceRightButton" to "}",
                        "lessButton" to "<",
                        "greaterButton" to ">",
                    )
            }

            Then("every entry is a distinct name and a single character") {
                CharKeyNames.ALL.size shouldBe CharKeyNames.LETTERS.size + CharKeyNames.DIGITS.size + CharKeyNames.PUNCTUATION.size
                CharKeyNames.ALL.values.toSet().size shouldBe CharKeyNames.ALL.size
                CharKeyNames.ALL.values.forEach { it.length shouldBe 1 }
            }

            Then("a name outside the table is not a character key") {
                CharKeyNames.of("shiftButton") shouldBe null
                CharKeyNames.of("q") shouldBe null
                CharKeyNames.of("") shouldBe null
            }
        }

        Given("an entry's key identity") {
            fun keyboard(vararg keys: TextKeyboard.TextKey) = TextKeyboard(keys = keys.toList())
            val theme =
                Theme(
                    name = "t",
                    presetKeys = mapOf("shiftButton" to PresetKey(send = "Shift_L")),
                    presetKeyboards = mapOf("kb" to keyboard(TextKeyboard.TextKey(key = "shiftButton"), TextKeyboard.TextKey(key = "qButton"), TextKeyboard.TextKey(key = "nope"))),
                )

            Then("a preset name becomes the preset action") {
                val resolved = ThemeLoader.resolveKeyIdentities(theme).getValue("kb").keys[0]
                resolved.click shouldBe KeyActionToken.Plain("shiftButton")
                resolved.key shouldBe "shiftButton"
            }

            Then("a character key name becomes the character") {
                ThemeLoader.resolveKeyIdentities(theme).getValue("kb").keys[1].click shouldBe
                    KeyActionToken.Plain("q")
            }

            Then("an unknown name stays unresolved, so the diagnostics can report it") {
                ThemeLoader.resolveKeyIdentities(theme).getValue("kb").keys[2].click shouldBe null
            }

            Then("an explicit click wins over the name") {
                val click = KeyActionToken.Plain("shiftButton")
                val explicit =
                    Theme(name = "t", presetKeyboards = mapOf("kb" to keyboard(TextKeyboard.TextKey(key = "qButton", click = click))))
                ThemeLoader.resolveKeyIdentities(explicit).getValue("kb").keys[0].click shouldBe click
            }

            Then("an entry without a name keeps its own click or nothing") {
                val clicked = TextKeyboard.TextKey(click = KeyActionToken.Plain("a"))
                val plain = Theme(name = "t", presetKeyboards = mapOf("kb" to keyboard(clicked)))
                ThemeLoader.resolveKeyIdentities(plain).getValue("kb").keys[0].let {
                    it.click shouldBe KeyActionToken.Plain("a")
                    it.key shouldBe ""
                }
            }
        }
    })
