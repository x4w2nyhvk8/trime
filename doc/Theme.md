<!--
SPDX-FileCopyrightText: 2015 - 2026 Rime community

SPDX-License-Identifier: GPL-3.0-or-later
-->

# 主题包（v3）

主题是一个**目录**，目录名就是主题 id。放在 `<应用外部存储>/shared/`（随应用分发）或
`<应用外部存储>/rime/`（用户自己的主题，同名时优先）下。

```
<主题id>/
├── theme.yaml     主题本体：名称、样式、按键、键盘布局、样式节点
├── day.yaml       日间配色（颜色令牌 + 资源覆盖）
├── night.yaml     夜间配色（同上，键集合应与 day.yaml 一致）
├── images/        本主题私有资源（点九图/图片）
│   ├── <共享资源>
│   ├── day/<仅日间>
│   └── night/<仅夜间>
├── fonts/         本主题私有字体（可选）
└── soundeffect/   本主题私有按键音（可选）
```

- `theme.yaml` **必需**；`day.yaml` **必需**；`night.yaml` 可选（缺失时回落 `day.yaml` 并给诊断警告）。
- 推荐目录名带 `.trime` 后缀（如 `mytheme.trime/`），与旧格式文件名 `mytheme.trime.yaml` 对应；不带后缀也可以。
- 资源只在主题包内解析，**不跨主题共享**，也不回落 app 的 `res/drawable`。
- **字体**（`fonts/`）与**按键音**（`soundeffect/`，含描述符 `*sound.yaml` 与音频文件）可以随主题包分发：
  解析时**先看主题包、再回落全局**（`<用户数据>/fonts/`、`<用户数据>/soundeffect/`），
  所以用户仍可在全局目录里替换同名文件；同名描述符以主题包内为准。

---

### 1.1 `style:` 里与观感相关的字段（节选）

| 字段 | 说明 |
| --- | --- |
| `keyboard_height` / `keyboard_height_land` | 键盘总高（四行按引擎公式等分） |
| `key_height` / `horizontal_gap` / `vertical_gap` | 单键可视高度与横/纵缝 |
| `round_corner` / `key_corner_ratio` | 键圆角：`key_corner_ratio > 0` 时按「键高 × 比例」缩放，否则用 `round_corner` 的 dp |
| `key_text_size` / `key_long_text_size` | 单字形 / 多字形标签字号 |
| `key_font_weight` / `candidate_font_weight` | 标签字重：`normal`（默认）/ `medium` / `bold` |
| `keyboard_padding*` | 键盘四周留白 |
| `candidate_view_height` / `comment_height` / `candidate_text_size` | 候选行 |
| `popup_*` | 长按弹层尺寸与字号 |

回车键的配色走**键级覆盖** + 两个专用令牌：条目上写
`key_back_color: enter_key_back_color`、`hilited_key_back_color: hilited_enter_key_back_color`
（同理 `enter_key_text_color` / `hilited_enter_key_text_color`），这样按下时是深一档的强调色而不是通用灰。

## 1. theme.yaml

主题本体。顶层键与旧格式一致，**但不包含 `preset_color_schemes`**：

| 键 | 说明 |
| --- | --- |
| `name` / `author` / `description` / `version` | 元信息，`name` 用于主题列表显示 |
| `style` | 全局样式与尺寸（键高/间距/圆角/字号/字重/候选行，见 §1.1） |
| `preedit` / `window` / `tool_bar` / `liquid_keyboard` | 各区域样式 |
| `preset_keys` | 按键预设，键名规则见 [Keyboard.md](Keyboard.md) |
| `preset_keyboards` | 键盘布局 |
| `styles` | 具名样式节点（v3 新增，见 §4、§8） |
| `option_panel` | 选项/开关面板的样式槽（v3 新增，见 §8） |
| `clipboard` | 剪贴板的样式槽（v3 新增，见 §8） |
| `fallback_colors` | 颜色回退链 |

**日间与夜间共用的东西只写在这里一次**：布局、键名、样式结构、动作、尺寸。任何"白天和夜里应该相同"的值都不允许出现在 `day.yaml`/`night.yaml` 里（§3 有校验）。

