<!--
SPDX-FileCopyrightText: 2015 - 2026 Rime community

SPDX-License-Identifier: GPL-3.0-or-later
-->

# 按键与键盘

本文是 v3 的按键契约：**键名规则**、**按键条目（placement）字段**、以及液态键盘/横屏等参数。

---

## 1. 按键命名规范

### 1.1 两类命名空间，语义一眼可辨

| 形态 | 含义 | 例 |
| --- | --- | --- |
| `^[a-z][a-zA-Z0-9]*Button$` | **输入法内功能键 / 字符键** | `spaceButton`、`qButton`、`digit0Button` |
| `^[A-Z][A-Z0-9_]*$`（白名单内） | **宿主 / Android Intent** | `VOLUME_UP`、`VOICE_ASSIST` |

键名回答的是"**这是哪颗键（角色）**"，不回答"按下它干什么"（那是 `send`/`command`/`option` 的事）。
键名同时充当布局引用名与样式节点名。

### 1.2 功能键：`<限定词?><角色>Button`

```yaml
preset_keys:
  spaceButton:    {functional: false, repeatable: false, send: space}
  returnButton:   {label: 回车, send: Return}
  backspaceButton:{label: 退格, send: BackSpace}
  shiftButton:    {label: 换档, send: Shift_L}
  hideButton:     {label: 收起, send: Escape}
  cursorLeftButton:  {label: '←', send: Left}
  cursorRightButton: {label: '→', send: Right}
  pageUpButton:   {label: 上页, send: Page_Up}
  keyboardSymbolsButton: {label: 符号, send: Eisu_toggle, select: symbols}
  liquidKeyboardSwitchButton: {label: 更多, send: Eisu_toggle, select: more}
  clipboardButton: {label: 剪贴板, send: Eisu_toggle, select: clipboard}
  schemaSwitchButton: {label: 方案, send: Eisu_toggle, select: schema}
```

常用角色词（新增按键优先从这里取，避免同义异名）：

```
space return backspace shift hide escape
cursorLeft cursorRight cursorUp cursorDown home end insert delete
pageUp pageDown
copy cut paste selectAll undo redo
keyboardDefault keyboardNumber keyboardSymbols keyboardLetters keyboardMini
keyboardSwitch liquidKeyboardSwitch liquidKeyboardExit liquidKeyboardEmoji
clipboard editor imeSwitch imeLast imeNext schemaSwitch colorSwitch candidateSwitch commentSwitch hintSwitch
oneHandLeft oneHandRight oneHandSwitch
optionPanel menu settings help info deploy sync updateConfig
date chineseDate time share
webSearch searchText
```

工具栏最左侧那颗键 = **`optionPanelButton`**（`preset_keys` 里的键名；它的 `command` 是 `openOptionPanel`，打开选项/开关面板）：
不要叫"菜单键"——`MENU`（方案选单）已经在用"菜单"这个语义；也不要按位置命名（`leftmostButton`），位置会变、角色不会。
面板里的条目**不是键**（不进 `preset_keys`），命名见 [Theme.md](Theme.md) §8。

变体只用**限定词**表达方位或层级，**禁止编号后缀**：

```yaml
shiftLeftButton / shiftRightButton            # 方位
verticalReturnButton / verticalPageUpButton   # 层级（元书同款写法）
```

### 1.3 字符键：字符英文名 + `Button`

字符键也要有样式节点（用于逐键配色/动效），命名按字符名：

| 类别 | 规则 | 例 |
| --- | --- | --- |
| 字母 | 小写字母 + `Button` | `aButton` … `zButton` |
| 数字 | `digit<0-9>Button` | `digit0Button` … `digit9Button` |
| 标点/符号 | **英文名** + `Button` | 见下表 |

```yaml
periodButton .    commaButton ,      questionButton ?  exclaimButton !
colonButton :     semicolonButton ;  apostropheButton ' quoteButton "
hyphenButton -    underscoreButton _ slashButton /      backslashButton \
atButton @        numberSignButton # dollarButton $     percentButton %
ampersandButton & asteriskButton *   plusButton +       equalButton =
tildeButton ~     caretButton ^      pipeButton |       parenLeftButton (
parenRightButton ) bracketLeftButton [ bracketRightButton ] braceLeftButton {
braceRightButton } lessButton <      greaterButton >
```

