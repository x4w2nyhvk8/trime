#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""从 ss.v5.trime.yaml 生成 preview.html（数据内嵌，无外部依赖）。"""
import json
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent
d5 = yaml.safe_load((ROOT / "ss.v5.trime.yaml").read_text(encoding="utf-8"))
mp = yaml.safe_load((ROOT / "v5-map.yaml").read_text(encoding="utf-8"))

# 路径 → 旧字段名（用于点键反查对照）
legacy_by_path = {}
for old, path in mp["color_map"].items():
    legacy_by_path.setdefault(path, []).append(old)
for old, path in mp["label_map"].items():
    legacy_by_path.setdefault(path, []).append(old)

LABELS = {
    "key.edit.space": "空格", "key.edit.enter": "回车", "key.edit.backspace": "⌫",
    "key.modifier.shift": "Shift", "key.layout.number": "123",
    "key.rime.asciiMode": "中/En", "key.rime.delimiter": "分词",
    "key.trime.clipboard": "符", "key.trime.settings": "配色",
    "key.nav.escape": "Esc", "key.edit.cut": "剪切", "key.edit.copy": "复制",
    "key.edit.paste": "粘贴", "key.edit.selectAll": "全选", "key.edit.redo": "重做",
    "key.nav.left": "←", "key.nav.right": "→", "key.nav.up": "↑", "key.nav.down": "↓",
}


def face(e):
    if isinstance(e, str):
        return e
    if "gap" in e and "key" not in e:
        return None
    k = e.get("key", "")
    return LABELS.get(k, k.split(".")[-1] if k.startswith("key.") else k)


def simplify(rows):
    out = []
    for r in rows:
        line = []
        for e in rows and r:
            if isinstance(e, str):
                line.append({"k": e, "c": None, "s": 1, "id": e})
                continue
            if "gap" in e and "key" not in e:
                line.append({"k": None, "gap": e["gap"], "s": e["gap"]})
                continue
            line.append({
                "k": face(e),
                "c": e.get("class"),
                "s": e.get("span", 1),
                "id": e.get("key", ""),
            })
        out.append(line)
    return out


data = {
    "schemes": {sid: sc["color"] for sid, sc in d5["schemes"].items()},
    "pairs": d5["pairs"],
    "rows": simplify(d5["layouts"]["standard26.portrait"]["rows"]),
    "legacy": legacy_by_path,
    "fallback": d5["fallback_colors"],
    "stats": {
        "paths": len(next(iter(d5["schemes"].values()))["color"]),
        "ids": len(d5["keys"]),
        "aliases": len(d5["key_aliases"]),
        "scenes": len(d5["layouts"]),
    },
}
DATA_JSON = json.dumps(data, ensure_ascii=False, separators=(",", ":"))