---

## 2. day.yaml / night.yaml

各是一套**扁平的颜色表**（key → 颜色值）或**资源覆盖表**，就是旧格式里
`preset_color_schemes` 下的一套方案。

```yaml
# day.yaml
back_color: 0xe4e7e9
text_color: 0x5a676e
key_back_color: 0xffffff
# 值也可以是一张图（含点九）
# key_back_color: key_9p.9.png

# night.yaml
back_color: 0x1c1c1e
text_color: 0xf0f0f0
key_back_color: key_9p_dark.9.png
```

颜色值的写法（解析器见 `util/ColorUtils.kt`，两种等价）：

| 写法 | 含义 |
| --- | --- |
| `0xRRGGBB` / `0xAARRGGBB` | 整数形式，24 位 RGB 或 32 位 ARGB |
| `"#RRGGBB"` / `"#AARRGGBB"` | 字符串形式 |
| `xxx.png` / `.webp` / `.jpg` / `.gif` | **图片**（含 `.9.png` 点九），相对于主题包 `images/` |

### 2.1 约束（把"漂移"变成编译期错误）

| # | 约束 | 违反时 |
| --- | --- | --- |
| 1 | `day.yaml` 与 `night.yaml` 的键集合**差集必须为空** | CI 报错（引擎层仍允许缺项，走 `fallback_colors` 补全，以兼容手写主题） |
| 2 | 两份文件里**只允许**颜色令牌与图片值；出现 `layout`/`key`/`style`/`action` 等结构类字段 | CI 报错 |
| 3 | `night.yaml` 缺失 | 回落 `day.yaml` + 诊断警告（**不**让键盘空白） |

引擎层"宽进"（缺项用 `fallback_colors` 补）、CI 层"严出"（差集为空），两侧都要。

### 2.2 日间 / 夜间如何生效

- 「跟随系统深浅色」（`follow_system_day_night`）关闭：始终用 `day.yaml`。
- 打开：系统处于夜间模式用 `night.yaml`，否则用 `day.yaml`。

主题内部**没有「配色方案」的概念**，因此没有方案选择器，旧格式的
`light_scheme` / `dark_scheme` 互链键与配色切换动作（`set_color_scheme`）都不再可用。
想提供多种风格 → 做成**多个主题包**（每个包自带 day/night），不要塞回这两个文件。

---

## 3. images/

```
<主题id>/images/
├── shared_9p.9.png      两模式共享
├── voice_icon.png
├── day/<仅日间>
└── night/<仅夜间>
```

**解析顺序**：`images/<当前模式>/<名>` → `images/<名>`。两条都没有 = 诊断 hard error。

| 资源类型 | 写法 | 说明 |
| --- | --- | --- |
| 点九图 | `key_back_color: key_9p.9.png` | 由图片自身的九宫格内边距决定拉伸；编译过的点九与源码点九都支持 |
| 普通图 | `background: voice_icon.png` | 按原图尺寸或样式指定的 `contentMode` 绘制 |
| 系统图标 | 见 §4 `content: systemIcon` | 不占主题包资源 |

资源名与键名**解耦**：不要写 `spaceButton.9.png`，否则键改名还要动资源文件。

---

## 4. styles：具名样式节点（v3 新增）

`styles:` 是主题级的具名样式表，供其它节点按名引用。

**命名约定（与 [Keyboard.md](Keyboard.md) 的按键命名一致）**

| 东西 | 形态 | 例 |
| --- | --- | --- |
| **样式名 / 键名 / id** | lowerCamelCase | `spaceKeyIconStyle`、`optionPanelSwitchItemStyle` |
| **YAML 字段名** | **snake_case**（解析器用 `SnakeCase` 命名策略） | `corner_radius`、`item_spacing`、`system_icon` |

样式节点按**引用它的位置**分成两族，**同一个名字不得跨族复用**（一义一名）：

### 4.1 键样式节点（被布局条目的 `style:` 引用）

