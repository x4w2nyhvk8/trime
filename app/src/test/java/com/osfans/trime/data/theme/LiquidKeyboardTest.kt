/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.util.mapping
import com.osfans.trime.util.pairs
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class LiquidKeyboardTest :
    BehaviorSpec({
        fun decode(yaml: String): Theme = ThemeTestSupport.yaml.decodeFromYamlNode<Theme>(
            ThemeTestSupport.yaml.parseToYamlNode(yaml.trimIndent()).mapping!!,
        )

        Given("a liquid keyboard with metrics, a fixed key bar and two keyboards") {
            val keyboard = decode(
                """
            name: test
            liquid_keyboard:
              single_width: 60
              key_height: 40
              margin_x: 5
              cell_back_color: key_back_color
              cell_border: 1
              cell_border_color: border_color
              cell_corner_radius: 6.0
              cell_text_color: key_text_color
              cell_divider: 1
              cell_divider_color: border_color
              tab_spacing: 4
              tab_divider: 1
              tab_divider_color: border_color
              tab_corner_radius: 6.0
              tab_text_color: candidate_text_color
              tab_active_text_color: hilited_candidate_text_color
              cell_text_size: 16.0
              cell_padding: [3, 6]
              margin_y: 2.0
              cell_highlight_color: hilited_key_back_color
              tab_text_size: 14.0
              tab_height: 36
              tab_padding: [0, 5]
              tab_min_width: 44
              fixed_key_bar:
                position: top
                keys: [Shift, Delete]
              keyboards: [emoji, symbols]
              emoji:
                type: SINGLE
                name: Emoji
                keys: "🙂😂"
              symbols:
                type: SYMBOL
                keys:
                  - "，"
                  - {click: "-"}
                  - {click: "", label: 换行}
                  - {k: v}
            """,
            ).liquidKeyboard

            Then("the cell styling is decoded") {
                keyboard.cellBackColor shouldBe "key_back_color"
                keyboard.cellBorder shouldBe 1
                keyboard.cellBorderColor shouldBe "border_color"
                keyboard.cellCornerRadius shouldBe 6f
                keyboard.cellTextColor shouldBe "key_text_color"
                keyboard.cellDivider shouldBe 1
                keyboard.cellDividerColor shouldBe "border_color"
                keyboard.tabSpacing shouldBe 4
                keyboard.tabDivider shouldBe 1
                keyboard.tabDividerColor shouldBe "border_color"
                keyboard.tabCornerRadius shouldBe 6f
                keyboard.tabTextColor shouldBe "candidate_text_color"
                keyboard.tabActiveTextColor shouldBe "hilited_candidate_text_color"
                keyboard.cellTextSize shouldBe 16f
                keyboard.cellPadding.vertical shouldBe 3
                keyboard.cellPadding.horizontal shouldBe 6
                keyboard.marginY shouldBe 2f
                keyboard.cellHighlightColor shouldBe "hilited_key_back_color"
                keyboard.tabTextSize shouldBe 14f
                keyboard.tabHeight shouldBe 36
                keyboard.tabPadding.horizontal shouldBe 5
                keyboard.tabMinWidth shouldBe 44
            }

            Then("the fixed keys are decoded") {
                keyboard.singleWidth shouldBe 60
                keyboard.keyHeight shouldBe 40
                keyboard.marginX shouldBe 5f
                keyboard.fixedKeyBar.position shouldBe LiquidKeyboard.KeyBar.Position.TOP
                keyboard.fixedKeyBar.keys shouldBe listOf("Shift", "Delete")
            }

            Then("the listed keyboards are decoded in order, named after their id") {
                keyboard.keyboards.map { it.id } shouldBe listOf("emoji", "symbols")
                keyboard.keyboards.map { it.name } shouldBe listOf("Emoji", "symbols")
                keyboard.getTagList() shouldBe listOf(
                    LiquidKeyboard.Tag("Emoji", LiquidKeyboard.DataType.SINGLE),
                    LiquidKeyboard.Tag("symbols", LiquidKeyboard.DataType.SYMBOL),
                )
            }

            Then("a single keyboard holds one key per code point") {
                keyboard.keyboards[0].keys shouldBe listOf(
                    LiquidKeyboard.KeyItem("🙂"),
                    LiquidKeyboard.KeyItem("😂"),
                )
            }

            Then("written out keys keep their order and expand every mapping") {
                keyboard.keyboards[1].keys shouldBe listOf(
                    LiquidKeyboard.KeyItem("，"),
                    LiquidKeyboard.KeyItem("-", ""),
                    LiquidKeyboard.KeyItem("", "换行"),
                    LiquidKeyboard.KeyItem("k", "v"),
                )
            }
        }

        Given("a keyboard whose keys are one multi line scalar") {
            Then("one key is read per line and empty lines are dropped") {
                decode(
                    """
                name: test
                liquid_keyboard:
                  keyboards: [sym]
                  sym:
                    type: SYMBOL
                    keys: |
                      a
                      b

                      c
                """,
                ).liquidKeyboard.keyboards.single().keys shouldBe listOf(
                    LiquidKeyboard.KeyItem("a"),
                    LiquidKeyboard.KeyItem("b"),
                    LiquidKeyboard.KeyItem("c"),
                )
            }
        }

        Given("a keyboard without keys") {
            Then("a missing keys entry means no keys") {
                decode(
                    """
                name: test
                liquid_keyboard:
                  keyboards: [sym]
                  sym:
                    type: SYMBOL
                """,
                ).liquidKeyboard.keyboards.single().keys shouldBe emptyList()
            }

            Then("an empty keys entry also means no keys") {
                decode(
                    """
                name: test
                liquid_keyboard:
                  keyboards: [sym]
                  sym:
                    type: SYMBOL
                    keys:
                """,
                ).liquidKeyboard.keyboards.single().keys shouldBe emptyList()
            }
        }

        Given("a keyboards list naming a section that is not defined") {
            Then("the missing keyboard is skipped") {
                decode(
                    """
                name: test
                liquid_keyboard:
                  keyboards: [emoji, missing]
                  emoji:
                    type: SINGLE
                    keys: "a"
                """,
                ).liquidKeyboard.keyboards.map { it.id } shouldBe listOf("emoji")
            }
        }

        Given("a keyboard without a type") {
            Then("only that keyboard is skipped, the rest is decoded") {
                decode(
                    """
                name: test
                liquid_keyboard:
                  keyboards: [broken, ok]
                  broken:
                    keys: "x"
                  ok:
                    type: SYMBOL
                    keys: "y"
                """,
                ).liquidKeyboard.keyboards.map { it.id } shouldBe listOf("ok")
            }
        }

        Given("a liquid keyboard section that is missing, empty or unrelated") {
            Then("the defaults are used instead of failing the theme") {
                decode("name: test").liquidKeyboard shouldBe LiquidKeyboard()
                decode("name: test\nliquid_keyboard:").liquidKeyboard shouldBe LiquidKeyboard()
                decode("name: test\nliquid_keyboard: 3").liquidKeyboard shouldBe LiquidKeyboard()
            }
        }

        Given("the built-in trime theme") {
            Then("it ships the grouped symbol library") {
                // The symbols page holds the common punctuation only; the rich,
                // grouped sets live here (doc/Theme.md §9).
                val liquid = ThemeTestSupport.decodeBuiltinTheme("trime").liquidKeyboard
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
                liquid.fixedKeyBar.keys shouldBe
                    listOf(
                        "liquidKeyboardExitButton",
                        "keyboardLettersButton",
                        "clipboardButton",
                        "backspaceButton",
                    )
                liquid.tabColumnWidth shouldBe 72
            }
        }
    })