HTML = r"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Trime V5 主题方案 · 方块</title>
<style>
  :root{
    --bg:#f4f6f9; --panel:#fff; --line:#e2e6ed; --text:#1d2430; --muted:#6b7280;
    --accent:#1a73e9; --code:#f6f7f9;
  }
  *{box-sizing:border-box}
  body{margin:0;padding:20px;background:var(--bg);color:var(--text);
       font:14px/1.6 -apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif}
  .wrap{max-width:1080px;margin:0 auto}
  h1{font-size:20px;margin:0 0 4px}
  .sub{color:var(--muted);font-size:12.5px;margin-bottom:18px}
  .bar{display:flex;gap:16px;flex-wrap:wrap;align-items:center;margin-bottom:14px}
  .group{display:flex;align-items:center;gap:6px}
  .group>span{font-size:12px;color:var(--muted);margin-right:2px}
  button{border:1px solid var(--line);background:var(--panel);color:var(--text);
         padding:5px 12px;border-radius:7px;cursor:pointer;font-size:12.5px}
  button.on{background:var(--accent);border-color:var(--accent);color:#fff}
  .cols{display:grid;grid-template-columns:minmax(0,1fr) 320px;gap:16px}
  @media(max-width:860px){.cols{grid-template-columns:1fr}}
  .card{background:var(--panel);border:1px solid var(--line);border-radius:12px;padding:14px}
  .kv{font:12px/1.7 "SFMono-Regular",Consolas,monospace}
  .kv b{font-weight:600;color:var(--accent)}
  .chip{display:inline-block;padding:1px 7px;border-radius:5px;background:var(--code);
        border:1px solid var(--line);font:11.5px "SFMono-Regular",Consolas,monospace;margin:2px 3px 2px 0}
  hr{border:0;border-top:1px solid var(--line);margin:12px 0}
  h3{font-size:13px;margin:0 0 8px}
  .hint{color:var(--muted);font-size:12px}

  /* 键盘 */
  .kb{border-radius:10px;padding:8px 6px 10px;transition:background .2s}
  .cand{display:flex;gap:6px;padding:6px 4px 8px;align-items:center;min-height:38px}
  .cand .item{padding:4px 12px;border-radius:6px;font-size:14px;cursor:pointer}
  .cand .cm{font-size:11px;opacity:.8;margin-left:5px}
  .pre{padding:2px 8px 8px;font-size:13px;font-family:"SFMono-Regular",Consolas,monospace}
  .row{display:flex;gap:3px;margin-bottom:3px}
  .key{flex:1;min-width:0;height:46px;border-radius:7px;display:flex;align-items:center;
       justify-content:center;font-size:15px;cursor:pointer;user-select:none;
       border:1px solid rgba(0,0,0,.06);transition:filter .12s,transform .08s;
       white-space:nowrap;overflow:hidden;padding:0 3px}
  .key:hover{filter:brightness(.94)}
  .key:active{transform:translateY(1px)}
  .key.sel{outline:2px solid var(--accent);outline-offset:1px}
  .key.gap{background:transparent!important;border:0!important;cursor:default}
  .key.gap:hover{filter:none}
  .foot{margin-top:12px;display:flex;gap:18px;flex-wrap:wrap;font-size:12px;color:var(--muted)}
  .foot b{color:var(--text)}
</style>
</head>
<body>
<div class="wrap">
  <h1>Trime V5 主题方案 · 方块</h1>
  <div class="sub">数据直接内嵌自 <code>ss.v5.trime.yaml</code>；点任意按键可反查 token 路径、色值与 V3 旧名对照。</div>

  <div class="bar">
    <div class="group"><span>配色</span><div id="schBtns"></div></div>
    <div class="group"><span>状态</span><div id="stBtns"></div></div>
  </div>

  <div class="cols">
    <div class="card">
      <div class="kb" id="kb">
        <div class="cand" id="cand"></div>
        <div class="pre" id="pre"></div>
        <div id="rows"></div>
      </div>
      <div class="foot" id="foot"></div>
    </div>

    <div class="card">
      <h3>按键反查</h3>
      <div id="info" class="kv">点击左侧任意按键。</div>
      <hr>
      <h3>状态与回退</h3>
      <div class="hint" id="resolveNote"></div>
      <hr>
      <h3>路径 → V3 旧名</h3>
      <div id="legacy" class="hint">点键后显示。</div>
    </div>
  </div>
</div>

<script id="v5-data" type="application/json">__DATA__</script>
<script>
const D = JSON.parse(document.getElementById('v5-data').textContent);
const SID = Object.keys(D.schemes);
const QS = new URLSearchParams(location.search);
const STATES = ['normal','pressed','selected','capsLock'];
let scheme = D.schemes[QS.get('scheme')] ? QS.get('scheme') : SID[0];
let state = STATES.includes(QS.get('state')) ? QS.get('state') : 'normal';
let picked = null;

function css(v){
  const s = String(v).replace(/^0x/,'');
  const a = parseInt(s.slice(0,2),16)/255;
  const r = parseInt(s.slice(2,4),16), g = parseInt(s.slice(4,6),16), b = parseInt(s.slice(6,8),16);
  return `rgba(${r},${g},${b},${a.toFixed(3)})`;
}
function resolve(domain, part, cls, st){
  const s = D.schemes[scheme], c = [];
  if(cls && st) c.push(`${domain}.${part}.${cls}.${st}`);
  if(cls) c.push(`${domain}.${part}.${cls}`);
  if(st) c.push(`${domain}.${part}.${st}`);
  c.push(`${domain}.${part}`);
  for(const p of c) if(s[p] !== undefined) return {path:p, val:s[p]};
  return {path:c[c.length-1], val:'0x00000000'};
}
function lum(v){
  const s=String(v).replace(/^0x/,'');
  const ch=[2,4,6].map(i=>parseInt(s.slice(i,i+2),16)/255)
    .map(x=>x<=.03928?x/12.92:Math.pow((x+.055)/1.055,2.4));
  return .2126*ch[0]+.7152*ch[1]+.0722*ch[2];
}
function contrast(a,b){
  const x=lum(a), y=lum(b), hi=Math.max(x,y), lo=Math.min(x,y);
  return ((hi+.05)/(lo+.05)).toFixed(2);
}
function stOf(cls){ return state === 'normal' ? null : state; }

function render(){
  const s = D.schemes[scheme], st = stOf();
  const root = resolve('root','surface',null,null);
  const kb = document.getElementById('kb');
  kb.style.background = css(root.val);

  // 候选栏
  const cs = resolve('candidate','surface',null,st==='selected'?null:st);
  const ct = resolve('candidate','content',null,st);
  const cc = resolve('candidate','comment','content',st==='selected'?'selected':null);
  const cl = resolve('candidate','label','content',null);
  const selS = resolve('candidate','surface',null,'selected');
  const selT = resolve('candidate','content',null,'selected');
  const cand = document.getElementById('cand');
  cand.style.background = css(cs.val);
  cand.innerHTML = [
    `<div class="item" style="color:${css(selT.val)};background:${css(selS.val)}">同文<span class="cm" style="color:${css(cc.val)}">tóng wén</span></div>`,
    `<div class="item" style="color:${css(ct.val)}">同闻<span class="cm" style="color:${css(cc.val)}">tóng wén</span></div>`,
    `<div class="item" style="color:${css(ct.val)}">铜<span class="cm" style="color:${css(cl.val)}">2</span></div>`
  ].join('');

  // 编码区
  const pt = resolve('preedit','content',null,st==='selected'?'selected':null);
  const pre = document.getElementById('pre');
  pre.style.color = css(pt.val);
  pre.textContent = "tong'wen │ 同文输入法";

  // 键盘
  const rows = document.getElementById('rows');
  rows.innerHTML = '';
  D.rows.forEach((r,ri)=>{
    const div = document.createElement('div'); div.className='row';
    const total = r.reduce((a,e)=>a+(e.s||1),0);
    r.forEach((e,ci)=>{
      const k = document.createElement('div');
      k.className = 'key' + (e.k===null?' gap':'') + (picked===`${ri}:${ci}`?' sel':'');
      k.style.flex = String(e.s||1);
      if(e.k===null){ div.appendChild(k); return; }
      const cls = e.c || 'character';
      const surf = resolve('key','surface',cls,st);
      const cont = resolve('key','content',cls,st);
      const hint = resolve('key','hint',cls,st);
      k.style.background = css(surf.val);
      k.style.color = css(cont.val);
      k.style.borderColor = css(resolve('key','outline',cls,st).val);
      k.textContent = e.k;
      k.title = `${e.id||e.k} · ${surf.path}`;
      k.onclick = ()=>{ picked = `${ri}:${ci}`; showInfo(e, cls, surf, cont, hint, st); render(); };
      div.appendChild(k);
    });
    rows.appendChild(div);
  });

  document.getElementById('foot').innerHTML =
    `<span>方案 <b>${scheme}</b></span><span>状态 <b>${state}</b></span>`+
    `<span>路径 <b>${D.stats.paths}</b>/套</span><span>行为 ID <b>${D.stats.ids}</b></span>`+
    `<span>别名 <b>${D.stats.aliases}</b></span><span>场景 <b>${D.stats.scenes}</b></span>`+
    `<span>根背景 <b>${root.val}</b></span>`;
  document.getElementById('resolveNote').textContent =
    `查找顺序：${st?`${'key'}.surface.<class>.${st} → `:''}key.surface.<class> → ${st?`key.surface.${st} → `:''}key.surface（长路径优先，短路径回退）。`+
    `共 ${Object.keys(D.fallback).length} 条 fallback 链可在运行时继续回退。`;
}

function showInfo(e, cls, surf, cont, hint, st){
  const ratio = contrast(cont.val, surf.val);
  const dom = (id)=>id.startsWith('key.')?id:'字面字符 '+id;
  document.getElementById('info').innerHTML =
    `<div>按键 <b>${e.k}</b></div>`+
    `<div>身份 <b>${dom(e.id)}</b></div>`+
    `<div>class <b>${cls}</b>　state <b>${state}</b></div><hr>`+
    `<div>surface <b>${surf.path}</b></div><div>　${surf.val}</div>`+
    `<div>content <b>${cont.path}</b></div><div>　${cont.val}</div>`+
    `<div>hint　　<b>${hint.path}</b></div><div>　${hint.val}</div><hr>`+
    `<div>对比度 <b>${ratio}:1</b> ${ratio>=4.5?'✓ AA':(ratio>=3?'△ AA Large':'✗ 不足')}</div>`+
    `<div>span <b>${e.s}</b>（行内相对权重）</div>`;
  const paths = [surf.path, cont.path, hint.path];
  document.getElementById('legacy').innerHTML = paths.map(p=>{
    const olds = D.legacy[p] || [];
    return `<div style="margin-bottom:6px"><span class="chip">${p}</span><br>`+
      (olds.length?olds.map(o=>`<span class="chip">${o}</span>`).join(''):'<i>（无对应旧名，V5 新增语义）</i>')+`</div>`;
  }).join('');
}

// 控件
const sb = document.getElementById('schBtns');
SID.forEach(s=>{
  const b=document.createElement('button'); b.textContent=s; b.dataset.s=s;
  b.onclick=()=>{scheme=s;picked=null;document.getElementById('info').textContent='点击左侧任意按键。';
    document.getElementById('legacy').textContent='点键后显示。';
    [...sb.children].forEach(x=>x.classList.toggle('on',x.dataset.s===s)); render();};
  sb.appendChild(b);
});
const stList=[['normal','常态'],['pressed','按下'],['selected','选中'],['capsLock','大写锁定']];
const tb=document.getElementById('stBtns');
stList.forEach(([v,label])=>{
  const b=document.createElement('button'); b.textContent=label; b.dataset.v=v;
  b.onclick=()=>{state=v;[...tb.children].forEach(x=>x.classList.toggle('on',x.dataset.v===v)); render();};
  tb.appendChild(b);
});
[...sb.children].forEach(x=>x.classList.toggle('on', x.dataset.s===scheme));
[...tb.children].forEach(x=>x.classList.toggle('on', x.dataset.v===state));
render();
</script>
</body>
</html>
"""

(ROOT / "preview.html").write_text(HTML.replace("__DATA__", DATA_JSON), encoding="utf-8")
print(f"preview.html 已生成：{len(DATA_JSON)} 字节内嵌数据，{len(data['schemes'])} 套方案，"
      f"{len(data['rows'])} 行，{len(legacy_by_path)} 条路径映射")