用 `content` 声明**呈现类型**（照搬元书 `buttonStyleType` 的思路）：

| `content` | 必填字段 | 图层 | 说明 |
| --- | --- | --- | --- |
| `text`（默认） | `text` 或由 placement 提供 | 前景 | 文字，`text_size` 可覆写字号 |
| `system_icon` | `system_icon` | 前景 | 系统矢量图标（走既有的 `ic@` 图标通道），`icon_size` 可覆写尺寸 |
| `asset_icon` | `asset_icon` | 前景 | 主题包 `images/` 里的图标，`icon_size` 可覆写尺寸 |
| `nine_patch` | `nine_patch` | **背景** | 主题包 `images/` 里的点九图，随尺寸拉伸 |
| `geometry` | — | **背景** | 纯形状（`corner_radius` / `border_width` / `border_color`） |

**一个样式节点只填一层**：上面第三列的"图层"由 `content` 决定；同一节点里出现另一层的字段 = 诊断 ERROR（一义一名）。
`content` 未声明时按 `text`。

**载荷值必须带扩展名**（`.png` / `.webp` / `.jpg` / `.gif`，点九用 `.9.png`），与颜色表的图片判定是同一套规则；
引用的资源在实际解析的目录里不存在 = **诊断 ERROR**（与颜色表引用缺失图片同等对待，见 §3）。

```yaml
styles:
  spaceKeyTextStyle:      { content: text,        font_face: default }
  spaceKeyIconStyle:      { content: asset_icon,  asset_icon: voice_icon.png,  icon_size: 24 }
  spaceKeyNinePatchStyle: { content: nine_patch,  nine_patch: key_9p.9.png }
  shiftButtonForegroundStyle: { content: system_icon, system_icon: shift }
```

样式名生成规则（便于机械推导，不靠记忆）：

```
<键名>[<部位>][<状态>]Style
shiftButtonForegroundStyle / shiftButtonCapsLockedForegroundStyle
spaceKeyIconStyle / spaceKeyNinePatchStyle / letterButtonBackgroundStyle
```

### 4.2 面板 / 列表样式节点（被 §8 的面板引用）

见 §8「面板与列表」。

**覆盖优先级：placement > style > 内置默认。**

引用不存在的样式名 = 诊断 hard error（不静默降级）。

---

## 5. 诊断

主题加载时会报出的 hard error（该主题不可用）：

- 缺 `theme.yaml` 或 `day.yaml`；
- `day.yaml` / `night.yaml` 里出现结构类字段（§2.1 第 2 条）；
- 引用了不存在的样式名 / 资源名；
- `preset_keys` 键名不符合命名规则（见 [Keyboard.md](Keyboard.md)）。

警告（主题可用）：

- `night.yaml` 缺失（回落 `day.yaml`）；
- `day.yaml` 与 `night.yaml` 键集合有差集；
- `__include` / `__patch` 等运行时会忽略的未知键。

---

## 6. 不再支持（v3 破坏性变更）

- 旧的单文件主题 `<id>.trime.yaml`：一律不再识别。
- `preset_color_schemes`（多套配色方案）与 `light_scheme` / `dark_scheme` 互链。
- `__include` / `__patch`：主题是自包含的 YAML，librime 的配置 DSL 不再解析。
  写了这两个键的主题仍能加载，但它们只作为"运行时会忽略的未知键"被诊断器报出来。
- 全局 `backgrounds/` 背景图目录（改为主题包内 `images/`）。

## 7. 从旧格式迁移

旧主题可以用迁移脚本转换（需要 Node 与 `yaml` 包）：

```sh
npm i yaml
node script/migrate-theme.js 旧主题.trime.yaml 输出目录 日间方案id 夜间方案id
```

它会：内联 `__include`、把本体写进 `theme.yaml`、把指定的两套方案分别写成
`day.yaml` 与 `night.yaml`、把 `backgrounds/` 里的图片搬进 `images/`，并在写出后重新解析校验。
旧主题里其他方案不会被保留 —— 请先挑好要用作日间与夜间的那两套（其余建议做成独立主题包）。

