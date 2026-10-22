# 主题命名约定

本文件规定主题 YAML 里**颜色键**的命名，以及状态化按键的颜色令牌。

## 1. 颜色键

格式为 `<role>_[pressed_]<part>_color`：

```
role  = keyboard | border | root | key | function_key | modifier_key | candidate
        | candidate_button | comment | popup | label | preedit | long_text
        | liquid_keyboard | preview
state = pressed（仅「按下 / 高亮」一种状态，默认状态不写）
part  = back | text | border | symbol | shadow | separator
```

`keyboard_back_color` 与 `keyboard_text_color` 是最底层画布的底色与基础文字颜色，
`border_back_color` 是这块画布的描边——`border` 是独立的对象，不与 `keyboard`
混用。所有组件的常态颜色直接回退到对应的基色，不会穿过 `pressed` 状态；
旧名 `back_color`、`border_color`、`text_color` 已移除，不再作为别名。

| 颜色键 | 含义 |
| --- | --- |
| `keyboard_back_color` | 最底层画布底色（兜底） |
| `keyboard_text_color` | 基础文字颜色（兜底） |
| `border_back_color` | 最底层画布描边 |
| `key_back_color` | 普通键常态底色 |
| `key_pressed_back_color` | 普通键按下底色 |
| `function_key_back_color` | 功能键常态底色 |
| `function_key_pressed_back_color` | 功能键按下底色 |
| `modifier_key_back_color` | 修饰键常态底色 |
| `modifier_key_pressed_back_color` | 修饰键按下（开启）底色 |
| `candidate_text_color` | 候选常态文字 |
| `candidate_pressed_text_color` | 候选按下/高亮文字 |
| `candidate_pressed_back_color` | 候选按下/高亮底色 |
| `candidate_button_back_color` | 候选栏按钮常态底色 |
| `candidate_button_pressed_back_color` | 候选栏按钮按下底色 |
| `popup_back_color` | 长按弹层底色 |
| `key_shadow_color` | 普通键阴影 |

约束：

1. 键名匹配 `^(role)_[pressed_](part)_color$`；不写冗余的 `normal`，例如画布底色是
   `keyboard_back_color`，不是 `keyboard_normal_back_color`；
2. 每个 `role` 只定义实际存在的状态组合，不做笛卡尔积填充；
3. 只有「按下 / 高亮」一种状态，统一用 `pressed`，不再区分 `selected` / `hilited` / `on`；
   候选的触摸按压与键盘高亮索引也共用 `candidate_pressed_*`，不再分两套；
4. `modifier_key_*` 专用于修饰键（Shift、Control、Alt、Meta 等），文档与 schema 均按
   「修饰键」标注；其他功能键（回车、退格、符号切换等）使用 `function_key_*`；
5. 旧主题的 `hilited_*`、`off_key_*`、`on_key_*`、`function_key_on_*`、`candidate_selected_*`
   等名称不再兼容，也没有 `LegacyColorAliases` 之类的兼容层，需要迁移到上表名称；
   内置主题 `trime.yaml`、`tongwenfeng.trime.yaml` 已完成迁移。

## 2. 状态化按键：`toggle` + `states`

任何键带 `toggle:` 就是开关按键，点一下切换对应的 Rime 选项：

```yaml
preset_keys:
  PunctSwitch: {toggle: ascii_punct, states: [。，, ．，]}
  Language: {send: SWITCH_CHARSET, toggle: ascii_mode, states: [中, 英]}
```

引擎行为：

- 选项变化时把新值同步到带同一 `toggle:` 的按键，只重绘状态真正变化的按键（不重建键盘）；
- 标签取 `states[开 ? 1 : 0]`，没有 `states` 则用普通 `label`；
- 开关态颜色用 `modifier_key_*` 一组令牌，开态按下用 `modifier_key_pressed_*`；
- 键盘切换/重建时按运行时选项值初始化开关态。

这条机制对主题里所有 `toggle:` 键生效（全角/半角、简繁、字符集、标点、语言、单手开关等），不需要引擎逐个写特例。