表外符号：**先补这张表，再命名**。名字里只允许英文名，禁止把符号/拼音/图形字符写进 id。

### 1.4 系统 Intent 键：保留全大写原名

这 15 个是 Android Intent，键名保持上游原样，**不加前缀、不改名**：

```
BRIGHTNESS_DOWN  BRIGHTNESS_UP  VOLUME_DOWN  VOLUME_UP  VOLUME_MUTE
CALCULATOR  CALENDAR  CONTACTS  ENVELOPE  EXPLORER  MUSIC
POWER  SEARCH  SLEEP  VOICE_ASSIST

SEARCH: {label: 搜索, send: SEARCH}   # 原始 Intent 名仍然只出现在 send 值里
```

### 1.5 明确禁止

| 违规 | 反例 | 正确做法 |
| --- | --- | --- |
| 编号后缀 | `Return1`、`Return2`、`space1`、`one_hand_switch_1`、`Zdic2` | 差异进条目字段或限定词 |
| 呈现进键名 | `space1`（其实只是 label 不同） | 条目写 `label: {text: 空格}` 或引用样式 |
| 位置进键名 | `space_R`（其实是语种切换键） | `languageSwitchButton`，位置由布局树表达 |
| 键名重复动作 | `CommitRawInput`、`DeleteCandidate` | `commitRawInputButton`、`deleteCandidateButton` |
| keysym 当键名 | `BackSpace`、`Page_Up`、`Zenkaku_Hankaku`、`F4` | `backspaceButton`、`pageUpButton`、`eisuButton`、`schemaMenuButton` |
| 空值触发特殊语义 | `label` 为空 ⇒ 显示方案名 | 显式写 `label: {source: schemaName}` |
| 外部站点各占一个键 | `Baidu`/`MoeDict`/`Zdic`/`Zdic2`/`WebSearch` | `webSearchButton` + 搜索源列表 |

### 1.6 机器校验（CI）

1. 键名必须匹配 `^[a-z][a-zA-Z0-9]*Button$`，或匹配 `^[A-Z][A-Z0-9_]*$` 且落在 §1.4 的 15 名白名单内；白名单外的大写一律报错。
2. **全库无编号后缀**（`/\d$/` 报错；`digit0Button` 以 `n` 结尾，不受影响）。
3. 一义一名：同名冲突、近义重名（如 `searchButton` 与 `SEARCH`）报错。
4. `send` / `command` 的取值走 keysym / Intent 白名单。

---

## 2. 按键条目与覆盖优先级

`preset_keyboards.<id>.keys` 的每一项是一个**条目（placement）**，用 `key` 声明"这一格是哪颗键"，再按位置覆写。

```yaml
keys:
  - {key: qButton}                                  # 字符键：动作隐含为输入 q
  - {key: spaceButton, width: 5/10, label: {source: schemaName}}
  - {key: spaceButton, width: 2/10, label: {text: 空格}, style: spaceKeyTextStyle}
```

### 2.1 `key` 的解析规则

`key: X` 按顺序解析（**`key` 声明身份，不声明动作**）：

| X 是什么 | 结果 |
| --- | --- |
| 命中 `preset_keys` | 采用该 preset 的动作与默认外观 |
| 命中**内置字符键表**（§1.3 的 `aButton`…/`digit0Button`…/`periodButton`…） | 动作隐含为"输入对应字符"，外观取默认 |
| 都没命中 | **诊断错误**（除非主题在 `preset_keys` 里自定义了 X，且不与内置字符键表冲突） |

**`key` 与 `click` 的关系**：`key` 是身份，`click` 是"点击时干什么"。两者可同时出现，**显式字段一律覆盖 `key` 带来的默认值**：

```yaml
- {key: spaceButton, click: ' '}        # 身份是空格键，但这一格点击只输入一个空格
- {key: 'q', click: shiftButton}        # 也可以只要动作：不写 key，直接给 click
```