例如内置主题就是这样迁过来的：`trime.yaml` → `trime/{theme.yaml,day.yaml,night.yaml}`，
日间取 `default`、夜间取 `steam`。

---

## 8. 面板与列表（`option_panel` / `clipboard`）

两块 UI **不是键盘**，但今天完全借键盘的东西活：

| | 今天借了什么 | 今天硬编码了什么 |
| --- | --- | --- |
| 选项/开关面板 | 条目背景=`key_back_color`+`key_border_color`，圆角=`style.round_corner`，文字=`key_text_color` | 行高 `96dp`、图标 `48dp`、文字 `12f`、文字图标 `20f`、间距 `4/6dp` |
| 剪贴板 | 同上 + 图钉=`key_symbol_color` | 条目 `min_height 30dp`、图钉 `12dp`、文字 `14f`、内边距 `8/4` |
| 面板自身 | 只读 `candidate_view_height + comment_height`（面板按钮尺寸） | 窗口背景/圆角/内边距/条目间距/列表左右边距 **无任何入口** |

后果：想让剪贴板更暗或更透，会**连带改主键盘**；想让条目矮一点、换个圆角，**只能改代码**。
因此 v3 给这两块独立的样式槽（与键盘解耦）。

### 8.1 theme.yaml 的两个新顶层节

```yaml
option_panel:
  panel_style: optionPanelStyle
  action_item_style: optionPanelActionItemStyle
  switch_item_style: optionPanelSwitchItemStyle
  title_item_style: optionPanelTitleItemStyle

clipboard:
  panel_style: clipboardPanelStyle
  item_style: clipboardItemStyle
  pinned_item_style: clipboardPinnedItemStyle
  title_item_style: clipboardTitleItemStyle
```

（工具栏最左那颗键叫 `optionPanelButton`，动作 `openOptionPanel`；面板条目**不是键**，不进 `preset_keys`。）

**工具栏的尺寸（与候选栏解耦）**：
候选页与工具栏页共用输入栏这一行，所以行高至少容下候选文字；`tool_bar.height`
让工具栏可以比候选行更高（按钮更大），设了「隐藏候选栏」时行高收缩到工具栏自己的高度。
- `tool_bar.height`：工具栏高度，**0 = 沿用 `candidate_view_height + comment_height`**；正数 = 该 dp；
- `tool_bar.button_size`：**所有工具栏按钮的默认边长**，0 = 跟随 `height`；
- 单个按钮的 `size: [宽, 高]` 仍然优先。


**工具栏的 `action:` 是 `preset_keys` 里的键名**（如 `optionPanelButton`），不是裸 command；
`openOptionPanel` 是那个 preset 的 **`command` 值**。未知 command 现在只会记一条警告，
不再把输入法拖崩（以前会落到 Android Intent 兜底并抛 ActivityNotFoundException）。

**工具栏按钮的字段命名（一义一名）**：按钮图标 = `foreground.icon`（值 `ic@<图标名>`）；
多态图标 = `foreground.option_icons`；返回键图标 = `back_icon`。
这三个是图标载荷，**不是** `styles:` 引用 —— `style` 这个名字只用于『引用样式表』这一件事。

### 8.2 面板样式节点（`*PanelStyle`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `background` | 颜色令牌 / 图片名 | 面板背景（支持点九） |
| `corner_radius` | number | 面板圆角 |
| `padding` | number 或 `[上下, 左右]` | 面板内边距 |
| `height` / `width` | number | 面板整体尺寸；**不声明** = 不动宿主给的参数（即今天的行为）；`0` = 自适应（wrap_content），`-1` = 填满可用空间（match_parent），其它正数 = 该 dp |
| `max_height` | number | 高度上限；0 = 不限 |
| `side_margin` | number | 列表左右边距 |
| `item_spacing` | number | 条目之间的纵向间距 |
| `action_button_size` | number | 面板 chrome 上那颗操作按钮的尺寸（选项面板右上角「设置」、剪贴板标题栏「全部删除」）；0 = 内置默认 |
| `divider` | number | 列表条目之间的细线宽度（dp）；0 = 不画（目前用于剪贴板列表） |
| `divider_color` | 颜色令牌 | 细线颜色；空 = 条目描边色（`key_border_color`） |

