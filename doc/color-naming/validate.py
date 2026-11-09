#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2015 - 2026 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later
"""校验本目录的颜色命名提案数据是否自洽。

用法：cd doc/color-naming && python3 validate.py
依赖：PyYAML（仅解析 develop-color.map.yaml 用）。

检查项：
  1. token 引用链：所有 palette/role/component token 在两套主题下可解析、无悬空/循环、值合法；
  2. 主题覆盖：两套主题的 role 键与 role 组完全对齐；
  3. 变体规则：selector 引用的 token 全部有效；
  4. 映射表：develop 全部颜色字段被覆盖，目标 token 存在，selector 与变体表一致；
  5. 对比度：主键文字/键面、强调键白字/蓝底满足 WCAG；
  6. 预览原型：内嵌 token / legacy 数据与本目录两份数据文件一致，无占位符、无外链。
"""
from __future__ import annotations

import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TOKENS = os.path.join(HERE, "keyboard.color.tokens.json")
MAPPING = os.path.join(HERE, "develop-color.map.yaml")
PREVIEW = os.path.join(HERE, "preview.html")
FIELDS = os.path.join(HERE, "develop-fields.json")

HEX8 = re.compile(r"^#[0-9A-Fa-f]{8}$")
REF = re.compile(r"^\{([\w.]+)\}$")


class Check:
    def __init__(self) -> None:
        self.ok = True

    def __call__(self, label: str, cond: bool, extra: str = "") -> None:
        print(f'{"OK  " if cond else "FAIL"} | {label}{(" · " + extra) if extra else ""}')
        if not cond:
            self.ok = False


def load_json(path: str):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def node_at(root: dict, path: str):
    cur = root
    for seg in path.split("."):
        if not isinstance(cur, dict) or seg not in cur:
            return None
        cur = cur[seg]
    return cur


def resolve(root: dict, path: str, theme: str | None, seen: frozenset = frozenset()) -> str:
    """按 {token.path} 逐跳解析；theme 非空时用 $extensions.themes[theme].role 覆盖 role 层。"""
    if path in seen:
        raise ValueError(f"循环引用: {path}")
    seen = seen | {path}
    if theme and path.startswith("role."):
        overrides = root["$extensions"]["org.trime"]["themes"][theme]["role"]
        key = path[5:]
        if key in overrides:
            return overrides[key]
    node = node_at(root, path)
    if not isinstance(node, dict) or not isinstance(node.get("$value"), str):
        raise ValueError(f"悬空引用: {path}")
    value = node["$value"].strip()
    m = REF.fullmatch(value)
    return resolve(root, m.group(1), theme, seen) if m else value


def collect_tokens(root: dict, group: str) -> list[str]:
    out: list[str] = []

    def walk(node, path: str) -> None:
        if isinstance(node, dict):
            if "$value" in node:
                out.append(path)
                return
            for k, v in node.items():
                if not k.startswith("$"):
                    walk(v, f"{path}.{k}" if path else k)

    walk(root[group], group)
    return out


def role_keys(root: dict) -> set[str]:
    return {p[5:] for p in collect_tokens(root, "role")}


def rgba(hex8: str) -> tuple[int, int, int, int]:
    h = hex8.lstrip("#")
    return tuple(int(h[i : i + 2], 16) for i in (0, 2, 4, 6))  # type: ignore[return-value]


def over(fg: str, bg: str) -> tuple[int, int, int]:
    fr, fg_, fb, fa = rgba(fg)
    br, bg_, bb, _ = rgba(bg)
    a = fa / 255.0
    return tuple(round(x * a + y * (1 - a)) for x, y in zip((fr, fg_, fb), (br, bg_, bb)))  # type: ignore[return-value]


def luminance(c) -> float:
    s = []
    for x in c:
        x /= 255.0
        s.append(x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4)
    return 0.2126 * s[0] + 0.7152 * s[1] + 0.0722 * s[2]


def contrast(fg: str, bg: str) -> float:
    a, b = luminance(over(fg, bg)), luminance(rgba(bg)[:3])
    hi, lo = max(a, b), min(a, b)
    return (hi + 0.05) / (lo + 0.05)


