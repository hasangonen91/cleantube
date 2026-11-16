#!/usr/bin/env python3
"""uiautomator XML'inden eleman bulur. Kullanim:
  ui_tap.py dump.xml texts            -> ekrandaki tum metin / content-desc degerleri
  ui_tap.py dump.xml tap "Metin"      -> eslesen elemanin merkezini 'x y' olarak yazar (en alttaki eslesme)
  ui_tap.py dump.xml top "Metin"      -> en ustteki eslesme
  ui_tap.py dump.xml card             -> ilk buyuk tiklanabilir video karti
  ui_tap.py dump.xml edit             -> ilk EditText
Bulamazsa cikis kodu 1."""
import re, sys
import xml.etree.ElementTree as ET

def nodes(path):
    for n in ET.parse(path).iter("node"):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
        if not m: continue
        x1, y1, x2, y2 = map(int, m.groups())
        yield n, (x1, y1, x2, y2)

def center(b): return f"{(b[0]+b[2])//2} {(b[1]+b[3])//2}"

path, mode = sys.argv[1], sys.argv[2]
arg = sys.argv[3] if len(sys.argv) > 3 else ""
if mode == "texts":
    seen = []
    for n, b in nodes(path):
        for k in ("text", "content-desc"):
            v = (n.get(k) or "").strip()
            if v and v not in seen: seen.append(v)
    print(" | ".join(seen))
    sys.exit(0)
if mode in ("tap", "top"):
    hits = [(n, b) for n, b in nodes(path) if arg.lower() in ((n.get("text") or "") + " " + (n.get("content-desc") or "")).lower()]
    if not hits: sys.exit(1)
    hits.sort(key=lambda t: t[1][1])
    print(center((hits[-1] if mode == "tap" else hits[0])[1])); sys.exit(0)
if mode == "card":
    for n, b in nodes(path):
        if n.get("clickable") == "true" and (b[2]-b[0]) > 800 and (b[3]-b[1]) > 450 and b[1] > 250:
            print(center(b)); sys.exit(0)
    sys.exit(1)
if mode == "edit":
    for n, b in nodes(path):
        if "EditText" in (n.get("class") or ""):
            print(center(b)); sys.exit(0)
    sys.exit(1)
sys.exit(2)
