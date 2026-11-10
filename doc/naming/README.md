# doc/naming — 颜色与按键动作统一命名规范

**状态**：规范正文是 RFC；本分支在其上追加了**可选的运行时落地增量**。
**分支**：`feat/naming-action-verbs`（基于纯文档分支 `docs/unified-naming` @ `577f3f2`）。
**范围**：`docs/unified-naming` 只有本目录 6 个文件、零 Kotlin 改动；本分支另加 `ActionVerb.kt` / `ActionVerbTest.kt`，并只修改 `KeyAction.kt` 一个生产文件（详见 `Runtime.md`）。布局、schema、其余 Kotlin 零改动。

## 这套规范解决什么

`develop` 里颜色和按键动作共享同一个病根：状态、坐标、键面文字、行为被压进一个名字。`hilited_key_back_color` 把状态写成前缀，`bh1~bh4` 把行号写成色名，`Return1` / `space1` 是为改一个 `label` 而造的替身键。二维布局一旦落地，绑行号的色名会整体失效。

方案是**四轴正交 + 一条语法 + 四个命名空间**：`when`（触发）、`action`（动词）、`class`/`state`（身份与状态）、`geometry`（位置）各自封闭，由组合表达，不再产生笛卡尔积字段。

命名采用 **B1**：token 字符串值的段内用 lowerCamel（`action.edit.moveCursor`、`capsLock`）；YAML 结构字段名与 `event` 名保留 snake_case（受 `ThemeYaml.kt` 的 `YamlNamingStrategy.SnakeCase` 约束，写成驼峰会被静默丢弃）。

## 文件

| 文件 | 内容 |
| --- | --- |
| `Naming.md` | 规范正文：决策依据、语法、共享词表、四轴模型、空格键例子、兼容与落地顺序 |
| `Runtime.md` | **运行时落地第 1 步**：接线点、39 动词分类、不做的事、已知限制 |
| `vocabulary.yaml` | **唯一事实来源**：受控词表 + 语法约束 + B1 边界声明 |
| `examples.yaml` | 空格键 / 候选栏 / 工具栏三键对照 + 二维几何；`current.*` 逐字引用仓库源码 |
| `ActionCatalog.yaml` | 39 个封闭动词 → 同文现有行为的落地目录（`available` / `alias` / `partial` / `missing`） |
| `legacy-map.yaml` | 兼容层：旧颜色名 / 自定义色标签 → 路径，旧键名 → 新 ID |
| `validate.py` | 29 项可运行校验，含 Kotlin 枚举与词表 / 目录三方一致性 |
| `README.md` | 本文件 |

运行时侧另有 `app/src/main/java/com/osfans/trime/data/theme/naming/ActionVerb.kt`（封闭枚举 + 归一化）与 `app/src/test/java/com/osfans/trime/data/theme/naming/ActionVerbTest.kt`（Kotest 单测）。

## 运行校验

```bash
python3 -m pip install pyyaml       # 仅校验器需要
python3 doc/naming/validate.py      # 29 项，退出码 0 即全绿
```

校验器做五件事：

1. 词表自洽：无重复、段字符符合 B1、缩写不连续大写、`class`/`state`/`context` 两两不交
2. 路径语法：`color.*` 可解析且各段命中词表、修饰段顺序合法、色值符合 `0xAARRGGBB`
3. **双向咬合**：按键的 `class` / `action` / `event` / `geometry` 与颜色路径修饰段互相对得上
4. 防脱节：`examples.yaml` 的 `current.*` 回查源文件；`Naming.md` 计数标记反查词表与 `ColorKey.kt`
5. 运行时一致性：`ActionCatalog.yaml` ↔ `ActionVerb.kt` ↔ `vocabulary.yaml` 的 39 个动词、domain、`defaults = null` 双向核对，并确认 `KeyAction` 接线保留了旧路径

## 与 compiling 的关系

纯文档分支 `docs/unified-naming` 编译零行为变化。本分支**让 `action.*` 动词真正参与运行时解析**：`KeyAction.init` 在原有 `presetKeys` 查表前加一层归一化，非 `action.` token 走原链，存量主题零回归。可验证的是：

- `python3 doc/naming/validate.py` 全绿（沙箱已跑通）
- `./gradlew :app:testDebugUnitTest --tests '*ActionVerbTest'`
- 工程正常编译（`./gradlew assembleDebug` 或你平时的命令）

沙箱没有 Android SDK，Kotlin 编译与 JVM 单测需你本地执行。接线只改一个局部变量，编译风险集中在一处；报错把日志给我即可。

## 参考

- [osfans/trime#732](https://github.com/osfans/trime/issues/732) 下一代主题设计规范（`preset_color` / `preset_key_style` / `class` / 实时预览）
- 本仓库 `app/src/main/java/com/osfans/trime/data/theme/ThemeYaml.kt`、`ColorKey.kt`、`ColorTable.kt`、`model/PresetKey.kt`、`ime/keyboard/KeyAction.kt`