### 8.3 条目样式节点（`*ItemStyle`）

| 字段 | 说明 |
| --- | --- |
| `height` / `min_height` | 条目高度（0 = 由内容决定） |
| `corner_radius` / `border_width` / `border_color` | 条目形状与描边 |
| `background` / `background_on` / `background_highlight` | 背景：普通 / 开态（开关） / 按下 |
| `text_color` / `label_size` / `max_lines` / `ellipsize` | 文字 |
| `icon_size` / `icon_color` | 左侧图标 |
| `pin_size` / `pin_color` / `pin_inset` | 置顶角标（剪贴板） |
| `padding` | 条目内边距（number 或 `[上下, 左右]`）；选项面板条目的「顶部内边距」与「图标-文字间距」也由它表达 |

### 8.4 条目类型槽（引擎已知的类型 → 样式）

按**类型**分槽，**不按实例**（条目是运行时产生的：剪贴板内容、方案自带的开关都不确定）。

| 面板 | 槽 | 对应引擎里的东西 |
| --- | --- | --- |
| `option_panel` | `action_item_style` | `SwitchOptionEntry.Static`（主题 / 方案 / 更新配置 / 虚拟键盘） |
| | `switch_item_style` | `SwitchOptionEntry.Custom`（Rime 开关，有开 / 关两态 → 用 `background_on`） |
| | `title_item_style` | 分组 / 标题行（引擎当前还没有标题条目，槽位已就绪） |
| `clipboard` | `item_style` | 普通文本条目 |
| | `pinned_item_style` | 置顶条目（图钉角标） |
| | `title_item_style` | 剪贴板标题行（含"全部删除"按钮） |

### 8.5 规则

- 面板条目**不是键**：不进 `preset_keys`，不受键名正则约束；
- **一义一名**：样式名不得跨族复用（键样式 / 面板样式 / 条目标式）；同一个名字两处引用即诊断报错；
- 不提供"按实例"配置；
- 未定义样式名、未知字段 = 诊断 hard error；
- 新增颜色令牌（同样走 day/night 两套）：`option_panel_back_color`、`clipboard_back_color`。

---

## 9. 符号面板（分组符号库，原「液体键盘」）

符号页（`preset_keyboards.symbols`）只放最常用的一页标点；**细分、可扩充的符号集放进符号面板**（主题里仍叫 `liquid_keyboard:`，历史名）：
分类条 + 网格，一屏一组，和主流输入法的"符号库"同构。

```yaml
liquid_keyboard:
  single_width: 56        # SINGLE 类每个键的宽度（dp）
  key_height: 44
  margin_x: 4             # 左右键距的 1/2
  fixed_key_bar:          # 固定在滚动区旁的一条键（只能用 preset 名，显示其静态 label）
    position: BOTTOM      # TOP | BOTTOM | LEFT | RIGHT
    keys: [keyboardLettersButton, clipboardButton, backspaceButton]
  keyboards: [punct, math, currency, arrow, ordinal, kaomoji, tabs]   # 分类顺序即分类条顺序
  punct:                  # 分类 id 就是与 keyboards 同级的键
    name: 标点            # 分类条上的文字
    type: SINGLE          # SINGLE | SYMBOL | TABS | HISTORY
    keys: "，。、；：！？…—～·《》〈〉「」『』【】（）"
```

- **`SINGLE`**：`keys` 是**文本**，按**字符**拆成固定宽度的键（`single_width`），适合标点/数学/货币/箭头/序号。
- **`SYMBOL`**：`keys` 是**列表**（每项一个键，可含多字符，如颜文字），适合宽度不等的条目。
- **`TABS`**：内容是该分类下所有非 `TABS` 分类的名字（"更多"页），`keys` 不写。
- **`HISTORY`**：最近使用的条目。
- **打开方式**：preset 写 `send: FUNCTION`、`command: liquid_keyboard`、`option: <分类 name>`；
  `option` 命中分类名就开那一组，命中 `SINGLE`/`SYMBOL` 这类类型名就开第一个该类分类，都不命中则回主键盘。
