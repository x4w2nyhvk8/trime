/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.KeyStyle
import com.osfans.trime.data.theme.model.LiquidKeyboard
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Pins the shipped theme package: it is the one theme the app starts with, so a
 * change to it — or to the loader that reads it — surfaces here.
 */
class ThemeGoldenTest :
    BehaviorSpec({
        Given("the shipped theme package") {
            val theme = ThemeTestSupport.decodeBuiltinTheme("trime")

            Then("it loads and names itself") {
                // `author` is YAML metadata the Theme bean does not carry.
                theme.name shouldBe "同文"
            }

            Then("it ships the five keyboards") {
                theme.presetKeyboards.keys shouldBe setOf("default", "number", "symbols", "english", "editor")
            }

            Then("the grouped symbol library is declared") {
                // The symbols page holds the commonly used punctuation; the rich,
                // grouped sets live in the liquid keyboard (doc/Theme.md §9).
                val liquid = theme.liquidKeyboard
                liquid.keyboards.map { it.id } shouldBe
                    listOf(
                        "history",
                        "punct",
                        "math",
                        "currency",
                        "arrow",
                        "ordinal",
                        "greek",
                        "russian",
                        "unit",
                        "emoji",
                        "kaomoji",
                        "tabs",
                    )
                liquid.keyboards.filter { it.type == LiquidKeyboard.DataType.SINGLE }.size shouldBe 9
                liquid.fixedKeyBar.position shouldBe LiquidKeyboard.KeyBar.Position.BOTTOM
            }

            Then("every key has a width and every row fills the keyboard") {
                // A zero-width key is an invisible key: the first generated theme
                // shipped one for every letter and the keyboard came up blank.
                // A negative width is the explicit "take the rest of the row".
                theme.presetKeyboards.forEach { (id, keyboard) ->
                    withClue("keyboard '$id'") {
                        keyboard.keys.forEach { key ->
                            (key.width == -1f || key.width > 0f) shouldBe true
                        }
                        // Rows now come from `row_break` (doc/Keyboard.md §3.1); a row
                        // with a fill key must leave it something to take.
                        var row = 0f
                        var fills = 0
                        fun endRow() {
                            // The very first key may carry the row break itself.
                            if (row > 0f || fills > 0) {
                                if (fills > 0) {
                                    (row < 100f) shouldBe true
                                } else {
                                    row shouldBe 100f
                                }
                            }
                            row = 0f
                            fills = 0
                        }
                        keyboard.keys.forEach { key ->
                            if (key.rowBreak) endRow()
                            if (key.width < 0f) fills++ else row += key.width
                        }
                        endRow()
                    }
                }
            }

            Then("it ships its preset keys, including the option panel button") {
                theme.presetKeys.size shouldBe 50
                val optionPanelButton = theme.presetKeys["optionPanelButton"].shouldNotBeNull()
                optionPanelButton.command shouldBe "openOptionPanel"
                optionPanelButton.label.staticText shouldBe "选项"
            }

            Then("it resolves its key identities into actions") {
                // Every entry spells `key: <name>` (doc/Keyboard.md §2.1); the loader
                // turns those into clicks, so a letter key types its letter.
                val letter = theme.presetKeyboards.getValue("default").keys.first { it.key.endsWith("Button") }
                letter.click shouldBe KeyActionToken.Plain(letter.key.removeSuffix("Button"))
            }

            Then("it ships a day and a night color table of the full vocabulary") {
                theme.dayColors.size shouldBe 58
                theme.nightColors.shouldNotBeNull().size shouldBe 58
            }

            Then("its style nodes belong to the three families") {
                val panel = theme.optionPanel
                val clipboard = theme.clipboard
                val names =
                    listOf(
                        panel.panelStyle,
                        panel.actionItemStyle,
                        panel.switchItemStyle,
                        panel.titleItemStyle,
                        clipboard.panelStyle,
                        clipboard.itemStyle,
                        clipboard.pinnedItemStyle,
                        clipboard.titleItemStyle,
                    )
                names.forEach { name ->
                    name.isNotEmpty() shouldBe true
                    theme.styles[name].shouldNotBeNull()
                    // A panel or item slot resolves through the family map its page decodes.
                    (theme.panelStyles.containsKey(name) || theme.itemStyles.containsKey(name)) shouldBe true
                }
                theme.styles.getValue("spaceKeyTextStyle").content shouldBe KeyStyle.Content.TEXT
                // No name is shared by two families (doc/Theme.md §8.5).
                StyleFamilyResolver.references(theme).forEach { (name, families) ->
                    if (families.size > 1) error("style '$name' is referenced by $families")
                }
            }
        }
    })
