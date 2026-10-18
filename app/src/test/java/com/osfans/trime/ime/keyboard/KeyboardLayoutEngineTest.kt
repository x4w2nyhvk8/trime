/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.KeyboardCollection
import com.osfans.trime.data.theme.model.KeyboardNode
import com.osfans.trime.data.theme.model.TextKeyboard
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * The pure layout math of doc/Keyboard.md §2.6. No Android class is touched, so
 * the rectangles a nested theme produces can be asserted exactly.
 *
 * Fixtures use a 1000 px wide, 300 px tall keyboard with 10 % default key widths
 * and 100 px default rows: every percentage is then 10 px per weight unit and
 * the arithmetic stays readable.
 */
class KeyboardLayoutEngineTest :
    BehaviorSpec({
        fun key(
            name: String,
            width: Float = 0f,
        ): KeyboardNode.Key = KeyboardNode.Key(TextKeyboard.TextKey(key = name, width = width, click = KeyActionToken.Plain(name)))

        fun row(vararg children: KeyboardNode) = KeyboardNode.Row(children = children.toList())

        fun config() = KeyboardLayoutEngine.Config(width = 1000, height = 300, defaultKeyWidth = 10f, defaultRowHeight = 100)

        fun KeyboardLayoutEngine.Result.named(name: String) = keys.single { it.key.key == name }

        Given("a 3×3 grid beside a column of rails") {
            // The column on the left is 20 % wide, the grid on the right 80 %;
            // both stretch to the single root row, so both split 300 px into
            // three 100 px lines.
            val rail = KeyboardNode.Column(children = listOf(key("a"), key("b"), key("c")), width = 20f)
            val grid =
                KeyboardNode.Column(
                    children = listOf(
                        row(key("d1", 33.34f), key("d2", 33.33f), key("d3", 33.33f)),
                        row(key("d4", 33.34f), key("d5", 33.33f), key("d6", 33.33f)),
                        row(key("d7", 33.34f), key("d8", 33.33f), key("d9", 33.33f)),
                    ),
                    width = 80f,
                )
            val result = KeyboardLayoutEngine.layout(listOf(row(rail, grid)), config())

            Then("the rail takes the left fifth, one 100 px line per cell") {
                result.named("a").apply {
                    x shouldBe 0
                    y shouldBe 0
                    width shouldBe 200
                    height shouldBe 100
                    column shouldBe 0
                }
                result.named("b").apply {
                    x shouldBe 0
                    y shouldBe 100
                    width shouldBe 200
                    height shouldBe 100
                }
                result.named("c").apply {
                    x shouldBe 0
                    y shouldBe 200
                    width shouldBe 200
                    height shouldBe 100
                }
            }

            Then("the grid starts at the rail's right edge") {
                result.named("d1").apply {
                    x shouldBe 200
                    y shouldBe 0
                    width shouldBe 266
                    height shouldBe 100
                    column shouldBe 0
                }
                result.named("d2").apply {
                    x shouldBe 466
                    y shouldBe 0
                    width shouldBe 266
                    height shouldBe 100
                    column shouldBe 1
                }
                // The row's last key is snapped to the frame's right edge: the
                // three truncated 266 px thirds only reach 798 of 800 px.
                result.named("d3").apply {
                    x shouldBe 732
                    y shouldBe 0
                    width shouldBe 268
                    height shouldBe 100
                    column shouldBe 2
                }
            }

            Then("every grid line sits under the previous one") {
                result.named("d4").y shouldBe 100
                result.named("d7").y shouldBe 200
                result.named("d9").apply {
                    x shouldBe 732
                    y shouldBe 200
                    width shouldBe 268
                    height shouldBe 100
                }
            }

            Then("the result spans the whole keyboard") {
                result.keys.size shouldBe 12
                result.collections shouldBe emptyList()
                result.width shouldBe 1000
                result.height shouldBe 300
            }
        }

        Given("a row with a fill key") {
            val result = KeyboardLayoutEngine.layout(listOf(row(key("a", 20f), key("space", -1f), key("b", 30f))), config())

            Then("the fill key takes the rest of the row") {
                result.named("a").apply {
                    x shouldBe 0
                    width shouldBe 200
                }
                result.named("space").apply {
                    x shouldBe 200
                    width shouldBe 500
                }
                result.named("b").apply {
                    x shouldBe 700
                    width shouldBe 300
                }
            }
        }

        Given("a flow that does not fit on one line") {
            val flow = KeyboardNode.Flow(children = listOf(key("a", 60f), key("b", 60f), key("c", 30f)))
            val result = KeyboardLayoutEngine.layout(listOf(flow), config())

            Then("it wraps and shares the height between the lines") {
                result.named("a").apply {
                    x shouldBe 0
                    y shouldBe 0
                    width shouldBe 600
                    height shouldBe 150
                }
                result.named("b").apply {
                    x shouldBe 0
                    y shouldBe 150
                    width shouldBe 600
                    height shouldBe 150
                }
                result.named("c").apply {
                    x shouldBe 600
                    y shouldBe 150
                    width shouldBe 300
                    height shouldBe 150
                }
            }
        }

        Given("a column whose children declare a share of its height") {
            val column =
                KeyboardNode.Column(
                    children = listOf(
                        KeyboardNode.Row(children = listOf(key("a")), heightPercent = 50f),
                        KeyboardNode.Collection(spec = KeyboardCollection(source = "punct"), heightPercent = 50f),
                    ),
                )
            val result = KeyboardLayoutEngine.layout(listOf(column), config())

            Then("the percentage heights split the column evenly") {
                result.named("a").apply {
                    y shouldBe 0
                    height shouldBe 150
                    width shouldBe 100
                }
            }

            Then("the collection starts after the row and takes its half") {
                val collection = result.collections.single()
                collection.spec.source shouldBe "punct"
                collection.width shouldBe 1000
                collection.y shouldBe 150
                collection.height shouldBe 150
            }
        }

        Given("an embedded collection beside a key") {
            val result =
                KeyboardLayoutEngine.layout(
                    listOf(
                        row(
                            key("a", 80f),
                            KeyboardNode.Collection(
                                spec = KeyboardCollection(source = "punct"),
                                width = 20f,
                            ),
                        ),
                    ),
                    config(),
                )

            Then("it occupies its percentage of the row and the full row height") {
                val collection = result.collections.single()
                collection.x shouldBe 800
                collection.y shouldBe 0
                collection.width shouldBe 200
                collection.height shouldBe 300
                result.named("a").width shouldBe 800
            }
        }

        Given("a keyboard with no nodes") {
            val result = KeyboardLayoutEngine.layout(emptyList(), config())

            Then("it has the configured height and nothing in it") {
                result.keys shouldBe emptyList()
                result.collections shouldBe emptyList()
                result.width shouldBe 0
                result.height shouldBe 300
                result.lastRow shouldBe -1
            }
        }
    })
