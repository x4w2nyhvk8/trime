/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * The day/night selection matrix: follow-system-day-night x night state, over
 * the theme's `day.yaml` and `night.yaml` tables. Colors are irrelevant here;
 * only which of the two tables is picked matters.
 */
class ColorSchemeResolverTest :
    BehaviorSpec({
        // The marker value keeps the fixtures distinguishable: the resolver
        // returns the table it picked, and these tests assert which one that is.
        fun table(id: String) = mapOf("name" to id, "back_color" to "#000000")

        val day = table("day")
        val night = table("night")

        fun resolve(
            nightColors: ColorScheme?,
            follow: Boolean,
            isNight: Boolean,
        ) = ColorSchemeResolver.resolve(day, nightColors, follow, isNight)

        fun mode(
            nightColors: ColorScheme?,
            follow: Boolean,
            isNight: Boolean,
        ) = ColorSchemeResolver.modeOf(day, nightColors, follow, isNight)

        Given("followSystemDayNight is off") {
            When("the system is in either mode") {
                Then("the day table is used") {
                    resolve(night, follow = false, isNight = false) shouldBe day
                    resolve(night, follow = false, isNight = true) shouldBe day
                }

                Then("the mode names day, so resources read images/day/") {
                    mode(night, follow = false, isNight = false) shouldBe ThemeMode.DAY
                    mode(night, follow = false, isNight = true) shouldBe ThemeMode.DAY
                }
            }
        }
        Given("followSystemDayNight is on") {
            When("daytime") {
                Then("the day table is used") {
                    resolve(night, follow = true, isNight = false) shouldBe day
                }

                Then("the mode names day") {
                    mode(night, follow = true, isNight = false) shouldBe ThemeMode.DAY
                }
            }
            When("night") {
                Then("the night table is used") {
                    resolve(night, follow = true, isNight = true) shouldBe night
                }

                Then("the mode names night, so resources read images/night/") {
                    mode(night, follow = true, isNight = true) shouldBe ThemeMode.NIGHT
                }
            }
        }
        Given("the theme declares no night table") {
            When("night falls back") {
                Then("the day table is used, for a null and for an empty one") {
                    resolve(null, follow = true, isNight = true) shouldBe day
                    resolve(emptyMap(), follow = true, isNight = true) shouldBe day
                }

                Then("the mode stays day, so resources keep reading images/day/") {
                    mode(null, follow = true, isNight = true) shouldBe ThemeMode.DAY
                    mode(emptyMap(), follow = true, isNight = true) shouldBe ThemeMode.DAY
                }
            }
        }
        Given("a theme that defines no day colors at all") {
            // ThemeLoader refuses such a theme, so reaching this point is a
            // programming error: it says so instead of failing on the empty map.
            Then("resolving says why instead of returning nothing") {
                val e =
                    shouldThrow<IllegalArgumentException> {
                        ColorSchemeResolver.resolve(emptyMap(), null, followSystemDayNight = false, isNightMode = false)
                    }
                e.message shouldBe "The theme defines no day colors"
            }

            Then("the mode says the same") {
                val e =
                    shouldThrow<IllegalArgumentException> {
                        ColorSchemeResolver.modeOf(emptyMap(), null, followSystemDayNight = false, isNightMode = false)
                    }
                e.message shouldBe "The theme defines no day colors"
            }
        }
    })