> 只写 `click`（旧写法）依然可用，只是那颗键没有身份名，因此**不能**被样式节点/诊断按名引用。

**`key` 同时是样式节点名**：`{key: qButton}` 会去找 `styles.qButton*` 一类的样式；没有定义就用默认外观。

**覆盖优先级：placement 显式字段 > style > `key` 带来的 preset 默认。**

### 2.2 `label` 的两种形态

| 写法 | 含义 |
| --- | --- |
| `label: 空格` | 静态文字（等价于 `label: {text: 空格}`） |
| `label: {text: 空格}` | 静态文字（显式形式） |
| `label: {source: schemaName}` | **动态**：当前方案名（典型用途：字母键盘的空格键） |
| `label: {source: schemaId}` | 动态：当前方案 id |
| `label: {source: enterLabel}` | 动态：App 提供的回车键文字（配合 `style.enter_labels`，如"前往/完成/发送"） |
| `label: {source: custom, text: …}` | 动态：自定义模板 |
| 不写 `label` | 该键**没有**文字（不再隐式变成方案名） |

> v3 之前的行为是"空格键 `label` 为空 ⇒ 显示方案名"，属于隐式约定，已取消。
> 需要显示方案名就显式写 `label: {source: schemaName}`。

**中文标点**：`ascii_punct` 关闭（中文标点模式）时，单字符的 ASCII 标点键（`,` `.` `?` `!` `:` `;` `(` `)`）
**标签**自动显示对应全角形式（`，` `。` `？` `！` `：` `；` `（` `）`）；动作不变——上屏仍发 ASCII，由引擎转换。

### 2.3 常用条目字段

| 字段 | 说明 |
| --- | --- |
| `key` | **身份**：引用 `preset_keys` 键名，或内置字符键名（§1.3）；见 §2.1 |
| `click` / `long_click` | 该格子的点击/长按动作（preset 名或字面量）；覆盖 `key` 的默认动作 |
| `width` / `height` | 本处的宽度/高度权重（分数写法） |
| `label` | 见 §2.2 |
| `style` | 引用 `styles:` 里的具名样式节点 |
| `key_back_color` / `key_text_color` | 本处颜色覆写（支持图片值，含点九 `.9.png`） |
| `background` | 本处背景（颜色或图片） |

---

### 2.4 `popup`（长按符号）

按键条目可以写 `popup: [符号, …]`：长按时弹出一格网格，滑到目标格松手即上屏。

- 有 `popup` 的键会在**右下角画一个小三角**提示"这里长按还有东西"（参考元书 / fcitx5 的 alt 角标做法）；
- 显式 `long_click` 与 `popup` 同时存在时，**`popup` 优先**（长按先弹网格）；
- 弹层宽度 = `popup_width × 列数`，列数按 `ceil(项数 / 5)` 排；键在键盘左右边缘时弹层会自动收进边界内；
- 选择方式是**相对位移**（元书手感）：以按下点为原点，手指每移动约一格的距离就换一格，
  **不必把手指移到弹层上**；移动不足 10dp 视为抖动，滑出网格一格以上清除高亮（松手即取消），
  换格时给一次触感反馈。

### 2.5 `alt_label`（键角备用字符）

小企鹅（fcitx5）的 `AlphabetKey` 有 `alt`：键面上显示一个备用字符，滑动该键就输入它。
trime 用**两个互不占用的字段**表达同一件事：

- `alt_label`：**只显示**，画在键的**左下角**（不碰 `label_symbol`，那个槽归 `long_click` 的自动提示用）；
- `swipe_down`（或其它 `swipe_*`）：**动作**，写成那个备用字符本身（纯 token；引擎没有对应键码的字符按文本上屏）。

两者由主题配对，引擎不假设它们一致——写 `alt_label` 而不配 `swipe_*` 就是纯提示。
默认主题的 26 个字母键用「长按列表的第一项」同时作 `alt_label` 与 `swipe_down`。

### 2.6 布局容器（二维 / 可嵌套 / 嵌入式集合）

