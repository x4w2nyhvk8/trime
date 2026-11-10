# 同文输入法「颜色 / 按键动作」统一命名规范（RFC）

> 状态：**纯文档 / 规范提案**，不参与编译、不改变 App 行为。
> 范围：仅 `doc/naming/` 下新增文件；零 Kotlin、零布局、零 schema 改动。
> 决策：命名方案 **B1**（token 段内 lowerCamel，YAML 字段名与 event 名保留 snake_case）。

<!-- COUNT:namespaces=4 -->
<!-- COUNT:action=39 -->
<!-- COUNT:event=13 -->
<!-- COUNT:color_enum=52 -->
<!-- COUNT:geometry=6 -->
<!-- COUNT:scenes=3 -->
<!-- COUNT:map_colors=49 -->
<!-- COUNT:map_keys=18 -->
<!-- COUNT:map_aliases=12 -->

## 1. 要解决的问题

`develop` 分支里，颜色与按键动作共享同一个病根：**多条正交的轴被压进一个名字**。

| 现象 | 颜色侧 | 按键侧 |
| --- | --- | --- |
| 状态写进名字前缀 | `hilited_key_back_color`、`off_key_*`、`on_key_*` | `Mode_switch`、`Henkan`、`Zenkaku_Hankaku` |
| 坐标写进名字 | `bh1`~`bh4`（第几行）、`bgn`/`bkg` | 无行列坐标，靠数组顺序 |
| 同一事物多个名字 | `keyboard_back_color` / `root_background` / `back_color` | `Return` / `Return1` / `Return2`、`space` / `space1` |
| 一个值管两件事 | `shadow_color` 既是文字阴影又是下缘阴影 | `functional: false` 既管行为分类又管颜色回退链 |
| 命名风格混用 | 全小写蛇形 | 大写下划线 / X11 keysym / PascalCase / 小驼峰 四种并存 |

结果是加一种状态或一种按键变体，字段数就要乘一次。内置颜色枚举已有 **52** 项，其中状态维度只有 `hilited_` / `on_` / `off_` 三个前缀；`preset_keys` 里 `Return1` 这类数字后缀，是为「改一个 `label`」而造的替身键。二维布局一旦落地，绑行号的 `bh1~bh4` 会整体失效。

## 2. 决策：方案 B1（驼峰）

**采用驼峰，但只用于 token 字符串值的段内**；YAML 结构字段名与 `event` 名保留 snake_case。

依据是 `app/src/main/java/com/osfans/trime/data/theme/ThemeYaml.kt`：

```kotlin
YamlConfiguration(
    strictMode = false,
    decodeEnumCaseInsensitive = true,
    yamlNamingStrategy = YamlNamingStrategy.SnakeCase,
    ...
)
```

Kotlin 模型层本来就是驼峰（`keyBackColor`、`hilitedKeyTextColor`、`longClick`、`swipeUp`、`hasMenu`、`slideCursor`），YAML 里的蛇形是**自动转换**出来的。因此：

| 位置 | 例子 | 能否驼峰 |
| --- | --- | --- |
| YAML 结构字段名 | `preset_color_schemes:`、`long_click:`、`key_back_color:` | ❌ 必须 snake_case |
| token 字符串值 | `"color.key.surface.space.capsLock"`、`"action.edit.moveCursor"` | ✅ 段内 lowerCamel |

`strictMode = false` 意味着在 YAML 里写 `keyBackColor:` 会被**静默丢弃**——配置看着写了却不生效，这是必须避开陷阱。

**为什么 event 是唯一例外**：`when:` 映射的键既是词表成员，又是 YAML 字段名。彻底驼峰（方案 B2）需要把 `when` 从 map 改成数组、把事件退到值里（`- { on: event.longClick, do: ... }`），并改动 `KeyBehavior` 的绑定代码，超出纯文档范围。B1 只留这一处不齐，换得零解析器改动。

