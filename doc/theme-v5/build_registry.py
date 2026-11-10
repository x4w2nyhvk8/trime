#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从引擎源码生成「规范色表」color-registry.yaml。

事实来源不是任何一份主题，而是：
  ColorKey.kt      52 个规范色键 + 49 条内置回退链
  GeneralStyle.kt  58 个 style 字段名（KNOWN_KEYS）
  KeyBehavior.kt   13 个触发时机
  ColorTable.kt    三级解析顺序与值域（颜色 / 图片）

用法：
  python3 build_registry.py [--source /path/to/ColorKey.kt] [--check]

--check 只做自检、不写文件；退出码 0 表示源码解析、覆盖度、
路径唯一性、回退链完整性与无环性全部通过。
"""
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parent
REGISTRY = ROOT / "color-registry.yaml"
SOURCE_REL = pathlib.Path(
    "app/src/main/java/com/osfans/trime/data/theme/ColorKey.kt"
)


def find_source() -> pathlib.Path:
    """从脚本所在目录向上找仓库根；兼容工作区与仓库内两种布局。"""
    here = pathlib.Path(__file__).resolve().parent
    for base in (here, here.parent, here.parent.parent,
                 pathlib.Path("/root/trime-repo")):
        p = base / SOURCE_REL
        if p.is_file():
            return p
    return here / SOURCE_REL

# ── 规范路径表：52 个 ColorKey 枚举，逐条给出唯一路径 ──────────────────
# 规则：domain 按 UI 归属；part 按视觉对象；持续态(active) 与瞬时态(pressed)
# 分列两段，顺序固定；class 在 state 之前。同义枚举不进本表，进 SYNONYMS。
# 依据见 doc/naming/Naming.md「以源码为锚的规范色表」一章。
CANONICAL = {
    # root / keyboard / preedit ------------------------------------------
    "back_color":                       "root.background",
    "border_color":                     "root.outline",
    "shadow_color":                     "root.elevation",
    "keyboard_back_color":              "keyboard.background",
    "liquid_keyboard_background":       "toolbar.background",
    "text_back_color":                  "preedit.background",
    "text_color":                       "preedit.foreground",
    "hilited_text_color":               "preedit.foreground.selected",
    "hilited_back_color":               "preedit.background.selected",
    # candidate ----------------------------------------------------------
    "candidate_background":             "candidate.background",
    "candidate_border_color":           "candidate.outline",
    "candidate_separator_color":        "candidate.separator",
    "candidate_text_color":             "candidate.foreground",
    "hilited_candidate_back_color":     "candidate.background.selected",
    "hilited_candidate_text_color":     "candidate.foreground.selected",
    "hilited_candidate_button_color":   "candidate.bar.background",
    "comment_text_color":               "candidate.comment.foreground",
    "hilited_comment_text_color":       "candidate.comment.foreground.selected",
    "label_color":                      "candidate.label.foreground",
    "hilited_label_color":              "candidate.label.foreground.selected",
    # key 常态 / 按下态 ---------------------------------------------------
    "key_back_color":                   "key.background",
    "key_border_color":                 "key.outline",
    "key_text_color":                   "key.foreground",
    "key_symbol_color":                 "key.hint",
    "hilited_key_back_color":           "key.background.pressed",
    "hilited_key_border_color":         "key.outline.pressed",
    "hilited_key_text_color":           "key.foreground.pressed",
    "hilited_key_symbol_color":         "key.hint.pressed",
    # key 可切换键：源码 OFF_/ON_ 是持续态，HILITED_ 是瞬时态（Key.kt:340-341）。
    # 不用 on/off 作 token：YAML 1.1 把二者解析为布尔字面量，本身就是歧义；
    # 改用 active/inactive，与词表已有 active 对齐。
    "off_key_back_color":               "key.background.modifier.inactive",
    "off_key_border_color":             "key.outline.modifier.inactive",
    "off_key_text_color":               "key.foreground.modifier.inactive",
    "off_key_symbol_color":             "key.hint.modifier.inactive",
    "on_key_back_color":                "key.background.modifier.active",
    "on_key_border_color":              "key.outline.modifier.active",
    "on_key_text_color":                "key.foreground.modifier.active",
    "on_key_symbol_color":              "key.hint.modifier.active",
    "hilited_off_key_back_color":       "key.background.modifier.inactive.pressed",
    "hilited_off_key_border_color":     "key.outline.modifier.inactive.pressed",
    "hilited_off_key_text_color":       "key.foreground.modifier.inactive.pressed",
    "hilited_off_key_symbol_color":     "key.hint.modifier.inactive.pressed",
    "hilited_on_key_back_color":        "key.background.modifier.active.pressed",
    "hilited_on_key_border_color":      "key.outline.modifier.active.pressed",
    "hilited_on_key_text_color":        "key.foreground.modifier.active.pressed",
    "hilited_on_key_symbol_color":      "key.hint.modifier.active.pressed",
    # popup / 长文本 ------------------------------------------------------
    "popup_back_color":                 "popup.background",
    "popup_text_color":                 "popup.foreground",
    "hilited_popup_back_color":         "popup.background.pressed",
    "hilited_popup_text_color":         "popup.foreground.pressed",
    "long_text_back_color":             "key.longText.background",
    "long_text_color":                  "key.longText.foreground",
}

# 源码里语义重合的两代枚举：显式声明为同义词，不隐式合并。
SYNONYMS = {
    "root_background": {
        "path": "root.background",
        "evidence": "ColorKey.builtinFallbackColors: ROOT_BACKGROUND -> BACK_COLOR",
    },
    "keyboard_background": {
        "path": "keyboard.background",
        "evidence": "ColorKey.builtinFallbackColors: KEYBOARD_BACKGROUND -> KEYBOARD_BACK_COLOR",
    },
}

# ── 补充色码：源码 52 枚举之外、语义必需且可考据的槽位 ──────────────────
# 每条必须有 origin（参照实现）与 reason（为何源码缺、为何必须补）。
# status: proposed = V5 解析器需支持；当前 Trime 会忽略，不产生视觉回退。
SUPPLEMENTARY = {
    "key.hint.up": {
        "origin": "foxy keyHintTextTopColor / hintUp；元书 keyHintTextTopColor",
        "reason": "上滑提示文字按方位独立取色，源码只有一个 key_symbol_color，无法表达四方位",
    },
    "key.hint.down": {
        "origin": "foxy keyHintTextBottomColor",
        "reason": "同上，下滑提示",
    },
    "key.hint.left": {
        "origin": "foxy keyHintTextLeftColor",
        "reason": "同上，左滑提示",
    },
    "key.hint.right": {
        "origin": "foxy keyHintTextRightColor",
        "reason": "同上，右滑提示",
    },
    "key.foreground.disabled": {
        "origin": "AOSP Keyboard.Key isModifier/isSticky 状态机；MD3 disabled 层级",
        "reason": "源码无禁用态色键，主流输入法在按键不可用时降透明度，纯 YAML 只能靠独立槽位",
    },
    "key.background.disabled": {
        "origin": "AOSP state-list selector disabled 态",
        "reason": "与 foreground.disabled 成对，避免只改文字不改底色",
    },
    "toolbar.foreground.active": {
        "origin": "foxy 工具栏图标「单手/语音/分体/尺寸激活」",
        "reason": "源码 toolbar 只有 liquid_keyboard_background，激活态无槽位；state.active 已入词表但无色码",
    },
    "candidate.foreground.focused": {
        "origin": "MD3 focus 层级；Fcitx5 SelectedColor",
        "reason": "selected 表示已高亮候选，focused 表示键盘导航焦点，二者可同时存在，源码合并了",
    },
    "popup.background.selected": {
        "origin": "foxy 长按气泡选中项",
        "reason": "pressed 是手指按下瞬间，selected 是气泡内当前选中项，源码只提供 hilited_popup_* 一个态",
    },
    "root.foreground": {
        "origin": "元书 Foreground / foxy Text / MD3 onSurface",
        "reason": "源码 key_text_color 被 TabUi / PaginationUi 当通用图标 tint 复用，却没有全局前景槽位",
    },
}

# 主题自定义字段与同义旧名：不属于引擎规范字段，只能作别名。
# kind: synonym = 同一 UI 对象的旧拼写；theme_defined = 主题自造的分组标签。
ALIASES = {
    "preview_back_color":        {"path": "popup.background", "kind": "synonym",
                                  "evidence": "旧版悬浮预览字段名，与 popup_back_color 同义"},
    "preview_text_color":        {"path": "popup.foreground", "kind": "synonym",
                                  "evidence": "旧版悬浮预览字段名，与 popup_text_color 同义"},
    "key_hint_color":            {"path": "key.hint", "kind": "synonym",
                                  "evidence": "与 key_symbol_color 同义，均绘制键面辅助小字"},
    "functional_back_color":     {"path": "key.background.system", "kind": "theme_defined"},
    "functional_text_color":     {"path": "key.foreground.system", "kind": "theme_defined"},
    "hilited_functional_back_color": {"path": "key.background.system.pressed", "kind": "theme_defined"},
    "hilited_functional_text_color": {"path": "key.foreground.system.pressed", "kind": "theme_defined"},
    "candidate_back_color":      {"path": "candidate.background", "kind": "synonym"},
    "candidate_shadow_color":    {"path": "candidate.elevation", "kind": "theme_defined"},
    "hilited_candidate_shadow_color": {"path": "candidate.elevation.selected", "kind": "theme_defined"},
    "hilited_shadow_color":      {"path": "preedit.elevation", "kind": "theme_defined"},
    "bc1": {"path": "key.background.modifier", "kind": "theme_defined"},
    "tc1": {"path": "key.foreground.modifier", "kind": "theme_defined"},
    "bc2": {"path": "key.background.system", "kind": "theme_defined"},
    "tc2": {"path": "key.foreground.system", "kind": "theme_defined"},
    "bc3": {"path": "key.background.space", "kind": "theme_defined"},
    "tc3": {"path": "key.foreground.space", "kind": "theme_defined"},
    "bc4": {"path": "key.background.action", "kind": "theme_defined"},
    "tc4": {"path": "key.foreground.action", "kind": "theme_defined"},
    "baoe": {"path": "key.background.modifier", "kind": "theme_defined"},
    "taoe": {"path": "key.foreground.modifier", "kind": "theme_defined"},
}

# 去歧义硬规则（同时写进 Naming.md，校验器逐条落实）
RULES = [
    ("R1", "一个可独立赋值的引擎色键占且仅占一条规范路径；禁止隐式合并"),
    ("R2", "语义重合的两代枚举必须在 synonyms 中显式声明并附源码证据，不得静默共用路径"),
    ("R3", "主题自定义字段只能进 aliases 或 class 修饰段，不得进 canonical"),
    ("R4", "持续态(on/off/active) 与瞬时态(pressed/selected) 正交，顺序固定：class -> 持续态 -> 瞬时态"),
    ("R5", "49 条内置回退链全部路径化显式写入 fallbacks，主题未写字段不依赖运行时隐式行为"),
    ("R6", "值域二元：0xAARRGGBB 或图片文件名(.png/.webp/.jpg/.gif)，二者不可混用"),
]


def parse_colorkey(src: pathlib.Path):
    text = src.read_text(encoding="utf-8")
    enum_block = text.split("enum class ColorKey {")[1].split(";")[0]
    enums = [e.lower() for e in re.findall(r"^\s{4}([A-Z_]+),", enum_block, re.M)]
    fb_block = text.split("builtinFallbackColors")[1].split("\n            )")[0]
    chains = [(a.lower(), b.lower())
              for a, b in re.findall(r"([A-Z_]+) to ([A-Z_]+)", fb_block)]
    return enums, chains, hashlib.sha256(text.encode()).hexdigest()


def parse_style_keys(src: pathlib.Path):
    text = src.read_text(encoding="utf-8")
    block = re.search(r"KNOWN_KEYS.*?setOf\((.*?)\n\s*\)", text, re.S).group(1)
    return re.findall(r'"([a-z_]+)"', block)


def path_of(key: str) -> str | None:
    if key in CANONICAL:
        return CANONICAL[key]
    if key in SYNONYMS:
        return SYNONYMS[key]["path"]
    return None


def build(source: pathlib.Path):
    enums, chains, sha = parse_colorkey(source)
    style_keys = parse_style_keys(source.parent / "model" / "GeneralStyle.kt")
    issues = []

    # 覆盖度：52 枚举必须全部有归属
    unassigned = [k for k in enums if path_of(k) is None]
    if unassigned:
        issues.append(f"枚举未分配路径: {unassigned}")
    extra = [k for k in set(CANONICAL) | set(SYNONYMS) if k not in enums]
    if extra:
        issues.append(f"路径表含非源码枚举: {extra}")

    # 唯一性：canonical 内不允许两条不同枚举共占一条路径
    seen = {}
    for k, p in CANONICAL.items():
        if p in seen:
            issues.append(f"路径冲突: {seen[p]} 与 {k} 同为 {p}")
        seen[p] = k

    # 回退链显式化：两端都能转路径；自环需注明
    fallbacks, self_loops = {}, []
    for a, b in chains:
        pa, pb = path_of(a), path_of(b)
        if pa is None or pb is None:
            issues.append(f"回退链端点无路径: {a} -> {b}")
            continue
        if pa == pb:
            self_loops.append(f"{a} -> {b} (同义，均 {pa})")
            continue
        if pa in fallbacks and fallbacks[pa] != pb:
            issues.append(f"回退链起点冲突: {pa} 同时指向 {fallbacks[pa]} 与 {pb}")
        fallbacks[pa] = pb

    # 无环检测
    def acyclic() -> bool:
        for start in fallbacks:
            cur, visited = start, set()
            while cur in fallbacks:
                if cur in visited:
                    return False
                visited.add(cur)
                cur = fallbacks[cur]
        return True
    if not acyclic():
        issues.append("fallbacks 存在环")

    # 所有路径的段必须逐字命中受控词表（唯一事实来源 vocabulary.yaml）
    voc = yaml.safe_load((ROOT / "vocabulary.yaml").read_text(encoding="utf-8"))["vocabulary"]
    domains = set(voc["color_domain"])
    parts = set(voc["color_part"])
    allowed = (set(voc["color_sub"]) | set(voc["class"]) | set(voc["state"])
               | set(voc["context"]) | set(voc["direction"]) | parts)

    def check_path(label: str, path: str) -> None:
        segs = path.split(".")
        if not re.match(r"^[a-z][a-zA-Z0-9]*(\.[a-z][a-zA-Z0-9]*)+$", path):
            issues.append(f"{label} 路径语法非法: {path}")
            return
        if segs[0] not in domains:
            issues.append(f"{label} domain 未命中词表: {segs[0]}")
        bad = [s for s in segs[1:] if s not in allowed]
        if bad:
            issues.append(f"{label} 段未命中词表: {path} -> {bad}")
        if not any(s in parts for s in segs):
            issues.append(f"{label} 缺部件段: {path}")

    for k, p in list(CANONICAL.items()) + [(k, v["path"]) for k, v in SYNONYMS.items()]:
        check_path(f"canonical[{k}]", p)
    for k in SUPPLEMENTARY:
        check_path(f"supplementary[{k}]", k)
    for a, meta in ALIASES.items():
        check_path(f"aliases[{a}]", meta["path"])
    for a, b in fallbacks.items():
        check_path(f"fallbacks[{a}]", a)
        check_path(f"fallbacks[{b}]", b)

    # 补充色码不得与 canonical 撞路径
    for p in SUPPLEMENTARY:
        if p in CANONICAL.values():
            issues.append(f"补充色码与规范路径冲突: {p}")

    # 与样本翻译表 v5-map.yaml 的差异（迁移对照，不改动样本产物）
    migration = {}
    map_path = ROOT / "v5-map.yaml"
    if map_path.exists():
        old = yaml.safe_load(map_path.read_text(encoding="utf-8"))["color_map"]
        for k, p in old.items():
            new = path_of(k)
            if new and new != p:
                migration[k] = {"sample_v5": p, "registry": new}

    data = {
        "source": {
            "color_key_file": "app/src/main/java/com/osfans/trime/data/theme/ColorKey.kt",
            "color_key_sha256": sha,
            "enum_count": len(enums),
            "fallback_chain_count": len(chains),
            "style_file": "app/src/main/java/com/osfans/trime/data/theme/model/GeneralStyle.kt",
            "style_key_count": len(style_keys),
            "behavior_count": 13,
            "action_verb_count": 39,
            # 源码事实自包含：校验器无需读仓库即可核对覆盖度
            "color_keys": enums,
            "fallback_chains": [f"{a} -> {b}" for a, b in chains],
        },
        "canonical": CANONICAL,
        "synonyms": SYNONYMS,
        "fallbacks": fallbacks,
        "supplementary": {k: {**v, "status": "proposed"} for k, v in SUPPLEMENTARY.items()},
        "aliases": ALIASES,
        "style_fields": style_keys,
        "rules": [{"id": i, "text": t} for i, t in RULES],
        "value_types": {"color": "0xAARRGGBB",
                        "image": [".png", ".webp", ".jpg", ".gif"]},
        "migration": migration,
        "_meta": {
            "canonical_count": len(CANONICAL),
            "synonym_count": len(SYNONYMS),
            "fallback_count": len(fallbacks),
            "self_loop_count": len(self_loops),
            "supplementary_count": len(SUPPLEMENTARY),
            "alias_count": len(ALIASES),
            "migration_count": len(migration),
            "issues": issues,
        },
    }
    return data, issues


def emit(d: dict) -> str:
    L = []
    A = L.append
    A("# SPDX-License-Identifier: GPL-3.0-or-later")
    A("# 同文输入法规范色表 —— 唯一事实来源是引擎源码，不是任何一份主题。")
    A("# 由 build_registry.py 自动生成，请勿手改数值；改规则请改脚本。")
    A("# 规范正文：doc/naming/Naming.md「以源码为锚的规范色表」一章。")
    A("")
    A("version: 1")
    A("source:")
    for k, v in d["source"].items():
        if isinstance(v, list):
            A(f"  {k}:")
            for x in v:
                A(f'    - "{x}"')
        else:
            A(f"  {k}: {json.dumps(v, ensure_ascii=False)}")
    A("")
    A("# 去歧义硬规则（validate.py 逐条校验）")
    A("rules:")
    for r in d["rules"]:
        A(f'  - {{ id: "{r["id"]}", text: "{r["text"]}" }}')
    A("")
    A("# R1/R2：52 个引擎色键的规范路径。同义两代枚举另列 synonyms。")
    A("canonical:")
    for k, v in d["canonical"].items():
        A(f'  {k}: "{v}"')
    A("")
    A("# R2：语义重合的枚举，显式声明并附源码证据，杜绝静默合并。")
    A("synonyms:")
    for k, v in d["synonyms"].items():
        A(f'  {k}: {{ path: "{v["path"]}", evidence: "{v["evidence"]}" }}')
    A("")
    A(f'# R5：源码 {d["source"]["fallback_chain_count"]} 条内置回退链的路径化全集。')
    A("fallbacks:")
    for k, v in d["fallbacks"].items():
        A(f'  "{k}": "{v}"')
    A("")
    A("# 补充色码：源码 52 枚举之外、语义必需且有参照实现依据的槽位。")
    A("# status: proposed —— 需 V5 解析器支持；当前 Trime 忽略，不影响回退。")
    A("supplementary:")
    for k, v in d["supplementary"].items():
        A(f'  "{k}":')
        A(f'    origin: "{v["origin"]}"')
        A(f'    reason: "{v["reason"]}"')
        A(f'    status: {v["status"]}')
    A("")
    A("# R3：主题自定义字段 / 同义旧名，只能作别名，不进 canonical。")
    A("aliases:")
    for k, v in d["aliases"].items():
        ev = f', evidence: "{v["evidence"]}"' if "evidence" in v else ""
        A(f'  {k}: {{ path: "{v["path"]}", kind: {v["kind"]}{ev} }}')
    A("")
    A("# 源码 style 字段全集（几何/字体，与颜色共享对象词，不进 color.*）")
    A("style_fields:")
    for k in d["style_fields"]:
        A(f"  - {k}")
    A("")
    A("# R6：值域二元")
    A("value_types:")
    A('  color: "0xAARRGGBB"')
    A("  image: [.png, .webp, .jpg, .gif]")
    A("")
    # 规范色表只含源码事实；样本翻译表的路径差异另存 migration.yaml，
    # 避免把某一份主题的信息带进规范、也保证两分支 registry 字节一致。
    return "\n".join(L) + "\n"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", type=pathlib.Path, default=find_source())
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    if not args.source.exists():
        print(f"找不到源码: {args.source}（用 --source 指定 ColorKey.kt）", file=sys.stderr)
        return 2
    d, issues = build(args.source)
    m = d["_meta"]
    print(f"枚举 {d['source']['enum_count']} / canonical {m['canonical_count']}"
          f" / synonyms {m['synonym_count']} / 回退链 {m['fallback_count']}"
          f" (+{m['self_loop_count']} 同义自环)")
    print(f"补充色码 {m['supplementary_count']} / 别名 {m['alias_count']}"
          f" / 迁移对照 {m['migration_count']} 条")
    if issues:
        for x in issues:
            print("  !! " + x, file=sys.stderr)
        return 1
    print("自检通过：覆盖度 / 唯一性 / 回退链 / 无环 / 语法 全部 OK")
    if not args.check:
        REGISTRY.write_text(emit(d), encoding="utf-8")
        print(f"已写出 {REGISTRY.name}（纯源码事实，两分支字节一致）")
        mig = d["migration"]
        if mig:
            ml = ["# SPDX-License-Identifier: GPL-3.0-or-later",
                  "# 样本侧信息：v5-map.yaml 的旧路径与规范色表的差异对照。",
                  "# 由 build_registry.py 生成；不属于规范色表，V5 解析器接入时按此切换。",
                  "", "migration:"]
            for k, v in mig.items():
                ml.append(f'  {k}: {{ sample_v5: "{v["sample_v5"]}", '
                          f'registry: "{v["registry"]}" }}')
            (ROOT / "migration.yaml").write_text("\n".join(ml) + "\n",
                                                  encoding="utf-8")
            print(f"已写出 migration.yaml（{len(mig)} 条，样本侧）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
