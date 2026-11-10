# 运行时落地（第 1 步）：让 `action.*` 动词真正参与解析

> 分支：`feat/naming-action-verbs`，基于纯文档分支 `docs/unified-naming` @ `577f3f2`。
> 这一阶段**不再是纯文档**：新增 4 个文件、修改 4 个文件，其中 `KeyAction.kt` 是唯一的生产代码接线点。
> 规范本身（`Naming.md`）仍不参与编译；运行时增量默认对存量主题零行为变化。

## 1. 为什么这样接

`KeyAction.init` 处理 `KeyActionToken.Plain` 时原本是四级回退：

```
token.token → presetKeys[name] → KeyCode.parse → nameToKeyCode → 字面字符
```

本阶段只在前两级之间插一层：

```
token.token → ActionVerb.resolve → 归一化成 PresetKey → 原有链
```

关键选择是**归一化成 `PresetKey`，而不是新增解析分支**。这样 39 个动词直接复用 `KeyAction` 既有的 `command / option / toggle / select / send / commit / text / slideCursor / slideDelete / shiftLock / repeatable / states` 全部处理逻辑，因此：

- 不改 `KeyBehavior`、不改 `TextKeyboard`、不改 `preset_keys`、不改 `trime-schema.json`
- 不改 `KeyAction` 里 `if (preset != null)` 之后的任何一行
- 非 `action.` 开头的 token 返回 `ActionResolution.NotAction`，原链一字不动 → **存量主题零回归**

接线代码只有一处（`KeyAction.kt` 的 `val preset`）：

```kotlin
val preset = when (val resolution = ActionVerb.resolve(token.token)) {
    is ActionResolution.Ok -> resolution.defaults
    ActionResolution.NotAction -> presetKeys[token.token]
    is ActionResolution.Unimplemented -> { Timber.w(...); PresetKey() }
    is ActionResolution.Invalid -> { Timber.w(...); PresetKey() }
}
```

## 2. 39 个动词的四类落地状态

逐条依据见 `ActionCatalog.yaml`（源：`preset_keys` 106 条 + `KeyCode` 键名表）。

| status | 数量 | 含义 | 例子 |
| --- | ---: | --- | --- |
| `available` | 20 | 现有一等实现，直接归一化 | `action.rime.toggleOption` → `toggle: ascii_mode` + `states` |
| `alias` | 14 | 由组合键或布尔开关等价表达 | `action.edit.undo` → `send: "{Control+z}"`；`action.edit.moveCursor` → `slideCursor: true` |
| `partial` | 1 | 有近似实现但需运行时确认 | `action.edit.delete` → `send: Delete` |
| `missing` | 4 | 同文当前无对应行为 | `selectText` / `hideKeyboard` / `glideType` / `expandPopup` |

这个分布修正了前几轮的一个判断：**同文不缺动词词表，缺的是 4 个具体能力**。其中 `glideType`（连续轨迹输入）、`expandPopup`（长按面板滑动选字）、`selectText`（选区 API）恰好是主流输入法比同文多出来的部分；`hideKeyboard` 则是一个应当补齐的基础动作。

另外 14 个 `alias` 值得单独看：它们能跑，但语义是「模拟按键」而非一等动词——`action.edit.undo` 实际是发 `{Control+z}`，App 不消费就没效果。把布尔开关（`slideCursor` / `slideDelete` / `repeatable` / `shiftLock`）升格为带参动词，是下一阶段真正要做的表达升级，本阶段只是先给它们一个统一名字。

## 3. 未实现动词不会静默生效

`ActionResolution.Unimplemented` 与 `Invalid` 都退化成 `PresetKey()`（全默认空值）：`send` / `command` / `label` 全空，`KeyAction` 走完原有链后得到一个 `code = 0` 的 inert 键，同时打一条 `Timber.w` 日志。

这是刻意设计：如果放任它落入「字面字符」分支，主题里写 `action.system.hideKeyboard` 会在键面上显示这串文字，看起来像配置生效了，实际什么都没做。宁可明确空键 + 日志，也不要静默错误。

## 4. B1 大小写硬校验落在哪

`ActionVerb.resolve` 用 `byId[verb]` **逐字查表**，不做 `lowercase()` 归一化：

- `action.modifier.toggleCapsLock` → `Ok`
- `action.modifier.toggleCapslock` → `Invalid`（`decodeEnumCaseInsensitive = true` 只兜枚举值，不兜字符串 token）
- `action.edit.move_cursor` → `Invalid`（词表里是 `moveCursor`）
- `action.text.backspace` → `Invalid`（`backspace` 属 `edit` 域）

