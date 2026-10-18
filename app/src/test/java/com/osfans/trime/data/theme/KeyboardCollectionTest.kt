/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.ThemeDiagnostics.Severity
import com.osfans.trime.util.mapping
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * The embedded collection container (doc/Keyboard.md §2.6.3): its cells come
 * from the theme's own `collections:` map, and a reference the theme does not
 * declare is an empty collection plus a warning — never a crash.
 */
class KeyboardCollectionTest :
    BehaviorSpec({
        /** Hex-only parser, enough for the fixture's one color. */
        fun parseHex(value: String): Int? {
            val digits = value.removePrefix("#")
            return digits.toLongOrNull(16)?.toInt()
        }

        fun lintFindings(themeYaml: String): List<ThemeDiagnostics.Finding> {
            val node = ThemeTestSupport.yaml.parseToYamlNode(themeYaml)
            val colors = ResolvedColors(day = mapOf("back_color" to "#000000"))
            val theme = ThemeTestSupport.yaml.decodeFromYamlNode<Theme>(node).copy(dayColors = colors.day)
            return ThemeDiagnostics.lint(theme, node.mapping ?: error("theme root is not a mapping"), colors, ::parseHex)
        }

        fun themeWithCollection(source: String): String =
            """
            config_version: "3.0"
            name: collection
            style: {}
            collections:
              punct: ["，", "。"]
              blank: []
              measured:
                columns: 2
                cell_height: 44
                items: ["+", "−", "×"]
            preset_keyboards:
              letter:
                name: letter
                keys:
                  - row:
                      - {click: a}
                      - collection: {source: '$source', width: 20, scroll: vertical, cell_height: 56}
            """.trimIndent()

        Given("a theme declaring collections") {
            val theme = ThemeTestSupport.yaml.decodeFromYamlNode<Theme>(
                ThemeTestSupport.yaml.parseToYamlNode(themeWithCollection("punct")),
            )

            Then("the shorthand list form carries its cells") {
                theme.collections["punct"]?.items shouldContainExactly listOf("，", "。")
                theme.collections["blank"]?.items shouldBe emptyList()
            }

            Then("the object form carries its metrics") {
                val measured = theme.collections.getValue("measured")
                measured.items shouldContainExactly listOf("+", "−", "×")
                measured.columns shouldBe 2
                measured.cellHeight shouldBe 44f
            }

            Then("a name the theme does not declare is absent, not failing") {
                theme.collections["nope"] shouldBe null
            }
        }

        Given("a layout whose collection names a declared set") {
            val findings = lintFindings(themeWithCollection("punct"))

            Then("the reference is silent") {
                findings.filter {
                    it.code == ThemeDiagnostics.Code.MISSING_COLLECTION_SOURCE ||
                        it.code == ThemeDiagnostics.Code.EMPTY_COLLECTION
                } shouldBe emptyList()
            }
        }

        Given("a layout whose collection names a set the theme does not declare") {
            val findings = lintFindings(themeWithCollection("nope"))

            Then("it is a warning that points at the collection's source") {
                val finding =
                    findings.single { it.code == ThemeDiagnostics.Code.MISSING_COLLECTION_SOURCE }
                finding.severity shouldBe Severity.WARNING
                finding.path shouldBe "preset_keyboards/letter/keys/0/1/collection/source"
                finding.message shouldBe
                    "collection of keyboard 'letter' names missing 'collections' entry 'nope'; " +
                    "it renders empty"
            }
        }

        Given("a layout whose collection names no set at all") {
            val findings = lintFindings(themeWithCollection(""))

            Then("it is the same warning, so the collection cannot silently stay empty") {
                val finding =
                    findings.single { it.code == ThemeDiagnostics.Code.MISSING_COLLECTION_SOURCE }
                finding.path shouldBe "preset_keyboards/letter/keys/0/1/collection/source"
                finding.message shouldBe
                    "collection of keyboard 'letter' names no 'collections' entry; it renders empty"
            }
        }

        Given("a layout whose collection reads an empty set") {
            val findings = lintFindings(themeWithCollection("blank"))

            Then("the empty set is a warning of its own") {
                val finding = findings.single { it.code == ThemeDiagnostics.Code.EMPTY_COLLECTION }
                finding.severity shouldBe Severity.WARNING
                finding.path shouldBe "preset_keyboards/letter/keys/0/1/collection/source"
                finding.message shouldBe
                    "collection of keyboard 'letter' reads 'collections: blank', which has no cells; " +
                    "it renders empty"
            }
        }
    })
