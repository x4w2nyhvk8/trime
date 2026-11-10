/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.naming

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * 封闭动作动词的归一化语义：B1 大小写硬校验、域一致性、未实现诊断、
 * 以及「旧 token 不被误判」这一零回归底线。
 */
class ActionVerbTest :
    BehaviorSpec({
        Given("封闭动作动词枚举") {
            Then("39 个 id 唯一且全部符合 B1 lowerCamel") {
                ActionVerb.entries.size shouldBe 39
                ActionVerb.ids.size shouldBe 39
                val segment = Regex("^[a-z][a-zA-Z0-9]*$")
                ActionVerb.ids.forEach {
                    segment.matches(it) shouldBe true
                    Regex("[A-Z]{2,}").containsMatchIn(it) shouldBe false
                }
            }

            Then("每个动词用自身 domain 都能解析成 Ok 或 Unimplemented") {
                ActionVerb.entries.forEach { verb ->
                    val result = ActionVerb.resolve("action.${verb.domain}.${verb.id}")
                    (result is ActionResolution.Ok || result is ActionResolution.Unimplemented) shouldBe true
                }
            }

            Then("4 个 missing 动词的 defaults 为 null") {
                val missing = ActionVerb.entries.filter { it.defaults == null }.map { it.id }.toSet()
                missing shouldBe setOf("selectText", "hideKeyboard", "glideType", "expandPopup")
            }
        }

        Given("合法的 action token") {
            Then("退格归一化到现有 send + repeatable") {
                val ok = ActionVerb.resolve("action.edit.backspace").shouldBeInstanceOf<ActionResolution.Ok>()
                ok.verb.id shouldBe "backspace"
                ok.defaults.send shouldBe "BackSpace"
                ok.defaults.repeatable shouldBe true
            }

            Then("大写锁定归一化到 shiftLock: click") {
                val ok = ActionVerb.resolve("action.modifier.toggleCapsLock").shouldBeInstanceOf<ActionResolution.Ok>()
                ok.defaults.send shouldBe "Shift_L"
                ok.defaults.shiftLock shouldBe "click"
            }

            Then("Rime 状态切换保留 toggle 与 states") {
                val ok = ActionVerb.resolve("action.rime.toggleOption").shouldBeInstanceOf<ActionResolution.Ok>()
                ok.defaults.toggle shouldBe "ascii_mode"
                ok.defaults.states shouldBe listOf("中文", "西文")
            }

            Then("commit 默认上屏空格") {
                val ok = ActionVerb.resolve("action.text.commit").shouldBeInstanceOf<ActionResolution.Ok>()
                ok.defaults.commit shouldBe " "
            }

            Then("带 % 占位符的 option 不被 Kotlin 模板吞掉") {
                val ok = ActionVerb.resolve("action.text.repeatCommit").shouldBeInstanceOf<ActionResolution.Ok>()
                ok.defaults.command shouldBe "commit"
                ok.defaults.option shouldBe ("%1" + '$' + "s")
            }
        }

        Given("B1 大小写与词表硬校验") {
            Then("大小写写错会被拒绝，而不是静默失配") {
                ActionVerb.resolve("action.modifier.toggleCapslock")
                    .shouldBeInstanceOf<ActionResolution.Invalid>()
            }

            Then("蛇形动词被拒绝") {
                ActionVerb.resolve("action.edit.move_cursor")
                    .shouldBeInstanceOf<ActionResolution.Invalid>()
            }

            Then("域写错被拒绝") {
                ActionVerb.resolve("action.text.backspace")
                    .shouldBeInstanceOf<ActionResolution.Invalid>()
            }

            Then("未知动词被拒绝") {
                ActionVerb.resolve("action.edit.frobnicate")
                    .shouldBeInstanceOf<ActionResolution.Invalid>()
            }

            Then("段数不对被拒绝") {
                ActionVerb.resolve("action.edit")
                    .shouldBeInstanceOf<ActionResolution.Invalid>()
                ActionVerb.resolve("action.edit.backspace.extra")
                    .shouldBeInstanceOf<ActionResolution.Invalid>()
            }
        }

        Given("同文当前没有对应行为的动词") {
            Then("返回 Unimplemented，不静默生效") {
                listOf(
                    "action.edit.selectText",
                    "action.system.hideKeyboard",
                    "action.text.glideType",
                    "action.system.expandPopup",
                ).forEach { token ->
                    ActionVerb.resolve(token).shouldBeInstanceOf<ActionResolution.Unimplemented>()
                }
            }
        }

        Given("零回归底线") {
            Then("现有 preset_keys 名字、字面字符、组合键都不被误判为 action") {
                listOf("BackSpace", "Return1", "space1", "q", "{Control+a}", "SWITCH_CHARSET", "").forEach {
                    ActionVerb.resolve(it) shouldBe ActionResolution.NotAction
                }
            }
        }
    })
