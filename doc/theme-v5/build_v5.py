#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Trime V5 主题方案构建器。

从 ss.v3.trime.yaml（方块主题，config_version 3.0）按 v5-map.yaml 的规则生成：
  ss.v5.trime.yaml            V5 主题方案（路径式颜色 / 行为 ID / 行数组布局，
                              固定 day / night 两套配色）
  parked-schemes.yaml         超出两套的 V3 配色完整归档（信息不丢失）
  ss.v3.generated.trime.yaml  反向降级产物，可导入当前 Trime
  build-report.json           转换统计与信息等价结论

用法：python3 build_v5.py [--check]
"""
import argparse
import copy
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent
V3 = ROOT / "ss.v3.trime.yaml"
MAP = ROOT / "v5-map.yaml"
V5 = ROOT / "ss.v5.trime.yaml"
V3_GEN = ROOT / "ss.v3.generated.trime.yaml"
PARKED = ROOT / "parked-schemes.yaml"
REPORT = ROOT / "build-report.json"

EPS = 1e-6
META_KEYS = {"name", "author", "dark_scheme", "light_scheme", "color_format"}


def hx(v):
    """V3 十进制 AARRGGBB → V5 字符串 0xAARRGGBB。"""
    if isinstance(v, int):
        return f"0x{v & 0xFFFFFFFF:08X}"
    return str(v)


def unhex(s):
    return int(str(s), 16) if str(s).startswith("0x") else int(s)


def q(s):
    """YAML 安全字符串字面量。"""
    return json.dumps(str(s), ensure_ascii=False)


def load_yaml(path):
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def passthrough_block(text):
    """逐字取出与命名无关的区块原文（含锚点定义，保证 YAML 别名可解析）。"""
    lines = text.split("\n")
    start = next(i for i, l in enumerate(lines) if l.startswith("# 锚点定义"))
    end = next(i for i, l in enumerate(lines) if l.startswith("fallback_colors:"))
    return "\n".join(lines[start:end]).rstrip()


# ── 颜色 ────────────────────────────────────────────────────────
def build_colors(d3, m):
    """返回 (schemes_v5, legacy_colors, stats)。"""
    cmap, lmap = m["color_map"], m["label_map"]
    schemes, legacy, stats = {}, {}, {"merged": 0, "conflicts": 0, "labels": 0}

    for sid, s3 in d3["preset_color_schemes"].items():
        # path -> [(legacy_name, value)]，检测同路径多值
        buckets = defaultdict(list)
        for field, val in s3.items():
            if field in META_KEYS:
                continue
            if field in cmap:
                buckets[cmap[field]].append((field, val))
            elif field in lmap:
                buckets[lmap[field]].append((field, val))
                stats["labels"] += 1
            else:
                legacy.setdefault(sid, {})[field] = val

        color, unresolved = {}, {}
        for path, pairs in buckets.items():
            values = {v for _, v in pairs}
            if len(values) == 1:
                color[path] = hx(next(iter(values)))
                if len(pairs) > 1:
                    stats["merged"] += len(pairs) - 1
            else:
                # 同路径多值：布局标签优先（实际生效的视觉），其余进 legacy_colors
                label_pairs = [(f, v) for f, v in pairs if f in lmap]
                other = [(f, v) for f, v in pairs if f not in lmap]
                chosen = label_pairs[0] if label_pairs else other[0]
                color[path] = hx(chosen[1])
                for f, v in pairs:
                    if (f, v) != chosen:
                        legacy.setdefault(sid, {})[f] = v
                        unresolved.setdefault(path, []).append(f)
                stats["conflicts"] += len(pairs) - 1

        schemes[sid] = {
            "name": s3.get("name"),
            "author": s3.get("author"),
            "appearance": "dark" if sid == "night" or "dark_scheme" not in s3 else "light",
            "color": dict(sorted(color.items())),
        }
        if unresolved:
            legacy.setdefault(sid, {})["_conflict_paths"] = unresolved
    return schemes, legacy, stats


def build_fallbacks(d3, m):
    """旧名回退链 → 路径式回退链。"""
    cmap, lmap = m["color_map"], m["label_map"]
    out, dropped = {}, []
    for k, v in (d3.get("fallback_colors") or {}).items():
        a = cmap.get(k, lmap.get(k))
        b = cmap.get(v, lmap.get(v))
        if a and b:
            out[a] = b
        else:
            dropped.append(f"{k} -> {v}")
    return dict(sorted(out.items())), dropped


def build_pairs(d3):
    """双向 dark_scheme/light_scheme → 显式 pairing，修正 Spring→night 悬空。"""
    pcs = d3["preset_color_schemes"]
    light_of_dark = defaultdict(list)
    for k, v in pcs.items():
        if v.get("dark_scheme"):
            light_of_dark[v["dark_scheme"]].append(k)
    darks = [k for k, v in pcs.items() if "dark_scheme" not in v]
    pairs = []
    for dark in darks:
        for light in light_of_dark.get(dark, []):
            pairs.append({"light": light, "dark": dark, "default": light == "default"})
    return pairs


def canonical_schemes(schemes, d3):
    """V5 规范：每主题只存在日间 / 夜间两套配色。

    返回 (two, parked, ren)：
      two    {"day": ..., "night": ...}，取默认配对的两个成员；
      parked 其余配色（本例 Spring），完整归档到 parked-schemes.yaml，
             信息不丢失，需要时拆成独立主题文件；
      ren    原 sid → V5 槽位名，供 legacy_colors 改键。
    """
    dflt = next(p for p in build_pairs(d3) if p["default"])
    two = {"day": schemes[dflt["light"]], "night": schemes[dflt["dark"]]}
    ren = {dflt["light"]: "day", dflt["dark"]: "night"}
    parked = {sid: s for sid, s in schemes.items() if sid not in ren}
    return two, parked, ren


# ── 键 ──────────────────────────────────────────────────────────
# send 值 → 带参动词的附加参数（封闭动词名仍取自 v5-map.yaml 的 action_map）
SEND_PARAMS = {
    "space": {"text": " "},
    "Left": {"axis": "x", "step": "char", "direction": -1},
    "Right": {"axis": "x", "step": "char", "direction": 1},
    "Up": {"axis": "y", "step": "line", "direction": -1},
    "Down": {"axis": "y", "step": "line", "direction": 1},
    "Tab": {"text": "\t"},
}


def infer_action(pk, amap):
    """从 V3 行为字段推断封闭动词 + 参数。"""
    for field in ("select", "toggle", "command", "send", "commit", "text"):
        if field not in pk:
            continue
        val = pk[field]
        key = f"{field}:{val}"
        verb = amap.get(key) or amap.get(f"{field}:*")
        if verb:
            params = {}
            if field == "select":
                params["target"] = val
            elif field == "toggle":
                params["option"] = val
                if "states" in pk:
                    params["states"] = pk["states"]
            elif field == "command" and "option" in pk:
                params["option"] = pk["option"]
            if field == "send" and val in SEND_PARAMS:
                params.update(SEND_PARAMS[val])
            if field == "text":
                params["text"] = val
            return {"name": verb, "params": params} if params else {"name": verb}
    return {"name": "action.text.insertText"}


CLASS_PRIORITY = [("bc1", "modifier"), ("baoe", "modifier"),
                  ("bc2", "system"), ("bc3", "space"), ("bc4", "action")]


def class_from_labels(labels):
    """布局行的颜色标签 → class 词表成员（固定优先级，不依赖集合遍历顺序）。"""
    for lab, cls in CLASS_PRIORITY:
        if lab in labels:
            return cls
    return None


def build_keys(d3, m):
    """布局实际引用的旧键 → V5 行为 ID；替身键合并，差异记入 aliases。

    基底选择按 v5-map.yaml 的 key_map 顺序（主名写在最前），避免取到替身键定义；
    class 只取主场景 default 布局的颜色标签，其余场景由布局元素就地覆盖。
    """
    kmap, amap = m["key_map"], m["action_map"]
    used, primary = set(), defaultdict(set)
    for kbname, kb in d3["preset_keyboards"].items():
        for e in kb.get("keys") or []:
            if not isinstance(e, dict):
                continue
            c = e.get("click")
            if isinstance(c, str):
                used.add(c)
                if kbname == "default":
                    for f in ("key_back_color", "key_text_color"):
                        if f in e:
                            primary[c].add(e[f])
            for f in ("composing", "has_menu", "ascii", "paging", "long_click",
                      "swipe_up", "swipe_down", "swipe_left", "swipe_right"):
                if isinstance(e.get(f), str):
                    used.add(e[f])

    # 规范基底：每个 ID 取 key_map 中第一个「被布局实际引用」的旧名。
    # 只按存在性取会让 Mode_switch2（布局里唯一被引用）落到未被引用的 Mode_switch，
    # 结果 when 中出现没有 keys 定义的 ID。
    canonical = {}
    for old, kid in kmap.items():
        if old in d3["preset_keys"] and old in used:
            canonical.setdefault(kid, old)

    keys, aliases = {}, {}
    for kid, base_old in canonical.items():
        if base_old not in used:
            continue
        pk = d3["preset_keys"][base_old]
        entry = {}
        cls = class_from_labels(primary.get(base_old, set()))
        if cls:
            entry["class"] = cls
        if "label" in pk:
            entry["label"] = pk["label"]
        entry["action"] = infer_action(pk, amap)
        # 键属性轴：静态声明「这键是什么性质」，与 action(做什么) / when(何时) 正交。
        # 对齐 AOSP isSticky / isRepeatable / isModifier，词表见 vocabulary.yaml。
        attrs = {}
        if pk.get("repeatable"):
            attrs["repeatable"] = True
        if pk.get("shift_lock") or kid == "key.modifier.shift":
            attrs["sticky"] = True
        if kid.startswith("key.modifier.") or cls == "modifier":
            attrs["modifier"] = True
        if attrs:
            entry["attributes"] = attrs
        if pk.get("states"):
            entry["states"] = pk["states"]
        if pk.get("shift_lock"):
            entry["when"] = {"double_click": {"name": "action.modifier.toggleCapsLock"}}
        keys[kid] = entry

    for old in sorted(used):
        kid = kmap.get(old)
        if not kid or kid not in keys:
            continue
        pk = d3["preset_keys"].get(old)
        if not isinstance(pk, dict):
            continue
        diff = {}
        if pk.get("label") != keys[kid].get("label"):
            diff["label"] = pk.get("label")
        if pk.get("states") and pk["states"] != keys[kid].get("states"):
            diff["states"] = pk["states"]
        if pk.get("shift_lock"):
            diff["lock"] = pk["shift_lock"]
        aliases[old] = {"id": kid, **diff} if diff else kid
    return dict(sorted(keys.items())), dict(sorted(aliases.items())), used


# ── 布局 ────────────────────────────────────────────────────────
def fold_rows(kb, unit, base_width):
    """一维 keys 按 width 累加折成行数组（顺序即 X，行容器即 Y）。"""
    rows, cur, acc = [], [], 0.0
    for e in kb.get("keys") or []:
        w = float(e.get("width", base_width))
        if cur and acc + w > 100 + EPS:
            rows.append(cur)
            cur, acc = [], 0.0
        cur.append(e)
        acc += w
        if acc >= 100 - EPS:
            rows.append(cur)
            cur, acc = [], 0.0
    if cur:
        rows.append(cur)
    return rows


# V3 条件态 / 手势字段 → V5 event 词表（B1：event 名保留 snake_case）
EVENT_FIELDS = {
    "composing": "composing", "has_menu": "has_menu", "ascii": "ascii",
    "paging": "paging", "long_click": "long_click",
    "swipe_up": "swipe_up", "swipe_down": "swipe_down",
    "swipe_left": "swipe_left", "swipe_right": "swipe_right",
}
# 键面显示覆盖：V5 原生字段，就地写在布局元素上
DISPLAY_FIELDS = {"label", "hint", "label_symbol", "key_text_size"}
# 已由 V5 语义承载的字段，不再重复保留
# key_back_color → class；key_text_color 暂留 v3extra（与前者成对，避免猜原文件是否写过）
MIGRATED_FIELDS = {"click", "width", "key_back_color"} | set(EVENT_FIELDS) | DISPLAY_FIELDS


def layout_element(e, kmap, unit, preferred):
    """V3 布局条目 → V5 行数组元素；占位假键转成显式 gap。

    preferred: {新 ID: 反向降级的首选旧键名}。替身键合并后，若布局原本用的是
    非首选旧名（如 Shift_L_Lock），加 v3 迁移提示，保证反向 100% 还原。
    条件态 / 手势统一进 when；其余逐键视觉参数进 v3extra（后续可归入 preset_key_style）。
    """
    if "click" not in e:
        return {"gap": round(float(e.get("width", 0)) / unit, 4)}
    c = e["click"]
    ref = kmap.get(c, c)
    out = {}
    if ref in preferred and c != preferred[ref]:
        out["v3"] = c
    span = round(float(e.get("width", unit)) / unit, 6)
    if abs(span - 1.0) > 1e-6:
        out["span"] = span
    for f in DISPLAY_FIELDS:
        if f in e:
            out[f] = e[f]
    when = {}
    for f, ev in EVENT_FIELDS.items():
        v = e.get(f)
        if isinstance(v, str):
            when[ev] = {"key": kmap.get(v, v), "v3": v}
        elif isinstance(v, (int, float, bool)):
            # 数字键盘里 long_click: 1 这类整数字面值，同样进 when
            when[ev] = {"key": v, "v3": v}
        elif isinstance(v, dict):
            when[ev] = {"key": v, "v3": v}
    if when:
        out["when"] = when
    cls = class_from_labels([e.get("key_back_color", "")])
    if cls:
        out["class"] = cls
    extra = {f: v for f, v in e.items() if f not in MIGRATED_FIELDS}
    if extra:
        out["v3extra"] = extra
    if not out:
        return ref
    return {"key": ref, **out}


def build_layouts(d3, m):
    scenes = m["layout_scenes"]
    preferred = {}
    for old, kid in m["key_map"].items():
        if old in d3["preset_keys"]:
            preferred.setdefault(kid, old)
    layouts = {}
    for name, kb in d3["preset_keyboards"].items():
        cfg = scenes[name]
        unit = float(cfg["unit"])
        base = float(kb.get("width", 10))
        rows = fold_rows(kb, unit, base)
        vrows = [[layout_element(e, m["key_map"], unit, preferred) for e in r] for r in rows]
        entry = {}
        if cfg["scene"].endswith("portrait") or cfg["scene"].endswith("landscape"):
            entry["columns"] = int(round(100 / unit))
        entry["unit"] = unit
        if "include_of" in cfg:
            entry["include"] = cfg["include_of"]
            entry["rows"] = []
        else:
            entry["rows"] = vrows
        for f in ("landscape_keyboard", "ascii_keyboard", "landscape_split_percent",
                  "reset_ascii_mode", "label_transform", "vertical_gap", "horizontal_gap"):
            if f in kb:
                entry[f] = kb[f]
        layouts[cfg["scene"]] = entry
    return layouts


# ── 输出 ────────────────────────────────────────────────────────
def emit_scalar(v, indent=0):
    pad = " " * indent
    if isinstance(v, dict):
        return "{" + ", ".join(f"{q(k)}: {emit_scalar(x, 0)}" for k, x in v.items()) + "}"
    if isinstance(v, list):
        return "[" + ", ".join(emit_scalar(x, 0) for x in v) + "]"
    if isinstance(v, bool):
        return "true" if v else "false"
    if v is None:
        return "null"
    if isinstance(v, (int, float)):
        return str(v)
    return q(v)


def emit_rows(rows, indent=6):
    pad = " " * indent
    out = []
    for r in rows:
        items = ", ".join(emit_scalar(x) for x in r)
        out.append(f"{pad}- [{items}]")
    return "\n".join(out)


def emit_v5(d3, m, two, parked, ren, legacy, fallbacks, dropped_fb,
            keys, aliases, layouts, pt):
    L = []
    A = L.append
    A("# Trime V5 主题方案 —— 方块（由 build_v5.py 自 ss.v3.trime.yaml 生成）")
    A("# 命名规范：doc/naming/Naming.md；受控词表：doc/naming/vocabulary.yaml")
    A("# 映射规则：v5-map.yaml；本文件不直接参与当前 Trime 运行时解析，")
    A("# 可导入版本见 ss.v3.generated.trime.yaml（由本文件反向降级）。")
    A("")
    A('config_version: "5.0"')
    A("trime_version: 5")
    A(f"name: {q(d3['name'])}")
    A(f"author: {q(d3['author'])}")
    A('vocabulary: "doc/naming/vocabulary.yaml"')
    A('preview_scene: "standard26.portrait@1.0"')
    A("")
    A("# ── A. 未迁移区：与命名无关的视觉 / 几何参数，逐字保留自 V3 ──────────")
    A(pt)
    A("")
    A("# ── B. 颜色：路径式 color.<domain>[.<sub>].<part>[.<class>][.<state>] ──")
    A("# 规范：每个主题只存在日间（day）与夜间（night）两套配色，配对由结构隐含，")
    A("# 不再需要 pairs / dark_scheme / light_scheme。V3 的多余配色归档于")
    A("# parked-schemes.yaml，需要时拆成独立主题文件（自带 day + night）。")
    A("# 值统一 \"0xAARRGGBB\"；长路径优先，短路径回退；状态恒在末段便于 patch 一截就到。")
    A("schemes:")
    for sid, s in two.items():
        A(f"  {sid}:")
        A(f"    name: {q(s['name'])}")
        A(f"    author: {q(s['author'])}")
        A(f"    appearance: {s['appearance']}")
        A("    color:")
        for path, val in s["color"].items():
            A(f"      {q(path)}: {q(val)}")
    A("")
    A("# 回退链：值可以是另一个路径名，运行时沿链解析（沿用 ColorTable.resolveRawSource）。")
    A("fallback_colors:")
    for a, b in fallbacks.items():
        A(f"  {q(a)}: {q(b)}")
    if dropped_fb:
        A(f"  # 无法归约的旧链 {len(dropped_fb)} 条，保留在 legacy_colors，需作者确认")
    A("")
    A("# 第三方自定义色标签降为别名：布局改由 class 决定取哪个修饰段。")
    A("color_aliases:")
    for lab, path in m["label_map"].items():
        A(f"  {lab}: {q(path)}")
    A("")
    legacy_two = {ren[sid]: fields for sid, fields in legacy.items() if sid in ren}
    if legacy_two:
        A("# 一词多义 / 同路径多值：不猜测语义，逐字保留原值，待作者确认后收敛。")
        A("# 被归档配色（见 parked-schemes.yaml）的遗留字段随归档走，不占主题槽位。")
        A("legacy_colors:")
        for sid, fields in legacy_two.items():
            A(f"  {sid}:")
            for k, v in fields.items():
                if k == "_conflict_paths":
                    A(f"    # 路径冲突: {json.dumps(v, ensure_ascii=False)}")
                else:
                    A(f"    {k}: {v if isinstance(v, (int, float)) else q(v)}")
        A("")
    A("# ── C. 键定义：行为 ID 全局唯一一份，与设备 / 方向 / 主题无关 ──────────")
    A("# class / state 是按键侧与颜色侧的接缝；attributes 是静态性质声明，与")
    A("# action（做什么）、when（何时触发）三轴正交；取值均来自 vocabulary.yaml。")
    A("keys:")
    for kid, e in keys.items():
        A(f"  {q(kid)}:")
        for f, v in e.items():
            if isinstance(v, dict):
                A(f"    {f}: {emit_scalar(v)}")
            elif isinstance(v, list):
                A(f"    {f}: [{', '.join(q(x) if isinstance(x, str) else str(x) for x in v)}]")
            else:
                A(f"    {f}: {emit_scalar(v)}")
    A("")
    A("key_aliases:")
    for old, ref in aliases.items():
        if isinstance(ref, dict):
            A(f"  {q(old)}: {emit_scalar(ref)}")
        else:
            A(f"  {q(old)}: {q(ref)}")
    A("")
    A("# ── D. 布局：行数组即坐标，每键 0 个坐标，只有不规则键写 span 覆盖 ──")
    A("# span 为行内相对权重（1 span = unit%）；横竖屏 / 分体只换本层，不动 keys 与 schemes。")
    A("layouts:")
    for scene, cfg in layouts.items():
        A(f"  {q(scene)}:")
        for f, v in cfg.items():
            if f == "rows":
                A("    rows:")
                A(emit_rows(v, 6) if v else "      []")
            elif isinstance(v, dict):
                A(f"    {f}: {emit_scalar(v)}")
            else:
                A(f"    {f}: {emit_scalar(v)}")
    return "\n".join(L) + "\n"


# ── 反向降级 ────────────────────────────────────────────────────
def emit_parked(parked, legacy, ren_unused=None):
    """多余配色归档：V5 主题槽位只有 day / night，其余方案完整落盘。"""
    L = []
    A = L.append
    A("# V5 规范：每个主题只存在日间（day）与夜间（night）两套配色。")
    A("# 原 V3 里超出两套的方案完整归档于此，颜色值逐字保留、信息不丢失；")
    A("# 需要启用时，复制本文件为一个独立主题（自带 day + night 两个槽位）。")
    A("parked_schemes:")
    for sid, s in parked.items():
        A(f"  {sid}:")
        A(f"    name: {q(s['name'])}")
        A(f"    author: {q(s['author'])}")
        A(f"    appearance: {s['appearance']}")
        A("    color:")
        for path, val in s["color"].items():
            A(f"      {q(path)}: {q(val)}")
        fields = legacy.get(sid) or {}
        if fields:
            A("    legacy:")
            for k, v in fields.items():
                if k == "_conflict_paths":
                    A(f"      # 路径冲突: {json.dumps(v, ensure_ascii=False)}")
                else:
                    A(f"      {k}: {v if isinstance(v, (int, float)) else q(v)}")
    return "\n".join(L) + "\n"


def reverse_v3(d3, m, schemes, legacy, fallbacks, layouts):
    """以原 V3 为基底，用 V5 的配色与布局重建可导入文件；未迁移键库原样保留。"""
    out = copy.deepcopy(d3)
    cmap, lmap = m["color_map"], m["label_map"]
    inv_color = defaultdict(list)
    for old, path in cmap.items():
        inv_color[path].append(old)
    inv_label = defaultdict(list)
    for old, path in lmap.items():
        inv_label[path].append(old)

    pcs = {}
    for sid, s in schemes.items():
        s3 = {"name": s["name"], "author": s["author"], "color_format": "rgb"}
        # 路径 → 所有旧字段名（含标签名）
        for path, val in s["color"].items():
            for old in inv_color.get(path, []) + inv_label.get(path, []):
                s3[old] = unhex(val)
        # legacy_colors 覆盖回原值
        for old, v in (legacy.get(sid) or {}).items():
            if old != "_conflict_paths":
                s3[old] = v
        pcs[sid] = s3
    # 明暗联动：所有 light 方案都还原 dark_scheme，dark 方案取默认组的 light
    for p in build_pairs(d3):
        pcs[p["light"]]["dark_scheme"] = p["dark"]
        if p["default"]:
            pcs[p["dark"]]["light_scheme"] = p["light"]
    out["preset_color_schemes"] = pcs

    # 降级产物保留 V3 原链：V5 的 28 条路径链是规范视图（多个旧名映射到同一
    # 路径），反推会丢 6 条；当前 Trime 的 ColorTable 需要完整的 34 条旧名链。
    out["fallback_colors"] = copy.deepcopy(d3["fallback_colors"])

    # 布局：行数组 → 一维 keys
    kmap = m["key_map"]
    inv_key = defaultdict(list)
    for old, kid in kmap.items():
        inv_key[kid].append(old)
    scene2old = {cfg["scene"]: name for name, cfg in m["layout_scenes"].items()}
    pkb = {}
    for scene, cfg in layouts.items():
        old = scene2old[scene]
        base = copy.deepcopy(d3["preset_keyboards"][old])
        if cfg.get("include"):
            pkb[old] = base
            continue
        unit = float(cfg["unit"])
        keys = []
        for r in cfg["rows"]:
            for e in r:
                if isinstance(e, dict) and "gap" in e and "key" not in e:
                    keys.append({"width": restore_width(e["gap"], unit)})
                    continue
                ref = e["key"] if isinstance(e, dict) and "key" in e else e
                # v3 迁移提示优先，保证替身键（Shift_L_Lock / Return2 ...）精确还原
                if isinstance(e, dict) and "v3" in e:
                    c = e["v3"]
                elif ref in inv_key:
                    candidates = [o for o in inv_key[ref] if o in d3["preset_keys"]]
                    c = candidates[0] if candidates else ref
                else:
                    c = ref
                item = {"click": c}
                if isinstance(e, dict):
                    if "span" in e:
                        item["width"] = restore_width(e["span"], unit)
                    for f in DISPLAY_FIELDS:
                        if f in e:
                            item[f] = e[f]
                    # when → V3 条件态 / 手势字段，v3 提示优先保证逐字还原
                    inv_ev = {v: k for k, v in EVENT_FIELDS.items()}
                    for ev, v in (e.get("when") or {}).items():
                        f = inv_ev.get(ev, ev)
                        item[f] = v.get("v3", v.get("key")) if isinstance(v, dict) else v
                    # class → 颜色标签
                    cls2label = {"modifier": "bc1", "system": "bc2",
                                 "space": "bc3", "action": "bc4"}
                    if e.get("class") in cls2label:
                        item["key_back_color"] = cls2label[e["class"]]
                    for f, v in (e.get("v3extra") or {}).items():
                        item[f] = v
                keys.append(item)
        base["keys"] = keys
        pkb[old] = base
    out["preset_keyboards"] = pkb
    return out


def restore_width(span, unit):
    """span → V3 百分比；贴近整数时还原成整数，避免 17 → 16.9994。"""
    w = round(float(span) * unit, 4)
    return int(round(w)) if abs(w - round(w)) < 1e-3 else w


def emit_v3(d3, pt):
    """用原 V3 的头部 + 逐字保留区块 + 重建数据生成可导入文件。"""
    head = [
        "# Trime 自定义样式配置文件（由 ss.v5.trime.yaml 反向降级生成，可导入当前 Trime）",
        "# 编码: utf-8",
        "",
        'config_version: "3.0"',
        f"name: {q(d3['name'])}",
        f"author: {q(d3['author'])}",
        "",
        "# 未迁移区：锚点定义 + style / preedit / window / tool_bar / liquid_keyboard",
        "# 逐字保留自 V5 的 A 区",
        pt,
        "",
    ]
    head.append("fallback_colors:")
    for a, b in (d3.get("fallback_colors") or {}).items():
        head.append(f"  {a}: {b}")
    head.append("")
    head.append("preset_color_schemes:")
    for sid, s in d3["preset_color_schemes"].items():
        head.append(f"  {sid}:")
        for k, v in s.items():
            head.append(f"    {k}: {v if isinstance(v, (int, float)) else q(v)}")
    head.append("")
    head.append("preset_keyboards:")
    for name, kb in d3["preset_keyboards"].items():
        head.append(f"  {name}:")
        for k, v in kb.items():
            if k == "keys":
                head.append("    keys:")
                for e in v:
                    head.append("      - " + emit_scalar(e))
            else:
                head.append(f"    {k}: {emit_scalar(v)}")
    head.append("")
    head.append("preset_keys:")
    for name, pk in d3["preset_keys"].items():
        head.append(f"  {q(name)}: {emit_scalar(pk)}")
    return "\n".join(head) + "\n"


# ── 等价校验 ────────────────────────────────────────────────────
def equivalence(d3, gen, layouts, m):
    """比对反向产物与原 V3 的颜色值 / 布局键序列 / 键库。"""
    issues = []
    # 1. 颜色字段
    for sid, s in d3["preset_color_schemes"].items():
        g = gen["preset_color_schemes"].get(sid, {})
        for field, val in s.items():
            if field in META_KEYS:
                if g.get(field) != val:
                    issues.append(f"meta {sid}.{field}: 原 {val!r} / 降级 {g.get(field)!r}")
                continue
            if g.get(field) != val:
                issues.append(f"color {sid}.{field}: 原 {val} / 降级 {g.get(field)}")
    # 2. 布局条目全字段（width 做浮点容差）
    def norm(e):
        d = dict(e) if isinstance(e, dict) else {"_": e}
        if d.get("width") is not None:
            d["width"] = round(float(d["width"]), 4)
        return d

    for name, kb in d3["preset_keyboards"].items():
        a = [norm(e) for e in (kb.get("keys") or [])]
        b = [norm(e) for e in (gen["preset_keyboards"].get(name, {}).get("keys") or [])]
        if len(a) != len(b):
            issues.append(f"layout {name}: 键数 {len(a)} → {len(b)}")
            continue
        for i, (x, y) in enumerate(zip(a, b)):
            if x != y:
                diff = {k: (x.get(k), y.get(k)) for k in set(x) | set(y)
                        if x.get(k) != y.get(k)}
                issues.append(f"layout {name}[{i}]: {diff}")
    # 3. 键库
    if d3["preset_keys"] != gen["preset_keys"]:
        issues.append("preset_keys 未逐字保留")
    # 4. 回退链（降级产物必须保留完整旧名链）
    if d3["fallback_colors"] != gen["fallback_colors"]:
        issues.append("fallback_colors 未逐字保留")
    return issues


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()

    d3 = load_yaml(V3)
    m = load_yaml(MAP)
    pt = passthrough_block(V3.read_text(encoding="utf-8"))

    schemes, legacy, cstats = build_colors(d3, m)
    fallbacks, dropped_fb = build_fallbacks(d3, m)
    two, parked, ren = canonical_schemes(schemes, d3)
    keys, aliases, usage = build_keys(d3, m)
    layouts = build_layouts(d3, m)

    text = emit_v5(d3, m, two, parked, ren, legacy, fallbacks, dropped_fb,
                   keys, aliases, layouts, pt)
    # 生成即自检可解析
    try:
        v5 = yaml.safe_load(text)
    except Exception as exc:
        print(f"FATAL: 生成的 V5 无法解析: {exc}", file=sys.stderr)
        (ROOT / "ss.v5.broken.yaml").write_text(text, encoding="utf-8")
        return 1
    V5.write_text(text, encoding="utf-8")
    PARKED.write_text(emit_parked(parked, legacy), encoding="utf-8")

    gen = reverse_v3(d3, m, schemes, legacy, fallbacks, layouts)
    gen_text = emit_v3(gen, pt)
    V3_GEN.write_text(gen_text, encoding="utf-8")
    gen_loaded = yaml.safe_load(gen_text)

    issues = equivalence(d3, gen_loaded, layouts, m)
    report = {
        "baseline": {"schemes": len(d3["preset_color_schemes"]),
                     "color_fields_per_scheme": len(next(iter(d3["preset_color_schemes"].values()))) - 4,
                     "preset_keys": len(d3["preset_keys"]),
                     "layouts": len(d3["preset_keyboards"]),
                     "fallback_chains": len(d3["fallback_colors"]),
                     "placeholder_keys": sum(1 for kb in d3["preset_keyboards"].values()
                                             for e in (kb.get("keys") or []) if "click" not in e)},
        "v5": {"color_paths_per_scheme": {k: len(v["color"]) for k, v in two.items()},
               "fallback_chains": len(fallbacks),
               "scheme_slots": ["day", "night"],
               "parked_schemes": sorted(parked),
               "key_ids": len(keys),
               "key_aliases": len(aliases),
               "layout_scenes": len(layouts),
               "rows_per_scene": {k: len(v["rows"]) for k, v in layouts.items()},
               "legacy_color_fields": {k: len([x for x in v if x != "_conflict_paths"])
                                       for k, v in legacy.items()}},
        "color_stats": cstats,
        "dropped_fallback_chains": dropped_fb,
        "equivalence_issues": issues,
        "bytes": {"v3": V3.stat().st_size, "v5": V5.stat().st_size,
                  "v3_generated": V3_GEN.stat().st_size,
                  "parked": PARKED.stat().st_size},
    }
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if args.check and issues:
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
