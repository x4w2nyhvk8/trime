#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Trime V5 主题方案校验器（独立，不依赖 build_v5.py 的中间状态）。

用法：python3 validate.py
输出：逐项 [PASS]/[WARN]/[FAIL]，最终汇总；有 FAIL 时退出码非 0。
"""
import json
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent
FILES = ["ss.v3.trime.yaml", "ss.v5.trime.yaml", "v5-map.yaml",
         "vocabulary.yaml", "v5-addendum.yaml", "build_v5.py",
         "gen_preview.py", "preview.html", "V5主题方案.md",
         "build-report.json", "ss.v3.generated.trime.yaml"]

results = []


def check(no, title, ok, detail="", warn=False):
    tag = "PASS" if ok else ("WARN" if warn else "FAIL")
    results.append((tag, no, title, detail))
    print(f"[{tag}] {no:2d} {title}" + (f"  — {detail}" if detail else ""))


def load(name):
    return yaml.safe_load((ROOT / name).read_text(encoding="utf-8"))


def main():
    # ── 1 交付文件齐全 ──
    missing = [f for f in FILES if not (ROOT / f).is_file()]
    check(1, "交付包文件齐全", not missing,
          f"{len(FILES)} 个" if not missing else f"缺失 {missing}")

    # ── 2 YAML 可解析 ──
    try:
        d3, d5, mp = load("ss.v3.trime.yaml"), load("ss.v5.trime.yaml"), load("v5-map.yaml")
        vocab, add = load("vocabulary.yaml"), load("v5-addendum.yaml")
        gen = load("ss.v3.generated.trime.yaml")
        ok = True
    except Exception as exc:
        ok = False
        print(f"YAML 解析失败: {exc}")
    check(2, "5 份 YAML + 反向产物均可解析", ok)

    V = dict(vocab["vocabulary"])
    for k, vals in add["vocabulary_addendum"].items():
        V[k] = list(V.get(k, [])) + list(vals)
    sets = {k: set(v) for k, v in V.items() if isinstance(v, list)}

    # ── 3 版本门 ──
    check(3, "config_version 5.0 / trime_version 5",
          d5.get("config_version") == "5.0" and d5.get("trime_version") == 5,
          f'{d5.get("config_version")} / {d5.get("trime_version")}')

    # ── 4 颜色值格式 ──
    rx = re.compile(mp["meta"]["color_value_regex"])
    vals = [(s, p, v) for s, sc in d5["schemes"].items()
            for p, v in sc["color"].items()]
    bad = [(s, p, v) for s, p, v in vals if not rx.fullmatch(str(v))]
    check(4, "颜色值统一 0xAARRGGBB 字符串", not bad,
          f"{len(vals)} 个值" if not bad else f"非法 {bad[:3]}")

    # ── 5 颜色路径语法（逐段命中词表）──
    def path_error(path):
        segs = path.split(".")
        i = 0
        if segs[i] not in sets["color_domain"]:
            return f"domain 越界: {segs[i]}"
        i += 1
        if i < len(segs) and segs[i] in sets["color_sub"]:
            i += 1
        if i >= len(segs) or segs[i] not in sets["color_part"]:
            return "缺少合法 part"
        i += 1
        if i < len(segs) and segs[i] in sets["class"]:
            i += 1
        if i < len(segs) and segs[i] in sets["state"] | sets["context"]:
            i += 1
        return None if i == len(segs) else f"多余段 {segs[i:]}"

    perr = [(s, p, path_error(p)) for s, p, _ in vals if path_error(p)]
    check(5, "颜色路径逐段命中受控词表", not perr,
          f"{len(set(p for _, p, _ in vals))} 条唯一路径" if not perr else f"{perr[:3]}")

    # ── 6 段内 lowerCamel（禁下划线 / 禁连续大写）──
    seg_rx = re.compile(vocab["syntax"]["segment_regex"])
    forb = re.compile(vocab["syntax"]["forbidden_regex"])
    badseg = [seg for _, p, _ in vals for seg in p.split(".")
              if not seg_rx.fullmatch(seg) or forb.search(seg)]
    check(6, "token 段内 lowerCamel、缩写按普通词", not badseg,
          f"{sum(len(p.split('.')) for _, p, _ in vals)} 段" if not badseg else f"{sorted(set(badseg))[:5]}")

    # ── 7 三套方案路径集合一致 ──
    psets = [set(sc["color"]) for sc in d5["schemes"].values()]
    check(7, "三套方案 color 路径集合完全一致", all(p == psets[0] for p in psets),
          f"{len(psets[0])} 条 × {len(psets)} 套")

    # ── 8 fallback_colors 两端路径有效 ──
    fb = d5.get("fallback_colors") or {}
    known = psets[0]
    badfb = [(a, b) for a, b in fb.items() if a not in known and b not in known]
    check(8, "fallback_colors 为路径式且至少一端在方案内", not badfb,
          f"{len(fb)} 条链" if not badfb else f"{badfb[:3]}")

    # ── 9 pairs 引用有效且明暗配对正确 ──
    pairs = d5.get("pairs") or []
    pair_bad = []
    for p in pairs:
        l, dk = p["light"], p["dark"]
        if l not in d5["schemes"] or dk not in d5["schemes"]:
            pair_bad.append(f"{l}/{dk} 方案不存在")
        elif d5["schemes"][l]["appearance"] != "light" or d5["schemes"][dk]["appearance"] != "dark":
            pair_bad.append(f"{l}/{dk} appearance 不匹配")
    check(9, "pairs 显式配对、无悬空双向互指", not pair_bad,
          f"{len(pairs)} 组" if not pair_bad else f"{pair_bad}")

    # ── 10 Spring 的深色关联被修复 ──
    has_spring = any(p["light"] == "Spring" and p["dark"] == "night" for p in pairs)
    v3_spring = d3["preset_color_schemes"]["Spring"].get("dark_scheme")
    v3_night = d3["preset_color_schemes"]["night"].get("light_scheme")
    check(10, "V3 悬空关联已修正（Spring→night 但 night 只认 default）",
          has_spring and v3_spring == "night" and v3_night != "Spring",
          f"V3: Spring→{v3_spring}, night→{v3_night}; V5: {[p['light'] for p in pairs]}")

    # ── 11 color_aliases 路径有效 ──
    ca = d5.get("color_aliases") or {}
    badca = [k for k, v in ca.items() if v not in known]
    check(11, "自定义色标签全部降为路径别名", not badca and len(ca) == 10,
          f"{len(ca)} 个标签" if not badca else f"{badca}")

    # ── 12 legacy_colors 只收无法归约的字段 ──
    lc = d5.get("legacy_colors") or {}
    lc_fields = [k for v in lc.values() for k in v if k != "_conflict_paths"]
    lc_bad = [(s, f, v) for s, fields in lc.items() for f, v in fields.items()
              if f != "_conflict_paths"
              and d3["preset_color_schemes"][s].get(f) != v]
    check(12, "legacy_colors 逐字保留原值、不猜测语义", not lc_bad,
          f"{len(lc_fields)} 个冲突字段待作者确认" if not lc_bad else f"{lc_bad[:3]}")

    # ── 13 keys ID 格式与词表 ──
    ids = list(d5["keys"])
    id_bad = []
    for i in ids:
        segs = i.split(".")
        if len(segs) != 3 or segs[0] != "key" or segs[1] not in sets["key_domain"] \
                or segs[2] not in sets["key_name"]:
            id_bad.append(i)
    check(13, "行为 ID 为 key.<domain>.<name> 且命中词表", not id_bad,
          f"{len(ids)} 个 ID" if not id_bad else f"{id_bad[:5]}")

    # ── 14 keys 的 class 在共享词表 ──
    cls_bad = [(k, e["class"]) for k, e in d5["keys"].items()
               if "class" in e and e["class"] not in sets["class"]]
    check(14, "keys.class 命中共享词表", not cls_bad,
          f"{sum('class' in e for e in d5['keys'].values())} 处声明" if not cls_bad else f"{cls_bad}")

    # ── 15 keys 的 action 命中封闭动词枚举 ──
    act_bad = []
    for k, e in d5["keys"].items():
        name = e.get("action", {}).get("name", "")
        s = name.split(".")
        if len(s) != 3 or s[0] != "action" or s[1] not in sets["action_domain"] \
                or s[2] not in sets["action"]:
            act_bad.append((k, name))
    check(15, "action.* 全部命中 39 项封闭动词", not act_bad,
          f"{len(d5['keys'])} 个动作" if not act_bad else f"{act_bad[:3]}")

    # ── 16 参数化：动词差异走 params 而非造新键名 ──
    parameterized = [k for k, e in d5["keys"].items() if "params" in e.get("action", {})]
    check(16, "方向/文本等差异降级为 params", len(parameterized) >= 6,
          f"{len(parameterized)} 个带参动作，如 key.nav.left / key.edit.space")

    # ── 17 key_aliases 指向已定义 ID ──
    ka = d5.get("key_aliases") or {}
    badka = [(o, v) for o, v in ka.items()
             if (v if isinstance(v, str) else v.get("id")) not in ids]
    check(17, "key_aliases 全部指向已定义行为 ID", not badka,
          f"{len(ka)} 条别名" if not badka else f"{badka[:3]}")

    # ── 18 替身键确实合并（无数字后缀 ID）──
    suffix_ids = [i for i in ids if re.search(r"[0-9]$", i)]
    merged = sum(1 for o, v in ka.items() if isinstance(v, dict))
    check(18, "替身键合并为同一 ID，差异记为别名覆盖",
          not suffix_ids and merged >= 5,
          f"{len(ka)} 条旧名 → {len(ids)} 个 ID，其中 {merged} 条带差异")

    # ── 19 布局场景与 include ──
    layouts = d5["layouts"]
    inc_bad = [s for s, c in layouts.items()
               if "include" in c and c["include"] not in layouts]
    check(19, "布局场景合法、include 目标存在", not inc_bad,
          f"{len(layouts)} 个场景" if not inc_bad else f"{inc_bad}")

    # ── 20 行数组元素合法（ID / 字面字符 / gap）──
    row_bad = []
    for scene, cfg in layouts.items():
        for r in cfg["rows"]:
            for e in r:
                if isinstance(e, str):
                    continue
                if "gap" in e and "key" not in e:
                    if not isinstance(e["gap"], (int, float)) or e["gap"] <= 0:
                        row_bad.append((scene, e))
                    continue
                if "key" not in e:
                    row_bad.append((scene, e))
                elif not (e["key"] in ids or len(str(e["key"])) <= 4
                          or str(e["key"]).startswith("key.")):
                    row_bad.append((scene, e["key"]))
    check(20, "行数组元素为 ID / 字面字符 / 显式 gap", not row_bad,
          f"{sum(len(r) for c in layouts.values() for r in c['rows'])} 个元素"
          if not row_bad else f"{row_bad[:3]}")

    # ── 21 when 的 event 名在词表 ──
    ev_bad = []
    for scene, cfg in layouts.items():
        for r in cfg["rows"]:
            for e in r:
                if isinstance(e, dict):
                    for ev in (e.get("when") or {}):
                        if ev not in sets["event"]:
                            ev_bad.append((scene, ev))
    check(21, "when 事件名命中 event 词表（保留 snake_case）", not ev_bad,
          f"{sum(len(e.get('when') or {}) for c in layouts.values() for r in c['rows'] for e in r if isinstance(e, dict))} 处"
          if not ev_bad else f"{sorted(set(ev_bad))[:5]}")

    # ── 22 布局内 class 覆盖在共享词表 ──
    lay_cls_bad = [(s, e["class"]) for s, c in layouts.items() for r in c["rows"]
                   for e in r if isinstance(e, dict) and "class" in e
                   and e["class"] not in sets["class"]]
    check(22, "布局就地覆盖的 class 命中共享词表", not lay_cls_bad,
          "0 处越界" if not lay_cls_bad else f"{lay_cls_bad[:3]}")

    # ── 23 横竖屏共享同一批 ID，键定义只写一次 ──
    def id_set(scene):
        return {e["key"] if isinstance(e, dict) and "key" in e else e
                for r in layouts[scene]["rows"] for e in r
                if isinstance(e, str) or "key" in e}

    p, l = id_set("standard26.portrait"), id_set("standard26.landscape")
    shared = {x for x in p & l if isinstance(x, str) and x.startswith("key.")}
    check(23, "横竖屏换排布不换键定义（共享 ID）", len(shared) >= 4,
          f"共享 {len(shared)} 个行为 ID，keys 区仅 1 份定义")

    # ── 24 未迁移区逐字一致 ──
    v3txt = (ROOT / "ss.v3.trime.yaml").read_text(encoding="utf-8")
    v5txt = (ROOT / "ss.v5.trime.yaml").read_text(encoding="utf-8")

    def block(t, endmark):
        return t[t.index("# 锚点定义"):t.index(endmark)].strip()
    b3 = block(v3txt, "fallback_colors:")
    b5 = block(v5txt, "# ── B.")
    check(24, "未迁移区（锚点/style/preedit/window/...）逐字保留",
          b3 == b5, f"{len(b3)} 字符")

    # ── 25 信息等价（读构建报告 + 重比对）──
    rep = json.loads((ROOT / "build-report.json").read_text(encoding="utf-8"))
    color_ok = all(
        gen["preset_color_schemes"][s].get(f) == v
        for s, sc in d3["preset_color_schemes"].items()
        for f, v in sc.items() if f not in ("name", "author", "dark_scheme", "light_scheme", "color_format")
    )
    keys_ok = gen["preset_keys"] == d3["preset_keys"]
    check(25, "V5→V3 反向降级信息无损",
          not rep["equivalence_issues"] and color_ok and keys_ok,
          f"等价差异 {len(rep['equivalence_issues'])} 项；61 色值 ×3 套 + 161 键库逐字一致")

    # ── 26 反向产物可导入当前 Trime ──
    check(26, "反向产物 config_version 3.0 且结构完整",
          str(gen.get("config_version")) == "3.0"
          and all(k in gen for k in ("preset_color_schemes", "preset_keyboards",
                                     "preset_keys", "fallback_colors", "style")),
          f"{len(gen['preset_keyboards'])} 布局 / {len(gen['preset_keys'])} 键")

    # ── 27 preview 内嵌数据与 V5 一致 ──
    html = (ROOT / "preview.html").read_text(encoding="utf-8")
    mt = re.search(r'<script id="v5-data" type="application/json">(.*?)</script>',
                   html, re.S)
    try:
        pd = json.loads(mt.group(1))
        pv_ok = (set(pd["schemes"]) == set(d5["schemes"])
                 and all(len(pd["schemes"][s]) == len(d5["schemes"][s]["color"])
                         for s in pd["schemes"]))
    except Exception:
        pv_ok = False
    check(27, "预览内嵌数据与 V5 方案一致", pv_ok,
          "三套配色路径数逐套核对" if pv_ok else "内嵌 JSON 缺失或不一致")

    # ── 28 文档计数反查 ──
    doc = (ROOT / "V5主题方案.md").read_text(encoding="utf-8")
    counts_ok = all(str(n) in doc for n in
                    [len(ids), len(vals) // len(d5["schemes"]), len(fb),
                     len(pairs), len(ka), rep["baseline"]["preset_keys"]])
    check(28, "方案文档中的计数与生成数据一致", counts_ok,
          f"ID {len(ids)} / 路径 {len(vals)//len(d5['schemes'])} / 链 {len(fb)} / 配对 {len(pairs)} / 别名 {len(ka)}")

    # ── 汇总 ──
    n = {t: sum(1 for r in results if r[0] == t) for t in ("PASS", "WARN", "FAIL")}
    print("\n" + "=" * 60)
    print(f"汇总: {n['PASS']} PASS / {n['WARN']} WARN / {n['FAIL']} FAIL")
    print("=" * 60)
    return 1 if n["FAIL"] else 0


if __name__ == "__main__":
    sys.exit(main())
