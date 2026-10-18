/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

/**
 * ThemeScope lifecycle semantics: colors stay unreadable until the first
 * scheme activation, and every activation replaces the typed colors view so
 * views that re-read it pick up the new scheme.
 */
class ThemeScopeTest :
    BehaviorSpec({
        val theme = ThemeTestSupport.decodeBuiltinTheme("trime")

        fun scope() = ThemeScope(theme) { value ->
            val digits =
                when {
                    value.startsWith("#") -> value.substring(1)
                    value.startsWith("0x", ignoreCase = true) -> value.substring(2)
                    else -> null
                }
            digits?.let { runCatching { java.lang.Long.parseLong(it, 16).toInt() }.getOrNull() }
        }

        val schemeA = mapOf("candidate_text_color" to "#112233")
        val schemeB = mapOf("candidate_text_color" to "#445566")

        Given("a fresh scope before any activation") {
            val fresh = scope()
            Then("colors is not readable yet") {
                shouldThrow<IllegalArgumentException> { fresh.colors }
            }
        }
        Given("a scope after activating scheme A") {
            val s = scope()
            s.updateScheme(schemeA)
            Then("colors resolves through the scheme") {
                s.activeColorScheme shouldBe schemeA
                s.colors.candidateTextColor shouldBe 0x112233
                shouldThrow<IllegalArgumentException> { s.colors.backColor }
            }
        }
        Given("a scope whose scheme switches from A to B") {
            val s = scope()
            s.updateScheme(schemeA)
            val colorsBefore = s.colors
            s.updateScheme(schemeB)
            Then("the colors view is replaced with the new scheme's values") {
                s.activeColorScheme shouldBe schemeB
                s.colors shouldNotBe colorsBefore
                s.colors.candidateTextColor shouldBe 0x445566
            }
        }
        Given("a scope with an unresolvable scheme key") {
            val s = scope()
            s.updateScheme(mapOf("candidate_text_color" to "not-a-color"))
            Then("activation succeeds and the key throws when read") {
                shouldThrow<IllegalArgumentException> { s.colors.candidateTextColor }
            }
        }
        Given("the scope's package directory") {
            // Only its identity matters here; nothing reads it in this test.
            val packageDir = File("theme-scope-fixture")

            Then("a theme read without a package carries none") {
                ThemeScope(theme.copy(packageDir = null)).packageDir shouldBe null
            }

            Then("a package theme carries the directory it was read from") {
                ThemeScope(theme, packageDir).packageDir shouldBe packageDir
            }

            Then("it defaults to what the theme itself carries") {
                ThemeScope(theme.copy(packageDir = packageDir)).packageDir shouldBe packageDir
            }
        }
        Given("a fresh scope") {
            Then("the mode it resolves resources in starts as day") {
                scope().activeMode shouldBe ThemeMode.DAY
            }
        }
        Given("a scope activated with a night table") {
            val s = scope()
            s.updateScheme(schemeA, ThemeMode.NIGHT)

            Then("the night mode is recorded for the resource lookup") {
                s.activeMode shouldBe ThemeMode.NIGHT
                s.activeColorScheme shouldBe schemeA
            }
        }
    })
