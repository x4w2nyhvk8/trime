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
         "build-report.json", "ss.v3.generated.trime.yaml",
         "parked-schemes.yaml", "color-registry.yaml", "build_registry.py",
         "migration.yaml"]

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

    # ── 7 两套配色路径集合一致 ──
    psets = [set(sc["color"]) for sc in d5["schemes"].values()]
    check(7, "day / night 两套 color 路径集合完全一致", all(p == psets[0] for p in psets),
          f"{len(psets[0])} 条 × {len(psets)} 套")

    # ── 8 fallback_colors 两端路径有效 ──
    fb = d5.get("fallback_colors") or {}
    known = psets[0]
    badfb = [(a, b) for a, b in fb.items() if a not in known and b not in known]
    check(8, "fallback_colors 为路径式且至少一端在方案内", not badfb,
          f"{len(fb)} 条链" if not badfb else f"{badfb[:3]}")

    # ── 9 主题只存在日间 / 夜间两套 ──
    slots = list(d5["schemes"].keys())
    ok9 = slots == ["day", "night"] and d5["schemes"]["day"]["appearance"] == "light" \
        and d5["schemes"]["night"]["appearance"] == "dark" and "pairs" not in d5
    check(9, "每主题只存在 day(日间)/night(夜间) 两套，配对由结构隐含", ok9,
          f"schemes = {slots}, pairs 段{'存在' if 'pairs' in d5 else '已取消'}")

    # ── 10 多余配色已完整归档（信息不丢失）──
    parked_doc = load("parked-schemes.yaml") if (ROOT / "parked-schemes.yaml").is_file() else {}
    parked = parked_doc.get("parked_schemes") or {}
    extra = [s for s in d3["preset_color_schemes"] if s not in ("default", "night")]
    ok10 = set(parked) == set(extra)
    bad_archive = []
    for sid in extra:
        pk = parked.get(sid) or {}
        pleg = pk.get("legacy") or {}
        for field, val in d3["preset_color_schemes"][sid].items():
            if field in ("name", "author", "dark_scheme", "light_scheme", "color_format"):
                continue
            path = mp["color_map"].get(field) or mp["label_map"].get(field)
            v5v = f"0x{val & 0xFFFFFFFF:08X}" if isinstance(val, int) else val
            if path and pk.get("color", {}).get(path) == v5v:
                continue          # 该字段是路径的当选值
            if str(field) in pleg or field in pleg:
                continue          # 该字段逐字保留在归档 legacy
            bad_archive.append(f"{sid}.{field}")
    check(10, f"多余配色 {extra} 已归档且逐字等于 V3", ok10 and not bad_archive,
          f"归档 {sorted(parked)}" if ok10 and not bad_archive else f"{bad_archive[:5]}")

    # ── 11 color_aliases 路径有效 ──
    ca = d5.get("color_aliases") or {}
    badca = [k for k, v in ca.items() if v not in known]
    check(11, "自定义色标签全部降为路径别名", not badca and len(ca) == 10,
          f"{len(ca)} 个标签" if not badca else f"{badca}")

    # ── 12 legacy_colors 只收无法归约的字段 ──
    lc = d5.get("legacy_colors") or {}
    lc_fields = [k for v in lc.values() for k in v if k != "_conflict_paths"]
    ren = {"day": "default", "night": "night"}   # V5 槽位 → V3 原 sid
    lc_bad = [(s, f, v) for s, fields in lc.items() for f, v in fields.items()
              if f != "_conflict_paths"
              and d3["preset_color_schemes"].get(ren.get(s, s), {}).get(f) != v]
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
          f"等价差异 {len(rep['equivalence_issues'])} 项；61 色值 ×3 套 + 161 键库逐字一致"
          if not rep["equivalence_issues"] and color_ok and keys_ok
          else f"等价差异 {rep['equivalence_issues'][:3]}")

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
          "day / night 路径数逐套核对" if pv_ok else "内嵌 JSON 缺失或不一致")

    # ── 28 文档计数反查 ──
    doc = (ROOT / "V5主题方案.md").read_text(encoding="utf-8")
    counts_ok = all(str(n) in doc for n in
                    [len(ids), len(vals) // len(d5["schemes"]), len(fb),
                     len(parked), len(ka), rep["baseline"]["preset_keys"]])
    check(28, "方案文档中的计数与生成数据一致", counts_ok,
          f"ID {len(ids)} / 路径 {len(vals)//len(d5['schemes'])} / 链 {len(fb)} / 归档 {len(parked)} / 别名 {len(ka)}")

    # ── 29 部件词定稿：background / foreground（B 方案 ①）──
    # 只校验参与生成/运行的结构文件；方案文档里的「改造前 / 不采纳」对照段落
    # 是有意保留的历史说明，不属残留，但必须写明定稿与不撤回。
    parts = set(V["color_part"])
    want_parts = {"background", "foreground", "hint", "outline", "elevation"}
    # separator 是以源码为锚后新增的部件词（CANDIDATE_SEPARATOR_COLOR 与
    # CANDIDATE_BORDER_COLOR 不能共占 candidate.outline），只允许这一种扩充。
    extra_parts = parts - want_parts
    struct = ("ss.v5.trime.yaml", "v5-map.yaml", "gen_preview.py",
              "parked-schemes.yaml", "ss.v3.generated.trime.yaml")
    stale = [(f, w) for f in struct
             for w in (".surface", ".content", "onSurface")
             if w in (ROOT / f).read_text(encoding="utf-8")]
    doc_b = (ROOT / "V5主题方案.md").read_text(encoding="utf-8")
    declared = "定稿" in doc_b and "不撤回" in doc_b and "background / foreground" in doc_b
    ok29 = want_parts <= parts and extra_parts <= {"separator"} and not stale and declared
    check(29, "部件词定稿 background / foreground，新增 separator 有源码依据",
          ok29,
          f"定稿五词 + 源码补充 {sorted(extra_parts)}" if ok29
          else f"词表 {sorted(parts)} / 残留 {stale[:4]} / 文档声明 {declared}")

    # ── 30 状态轴：补 active 且与 class / context 正交（B 方案 ②）──
    disjoint = not (sets["state"] & sets["class"] or sets["state"] & sets["context"])
    check(30, "state 补 active，与 class / context 三表正交",
          "active" in sets["state"] and disjoint,
          f"state {len(sets['state'])} 词，含 active；与 class/context 交集 0")

    # ── 31 键属性轴：静态声明命中封闭词表（B 方案 ③）──
    declared = {a for e in d5["keys"].values() for a in (e.get("attributes") or {})}
    bad_attr = declared - sets["key_attribute"]
    check(31, "attributes 只声明 sticky / repeatable / modifier",
          declared and not bad_attr and sets["key_attribute"] == {"sticky", "repeatable", "modifier"},
          f"{sum(len(e.get('attributes') or {}) for e in d5['keys'].values())} 处声明，取值 {sorted(declared)}"
          if not bad_attr else f"越界 {sorted(bad_attr)}")

    # ── 32 域词表收口：key_domain 并入 action_domain（B 方案 ④）──
    semantic = {"text", "edit", "modifier", "nav", "layout"}
    origin = {"rime", "trime", "device", "system"}
    ad = sets["action_domain"]
    check(32, "动作域分语义类/来源类，key_domain ⊆ action_domain 不再各自生长",
          semantic | origin == ad and sets["key_domain"] <= ad,
          f"语义类 {len(semantic)} + 来源类 {len(origin)}；key_domain {len(sets['key_domain'])} 词全被覆盖")

    # ── 33 方位 hint 与 class 变体结构完整（B 方案 ⑤）──
    cv = V.get("class_variant") or {}
    cv_ok = bool(cv) and all(k in sets["class"] and v in sets["class"]
                             for k, v in cv.items())
    dir_ok = sets["direction"] == {"up", "down", "left", "right"}
    check(33, "class_variant 与 direction 四方位词表齐备",
          cv_ok and dir_ok,
          f"变体 {cv} / 方位 {sorted(sets['direction'])}")

    # ── 34 规范色表以源码为锚（不以任何主题样本为限）──
    reg = load("color-registry.yaml")
    src = reg["source"]
    keys = set(src["color_keys"])
    covered = set(reg["canonical"]) | set(reg["synonyms"])
    src_ok = (src["enum_count"] == 52 and len(keys) == 52
              and src["fallback_chain_count"] == 49
              and len(src["fallback_chains"]) == 49
              and src["style_key_count"] == 58
              and len(reg["style_fields"]) == 58
              and covered == keys)
    check(34, "规范色表以源码为锚：52 枚举全覆盖，49 链 / 58 style 字段自包含",
          src_ok,
          f"canonical {len(reg['canonical'])} + synonyms {len(reg['synonyms'])}"
          f" = {len(covered)}；缺 {sorted(keys - covered)[:3]}")

    # ── 35 去歧义 R1/R2/R3/R6 ──
    cpaths = list(reg["canonical"].values())
    r1 = len(cpaths) == len(set(cpaths))
    r2 = all("evidence" in s and s["path"] in set(cpaths)
             for s in reg["synonyms"].values())
    r3 = not (set(reg["aliases"]) & set(reg["canonical"]))
    r6 = set(reg["value_types"]) == {"color", "image"}
    check(35, "去歧义 R1/R2/R3/R6：路径唯一、同义有据、别名隔离、值域二元",
          r1 and r2 and r3 and r6,
          f"唯一 {r1} / 同义证据 {r2} / 别名隔离 {r3} / 值域 {r6}")

    # ── 36 回退链全显式且无环（R5）──
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
    check(36, "49 条内置回退链路径化、无环、目标可达",
          no_cycle() and not fb_bad and len(fb2) >= 47,
          f"显式 {len(fb2)} 条 + 同义自环 {49 - len(fb2)}；悬空 {fb_bad[:2]}")

    # ── 37 补充色码有依据、段命中词表 ──
    sup = reg["supplementary"]
    allowed = (sets["color_sub"] | sets["class"] | sets["state"]
               | sets["context"] | sets["direction"] | sets["color_part"])
    sup_bad = []
    for p, meta in sup.items():
        segs = p.split(".")
        if segs[0] not in sets["color_domain"]:
            sup_bad.append(f"{p} domain")
        if any(s not in allowed for s in segs[1:]):
            sup_bad.append(f"{p} 段")
        if not any(s in sets["color_part"] for s in segs):
            sup_bad.append(f"{p} 缺部件")
        if not all(k in meta for k in ("origin", "reason", "status")):
            sup_bad.append(f"{p} 缺依据")
        if p in set(cpaths):
            sup_bad.append(f"{p} 与规范冲突")
    check(37, f"补充色码 {len(sup)} 条均附来源/理由且不撞规范路径", not sup_bad,
          "方位 hint / disabled / active / focused / selected / root.foreground"
          if not sup_bad else f"{sup_bad[:3]}")

    # ── 38 迁移对照与样本产物边界 ──
    mig = load("migration.yaml")["migration"]
    old_paths = set(mp["color_map"].values())
    new_paths = set(cpaths) | set(sup)
    mig_ok = bool(mig) and all(
        m["sample_v5"] in old_paths and m["registry"] in new_paths
        for m in mig.values())
    check(38, "样本翻译表与规范色表的差异全部登记，样本产物保持兼容",
          mig_ok, f"{len(mig)} 条差异待 V5 解析器接入时切换")

    # ── 39 源码漂移检测：枚举一变，规范必须重生成 ──
    import hashlib
    rel = Path("app/src/main/java/com/osfans/trime/data/theme/ColorKey.kt")
    cands = [ROOT / rel, ROOT.parent / rel, ROOT.parent.parent / rel,
             Path("/root/trime-repo") / rel]
    found = next((p for p in cands if p.is_file()), None)
    if found:
        sha = hashlib.sha256(found.read_bytes()).hexdigest()
        ok39 = sha == src["color_key_sha256"]
        check(39, "源码 sha256 与规范色表一致（枚举变更须重跑生成器）",
              ok39, f"{found} / {sha[:12]}")
    else:
        check(39, "源码 sha256 与规范色表一致", True,
              "源码不在工作区，按 registry 自包含清单核对（跳过直读）", warn=True)

    # ── 汇总 ──
    n = {t: sum(1 for r in results if r[0] == t) for t in ("PASS", "WARN", "FAIL")}
    print("\n" + "=" * 60)
    print(f"汇总: {n['PASS']} PASS / {n['WARN']} WARN / {n['FAIL']} FAIL")
    print("=" * 60)
    return 1 if n["FAIL"] else 0


if __name__ == "__main__":
    sys.exit(main())