`preset_keyboards.<id>.keys` 是**容器树的根**：一个自上而下堆叠的列表。
每个条目要么是**容器**，要么是**键**（叶子）。容器有四种——三种布局容器，加一种嵌入式集合：

| 容器 | 语义 |
| --- | --- |
| `row` | 一个物理行，子项水平排列；**不会自动换行**（装不下就按百分比分完，或裁在右边缘，不会把后面的键挤到下一行） |
| `flow` | 子项按宽度顺序排、装不下自动换行（旧的流式行为，现在要显式写） |
| `column` | 子项竖直排列，每个子项独占一行 |
| `collection` | 一块**嵌入式可滚动集合**（符号网格），数据来自主题顶层 `collections:` 的某个 id |

```yaml
keys:                            # 根：按顺序竖直堆叠
  - row:                         # 行容器：子项水平排列
      - {key: qButton, width: 10}
      - column:                  # 列容器：子项竖直排列
          - row: [{key: aButton, width: 100}]
          - row: [{key: bButton, width: 100}]
        width: 20                # 容器自己的宽度 = 父行的 20%
      - {key: wButton, width: 70}
  - flow:                        # 流式：按宽度排、装不下换行
      - {key: xButton, width: 20}
```

#### 2.6.1 嵌套

`row` / `flow` / `column` 可以**任意互相嵌套**，深度不限；键只能出现在叶子处。
一个容器作为另一个容器的子项时，就是该父容器里的一个"块"，父容器按自己的轴向分配它的尺寸：

- 在**水平**父容器（`row` / `flow`）里，子项用 `width` 决定宽度，高度一律拉满父行；
- 在**竖直**父容器（根、`column`）里，子项用 `height` / `height_percent` 决定高度，宽度一律拉满父栈。

所以"九宫格 + 左侧一列"就是 **一个 `row`，左边一个 `column`（width: 20），右边一个装 3 个 `row` 的 `column`（width: 80）**，
两个子列被同一个行高撑开，3×3 与左列自然对齐。

#### 2.6.2 尺寸字段

叶子键的 `width` 语义不变：

- 正数 = 占**父行**宽度的百分比（`10` = 10%）；同一行里固定项之和应 ≤ 100；
- **`-1` = 占满本行剩余**（通常给空格）：本行其它固定项之和必须 < 100；
- `0` 或省略 = 用键盘默认键宽（`preset_keyboards.<id>.width`，默认 10）。

容器可以带同样的位置字段（写在容器名旁边，与它同级）：

```yaml
- row: [ {key: aButton, width: 50} ]
  width: 40            # 占父行宽度 40%（-1 = 占剩余；0 = 默认键宽）
  height: 80           # 在竖直父栈里 80 dp
  height_percent: 50   # 或：占父栈高度 50%（>0 时优先于 height）
```

高度语义（只对**竖直**父容器有意义）：

| 写法 | 含义 |
| --- | --- |
| `height_percent: 50` | 父栈高度的 50%；优先于 `height` |
| `height: 56` | 56 dp；在没有 `-1` 兄弟时按比例缩放，最后一项吸收取整误差 |
| `height: -1` | 占满本栈剩余（其余固定项先分） |
| 省略 / `0` | 固有高度：行 = 行内最高子项（或键盘默认行高），列 = 各子项之和，集合 = 3 个单元格 |

> 竖直方向总是被拉伸到父容器的总高（根就是 `keyboard_height`），所以 `height`/`height_percent`
> 彼此之间是**比例**关系；需要"精确百分比"时，让同一栈里的固定项之和不超过目标值，其余交给 `-1`。
> `width` 只在水平父容器里生效，`height` 只在竖直父容器里生效——另一个轴向一律拉满。

`flow` 容器里可以用 `row_break: true` 在某个子项前强制换行；`row`/`column` 本来就是行边界，不需要它。
`preset_keyboards.<id>.columns`（默认 30）限制 `flow` 每行的**可点击键**数。

#### 2.6.3 `collection`（嵌入式可滚动集合）

`collection` 在键盘布局里占一块矩形，里面是可滚动/可点击的符号单元格；数据取自**主题自己声明的**
`collections:`（顶层节，和 `preset_keys`/`liquid_keyboard` 平级，与液态键盘互不引用）：