**缩写词按普通词处理**：`showIme`、`capsLock`、`keyUrl`、`colSpan`，禁止连续大写（`showIME` / `CAPSLock` 都是错的）。因为 `decodeEnumCaseInsensitive = true` 只兜枚举值、不兜字符串 token，`capsLock` 写成 `capslock` 不会报错、只会静默失配——所以 `validate.py` 把「段必须逐字命中词表」作为**硬错误**。

## 3. 语法：一条语法、四个命名空间

```
<ns>.<domain>[.<sub>].<subject>[.<modifier>...]
```

| ns | 管什么 | 例子 |
| --- | --- | --- |
| `color` | 视觉外观 | `color.key.surface.space.pressed` |
| `action` | 动作动词 | `action.edit.moveCursor` |
| `event` | 触发时机（字段名形式，snake_case） | `event.long_click` |
| `key` | 行为 ID | `key.edit.space` |

`color` 路径展开为 `color.<domain>[.<sub>].<part>[.<class>][.<state|context>]`：

- `<domain>`：`key / preedit / candidate / popup / toolbar / clipboard / root / smartbar`
- `<sub>`（可选）：`comment / label / bar / item` —— 容纳 Rime 特有的信息层级，做成子树而非平级兄弟字段
- `<part>`：固定五个 `surface / content / hint / outline / elevation`
- 修饰段来自共享词表，最多两个，且 `class` 必须排在 `state`/`context` 之前

**特异性规则**：路径越长越优先，短路径是长路径的回退。`color.key.surface.space.pressed` 未定义 → 退 `color.key.surface.space` → 退 `color.key.surface`。这与 `fallback_colors` 的字符串链、CSS 层叠是同一套规则，也与 `preset_key_style.default` 的样式回退一致——**一条规则，不是三套**。

**值格式**：`"0xAARRGGBB"`，一律加引号。YAML 里 `#` 是注释符，所以不用元书的 `#RRGGBBAA`；`0x...` 不加引号可能被 YAML 1.1 当整数。

**状态放末段**（而非现状的前缀 `hilited_`、官方扁平重命名的中缀），理由是 `patch` 寻址能「一截就到」：

```yaml
# my.trime.custom.yaml
patch:
  "preset_color_schemes/yuanshu-light/color/key/surface/system/pressed": "0xFFFFFFFF"
```

## 4. 协调的实质：`class` 与 `state` 是两份共享词表

统一**不是**把颜色和动作的名字改得一样长，而是让它们在一条驱动链上咬合：

```
event.swipe_left → action.edit.moveCursor → state: pressed → color.key.surface.space.pressed
```

按键声明的 `class`、动作产生的 `state`，**就是颜色路径的末两段**。拼错一个词，两侧同时失效，所以词表不能各写一份，必须统一放在 `vocabulary.yaml` 并双向校验：

| 词表 | 成员 |
| --- | --- |
| `class` | `default / character / system / modifier / action / space` |
| `state` | `normal / pressed / selected / focused / disabled / capsLock / popup / trail` |
| `context`（同文特有） | `composing / ascii / paging / menu` |

`class` / `state` / `context` **不加命名空间前缀**：在按键里作属性值、在颜色里作修饰段，靠位置区分；前缀只留给跨字段引用、`patch`、诊断和词表本身。

一个术语歧义必须消掉：`preedit` 是「编码区」这一 **UI 域**（仓库里本来就有 `InlinePreeditMode.kt`），`composing` 是「输入中」这一 **上下文态**。`color.preedit.content` 与 `color.key.surface.action.composing` 分工不同，不可混用。

## 5. 四轴正交

| 轴 | 词表来源 | 消费方 |
| --- | --- | --- |
| `when`（触发） | 现成 **13** 个 `KeyBehavior`（`Key.kt` 逐一 `bind`，非注释表） | 事件分发 |
| `action`（动词） | 封闭枚举 **39** 项，带 `params` | `KeyAction` 解析 |
| `class` / `state`（身份与状态） | 共享词表 | 颜色路径修饰段 |
| `geometry`（位置） | **6** 个数值属性，独立第四轴，不进 `color.*` | 二维网格布局 |

