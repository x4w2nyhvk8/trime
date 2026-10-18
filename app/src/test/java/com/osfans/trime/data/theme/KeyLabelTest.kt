/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyLabel
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * The polymorphic `label` of doc/Keyboard.md §2.2: its spellings, how a dynamic
 * source resolves against the app state, and the priority it takes part in.
 */
class KeyLabelTest :
    BehaviorSpec({
        fun labelOf(yaml: String): KeyLabel = ThemeTestSupport.yaml.decodeFromYamlNode<KeyLabel>(ThemeTestSupport.yaml.parseToYamlNode(yaml))

        val sample = LabelContext(schemaName = "明月拼音", schemaId = "luna_pinyin", enterLabel = "前往")

        Given("the label spellings") {
            Then("a scalar is a static text") {
                labelOf("空格") shouldBe KeyLabel.Text("空格")
            }

            Then("the explicit text form is the same static text") {
                labelOf("{text: 空格}") shouldBe KeyLabel.Text("空格")
            }

            Then("each source form is dynamic") {
                labelOf("{source: schemaName}") shouldBe KeyLabel.Dynamic(KeyLabel.Dynamic.Source.SCHEMA_NAME)
                labelOf("{source: schemaId}") shouldBe KeyLabel.Dynamic(KeyLabel.Dynamic.Source.SCHEMA_ID)
                labelOf("{source: enterLabel}") shouldBe KeyLabel.Dynamic(KeyLabel.Dynamic.Source.ENTER_LABEL)
            }

            Then("a custom source carries its template") {
                labelOf("{source: custom, text: 前往}") shouldBe
                    KeyLabel.Dynamic(KeyLabel.Dynamic.Source.CUSTOM, "前往")
            }

            Then("a source spelled with underscores is accepted too") {
                labelOf("{source: schema_name}") shouldBe KeyLabel.Dynamic(KeyLabel.Dynamic.Source.SCHEMA_NAME)
            }

            Then("a blank or missing value is no label") {
                labelOf("''") shouldBe KeyLabel.None
                labelOf("~") shouldBe KeyLabel.None
                labelOf("{}") shouldBe KeyLabel.None
                labelOf("{text: ''}") shouldBe KeyLabel.None
            }

            Then("a source that names nothing fails the parse instead of defaulting") {
                runCatching { labelOf("{source: nope}") }.isFailure shouldBe true
            }
        }

        Given("a dynamic label") {
            Then("each source reads its own value from the context") {
                KeyLabelResolver.text(KeyLabel.Dynamic(KeyLabel.Dynamic.Source.SCHEMA_NAME), sample) shouldBe "明月拼音"
                KeyLabelResolver.text(KeyLabel.Dynamic(KeyLabel.Dynamic.Source.SCHEMA_ID), sample) shouldBe "luna_pinyin"
                KeyLabelResolver.text(KeyLabel.Dynamic(KeyLabel.Dynamic.Source.ENTER_LABEL), sample) shouldBe "前往"
                KeyLabelResolver.text(KeyLabel.Dynamic(KeyLabel.Dynamic.Source.CUSTOM, "文本"), sample) shouldBe "文本"
            }

            Then("a source with nothing to show renders nothing instead of failing") {
                KeyLabelResolver.text(KeyLabel.Dynamic(KeyLabel.Dynamic.Source.SCHEMA_NAME)) shouldBe ""
                KeyLabelResolver.text(
                    KeyLabel.Dynamic(KeyLabel.Dynamic.Source.ENTER_LABEL),
                    LabelContext(enterLabel = ""),
                ) shouldBe ""
            }
        }

        Given("a static label") {
            Then("it reads back as-is, and no label reads as nothing") {
                KeyLabelResolver.text(KeyLabel.Text("空格")) shouldBe "空格"
                KeyLabelResolver.text(KeyLabel.None) shouldBe ""
                KeyLabel.None.staticText shouldBe ""
                KeyLabel.Text("空格").staticText shouldBe "空格"
                KeyLabel.Dynamic(KeyLabel.Dynamic.Source.SCHEMA_NAME).staticText shouldBe ""
            }
        }

        Given("what an entry declares versus the action's own label") {
            Then("the entry's text wins while it declares something and is shown") {
                KeyLabelResolver.resolve("空格", "space", showPlacement = true) shouldBe "空格"
            }

            Then("the action's label is the fallback otherwise") {
                KeyLabelResolver.resolve("", "space", showPlacement = true) shouldBe "space"
                KeyLabelResolver.resolve("空格", "space", showPlacement = false) shouldBe "space"
            }
        }

        Given("the built-in theme") {
            val theme = ThemeTestSupport.decodeBuiltinTheme("trime")

            Then("a preset with a static label decodes to that text") {
                // The shipped theme spells the mainstream glyphs rather than words.
                theme.presetKeys.getValue("backspaceButton").label shouldBe KeyLabel.Text("⌫")
                theme.presetKeys.getValue("shiftButton").label shouldBe KeyLabel.Text("⇧")
            }

            Then("the enter label is a dynamic source, so the app state fills it in") {
                // doc/Keyboard.md §2.2: the shipped theme asks for the label
                // instead of spelling a magic literal.
                theme.presetKeys.getValue("returnButton").label shouldBe
                    KeyLabel.Dynamic(KeyLabel.Dynamic.Source.ENTER_LABEL)
            }

            Then("a preset with no label declares none, so nothing is implicit any more") {
                val space = theme.presetKeys.getValue("spaceButton")
                space.label shouldBe KeyLabel.None
                // An empty space key used to show the schema name; a missing
                // label now reads nothing, even with a schema name available.
                KeyLabelResolver.text(
                    space.label,
                    LabelContext(schemaName = "明月拼音", schemaId = "luna_pinyin"),
                ) shouldBe ""
            }
        }
    })