```yaml
# theme.yaml 顶层
collections:
  punct: ["，", "。", "？", "！", "：", "；", "…", "、"]   # 简写：只要列表
  math:                                                    # 对象：可带自己的尺寸
    columns: 2
    cell_height: 44
    items: ["+", "−", "×", "÷", "="]

# 键盘布局里引用
- collection:
    source: punct          # collections: 的 id
    width: 20              # 占父行宽度百分比（-1 / 0 同键）
    scroll: vertical       # vertical | horizontal（默认 vertical）
    cell_height: 56        # 单元格高度，dp（省略 = 键盘默认行高）
    cell_width: 48         # 单元格宽度，dp（省略 = 与 cell_height 等宽）
    columns: 3             # vertical 时每行几个（省略 = 按 width 能放几个）
```

- 点击一格 = `commitText` 上屏该格的文本；
- 视觉沿用现有令牌：颜色取自配色表（`key_back_color` / `key_text_color` / `key_border_color`），
  尺寸与圆角取自 `style`（`key_text_size` / `key_border` / `round_corner`，或按键高比例的 `key_corner_ratio`）；
- `source` 找不到 / 没写 / 集合里没有数据 = 渲染为**空集合**，加载时给一条 **WARNING**
  （找不到或没写是 `MISSING_COLLECTION_SOURCE`，集合为空是 `EMPTY_COLLECTION`），不影响其它键。

#### 2.6.4 第三方作者：最小九宫格（T9）

```yaml
preset_keyboards:
  t9:
    name: 九宫格
    height: 56
    keys:
      # 左侧一列：占 20% 宽，3 个单元格（与右边 3 行同高）
      - row:
          - column:                # 竖向堆叠：子项宽度一律拉满本列
              - {key: keyboardSymbolsButton}
              - {key: backspaceButton}
              - {key: returnButton}
            width: 20
          # 右侧 3×3 数字网格：占 80% 宽
          - column:
              - row: [{key: digit1Button, width: 33.34}, {key: digit2Button, width: 33.33}, {key: digit3Button, width: 33.33}]
              - row: [{key: digit4Button, width: 33.34}, {key: digit5Button, width: 33.33}, {key: digit6Button, width: 33.33}]
              - row: [{key: digit7Button, width: 33.34}, {key: digit8Button, width: 33.33}, {key: digit9Button, width: 33.33}]
            width: 80
      # 底部：符号集合 + 空格 + 0
      - row:
          - collection:
              source: punct
              width: 20
              scroll: vertical
              cell_height: 44
              columns: 2
          - {key: spaceButton, width: -1}
          - {key: digit0Button, width: 20}
```

默认主题**不带**九宫格；上面的键盘只是给主题作者的最小样例（`width` 之和按 100 计）。

## 3. Liquid Keyboard 参数

(3.2.17), 只有使用 `single_width` 。其余的还未有作用。

- `single_width` : 在 `SINGLE` 类别中，每项的阔度 (和高度相等)。

新增一些参数 `fixed_key_bar:` 用于自定义液态键盘中的固定按键条：

例：
```yaml
liquid_keyboard:
  fixed_key_bar:  # 固定按键条
    position: bottom  # 摆放位置（在滚动区域的……） top|bottom|left|right
    keys:  # 按键（显示名称为对应的 label，不能放太多）
      - liquidKeyboardExitButton    # 返回键（在 preset_keys 内定义，同下）
      - spaceButton                 # 空格键
      - backspaceButton             # 退格键
      - returnButton                # 回车键
      - clipboardButton             # “剪贴板”跳转按键
      - liquidKeyboardSwitchButton  # “更多”跳转按键
```

## 4. 蓝牙键盘问题

https://github.com/osfans/trime/issues/1058#issuecomment-1672635413

## 5. 横屏键盘

(3.2.17) 新增两个参数到 `preset_keyboards`：