`13 × 39 × 状态数` 由**组合**表达，而不是再加几千个字段——这正是避免重演 `bh1~bh4` 笛卡尔积的办法。

同文不缺触发时机：`double_click`、`lazy_double_click`、`combo`（并击）、`composing`/`ascii`/`paging`/`has_menu`（Rime 状态条件）反而是同类里最细的，比 Gboard / FlorisBoard 更多。真正缺的是**把布尔开关升格为动词**：`slideCursor: true` 本该是 `action.edit.moveCursor` 带 `axis` / `continuous` 参数，且能挂到任意手势、任意键上。

**动作枚举必须封闭**：新增动作是改代码 + 改词表，不是造一个新键名；否则会滑回 `Return1` 那种替身键。`params` 必须可 `patch`（如 `"preset_keys/.../when/swipe_left/params/continuous"`）。

## 6. 例子：主键盘空格键

完整逐字对照见 `examples.yaml`（`current.*` 的 file/line/text 由 `validate.py` 回查源文件）。摘要：

现状里，一个空格键的信息散在四处：`preset_keys:884` 管功能、`:1027` 管位置、`:161/164/153` 管颜色（隐式回退）、`:885` + `:701` 是固定条上的替身键 `space1`。布局行**根本没写颜色**，靠 `functional: false` 被判为普通键后回退到 `key_back_color`；而第三方主题 `tongwenfeng.trime.yaml:194-196` 自己造了 `bkg` / `tkg` / `benter` 标签——**同一个空格键，两套互不相通的颜色来源**。

统一后：

```yaml
# 按键侧（功能 + 位置 + 身份）
key.edit.space:
  class: space                                    # ← 共享词表，只管颜色身份
  action: { name: action.text.commit, params: { text: " " } }
  when:
    long_click:  { name: action.system.showImePicker }
    swipe_left:  { name: action.edit.moveCursor, params: { axis: x, continuous: true } }
    double_click:{ name: action.modifier.toggleCapsLock, produces_state: capsLock }
  repeatable: false
  label: ______
  geometry: { row: 3, col: 3, span: 3 }

# 颜色侧（值取自默认方案，6 位补 alpha）
color.key.surface.space:           "0xFFECEFF1"
color.key.content.space:           "0xFF37474F"
color.key.surface.space.pressed:   "0xFFD3D7DA"
color.key.surface.space.capsLock:  "0xFFD0D8E8"
```

`class: space` 是颜色路径的修饰段，动作产生的 `state` 是末段。`fixed_key_bar` 只需 `{ key: key.edit.space, label: 空格 }`，`space1` 消失（`Return1` / `Return2` / `BackSpace2` / `Shift_L3` 同理）。

**几何是独立第四轴**：它不属 `color.*`，但语法一致——`geometry.row` 同样是路径式、可 patch、可回退。`examples.yaml` 的 `layout_geometry` 给出 1D / 分体两种几何下**完全相同的颜色路径**。

## 7. 兼容与迁移

兼容层在 `legacy-map.yaml`：**49** 条旧颜色名、**12** 条自定义色标签、**18** 组旧键名映射，旧名全部作为别名保留，存量主题零改动。

解析顺序：

1. 新 token 路径（`color.*` / `action.*` / `key.*`）
2. 修饰段覆盖（长路径优先，短路径回退）
3. 旧名别名（`colors` / `aliases` / `keys`）
4. `fallback_colors` 运行时字符串链（`ColorTable.resolveRawSource`，带 `visited` 环检测）
5. 内置兜底链

用 `trime_version: 4` 分流，未声明时按旧名解析，声明 4 时优先走 token 路径但旧名仍可读。好消息是**三层引用、别名层、校验几乎不用写新解析器**：`fallback_colors` 里的 `candidate_text_color: text_color` 就是「键 → 另一个颜色名」的字符串链，`ThemeDiagnostics` 也会报告悬空引用和环。

