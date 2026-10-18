/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The image lookup of [ThemeResources]: a theme package resolves inside its own
 * `images/` per mode (doc/Theme.md §3).
 */
class ThemeResourcesTest :
    BehaviorSpec({
        fun tempDir(): File = File.createTempFile("theme-resources", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }

        /** A directory holding [relativePaths] as (dummy) files and their parents. */
        fun dirWith(vararg relativePaths: String): File {
            val dir = tempDir()
            relativePaths.forEach { relative ->
                File(dir, relative).apply { parentFile?.mkdirs() }.writeText("dummy")
            }
            return dir
        }

        Given("a theme package lookup") {
            When("the image is in the mode directory and in the shared one") {
                val dir = dirWith("images/splash.png", "images/day/splash.png")

                Then("the mode directory wins") {
                    ThemeResources.resolveInPackage(dir, ThemeMode.DAY, "splash.png") shouldBe
                        File(dir, "images/day/splash.png")
                }
            }

            When("the image is only in the shared directory") {
                val dir = dirWith("images/splash.png")

                Then("either mode falls back to it") {
                    ThemeResources.resolveInPackage(dir, ThemeMode.DAY, "splash.png") shouldBe
                        File(dir, "images/splash.png")
                    ThemeResources.resolveInPackage(dir, ThemeMode.NIGHT, "splash.png") shouldBe
                        File(dir, "images/splash.png")
                }
            }

            When("the image is only in the other mode's directory") {
                val dir = dirWith("images/night/splash.png")

                Then("the day lookup reports it missing instead of borrowing it") {
                    ThemeResources.resolveInPackage(dir, ThemeMode.DAY, "splash.png").shouldBeNull()
                }

                Then("the night lookup finds it") {
                    ThemeResources.resolveInPackage(dir, ThemeMode.NIGHT, "splash.png") shouldBe
                        File(dir, "images/night/splash.png")
                }
            }

            When("the package carries no such image") {
                val dir = dirWith("images/other.png")

                Then("the lookup reports it missing") {
                    ThemeResources.resolveInPackage(dir, ThemeMode.DAY, "splash.png").shouldBeNull()
                }
            }

            When("the image sits in a subdirectory of images/") {
                val dir = dirWith("images/nine/splash.9.png")

                Then("a nested name resolves too") {
                    ThemeResources.resolveInPackage(dir, ThemeMode.DAY, "nine/splash.9.png") shouldBe
                        File(dir, "images/nine/splash.9.png")
                }
            }
        }
    })