- `landscape_keyboard`: 横屏时转用的 keyboard id。当判定需显示横屏键盘时，会显示此值代表的键盘。该 id 需包含在 `style/keyboards` 中。
- `landscape_split_percent`: 0 - 200。此值只在横屏时生效，优先等级高于「键盘设定」>「自动分割键盘比例」。此值代表键盘自动分割时中间的空白阔度比例。如：
	- `100` 代表 100%，即空白的阔度与键盘的总阔度比例为 1:1。
	- `150` 的话，代表空白的阔度与键盘的总阔度比例为 1.5:1。
	- `0` 代表不做自动分割。

例：
```yaml
patch:
  "style/keyboard": [my_keyboard, my_landscape_keyboard, mini, .....]
  "preset_keyboards/my_keyboard":
    name: My Keyboard
    landscape_keyboard: my_landscape_keyboard
    ...
    keys:
      - ......
  "preset_keyboards/my_landscape_keyboard":
    name: My Landscape Keyboard
    landscape_split_percent: 0
    ...
    keys:
      - ......
```
以上例子代表：
竖屏时使用 `my_keyboard`；
横屏时使用 `my_landscape_keyboard`，并且不做自动分割。若 `landscape_split_percent` > 0，则分割显示。

---

## 6. 旧键名 → 新键名（v3 破坏性变更）

v3 是 breaking change，**不提供别名表**。完整映射表见 CHANGELOG，常用对照：

| 旧名 | 新名 |
| --- | --- |
| `space` / `space1` / `space2` | `spaceButton`（要显示方案名 → 条目写 `label: {source: schemaName}`） |
| `Return` / `Return1` / `Return2` | `returnButton` |
| `BackSpace` | `backspaceButton` |
| `Shift_L` | `shiftButton` |
| `Home` / `End` / `Insert` / `Delete` / `Page_Up` / `Page_Down` | `homeButton` / `endButton` / `insertButton` / `deleteButton` / `pageUpButton` / `pageDownButton` |
| `Left` / `Right` / `Up` / `Down` | `cursorLeftButton` / `cursorRightButton` / `cursorUpButton` / `cursorDownButton` |
| `select_all` / `cut` / `copy` / `paste` / `undo` / `redo` | `selectAllButton` / `cutButton` / `copyButton` / `pasteButton` / `undoButton` / `redoButton` |
| `Keyboard_symbols` / `Keyboard_number` / `Keyboard_default` / `Keyboard_mini` | `keyboardSymbolsButton` / `keyboardNumberButton` / `keyboardDefaultButton` / `keyboardMiniButton` |
| `liquid_keyboard_switch` / `_exit` / `_emoji` | `liquidKeyboardSwitchButton` / `liquidKeyboardExitButton` / `liquidKeyboardEmojiButton` |
| `clipboard_window` | `clipboardButton` |
| `IME_switch` / `_last` / `_next` | `imeSwitchButton` / `imeLastButton` / `imeNextButton` |
| `Schema_switch` / `Color_switch` / `Candidate_switch` / `Comment_switch` / `Hint_switch` | `schemaSwitchButton` / `colorSwitchButton` / `candidateSwitchButton` / `commentSwitchButton` / `hintSwitchButton` |
| `one_hand_switch_1/2/3` | `oneHandLeftButton` / `oneHandRightButton` / `oneHandSwitchButton` |
| `F4`（方案選單） | `schemaMenuButton` |
| `WebSearch` / `Search` | `webSearchButton` / `searchTextButton`（`SEARCH` 保留） |
| `Baidu` / `MoeDict` / `Zdic` / `Zdic2` / `Google` / `Wiki` / `TrimeApp` | `webSearchButton` + 搜索源列表 |
| `Share` | `shareButton` |
| `Zenkaku_Hankaku` / `Henkan` / `Punct_switch` / `Charset_switch` / `Mode_switch` | `eisuButton` / `henkanButton` / `punctSwitchButton` / `charsetSwitchButton` / `modeSwitchButton` |
| `space_R` / `space_R1` / `space_R2` | `languageSwitchButton`（它们是语种/方案切换键） |
| `BRIGHTNESS_UP` 等 15 个系统 Intent | **原样保留** |
| 字母/数字/标点 | `qButton`… / `digit0Button`… / `periodButton`…（§1.3） |