def main() -> int:
    chk = Check()
    try:
        import yaml
    except ImportError:
        print("FAIL | 需要 PyYAML：pip3 install pyyaml")
        return 1

    T = load_json(TOKENS)
    M = yaml.safe_load(open(MAPPING, encoding="utf-8"))
    ext = T["$extensions"]["org.trime"]
    themes = list(ext["themes"])

    # 1) token 链
    tokens = [p for g in ("palette", "role", "component") for p in collect_tokens(T, g)]
    bad = []
    for p in tokens:
        for th in themes:
            try:
                v = resolve(T, p, th)
                if not HEX8.fullmatch(v):
                    bad.append((p, th, f"非法色值 {v}"))
            except ValueError as e:
                bad.append((p, th, str(e)))
    chk(f"{len(tokens)} 个 token × {len(themes)} 套主题可解析且为 #RRGGBBAA", not bad, str(bad[:3]))

    # 2) 主题 role 键对齐
    rk = role_keys(T)
    for th in themes:
        ks = set(ext["themes"][th]["role"])
        chk(f"{th} role 覆盖键与 role 组一致", ks == rk,
            f"多={sorted(ks - rk)} 缺={sorted(rk - ks)}")

    # 3) 变体引用
    vb = []
    for v in ext["variants"]:
        for part in ("surface", "content"):
            if part in v:
                m = REF.fullmatch(v[part].strip())
                try:
                    resolve(T, m.group(1), themes[0]) if m else None
                except (ValueError, AttributeError) as e:
                    vb.append((v["selector"], part, str(e)))
    chk(f"{len(ext['variants'])} 条变体规则引用有效", not vb, str(vb[:2]))

    # 4) 映射表
    ms = M["mappings"]
    legacy_names = [m["legacy"] for m in ms]
    chk("映射表可解析且 legacy 名无重复", len(legacy_names) == len(set(legacy_names)),
        f"({len(ms)} 条)")
    fields = load_json(FIELDS)
    allf = set(fields["colorKey"]) | set(fields["schema"]) | set(fields["customLabels"])
    covered = {m["legacy"] for m in ms}
    chk(f"develop 全部 {len(allf)} 个颜色字段被覆盖", allf <= covered,
        f"缺={sorted(allf - covered)}")
    miss = sorted({m["token"] for m in ms if node_at(T, m["token"]) is None})
    chk("映射目标 token 全部存在", not miss, str(miss[:5]))
    # 组合态选择器（如 key:pressed[role=system]）由变体规则按 标签/状态/属性 三部分匹配，
    # 这里要求每个 selector 的每个组成部分都能在某条变体规则里找到，避免映射表写出
    # 引擎根本无法命中的选择器。
    def parts(sel: str) -> set[str]:
        s = re.sub(r"\[([^=\]]+)=([^\]]+)\]", lambda m: f" [{m.group(1)}={m.group(2)}] ", sel)
        out = set()
        for tok in re.findall(r"[a-zA-Z]+:([a-zA-Z]+)|\[([^=\]]+)=([^\]]+)\]|([a-zA-Z]+)", s):
            if tok[0]:
                out.add(":" + tok[0])
            elif tok[1]:
                out.add(f"[{tok[1]}={tok[2]}]")
            elif tok[3]:
                out.add(tok[3])
        return out

    variant_parts: set[str] = set()
    for v in ext["variants"]:
        variant_parts |= parts(v["selector"])
    unexplained = sorted(
        {m["selector"] for m in ms
         if m.get("selector") and not parts(m["selector"]) <= variant_parts}
    )
    chk("映射中的状态选择器均可由变体规则解释", not unexplained,
        f"({len([m for m in ms if m.get('selector')])} 条带选择器, 无法解释={unexplained[:3]})")
    amb = [m["legacy"] for m in ms if m.get("ambiguous")]
    chk("语义歧义字段已标注", amb == ["shadow_color"], str(amb))
    stats = M["stats"]
    chk("stats 与实际一致",
        stats["developUnionFields"] == len(allf)
        and stats["mappedLegacyFields"] == len(ms)
        and stats["newComponentTokens"] == len(collect_tokens(T, "component"))
        and stats["variantRules"] == len(ext["variants"]),
        str(stats))

    # 5) 对比度
    for th in themes:
        surf = resolve(T, "component.key.surface", th)
        cont = resolve(T, "component.key.content", th)
        r = contrast(cont, surf)
        chk(f"{th} 主键文字/键面 ≥ 4.5", r >= 4.5, f"({r:.2f}:1)")
        role = ext["themes"][th]["role"]
        r2 = contrast(role["content.onAccent"], role["accent.primary"])
        chk(f"{th} 强调键文字/底色 ≥ 3.0", r2 >= 3.0, f"({r2:.2f}:1)")

    # 6) 预览原型一致性
    html = open(PREVIEW, encoding="utf-8").read()
    m = re.search(r"const T = (\{.*?\});\nconst LEGACY = (\{.*?\});", html, re.S)
    chk("预览内嵌 T / LEGACY 数据可提取", m is not None)
    if m:
        ht = json.loads(m.group(1))
        hl = json.loads(m.group(2))
        chk("预览内嵌 token 与文件一致", ht == T)
        expect = {x["legacy"]: {"token": x["token"], "selector": x.get("selector", ""),
                                "note": x.get("note", "")} for x in ms}
        # 生成器仅收敛了「二维布局后」措辞，其余逐字一致
        for k, v in expect.items():
            v = dict(v)
            if "二维布局后" in v["note"]:
                v["note"] = v["note"].replace("二维布局后改为 role 绑定", "改由 role 绑定，与位置无关")
            expect[k] = v
        chk("预览内嵌旧字段映射与文件一致", hl == expect,
            f"({len(hl)} 条)")
    chk("预览无占位符残留", "/*__TOKENS__*/" not in html and "/*__LEGACY__*/" not in html)
    chk("预览无外部依赖", "http://" not in html and "https://" not in html)

    print("\n==== 总体:", "全部通过" if chk.ok else "存在失败项", "====")
    return 0 if chk.ok else 1


if __name__ == "__main__":
    sys.exit(main())
