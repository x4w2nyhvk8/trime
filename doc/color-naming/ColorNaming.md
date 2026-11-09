<!--
SPDX-FileCopyrightText: 2015 - 2026 Rime community

SPDX-License-Identifier: GPL-3.0-or-later
-->

# 主题颜色命名统一规范（设计提案）

> 适用范围：**仅主题颜色**。键盘布局、`preset_keys` 的行为、`grid`/`keys` 结构、
> Kotlin 运行时均不在本提案内。状态为 RFC，未实现。

## 0. 结论

`develop` 把三条正交的轴——**部件、状态、角色**——拍平成了字段名前缀，导致字段数随
组合数增长、同一个词多种含义、颜色被绑定到按键坐标。本提案用「三层 token + 状态选择器
规则」替代扁平字段：**`develop` 的 72 个颜色名字（52 枚举 / 41 schema / 18 位置标签），
收敛为 26 个组件 token + 12 条变体规则**，并让设置页与真机共用同一份数据现场渲染预览。

## 1. 现状诊断（基于 `develop` 真实代码）

### 1.1 三套口径互不一致

来源全部取自本仓库 `develop` 分支：

| 口径 | 数量 | 出处 |
| --- | --- | --- |
| 运行时 `ColorKey` 枚举 | **52** | `app/.../data/theme/ColorKey.kt` |
| schema 声明的字段 | **41** | `doc/trime-schema.json` → `preset_color_schemes` |
| 内置主题自定义位置标签 | **18** | `app/src/main/assets/shared/tongwenfeng.trime.yaml` |

三者并集 **72 个**名字。其中：

- `ColorKey` 有而 schema 未收录 13 个：`candidate_background`、`candidate_border_color`、
  `hilited_candidate_button_color`、`hilited_label_color`、`hilited_popup_back_color`、
  `hilited_popup_text_color`、`keyboard_background`、`liquid_keyboard_background`、
  `long_text_back_color`、`long_text_color`、`popup_back_color`、`popup_text_color`、
  `root_background`；
- schema 有而 `ColorKey` 未收录 2 个：`preview_back_color`、`preview_text_color`；
- schema 用 `patternProperties: "\\w+"` 放开任意键，所以 18 个位置标签可以随意塞进方案，
  编辑器无法校验、无法补全。

也就是说：**新增一个颜色字段，要同时改枚举、schema、主题文件三处，且没有任何机制保证
三处一致**。

### 1.2 状态 × 部件的笛卡尔积

按键类字段由 6 个状态前缀 × 4 个部件拼成：

```
状态  key_ / off_key_ / on_key_ / hilited_ / hilited_off_key_ / hilited_on_key_
部件  _back_color / _text_color / _symbol_color / _border_color
```

仅按键就产生 24 个字段。官方近期的 `feat(theme): complete state color params for
key_border_color...` 提交继续往这个矩阵里补 `*_border_color` / `*_symbol_color`——
**每多一个状态或一个部件，字段数就乘一次**。这不是命名不好，是维度被压进了名字。

### 1.3 同一个词，多种含义

| 词 | 实际含义 |
| --- | --- |
| `hilited` | 有时是「按下」，有时是「高亮候选」，有时是「选中项」 |
| `shadow_color` | 说明书定义为**按键文字阴影**，多数皮肤却当成按键下缘立体阴影 |
| `on_key_*` / `off_key_*` | 是 Shift 的「锁定/未锁定」，却被泛化到所有功能键 |
| `preview_*` / `popup_*` / `long_text_*` / `callout` | 四个名字都指「变体按键背景」一类的东西 |

### 1.4 颜色绑定到按键坐标（最关键）

内置主题 `tongwenfeng.trime.yaml` 直接按**行号**和**位置缩写**定义颜色，注释原文：

```yaml
bgn:  *color4  #功能键背景色
bkg:  *color2  #空格键背景色
benter: *color6 #回车键背景色
bbs:  *color4  #退格键背景色
baoe: *color2  #aoeiu背景色
bh1:  *color2  #第一行背景色
bh2:  *color2  #第二行背景色
bh3:  *color2  #第三行背景色
bh4:  *color2  #第四行背景色
```

被引用的次数（`grep` 统计）：`bgn` 438、`tgn` 437、`benter` 174、`tenter` 172。

这些名字把颜色焊死在「第几行 / 是不是退格键」上。一旦布局改成二维网格、分体键盘、
或调整行列，所有引用都要跟着改——**而颜色本身根本不该依赖于坐标**。这是本提案最想
消除的一点，做法见 §5（注意：只改颜色的绑定方式，不改动布局结构）。

### 1.5 颜色格式自相矛盾

schema 声明 `"maximum": 16777215`（`0xFFFFFF`，24 位 RGB），同一文件里的默认值却是
`0xccd3d7da`（32 位 ARGB），说明书三章要求 `0xAARRGGBB`。三处口径不一，导致
`color_format` 这个用于补救的开关长期存在。

## 2. 参照系