### 9.2 分类条（Tab）样式

分类条默认只用"选中胶囊 + 文字颜色"，**未选中的 Tab 之间没有间距也没有分割线**。可配字段：

| 字段 | 说明 |
| --- | --- |
| `tab_spacing` | Tab 之间的间距（dp）；`0` = 紧挨 |
| `tab_divider` | Tab 之间的竖线宽度（dp）；`0` = 不画 |
| `tab_divider_color` | 竖线颜色令牌；空 = `cell_border_color`，再空 = `key_border_color` |
| `tab_corner_radius` | Tab 圆角；`-1` = `style.candidate_corner_radius` |
| `tab_back_color` / `tab_active_back_color` | 未选中 / 选中底色令牌（空 = 透明 / `hilited_candidate_back_color`） |
| `tab_text_color` / `tab_active_text_color` | 未选中 / 选中文字令牌（空 = `candidate_text_color` / `hilited_candidate_text_color`） |
| `tab_text_size` | Tab 字号（sp）；`0` = `style.candidate_text_size` |
| `tab_height` | 分类条高度（dp）；`0` = `candidate_view_height + comment_height` |
| `tab_padding` | Tab 文字水平内边距；不写 = `style.candidate_padding` |
| `tab_min_width` | Tab 最小宽度（dp）；`0` = 40dp |

### 9.0 面板结构

符号面板是**左侧分类列 + 右侧 4 列 × 5 行网格 + 底部固定键栏**：

```
┌──────┬───────────────────────┐
│ 分类 │  4 列 × 5 行（网格线）│
│(滑动)│                       │
├──────┴───────────────────────┤
│ 返回 │ 字母 │ 剪贴 │ 退格     │
└──────────────────────────────┘
```

- 分类列宽 `tab_column_width`（dp，默认 72）；
- 网格固定 4 列，高度按 5 行等分；格线用 `cell_divider` / `cell_divider_color`；
- 底部固定键栏由 `fixed_key_bar.keys` 决定，默认第一个是 `liquidKeyboardExitButton`（返回）——
  从数字键盘进入就回数字键盘，从主键盘进入就回主键盘。

### 9.1 符号面板的格子样式

符号面板的每个符号格默认**借用键盘按键的令牌**（`key_back_color` / `key_border_color` /
`key_border` / `round_corner` / `key_text_color`）。要让它和主键盘分开，写这几个字段
（**默认值 = 借用按键**，所以老主题零变化）：

| 字段 | 说明 |
| --- | --- |
| `cell_back_color` | 格子背景（颜色令牌或图片名）；空 = `key_back_color` |
| `cell_border` | 格子描边宽度（dp）；`-1` = `style.key_border` |
| `cell_border_color` | 描边颜色令牌；空 = `key_border_color` |
| `cell_corner_radius` | 格子圆角（dp）；`-1` = `style.round_corner` |
| `cell_text_color` | 符号文字颜色令牌；空 = `key_text_color` |
| `cell_divider` | **格间细线**宽度（dp）；`0` = 不画 |
| `cell_divider_color` | 细线颜色令牌；空 = `cell_border_color`（再空则 `key_border_color`） |
| `cell_text_size` | 符号字号（sp）；`0` = `style.key_text_size` |
| `cell_padding` | 符号内边距：一个数字或 `[上下, 左右]`；不写 = 内置 `8/4 dp` |
| `cell_highlight_color` | 按下时的格子底色令牌；空 = `hilited_key_back_color` |
| `margin_y` | **纵向**格间距（dp）；`-1` = 与 `margin_x` 相同 |

格间距仍由 `margin_x` 控制（每格四周各留 `margin_x`）。

- 默认主题开了 6 组：标点 / 数学 / 货币 / 箭头 / 序号 / 颜文字，由符号页底行的「更多」键
  （preset `symbolsMoreButton`，`option: 标点`）进入。
