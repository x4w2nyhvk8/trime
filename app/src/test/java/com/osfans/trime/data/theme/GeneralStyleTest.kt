/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.MaybeStringList
import com.osfans.trime.util.yamlMapOf
import com.osfans.trime.util.yamlScalarOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.SerializationException

/**
 * GeneralStyle decode baseline: field-level values from the built-in theme
 * package (golden values taken from its `theme.yaml`), and graceful fallback to
 * defaults for malformed values (empty / wrong types / unknown enums) in
 * incorrect.yaml.
 */
class GeneralStyleTest :
    BehaviorSpec({
        Given("the built-in trime theme") {
            val theme = ThemeTestSupport.decodeBuiltinTheme("trime")

            When("its style section is decoded") {
                val style = theme.style

                Then("plain scalar values from the file are preserved") {
                    style shouldNotBe null
                    style.autoCaps shouldBe false
                    style.candidatePadding shouldBe 6
                    style.candidateSpacing shouldBe 0f
                    style.candidateTextSize shouldBe 18f
                    style.candidateTextVerticalBias shouldBe 1f
                    style.candidateViewHeight shouldBe 28
                    style.commentHeight shouldBe 12
                    style.commentPosition shouldBe GeneralStyle.CommentPosition.RIGHT
                    style.commentTextSize shouldBe 11f
                    style.horizontalGap shouldBe 5
                    style.keyHeight shouldBe 56
                    style.keyLongTextSize shouldBe 14f
                    style.keyTextSize shouldBe 22f
                    style.keyWidth shouldBe 10f
                    style.labelTextSize shouldBe 20f
                    style.keyboardHeight shouldBe 256
                    style.keyboardHeightLand shouldBe 208
                    style.keyboardPaddingLand shouldBe 4
                    style.keyboardPaddingBottom shouldBe 8
                    style.popupKeyHeight shouldBe 44
                    style.popupTextSize shouldBe 22f
                    style.roundCorner shouldBe 8f
                    style.verticalGap shouldBe 8
                }

                Then("fonts the file does not declare decode to no font") {
                    style.candidateFont shouldBe MaybeStringList.Empty
                    style.keyFont shouldBe MaybeStringList.Empty
                }

                Then("theme header is decoded") {
                    theme.name shouldBe "同文"
                }
            }
        }

        Given("a style whose fonts are written in the supported shapes") {
            val scalarFont = ThemeTestSupport.yaml.decodeFromYamlNode<GeneralStyle>(
                ThemeTestSupport.yaml.parseToYamlNode("candidate_font: han.ttf\n"),
            )
            val nullFont = ThemeTestSupport.yaml.decodeFromYamlNode<GeneralStyle>(
                ThemeTestSupport.yaml.parseToYamlNode("candidate_font:\n"),
            )
            val listFont = ThemeTestSupport.yaml.decodeFromYamlNode<GeneralStyle>(
                ThemeTestSupport.yaml.parseToYamlNode("candidate_font: [a.ttf, b.ttf]\n"),
            )

            Then("a font declared as a single scalar decodes to a single element") {
                scalarFont.candidateFont shouldBe MaybeStringList.Scalar("han.ttf")
            }

            Then("a font without a value decodes to no font instead of failing") {
                nullFont.candidateFont shouldBe MaybeStringList.Empty
            }

            Then("a font declared as a list decodes to a sequence") {
                listFont.candidateFont shouldBe MaybeStringList.Sequence(listOf("a.ttf", "b.ttf"))
            }
        }

        Given("a theme with empty/incorrect style values") {
            Then("malformed values raise serialization exception") {
                shouldThrow<SerializationException> {
                    ThemeTestSupport.decodeThemeFile("src/test/assets/incorrect.yaml")
                }
            }
        }

        Given("an empty style section") {
            val style = ThemeTestSupport.yaml.decodeFromYamlNode<GeneralStyle>(yamlMapOf())

            Then("decode equals the constructor defaults") {
                style shouldBe GeneralStyle()
            }
            Then("decode fills explicit keys but keeps the defaults for the rest") {
                val style =
                    ThemeTestSupport.yaml.decodeFromYamlNode<GeneralStyle>(
                        yamlMapOf(
                            "candidate_text_size" to yamlScalarOf("20"),
                        ),
                    )
                style.candidateTextSize shouldBe 20f
                style.keyHeight shouldBe 0
            }
        }
    })