| 来源 | 可借鉴点 |
| --- | --- |
| 元书 / 仓输入法 `Colors.libsonnet` | 按**角色**命名（`standardButtonBackgroundColor`），浅深两套只换语义色值；状态由组件自行组合 |
| FlorisBoard Snygg | CSS 式选择器 `key:pressed`、`key[shiftstate=caps_lock]`，状态不生成字段 |
| W3C Design Tokens (DTCG) 2025.10 | 三层 token、组级 `$type` 继承、`{token.path}` 引用、`$extensions` 承载实现自定义数据 |
| Material 3 | 统一部件词表（`surface` / `onSurface` / `outline`），跨组件复用 |

## 3. 设计原则

1. **三层分离**：`palette`（原始色板）→ `role`（语义角色）→ `component`（组件绑定）。
   换主题只覆盖 `role` 层；渲染器只引用 `component` 层。
2. **状态是规则，不是字段**：`component.key.surface` 描述常态，按下/锁定/选中由
   `variants` 选择器覆盖，不再新增字段。
3. **颜色与坐标解耦**：按键用 `role` 表达「它是什么」而不是「它在第几行」。
4. **统一部件词表**：`surface` / `content` / `hint` / `outline` / `elevation`，
   取代 `back` / `text` / `symbol` / `border` / `shadow` 混搭。
5. **单一数据源**：token 文件同时驱动真机渲染与设置页预览，缩略图由渲染器现场生成。

## 4. 命名规范

### 4.1 语法

```
palette.<family>.<step>              # palette.neutral.300
role.<part>.<variant>                # role.surface.raised
component.<element>[.<sub>].<part>   # component.candidateBar.selected.surface
```

- 全部小驼峰，点分层级，禁止下划线拼接状态前缀；
- `part` 只允许 `surface` / `content` / `hint` / `outline` / `elevation`；
- 值一律 `#RRGGBBAA`（alpha 后置，与元书一致）。`0xAARRGGBB` → `#RRGGBBAA`，
  `0xRRGGBB` → `#RRGGBBFF`；
- 引用写成 `{token.path}`，禁止直接写死色值到 `component` 层。

### 4.2 26 个组件 token

| 组件 | token |
| --- | --- |
| 根背景 | `component.root.surface` |
| 候选栏 | `component.candidateBar.{surface,content,hint,outline}` |
| 候选栏·编码 | `component.candidateBar.composing.content` |
| 候选栏·选中 | `component.candidateBar.selected.{surface,content}` |
| 工具栏 | `component.toolbar.{content,pressed.surface}` |
| 按键区 | `component.keyArea.surface` |
| 按键 | `component.key.{surface,content,hint,outline,elevation,contentShadow}` |
| 气泡 | `component.callout.{surface,content,outline}` |
| 长按弹层 | `component.popup.{surface,content,hint}` |
| 剪贴板 | `component.clipboard.surface` |

完整定义见 [keyboard.color.tokens.json](keyboard.color.tokens.json)。

### 4.3 状态变体（12 条规则替代 24 个字段）

```json
{ "selector": "key:pressed", "surface": "{role.surface.sunken}" }
{ "selector": "key[role=system]", "surface": "{role.surface.muted}" }
{ "selector": "key:pressed[role=system]", "surface": "{role.surface.raised}",
  "note": "元书标志：功能键按下反而变白" }
{ "selector": "key[role=action]", "surface": "{role.accent.primary}",
  "content": "{role.content.onAccent}" }
{ "selector": "key:pressed[role=action]", ... }
{ "selector": "key[role=modifier][state=locked]", ... }
{ "selector": "candidate:selected", ... }
{ "selector": "popup:selected", ... }
{ "selector": "keyArea[keyboardType=numeric] key", "surface": "{role.surface.muted}" }
{ "selector": "key[role=character]", "surface": "{role.surface.raised}",
  "content": "{role.content.primary}", "note": "字符键默认色，便于 bh1~4 等行号标签迁移" }
{ "selector": "key[role=space]", "surface": "{role.surface.raised}",
  "content": "{role.content.primary}", "note": "空格键默认色，替代 bkg/tkg" }
{ "selector": "composing:active", "surface": "{role.surface.selected}",
  "content": "{role.content.primary}", "note": "编码区高亮部分" }
```

选择器借鉴 CSS：`元素:状态`、`元素[属性=值]`、后代组合。匹配优先级按声明顺序，
命中即覆盖对应 `part`。元书那套「普通键白→按下变灰 / 功能键灰→按下变白」的反转，
用两条规则就能表达，无需为每种组合预生成字段。

### 4.4 方案元数据

用一处 `pair` 声明明暗配对，替代双向 `dark_scheme` / `light_scheme`（后者容易悬空）：

```json
"meta": {
  "yuanshu-light": {
    "name": "元书·浅色", "appearance": "light",
    "pair": { "light": "yuanshu-light", "dark": "yuanshu-dark" },
    "contrast": { "standard": "wcag-aa", "minimum": 4.5, "checked": true },
    "previewScene": "standard-26key@1.0"
  }
}
```