同时校验 `domain` 一致性，段数必须是 3。`ActionVerbTest` 覆盖了以上全部情形。

## 5. 本阶段明确不做

1. **`params` 暂不接入**。`click:` 是 `KeyActionToken.Plain`（标量），`KeyAction` 构造函数拿不到 `params`。所以带参动词（`commit` / `insertText` / `toggleOption` / `switchKeyboard` / `run` / `date` 等）目前用**默认参数**归一化——例如 `action.text.commit` 默认上屏一个空格。现有 `KeyActionToken.Inline`（`commit` / `text` / `label`）已经能表达带参写法，所以常用场景不阻塞。要真正支持 `params`，需给 `TextKeyboard` 的键定义加字段并把它传进 `KeyAction` 构造函数。
2. **颜色侧别名层留给下一步**。`color.*` 路径归一化需要把 `legacy-map.yaml` 的数据放进 assets 或内嵌进 Kotlin，且 `ColorKey.from` 目前是枚举查表。它和 `trime_version` 分流一起做更合适。
3. **不加 `trime_version` 开关**。当前 `ActionVerb.resolve` 只对以 `action.` 开头的 token 生效，而存量主题不会写这种名字，天然零冲突。等颜色侧别名层进来（会与旧名发生重叠）时再统一加分流。
4. **不动几何与布局**。二维 `grid`、状态轴独立、设置页预览都不在本阶段。

## 6. 改动清单

| 文件 | 类型 | 内容 |
| --- | --- | --- |
| `app/src/main/java/com/osfans/trime/data/theme/naming/ActionVerb.kt` | 新增 | 39 项封闭枚举 + `resolve` + `ActionResolution`；纯 Kotlin，无 Android 依赖 |
| `app/src/main/java/com/osfans/trime/ime/keyboard/KeyAction.kt` | 修改 | 2 行 import + `val preset` 换成 `when` 块 |
| `app/src/test/java/com/osfans/trime/data/theme/naming/ActionVerbTest.kt` | 新增 | Kotest `BehaviorSpec`，覆盖枚举自洽、归一化、B1 校验、零回归 |
| `doc/naming/ActionCatalog.yaml` | 新增 | 39 动词 → 现有行为的落地目录 |
| `doc/naming/Runtime.md` | 新增 | 本文件 |
| `doc/naming/validate.py` | 修改 | 新增 4 项校验（目录 / Kotlin 枚举 / 接线 / missing 对齐）+ 改动白名单 |
| `doc/naming/README.md`、`Naming.md` | 修改 | 补充本阶段说明 |

## 7. 验证

```bash
python3 doc/naming/validate.py                      # 29 项，本地可跑
./gradlew :app:testDebugUnitTest --tests '*ActionVerbTest'
./gradlew assembleDebug                            # 或你平时的编译方式
```

沙箱**没有 Android SDK**，完整 Gradle 编译与 Kotest 运行需你本地执行。但已用独立 Kotlin 编译器 2.0.21 对新增代码做过编译验证：`ActionVerb.kt`、`ActionVerbTest.kt`（用 stub 替代 `PresetKey` 与 Kotest API），以及 `KeyAction.kt` 接线处的 `when` 表达式，均 **0 error** 通过。

这一步还抓到并修正了一个真实问题：Kotlin 原始字符串 `"""%1$s"""` 里的 `$` **仍会触发字符串模板插值**（编译报 `unresolved reference 's'`），现已改为 `"%1" + '$' + "s"`，`ActionVerbTest` 里有对应断言。

Python 侧 29 项校验已全部通过。Kotlin 代码刻意只用标准库 + 项目内 `PresetKey`，接线只改一个局部变量，编译风险集中在一处；若 Gradle 编译仍报错，把日志给我即可。

## 8. 已知限制

- `send: Delete` / `Page_Up` / `Home` / `End` 等键名能否被 `KeyCode.parse` 解析，需真机确认；`ActionCatalog.yaml` 已把 `delete` 标为 `partial`、相关动词标为 `alias`。
- `{Control+a}` 一类组合键依赖 `KeyCode` 的花括号语法，目标 App 不消费时无效果，这不是命名层能解决的。
- 未实现动词当前表现为「空键面 + 日志」，不是优雅降级；等对应能力补齐后把 `defaults` 从 `null` 换成实际 `PresetKey` 即可，无需改解析逻辑。
- `action_domain` 词表里的 `nav` / `device` 本阶段没有动词（`Page_Up`、`VOLUME_UP` 这类仍走旧 `preset_keys` 名字），留待后续补入。
