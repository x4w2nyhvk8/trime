<!--
SPDX-FileCopyrightText: 2015 - 2026 Rime community

SPDX-License-Identifier: GPL-3.0-or-later
-->

# 主题颜色命名统一方案（设计提案）

本目录是一份**只涉及颜色、不改动任何布局与运行时代码**的设计提案。
目标是给 `develop` 现有配色字段建立一套统一、正交、可预览、可迁移的命名规范。

> 状态：**提案（RFC）**，尚未实现。本目录内的文件不会参与编译，也不会改变
> App 的任何行为；`preset_keyboards` / `keys` / `grid` 等布局结构完全不在本提案范围内。

## 文件清单

| 文件 | 内容 |
| --- | --- |
| [ColorNaming.md](ColorNaming.md) | 规范正文：现状诊断、命名规则、三层 token、状态变体、颜色格式、迁移与兼容、落地路线 |
| [keyboard.color.tokens.json](keyboard.color.tokens.json) | 机器可读的 token 定义（W3C DTCG 2025.10 + `org.trime` 扩展），含元书浅/深两套示例主题与 12 条变体规则 |
| [develop-color.map.yaml](develop-color.map.yaml) | `develop` 全部 72 个颜色字段 → 新 token 的迁移映射表，含 18 个内置主题位置标签 |
| [preview.html](preview.html) | 由上述两份数据文件生成的设置页配色预览原型，可离线打开 |
| [validate.py](validate.py) | 校验脚本：token 引用链、映射覆盖率、对比度、原型数据一致性 |

## 本提案不做什么

- **不改动键盘布局**：`preset_keyboards`、按键数组、`width` 折行、以及未来二维化的
  `grid` 结构都不属于本提案。命名方案只是**不依赖**按键坐标，因此二维化时颜色无需跟着改。
- **不改动 Kotlin 运行时代码**：`ColorKey` / `ColorTable` / `ColorManager` /
  `ThemeDiagnostics` 保持原样。落地时如何接入现有抽象，见 [ColorNaming.md §7](ColorNaming.md)。
- **不删除旧字段**：存量主题通过别名层零改动继续工作。

## 快速校验

```bash
cd doc/color-naming
python3 validate.py
```

`preview.html` 直接用浏览器打开即可，无外部依赖、无需联网。

## 与仓库内既有命名分支的关系

远程已有 `theme/naming`、`theme/naming-final`、`v3-naming` 三个分支，它们已经做了
`<role>_[state_]<part>_color` 的扁平重命名与 `LegacyColorAliases` 兼容，方向与本提案一致。
本提案的差异在于：

1. 采用 **三层 token（palette → role → component）+ 状态选择器**，而非继续枚举扁平字段；
2. 把「按下 / 锁定 / 选中」当作**规则**而非字段，避免状态 × 部件的笛卡尔积继续膨胀；
3. 覆盖 `tongwenfeng.trime.yaml` 里 `bh1~4` / `bgn` / `benter` 等**按位置绑定颜色**的标签；
4. 附带方案元数据与标准预览场景，让设置页能现场渲染缩略图，替代易失真的静态 `demo.png`。

本提案基于 `develop` 新建分支，不继承上述分支夹带的非颜色改动（渐变、`KeyView`、
反馈重构、JNI、测试删减等），以便单独评审颜色命名这一件事。