## 5. 与二维布局的关系（**本提案不改布局**）

命名方案只要求一件事：**按键声明自己的 `role`，而不是引用位置色标签**。

```yaml
# 现在（颜色绑位置，改布局就得改颜色引用）
- { key_back_color: bh3, key_text_color: th3 }

# 提案（颜色由 role + variant 决定，与行列无关）
- { role: character }
```

`role` 取值：`character` / `system` / `modifier` / `action` / `space`。

带来的结果是：布局将来无论保持一维 `keys` 折行，还是改成 `grid` 二维网格、分体、
横竖屏切换，**配色表一个字都不用改**。本提案不提供 `grid` 结构、不改动 `preset_keyboards`、
不调整任何按键的 `width` 或坐标，仅给出颜色侧的解耦约定。

## 6. 设置页预览

静态 `demo.png` 与实物长期不一致（本仓库此前就出现过预览是小写键面、实物已改大写的
情况）。提案改为：

1. 方案用 `meta.previewScene` 声明标准场景（如 `standard-26key@1.0`）；
2. 设置页与真机**共用同一套 token 解析器和绘制代码**，现场生成缩略图；
3. 支持：明暗并排、状态预演（常态/按下/锁定/选中候选）、点按键反查 token 与对比度；
4. `meta.contrast` 让 lint 与预览共用 WCAG 校验，低于阈值直接标红。

[preview.html](preview.html) 是这条思路的可运行原型：由 `keyboard.color.tokens.json`
与 `develop-color.map.yaml` 生成，无外部依赖，浏览器直接打开。

## 7. 迁移与兼容

### 7.1 完整映射

[develop-color.map.yaml](develop-color.map.yaml) 覆盖 `develop` 全部 72 个字段，每条给出：

- `legacy`：旧字段名；`source`：`colorKey` / `schema` / `builtinLabel` / `extension`；
- `token`：目标组件 token；`selector`：若旧字段其实是某个状态变体；
- `ambiguous`：语义有歧义、需作者确认一次的字段（如 `shadow_color`）。

18 个位置标签的映射示例：

| 旧标签 | 含义（注释原文） | 目标 |
| --- | --- | --- |
| `bgn` / `tgn` | 功能键背景 / 文字 | `component.key.{surface,content}` + `key[role=system]` |
| `bkg` / `tkg` | 空格键 | `key[role=space]` |
| `benter` / `tenter` | 回车键 | `key[role=action]` |
| `bbs` / `tbs` | 退格键 | `key[role=system]` |
| `baoe` / `taoe` | aoeiu 元音键 | `key[role=character]` |
| `bh1~bh4` / `th1~th4` | 第 1~4 行 | `key[role=character]`（行号信息丢弃） |

### 7.2 别名层（存量皮肤零改动）

解析顺序：`新 token → variant 覆盖 → 旧名别名 → fallback default`。

别名层保留至少两个主版本，并在 `ThemeDiagnostics` 增加一条 INFO/WARNING：命中旧名时
提示可迁移到哪个 token。本仓库已有的 `ColorKey.from` / `ColorTable.resolveRawSource`
字符串链机制天然适合挂这一层——**无需改动现有解析语义**，只需在查表前加一次别名归一化。

### 7.3 落地路线（分阶段，均不含布局改动）

| 阶段 | 内容 | 兼容性 |
| --- | --- | --- |
| 1 | 引入 `doc/color-naming/` 规范与 token 定义；给 schema 加 `$schema` 与 lint 规则 | 零影响 |
| 2 | `ColorKey` 增加别名归一化；`ThemeDiagnostics` 报告旧名混用 | 存量主题零改动 |
| 3 | 新主题可选用 token 写法；设置页接入预览渲染器 | 新旧并存 |
| 4 | 内置主题逐步迁移到新名 | 别名层兜底 |

阶段 1 即本 PR 的内容（纯文档 + 数据 + 原型）。

## 8. 这套命名解决的三件事

1. **字段不再膨胀**：状态从字段变成规则，加一种状态只加一条 selector；
2. **换主题只改一处**：`role` 层覆盖即可，组件与布局完全不动；
3. **预览永不过期**：设置页与真机同源渲染，静态 `demo.png` 不再是唯一真相。

## 附：校验

```bash
cd doc/color-naming && python3 validate.py
```

检查项：token 引用链无悬空/循环、两套主题 `role` 键对齐、12 条变体引用有效、
72 个 develop 字段映射全覆盖、WCAG 对比度达标、预览原型内嵌数据与 token 文件一致。

## 附：来源

- 本仓库 `develop`：`ColorKey.kt`、`doc/trime-schema.json`、内置主题 `tongwenfeng.trime.yaml`
- [W3C Design Tokens Format Module](https://design-tokens.github.io/community-group/format/)
- [FlorisBoard Snygg](https://github.com/florisboard/florisboard) 主题选择器
- 元书输入法仓默认键盘 `Colors.libsonnet`（浅/深语义配色）
