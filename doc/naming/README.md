# doc/naming — 颜色与按键动作统一命名规范

**状态**：RFC / 纯文档提案，**不参与编译、不改变 App 行为**。
**分支**：`docs/unified-naming`（基于 `develop` 57edd25）。
**范围**：仅本目录 6 个新增文件；零 Kotlin、零布局、零 schema 改动。

## 这套规范解决什么

`develop` 里颜色和按键动作共享同一个病根：状态、坐标、键面文字、行为被压进一个名字。`hilited_key_back_color` 把状态写成前缀，`bh1~bh4` 把行号写成色名，`Return1` / `space1` 是为改一个 `label` 而造的替身键。二维布局一旦落地，绑行号的色名会整体失效。

方案是**四轴正交 + 一条语法 + 四个命名空间**：`when`（触发）、`action`（动词）、`class`/`state`（身份与状态）、`geometry`（位置）各自封闭，由组合表达，不再产生笛卡尔积字段。

命名采用 **B1**：token 字符串值的段内用 lowerCamel（`action.edit.moveCursor`、`capsLock`）；YAML 结构字段名与 `event` 名保留 snake_case（受 `ThemeYaml.kt` 的 `YamlNamingStrategy.SnakeCase` 约束，写成驼峰会被静默丢弃）。

## 文件

| 文件 | 内容 |
| --- | --- |
| `Naming.md` | 规范正文：决策依据、语法、共享词表、四轴模型、空格键例子、兼容与落地顺序 |
| `vocabulary.yaml` | **唯一事实来源**：受控词表 + 语法约束 + B1 边界声明 |
| `examples.yaml` | 空格键 / 候选栏 / 工具栏三键对照 + 二维几何；`current.*` 逐字引用仓库源码 |
| `legacy-map.yaml` | 兼容层：旧颜色名 / 自定义色标签 → 路径，旧键名 → 新 ID |
| `validate.py` | 可运行校验：词表自洽、路径语法、双向咬合、源码行号回查、文档计数一致性 |
| `README.md` | 本文件 |

## 运行校验

```bash
python3 -m pip install pyyaml       # 仅校验器需要
python3 doc/naming/validate.py
```

校验器零改动仓库其他文件；全部通过时退出码 0，并打印逐项结果。它做四件事：

1. 词表自洽：无重复、段字符符合 B1、缩写不连续大写、`class`/`state`/`context` 两两不交
2. 路径语法：`color.*` 可解析且各段命中词表、修饰段顺序合法、色值符合 `0xAARRGGBB`
3. **双向咬合**：按键的 `class` / `action` / `event` / `geometry` 与颜色路径修饰段互相对得上
4. 防脱节：`examples.yaml` 里每条 `current.*` 的 file/line/text 回查源文件；`Naming.md` 的计数标记反向核对词表与 `ColorKey.kt`

## 与 compiling 的关系

这批改动是文档、词表与校验脚本，**拉下来编译不会有任何行为变化**。可验证的是：

- `python3 doc/naming/validate.py` 全绿
- 工程正常编译（确认新增文件未破坏构建）

若目标是让新命名真正参与运行时解析（`ColorTable` / `KeyActionToken` / `KeyBehavior` 改动），属于后续阶段，见 `Naming.md` §7 的四步落地顺序。

## 参考

- [osfans/trime#732](https://github.com/osfans/trime/issues/732) 下一代主题设计规范（`preset_color` / `preset_key_style` / `class` / 实时预览）
- 本仓库 `app/src/main/java/com/osfans/trime/data/theme/ThemeYaml.kt`、`ColorKey.kt`、`ColorTable.kt`
