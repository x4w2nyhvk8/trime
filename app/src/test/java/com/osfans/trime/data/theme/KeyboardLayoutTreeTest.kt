/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyboardNode
import com.osfans.trime.data.theme.model.ScrollDirection
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.util.yamlMapOf
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * `keys:` decodes into a container tree (doc/Keyboard.md §2.6) while its flat
 * view keeps the order and the `row_break` marks the rest of the app reads.
 */
class KeyboardLayoutTreeTest :
    BehaviorSpec({
        Given("a keyboard whose keys nest containers") {
            val keyboard =
                ThemeTestSupport.yaml.decodeFromYamlNode<TextKeyboard>(
                    ThemeTestSupport.yaml.parseToYamlNode(
                        """
                        keys:
                          - row:
                              - {key: qButton, width: 10}
                              - column:
                                  - row: [{key: aButton, width: 100}]
                                  - row: [{key: bButton, width: 100}]
                                width: 20
                              - {key: wButton, width: 70}
                        """.trimIndent(),
                    ),
                )

            Then("the root holds the row container") {
                keyboard.keys.tree.size shouldBe 1
                keyboard.keys.tree.single().shouldBeInstanceOf<KeyboardNode.Row>()
            }

            Then("the row keeps its nested column and its keys in order") {
                val row = keyboard.keys.tree.single().shouldBeInstanceOf<KeyboardNode.Row>()
                row.children.size shouldBe 3

                row.children[0].shouldBeInstanceOf<KeyboardNode.Key>().key.key shouldBe "qButton"

                val column = row.children[1].shouldBeInstanceOf<KeyboardNode.Column>()
                column.width shouldBe 20f
                column.children.size shouldBe 2
                column.children.forEach { it.shouldBeInstanceOf<KeyboardNode.Row>() }
                column.children
                    .map { it.shouldBeInstanceOf<KeyboardNode.Row>().children.single() }
                    .map { it.shouldBeInstanceOf<KeyboardNode.Key>().key.key } shouldContainExactly
                    listOf("aButton", "bButton")

                row.children[2].shouldBeInstanceOf<KeyboardNode.Key>().key.key shouldBe "wButton"
            }

            Then("the flat view lists the leaves in document order") {
                keyboard.keys.map { it.key } shouldContainExactly
                    listOf("qButton", "aButton", "bButton", "wButton")
                keyboard.keys.map { it.rowBreak } shouldContainExactly listOf(true, true, true, false)
            }
        }

        Given("a keyboard with a collection container") {
            val keyboard =
                ThemeTestSupport.yaml.decodeFromYamlNode<TextKeyboard>(
                    ThemeTestSupport.yaml.parseToYamlNode(
                        """
                        keys:
                          - row:
                              - {key: aButton, width: 80}
                              - collection:
                                  source: punct
                                  width: 20
                                  scroll: vertical
                                  cell_height: 56
                                  cell_width: 48
                                  columns: 3
                        """.trimIndent(),
                    ),
                )

            Then("the collection node carries its spec and placement") {
                val row = keyboard.keys.tree.single().shouldBeInstanceOf<KeyboardNode.Row>()
                val collection = row.children[1].shouldBeInstanceOf<KeyboardNode.Collection>()
                collection.width shouldBe 20f
                collection.spec.source shouldBe "punct"
                collection.spec.scroll shouldBe ScrollDirection.VERTICAL
                collection.spec.cellHeight shouldBe 56f
                collection.spec.cellWidth shouldBe 48f
                collection.spec.columns shouldBe 3
            }

            Then("it contributes no leaf to the flat view") {
                keyboard.keys.map { it.key } shouldContainExactly listOf("aButton")
            }
        }

        Given("a keyboard written in the pre-container form") {
            val keyboard =
                ThemeTestSupport.yaml.decodeFromYamlNode<TextKeyboard>(
                    ThemeTestSupport.yaml.parseToYamlNode(
                        """
                        keys:
                          - {key: aButton, width: 50}
                          - {key: bButton, width: 50}
                        """.trimIndent(),
                    ),
                )

            Then("it is read as one wrapping flow") {
                val flow = keyboard.keys.tree.single().shouldBeInstanceOf<KeyboardNode.Flow>()
                flow.children.size shouldBe 2
                keyboard.keys.size shouldBe 2
            }
        }

        Given("a keyboard with no keys") {
            val keyboard = ThemeTestSupport.yaml.decodeFromYamlNode<TextKeyboard>(yamlMapOf())

            Then("both views are empty") {
                keyboard.keys shouldBe emptyList()
                keyboard.keys.tree shouldBe emptyList()
            }
        }
    })
