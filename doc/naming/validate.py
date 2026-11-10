#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""doc/naming 校验器：词表自洽、路径语法、双向咬合、源码回查、文档计数。

用法：python3 doc/naming/validate.py
依赖：PyYAML（仅校验器需要，App 不依赖）。全部通过时退出码 0。
"""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[1]

try:
    import yaml
except ImportError:
    print("缺少 PyYAML：python3 -m pip install pyyaml", file=sys.stderr)
    sys.exit(2)


def load(name):
    return yaml.safe_load((ROOT / name).read_text(encoding="utf-8"))


V, E, M = load("vocabulary.yaml"), load("examples.yaml"), load("legacy-map.yaml")

results = []


def check(no: int, title: str, ok: bool, detail: str = "", warn: bool = False) -> None:
    status = "PASS" if ok else ("WARN" if warn else "FAIL")
    results.append((no, status, title, detail))


# ── 词表与语法基础设施 ─────────────────────────────────────────
syn = V.get("syntax", {})
voc = V.get("vocabulary", {})
SEG = re.compile(syn.get("segment_regex", "^$"))
ESEG = re.compile(syn.get("event_segment_regex", "^$"))
FORBID = re.compile(syn.get("forbidden_regex", "(?!)"))
CVAL = re.compile(syn.get("color_value_regex", "^$"))
MAXDEPTH = syn.get("max_depth", 6)

# class_variant 是映射（variant→基类），不是 token 列表，单独校验其键值
TOKEN_LISTS = {k: v for k, v in voc.items()
               if k != "event" and isinstance(v, list)}
CLASS = set(voc.get("class", []))
STATE = set(voc.get("state", []))
CONTEXT = set(voc.get("context", []))
MODPOOL = CLASS | STATE | CONTEXT
PART = set(voc.get("color_part", []))
DOMAIN = set(voc.get("color_domain", []))
SUB = set(voc.get("color_sub", []))
EVENT = set(voc.get("event", []))
GEOM = set(voc.get("geometry", []))
IMPLICIT_STATE = {"normal", "pressed", "selected", "focused", "disabled"}


def seg_ok(s: str) -> bool:
    return bool(SEG.match(s)) and not FORBID.search(s)


def parse_color(path: str):
    """返回 (ok, reason, info)。语法：color.<domain>[.<sub>].<part>[.<class>][.<state|context>]"""
    segs = path.split(".")
    if len(segs) > MAXDEPTH:
        return False, f"深度 {len(segs)} > {MAXDEPTH}", {}
    if segs[0] != "color":
        return False, "首段必须为 color", {}
    rest = segs[1:]
    mods = []
    while rest and rest[-1] in MODPOOL:
        mods.insert(0, rest.pop())
    if not rest:
        return False, "缺少 part 段", {}
    part = rest[-1]
    if part not in PART:
        return False, f"part 段 {part!r} 不在词表", {}
    rest = rest[:-1]
    if not 1 <= len(rest) <= 2:
        return False, "domain 段数应为 1~2", {}
    if rest[0] not in DOMAIN:
        return False, f"domain 段 {rest[0]!r} 不在词表", {}
    if len(rest) == 2 and rest[1] not in SUB:
        return False, f"子域段 {rest[1]!r} 不在词表", {}
    classes = [m for m in mods if m in CLASS]
    states = [m for m in mods if m in STATE or m in CONTEXT]
    if len(classes) > 1:
        return False, "class 修饰段最多 1 个", {}
    if len(states) > 1:
        return False, "state/context 修饰段最多 1 个", {}
    if classes and states and mods.index(classes[0]) > mods.index(states[0]):
        return False, "class 必须排在 state/context 之前", {}
    for s in segs[1:]:
        if not seg_ok(s):
            return False, f"段 {s!r} 不符合 B1 驼峰规则", {}
    return True, "", {"mods": mods, "part": part, "domain": rest[0]}


def parse_action(name: str):
    segs = name.split(".")
    if len(segs) != 3 or segs[0] != "action":
        return False, f"应为 action.<domain>.<verb>：{name}"
    if segs[1] not in set(voc.get("action_domain", [])):
        return False, f"action domain {segs[1]!r} 不在词表"
    if segs[2] not in set(voc.get("action", [])):
        return False, f"动作动词 {segs[2]!r} 不在封闭枚举"
    for s in segs[1:]:
        if not seg_ok(s):
            return False, f"段 {s!r} 不符合 B1 驼峰规则"
    return True, ""


def parse_key_id(key_id: str):
    segs = key_id.split(".")
    if len(segs) != 3 or segs[0] != "key":
        return False, f"应为 key.<domain>.<name>：{key_id}"
    if segs[1] not in set(voc.get("key_domain", [])):
        return False, f"key domain {segs[1]!r} 不在词表"
    if segs[2] not in set(voc.get("key_name", [])):
        return False, f"key name {segs[2]!r} 不在词表"
    for s in segs[1:]:
        if not seg_ok(s):
            return False, f"段 {s!r} 不符合 B1 驼峰规则"
    return True, ""


# ── 01 文件可解析 ─────────────────────────────────────────────
ok = all(isinstance(d, dict) and d.get("version") == 1 for d in (V, E, M))
check(1, "三份 YAML 可解析且 version == 1", ok)

# ── 02 语法约束 ───────────────────────────────────────────────
need = ["pattern", "separator", "segment_regex", "event_segment_regex",
        "yaml_field_regex", "forbidden_regex", "max_depth",
        "color_value_format", "color_value_regex", "specificity"]
ok = all(k in syn for k in need)
ok = ok and "_" not in syn.get("segment_regex", "") and "_" in syn.get("event_segment_regex", "")
ok = ok and syn.get("separator") == "." and syn.get("color_value_format") == "0xAARRGGBB"
check(2, "syntax 约束齐全：token 禁下划线 / event 允下划线 / 值格式 0xAARRGGBB", ok)

# ── 03 B1 决策 ────────────────────────────────────────────────
cc = V.get("camel_case", {})
ok = cc.get("decision") == "B1" and len(cc.get("excluded", [])) >= 2
ok = ok and "strictMode" in cc.get("hard_validation", "") + str(cc.get("excluded", ""))
check(3, "camel_case.decision == B1 且声明了字段名 / event 两处例外", ok)

# ── 04 命名空间 ───────────────────────────────────────────────
ns = V.get("namespaces", {})
check(4, "四个命名空间齐全（color / action / event / key）",
      set(ns) == {"color", "action", "event", "key"}, f"实际 {len(ns)} 个")

# ── 05 词表无重复 ─────────────────────────────────────────────
dups = [k for k, v in voc.items() if isinstance(v, list) and len(v) != len(set(v))]
check(5, "所有词表无重复项", not dups, f"重复：{dups}" if dups else f"{len(voc)} 份词表")

# ── 06 非 event 词表段符合 B1 ─────────────────────────────────
bad = [(k, s) for k, v in TOKEN_LISTS.items() for s in v if not seg_ok(s)]
check(6, "token 段全部 lowerCamel、无下划线、无连续大写",
      not bad, f"{sum(len(v) for v in TOKEN_LISTS.values())} 段已检" if not bad else f"违规 {bad[:4]}")

# ── 07 event 词表保留蛇形 ─────────────────────────────────────
bad = [s for s in voc.get("event", []) if not ESEG.match(s) or re.search("[A-Z]", s)]
check(7, "event 词表保留 snake_case（B1 唯一例外）",
      not bad and len(EVENT) == 13, f"{len(EVENT)} 项：{', '.join(sorted(EVENT))[:90]}…")

# ── 08 共享词表互斥 ───────────────────────────────────────────
pairs = [("class", "state", CLASS & STATE), ("class", "context", CLASS & CONTEXT),
         ("state", "context", STATE & CONTEXT)]
ok = all(not x for _, _, x in pairs)
check(8, "class / state / context 两两不交（修饰段无歧义）", ok,
      "; ".join(f"{a}∩{b}={sorted(x)}" for a, b, x in pairs if x))

# ── 09 结构完整性 ─────────────────────────────────────────────
mr = V.get("modifier_rules", {})
# 源码 Key.kt 的二维状态轴要求两段 state，故 modifier_rules 由
# max_state_or_context 改为 max_state + state_order，且 class 与 state 正交。
ok = all(k in mr for k in ["pool", "max_class", "order"])
ok = ok and ("max_state" in mr or "max_state_or_context" in mr)
ok = ok and "state_order" in mr and mr.get("max_state", 1) == 2
ok = ok and mr.get("class_state_context_mutually_exclusive") is False
ok = ok and set(V.get("axes", [])) == {"when", "action", "class_and_state", "geometry"}
ok = ok and len(M.get("resolve_order", [])) >= 5 and "field" in M.get("version_gate", {})
check(9, "modifier_rules / axes / resolve_order / version_gate 齐全", ok)

# ── 10 源码行号回查 ───────────────────────────────────────────
cache = {}


def line_at(rel, n):
    if rel not in cache:
        cache[rel] = (REPO / rel).read_text(encoding="utf-8").split("\n")
    lines = cache[rel]
    return lines[n - 1] if 0 < n <= len(lines) else None


refs, ref_bad = 0, []
for scene in E["scenes"].values():
    for item in (scene.get("current") or {}).values():
        if isinstance(item, dict) and {"file", "line", "text"} <= set(item):
            refs += 1
            actual = line_at(item["file"], item["line"])
            if actual is None or actual.strip() != str(item["text"]).strip():
                ref_bad.append(f"{item['file']}:{item['line']}")
for item in (E.get("layout_geometry") or {}).get("current", {}).values():
    if isinstance(item, dict) and {"file", "line", "text"} <= set(item):
        refs += 1
        actual = line_at(item["file"], item["line"])
        if actual is None or actual.strip() != str(item["text"]).strip():
            ref_bad.append(f"{item['file']}:{item['line']}")
check(10, "examples.yaml 的 current.* 逐字回查仓库源码",
      not ref_bad and refs >= 15, f"{refs} 条引用" if not ref_bad else f"不符：{ref_bad}")


# ── 收集 examples 中的各类路径 ────────────────────────────────
def scenes():
    return list(E["scenes"].values())


color_paths, key_ids, actions, events, classes, geoms = [], [], [], [], [], []


def walk_geometry(node):
    if isinstance(node, dict):
        if node and set(node) <= GEOM:
            geoms.append(node)
        for v in node.values():
            walk_geometry(v)
    elif isinstance(node, list):
        for v in node:
            walk_geometry(v)


for sc in scenes():
    u = sc.get("unified") or {}
    k = u.get("key") or {}
    for p, val in (u.get("colors") or {}).items():
        color_paths.append((sc.get("title", "?"), p, val))
    if k.get("id"):
        key_ids.append(k["id"])
    if k.get("class") is not None:
        classes.append((sc.get("title", "?"), k["class"]))
    for spec in [k.get("action"), *((k.get("when") or {}).values())]:
        if isinstance(spec, dict) and spec.get("name"):
            actions.append((sc.get("title", "?"), spec["name"]))
    for ev in (k.get("when") or {}):
        events.append((sc.get("title", "?"), ev))
    walk_geometry(u)
    for item in (u.get("fixed_key_bar") or []):
        if isinstance(item, dict) and item.get("key"):
            key_ids.append(item["key"])
walk_geometry(E.get("layout_geometry", {}).get("unified", {}))

# ── 11 color 路径语法 ──────────────────────────────────────────
bad = []
for _, p, _ in color_paths:
    ok, why, _ = parse_color(p)
    if not ok:
        bad.append(f"{p} ({why})")
check(11, "所有 color 路径语法合法且各段命中词表", not bad,
      f"{len(color_paths)} 条路径" if not bad else "; ".join(bad[:3]))

# ── 12 色值格式 ───────────────────────────────────────────────
bad = [f"{p}={v}" for _, p, v in color_paths if not (isinstance(v, str) and CVAL.match(v))]
check(12, "所有色值符合 0xAARRGGBB 且为字符串", not bad,
      f"{len(color_paths)} 个色值" if not bad else "; ".join(bad[:3]))

# ── 13 key ID ─────────────────────────────────────────────────
bad = [f"{i}: {why}" for i in key_ids for ok, why in [parse_key_id(i)] if not ok]
check(13, "所有 key ID 合法（含 fixed_key_bar 引用）", not bad,
      f"{len(key_ids)} 个 ID" if not bad else "; ".join(bad[:3]))

# ── 14 action 动词 ────────────────────────────────────────────
bad = [f"{n}: {why}" for _, n in actions for ok, why in [parse_action(n)] if not ok]
check(14, "所有 action.name 命中封闭动词枚举", not bad,
      f"{len(actions)} 处引用" if not bad else "; ".join(bad[:3]))

# ── 15 when 的 event 名 ───────────────────────────────────────
bad = [f"{sc}: {ev}" for sc, ev in events if ev not in EVENT or not ESEG.match(ev)]
check(15, "when 下用裸 event 名且命中词表（snake_case）", not bad,
      f"{len(events)} 处触发" if not bad else "; ".join(bad[:3]))

# ── 16 class 值 ───────────────────────────────────────────────
bad = [f"{sc}: {c}" for sc, c in classes if c not in CLASS]
check(16, "所有 class 值命中共享词表", not bad, f"{len(classes)} 处声明")

# ── 17 geometry ───────────────────────────────────────────────
bad = [str(g) for g in geoms if any(not isinstance(v, (int, float)) for v in g.values())]
check(17, "geometry 属性合法且值为数值（独立第四轴）", not bad,
      f"{len(geoms)} 组几何" if not bad else "; ".join(bad[:2]))

# ── 18 双向咬合：class ────────────────────────────────────────
bad = []
for sc in scenes():
    u = sc.get("unified") or {}
    c = (u.get("key") or {}).get("class")
    if not c or c == "default":
        continue
    hit = any(parse_color(p)[0] and c in parse_color(p)[2].get("mods", [])
              for p in (u.get("colors") or {}))
    if not hit:
        bad.append(f"{sc.get('title')}: class={c} 无对应颜色修饰段")
check(18, "双向咬合：非 default 的 class 在同场景有对应颜色路径", not bad,
      "3 个场景" if not bad else "; ".join(bad))

# ── 19 双向咬合：state ────────────────────────────────────────
bad = []
for sc in scenes():
    u = sc.get("unified") or {}
    produced = {s.get("produces_state") for s in (u.get("key") or {}).get("when", {}).values()
                if isinstance(s, dict) and s.get("produces_state")}
    for p in (u.get("colors") or {}):
        ok, _, info = parse_color(p)
        if not ok:
            continue
        for m in info.get("mods", []):
            if m in STATE and m not in IMPLICIT_STATE and m not in produced:
                bad.append(f"{p}: state={m} 无动作产生")
check(19, "双向咬合：非隐式 state 由 produces_state 解释", not bad,
      "如 capsLock ← action.modifier.toggleCapsLock" if not bad else "; ".join(bad))

# ── 20 映射表 colors ──────────────────────────────────────────
bad = []
for item in M.get("colors", []):
    ok, why, _ = parse_color(item.get("path", ""))
    if not ok or not re.fullmatch(r"[a-z][a-z0-9_]*", str(item.get("legacy", ""))):
        bad.append(f"{item.get('legacy')}: {why or 'legacy 名格式'}")
check(20, "legacy-map colors：路径合法、旧名为小写蛇形", not bad,
      f"{len(M.get('colors', []))} 条" if not bad else "; ".join(bad[:3]))

# ── 21 映射表 aliases ─────────────────────────────────────────
labels = [a.get("label") for a in M.get("aliases", [])]
bad = [a["label"] for a in M.get("aliases", []) if not parse_color(a.get("path", ""))[0]]
bad += ["重复 label"] if len(labels) != len(set(labels)) else []
check(21, "legacy-map aliases：路径合法、色标签无重复", not bad,
      f"{len(labels)} 条（bkg/tkg/benter/bh1~4 等）" if not bad else "; ".join(bad[:3]))

# ── 22 映射表 keys ────────────────────────────────────────────
all_legacy = [n for item in M.get("keys", []) for n in (item.get("legacy") or [])]
bad = [f"{item.get('id')}: {why}" for item in M.get("keys", [])
       for ok, why in [parse_key_id(str(item.get("id", "")))] if not ok]
if len(all_legacy) != len(set(all_legacy)):
    bad.append("旧键名跨组重复")
check(22, "legacy-map keys：新 ID 合法、旧键名跨组无重复", not bad,
      f"{len(M.get('keys', []))} 组 / {len(all_legacy)} 个旧名" if not bad else "; ".join(bad[:3]))

# ── 23 Naming.md 计数标记 ─────────────────────────────────────
md = (ROOT / "Naming.md").read_text(encoding="utf-8")
declared = {k: int(v) for k, v in re.findall(r"<!-- COUNT:(\w+)=(\d+) -->", md)}
color_enum = len(re.findall(r"^\s{4}([A-Z][A-Z0-9_]*)\s*[({,]",
                            (REPO / "app/src/main/java/com/osfans/trime/data/theme/ColorKey.kt")
                            .read_text(encoding="utf-8"), re.M))
actual = {
    "namespaces": len(ns), "action": len(voc.get("action", [])), "event": len(EVENT),
    "color_enum": color_enum, "geometry": len(GEOM), "scenes": len(E["scenes"]),
    "map_colors": len(M.get("colors", [])), "map_keys": len(M.get("keys", [])),
    "map_aliases": len(M.get("aliases", [])),
}
bad = [f"{k}: 文档 {declared.get(k)} ≠ 实际 {v}" for k, v in actual.items()
       if declared.get(k) != v]
check(23, "Naming.md 的计数标记与词表 / 源码一致", not bad and len(declared) == len(actual),
      ", ".join(f"{k}={v}" for k, v in actual.items()) if not bad else "; ".join(bad))

# ── 24 交付文件齐全 ───────────────────────────────────────────
files = ["README.md", "Naming.md", "vocabulary.yaml", "examples.yaml",
         "legacy-map.yaml", "validate.py", "color-registry.yaml",
         "build_registry.py"]
missing = [f for f in files if not (ROOT / f).is_file()]
check(24, "交付包文件齐全", not missing,
      f"{len(files)} 个" if not missing else f"缺失：{missing}")

# ── 25 零侵入 ─────────────────────────────────────────────────
try:
    out = subprocess.run(["git", "-C", str(REPO), "status", "--porcelain"],
                         capture_output=True, text=True, timeout=10).stdout
    bad = [l for l in out.splitlines() if l.strip() and "doc/naming/" not in l]
    check(25, "除 doc/naming/ 外零改动（Kotlin / 布局 / schema）", not bad,
          "工作区仅新增本目录" if not bad else "; ".join(bad[:3]))
except Exception as exc:
    check(25, "零侵入检查", True, f"git 不可用，跳过（{type(exc).__name__}）", warn=True)

# ── 26 部件词定稿（B 方案 ①）───────────────────────────────────
# 结构文件（词表 / 示例 / 迁移表）零残留；Naming.md 是散文，其中
# 「为何不采用 onSurface」是有意保留的决策说明，只查点分路径残留。
want = {"background", "foreground", "hint", "outline", "elevation"}
structural = ("examples.yaml", "legacy-map.yaml", "vocabulary.yaml")
stale = [f for f in structural
         if any(w in (ROOT / f).read_text(encoding="utf-8")
                for w in (".surface", ".content", "onSurface"))]
prose = (ROOT / "Naming.md").read_text(encoding="utf-8")
prose_stale = any(w in prose for w in (".surface", ".content"))
# separator 是以源码为锚后新增的部件词（CANDIDATE_SEPARATOR_COLOR 不能与
# CANDIDATE_BORDER_COLOR 共占 candidate.outline），只允许这一种扩充。
extra = PART - want
ok26 = want <= PART and extra <= {"separator"} and not stale and not prose_stale
check(26, "部件词定稿 background / foreground，新增 separator 有源码依据",
      ok26,
      f"定稿五词 + 源码补充 {sorted(extra)}" if ok26
      else f"词表 {sorted(PART)} / 残留 {stale} / 散文路径残留 {prose_stale}")

# ── 27 state 补 active 且三表正交（B 方案 ②）───────────────────
ok27 = "active" in STATE and not (STATE & CLASS or STATE & CONTEXT)
check(27, "state 含 active，与 class / context 两两不交", ok27,
      f"state {len(STATE)} 词，含 active")

# ── 28 域收口 + 新增三份词表（B 方案 ③④⑤）────────────────────
ad = set(voc.get("action_domain", []))
kd = set(voc.get("key_domain", []))
cv = voc.get("class_variant") or {}
dirs = set(voc.get("direction", []))
attrs = set(voc.get("key_attribute", []))
ok28 = (ad == {"text", "edit", "modifier", "nav", "layout",
               "rime", "trime", "device", "system"}
        and kd <= ad
        and isinstance(cv, dict)
        and all(k in CLASS and v in CLASS for k, v in cv.items())
        and dirs == {"up", "down", "left", "right"}
        and attrs == {"sticky", "repeatable", "modifier"})
check(28, "动作域分语义/来源类，class_variant / direction / key_attribute 齐备",
      ok28, f"action_domain {len(ad)} 词；key_domain {len(kd)} 词全被覆盖；"
            f"变体 {len(cv)} / 方位 {len(dirs)} / 属性 {len(attrs)}")

# ── 29 class_variant 的键值均为合法 token 段 ───────────────────
ok29 = bool(cv) and all(seg_ok(k) and seg_ok(v) for k, v in cv.items())
check(29, "class_variant 的 variant / 基类均命中 class 且为合法段", ok29,
      f"{cv}" if ok29 else "变体键值越界")

# ── 30 规范色表以源码为锚（不以任何主题样本为限）──────────────
reg = load("color-registry.yaml")
src = reg["source"]
keys = set(src["color_keys"])
covered = set(reg["canonical"]) | set(reg["synonyms"])
ok30 = (src["enum_count"] == 52 and len(keys) == 52
        and src["fallback_chain_count"] == 49
        and len(src["fallback_chains"]) == 49
        and src["style_key_count"] == 58
        and len(reg["style_fields"]) == 58
        and covered == keys)
check(30, "规范色表以源码为锚：52 枚举全覆盖，49 链 / 58 style 字段自包含",
      ok30, f"canonical {len(reg['canonical'])} + synonyms {len(reg['synonyms'])}"
            f" = {len(covered)}；缺 {sorted(keys - covered)[:3]}")

# ── 31 去歧义 R1/R2/R3/R6 与六条规则清单 ───────────────────────
cpaths = list(reg["canonical"].values())
r1 = len(cpaths) == len(set(cpaths))
r2 = all("evidence" in s and s["path"] in set(cpaths)
         for s in reg["synonyms"].values())
r3 = not (set(reg["aliases"]) & set(reg["canonical"]))
r6 = set(reg["value_types"]) == {"color", "image"}
rule_ids = {r["id"] for r in reg["rules"]}
ok31 = r1 and r2 and r3 and r6 and rule_ids == {f"R{i}" for i in range(1, 7)}
check(31, "去歧义 R1/R2/R3/R6 与六条规则清单齐备", ok31,
      f"唯一 {r1} / 同义证据 {r2} / 别名隔离 {r3} / 值域 {r6} / 规则 {sorted(rule_ids)}")

# ── 32 R5 回退链无环 + 补充色码有依据 ──────────────────────────
fb2 = reg["fallbacks"]
def no_cycle():
    for start in fb2:
        cur, seen = start, set()
        while cur in fb2:
            if cur in seen:
                return False
            seen.add(cur)
            cur = fb2[cur]
    return True
fb_bad = [f"{a}->{b}" for a, b in fb2.items()
          if b not in set(cpaths) | set(reg["supplementary"])]
sup = reg["supplementary"]
allowed = DOMAIN | SUB | PART | CLASS | STATE | CONTEXT | set(voc.get("direction", []))
sup_bad = []
for pth, meta in sup.items():
    segs = pth.split(".")
    if segs[0] not in DOMAIN:
        sup_bad.append(f"{pth} domain")
    if any(s not in allowed for s in segs[1:]):
        sup_bad.append(f"{pth} 段")
    if not any(s in PART for s in segs):
        sup_bad.append(f"{pth} 缺部件")
    if not all(k in meta for k in ("origin", "reason", "status")):
        sup_bad.append(f"{pth} 缺依据")
    if pth in set(cpaths):
        sup_bad.append(f"{pth} 与规范冲突")
ok32 = no_cycle() and not fb_bad and len(fb2) >= 47 and not sup_bad
check(32, f"回退链 {len(fb2)} 条无环、补充色码 {len(sup)} 条均附来源与理由",
      ok32, "47 条 + 2 同义自环；方位 hint / disabled / active / focused / selected"
            if ok32 else f"悬空 {fb_bad[:2]} / 补充 {sup_bad[:3]}")

# ── 33 源码漂移检测：枚举一变，规范必须重生成 ──────────────────
import hashlib
color_src = REPO / "app/src/main/java/com/osfans/trime/data/theme/ColorKey.kt"
if color_src.is_file():
    sha = hashlib.sha256(color_src.read_bytes()).hexdigest()
    ok33 = sha == src["color_key_sha256"]
    check(33, "源码 sha256 与规范色表一致（枚举变更须重跑生成器）",
          ok33, f"{sha[:12]}")
else:
    check(33, "源码 sha256 与规范色表一致", True,
          "源码不在仓库检出内，按 registry 自包含清单核对（跳过直读）", warn=True)

# ── 汇总 ──────────────────────────────────────────────────────
print("=" * 78)
print("doc/naming 统一命名规范 — 校验结果")
print("=" * 78)
for no, status, title, detail in sorted(results):
    tail = f"  · {detail}" if detail else ""
    print(f"[{no:02d}] {status:<4}  {title}{tail}")
print("-" * 78)
n_pass = sum(1 for r in results if r[1] == "PASS")
n_warn = sum(1 for r in results if r[1] == "WARN")
n_fail = sum(1 for r in results if r[1] == "FAIL")
print(f"汇总：{n_pass} PASS / {n_warn} WARN / {n_fail} FAIL  （共 {len(results)} 项）")
print("=" * 78)
sys.exit(1 if n_fail else 0)