落地顺序（每步独立可回滚）：

1. `trime_version` 分流 + 别名层（`ColorKey.from` / `ColorTable.resolveRawSource` 的字符串链挂一层归一化）
2. 设置页预览：与真机共用同一套 token 解析器和绘制代码，现场生成缩略图，零解析语义改动
3. 对齐 [osfans/trime#732](https://github.com/osfans/trime/issues/732) 的 `preset_color` + `preset_key_style` + `class` 骨架，从布局移除颜色参数（`class` 词表直接对应官方 `class`）
4. `slideCursor` / `slideDelete` / `repeatable` / `shift_lock` 四个布尔收编成 `action` 动词 + 参数；状态轴独立；最后才补 Glide / 扩展 popup 等新能力

## 8. 四条同文自己的东西

1. **Rime 语义进命名树**：`preedit`（编码区）、`candidate.comment`（码提示）、`candidate.label`（候选序号）是中文输入法独有的信息层级，做成子树而非平级字段。
2. **一切皆按键 + class**：[#732](https://github.com/osfans/trime/issues/732) 第 7 条——「候选栏中的选项、悬浮窗、点击按键弹出字符也抽象地看作具有特殊 class 的按键」。于是 `candidate` / `popup` / `toolbar` 复用 `color.key.*`，靠 `class` 区分，不必各有一套部件词表。
3. **路径即补丁地址**：第三方皮肤靠 `*.trime.custom.yaml` 对单个字段打补丁，倒逼命名必须是从粗到细、可精确切分的路径。这是把 CSS 选择器「编译」成路径的理由——选择器不能被 `patch`，路径可以。
4. **值可以是另一个名字**：`fallback_colors` + `ColorTable.resolveRawSource` 让颜色值不必是终结色值，可以是另一个 token 名，运行时沿链解析，主题作者还能在 custom 文件里往链上插一节点。命名在同文不只是标识符，是一张**可被 patch 改写的解析图**。

## 9. 依据

| 依据 | 位置 |
| --- | --- |
| 命名策略 `SnakeCase` + `strictMode=false` | `app/src/main/java/com/osfans/trime/data/theme/ThemeYaml.kt` |
| 52 项内置颜色枚举 | `app/src/main/java/com/osfans/trime/data/theme/ColorKey.kt` |
| 回退链解析（带环检测） | `app/src/main/java/com/osfans/trime/data/theme/ColorTable.kt` |
| 13 个 `KeyBehavior` 逐一绑定 | `app/src/main/java/com/osfans/trime/ime/keyboard/Key.kt`、`KeyBehavior.kt` |
| `android_keys` 的 `when` / `property` / `action` 三组分类 | `app/src/main/assets/shared/trime.yaml:823-858` |
| 空格键 / 候选栏 / 固定条真实定义 | `app/src/main/assets/shared/trime.yaml:884,885,1027,147-165,701` |
| 自定义色标签 `bkg/tkg/benter` | `app/src/main/assets/shared/tongwenfeng.trime.yaml:194-196` |
| 下一代主题设计规范讨论（`preset_color` / `class` / 实时预览） | [osfans/trime#732](https://github.com/osfans/trime/issues/732) |

## 10. 明确不做

- 不批量重命名存量 `preset_keys` 键名（它们是主题作者的事实 API）
- 不把颜色定义搬出 YAML（`patch` / `__include` / 注释丢不起；JSON 仅作工具交换格式）
- 不引入二维 `grid` 布局本身，只约定颜色与几何解耦后**配色表无需改动**
- **规范本身**不改任何 Kotlin / 布局 / schema。运行时落地放在独立分支 `feat/naming-action-verbs`，只接 `action.*` 动词归一化、保留旧路径，见 `Runtime.md`
