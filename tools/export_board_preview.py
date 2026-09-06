#!/usr/bin/env python3
"""Offline UI design preview from real targets; sample state, NOT a game screenshot."""
import argparse
import base64
import csv
import html
import io
import json
from pathlib import Path
import zipfile
from build_task_icons import ClientTextures, ROOT
from task_icon_animation import decode_texture, gif_bytes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client-jar", required=True, type=Path)
    args = parser.parse_args()
    specs = json.loads((ROOT / "src/main/resources/target-icons.json").read_text())
    with (ROOT / "src/main/resources/Targets.csv").open() as source:
        rows = [r for r in csv.DictReader(source) if int(r["score"]) > 0]
    goals = [r for r in rows if r["type"] == "goal" and int(r["score"]) <= 10]
    picks = ["SPY_ON_20_UNIQUE_MOBS", "OBTAIN_5_UNIQUE_DISCS", "MINE_DIAMOND_ORE", "KILL_WITHER"]
    chosen = [r for name in picks for r in goals if r["id"] == name]
    chosen += [r for r in goals if r not in chosen][:20]
    chosen += [r for r in rows if r["type"] == "block" and int(r["score"]) <= 10][:64-len(chosen)]
    bonuses = [r for r in rows if int(r["score"]) >= 11][:3]
    total = sum(int(r["score"]) for r in chosen)
    score = sum(int(r["score"]) for i, r in enumerate(chosen) if i in (4, 8, 21))
    with zipfile.ZipFile(args.client_jar) as client, zipfile.ZipFile(ROOT / "target/task-ui/BlockRacing-TaskIcons-26.2.zip") as pack:
        textures = ClientTextures(client)

        def picture(row):
            if row["id"] in specs:
                suffix = "_bonus" if int(row["score"]) >= 11 else ""
                path = "assets/blockracing/textures/item/task/" + row["id"].lower() + suffix + ".png"
                frames, order, durations = decode_texture(pack, path)
                content = gif_bytes(tuple(frames[i] for i in order), durations) if len(order) > 1 else frames[0]
                kind = "gif" if len(order) > 1 else "png"
            else:
                image = textures.icon(row["id"])
                output = io.BytesIO()
                image.save(output, "PNG")
                content, kind = output.getvalue(), "png"
            return f"data:image/{kind};base64," + base64.b64encode(content).decode()

        def cell(row, index, bonus=False):
            state = "resolved" if index in (4, 8, 21) else "queued" if index in (14, 15) else "active"
            status = {"resolved": "已结算", "queued": "未开放", "active": "可完成"}[state]
            if index == 0: status = "8 / 20"
            title = html.escape(row["中文显示"], quote=True)
            rule = html.escape(row["requirement"], quote=True)
            return f'''<button class="task {state}{' bonus' if bonus else ''}" data-title="{title}" data-rule="{rule}" aria-label="{title}，{status}">
<span class="number">#{index+1}</span><span class="points">{row['score']}分</span>
<img src="{picture(row)}" alt=""><span class="title">{title}</span><span class="status">{status}</span>
<span class="progress" style="--done:{40 if index == 0 else 0}%"></span></button>'''

        cells = "".join(cell(r, i) for i, r in enumerate(chosen))
        bonus_cells = "".join(cell(r, i + 64, True) for i, r in enumerate(bonuses))
    document = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>BlockRacing · Tab 目标面板设计预览</title><style>
*{box-sizing:border-box}body{margin:0;background:#0a1019;color:#f1f5f9;font:14px system-ui,sans-serif}
header{padding:12px 20px;background:#152232;display:flex;gap:12px;flex-wrap:wrap;align-items:center}header p{margin:0;color:#afbdca}
button{font:inherit;cursor:pointer}.toggle{background:#304c64;color:#f1f5f9;border:1px solid #7897ac;border-radius:4px;padding:6px 12px}
.scene{min-height:calc(100vh - 60px);display:flex;align-items:center;justify-content:center;padding:16px;background:radial-gradient(ellipse at center,#203b40,#0a1019 75%)}
.board{background:#111c2a;padding:14px;width:min(760px,100%);box-shadow:0 14px 65px #0008}.board[hidden]{display:none}
h1{font-size:21px;margin:0 0 4px}.summary{color:#9eb7ce;margin-bottom:12px}.grid{display:grid;grid-template-columns:repeat(8,minmax(0,1fr));gap:3px}
.task{position:relative;border:0;border-radius:0;min-width:0;aspect-ratio:1;background:#1b3044;color:#f1f5f9;padding:3px;display:flex;align-items:center;flex-direction:column;justify-content:end;gap:1px}
.task:hover,.task:focus-visible{outline:2px solid #9eebee;z-index:1;background:#304c64}.number,.points{position:absolute;top:3px;font-size:10px}.number{left:4px;color:#96a9b8}.points{right:4px;color:#f0cb83}
.task img{width:28%;height:28%;image-rendering:pixelated;margin-top:10px}.title{width:100%;font-size:12px;line-height:1.2;height:2.4em;overflow:hidden;overflow-wrap:anywhere;text-align:left}.status{align-self:start;font-size:10px;color:#afbdca}.resolved{background:#202b32;color:#9aaab5}.queued{color:#9aaab5}
.progress{position:absolute;bottom:0;left:0;width:var(--done);height:2px;background:#77d5ca}.bonus-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:6px}.bonus{aspect-ratio:auto;height:66px;border-left:3px solid #f0bc62;background:#292937;display:grid;grid-template-columns:36px 1fr;grid-template-rows:1fr 16px;padding:5px 8px}.bonus img{width:28px;height:28px;margin:0;grid-column:1;grid-row:1/3}.bonus .number{display:none}.bonus .title{height:auto;grid-column:2;grid-row:1;margin-top:6px}.bonus .points{position:absolute;top:auto;bottom:5px;right:8px}.bonus .status{position:absolute;bottom:5px;left:48px}.bonus-label{font-size:12px;color:#f0bc62;margin:10px 0 6px}
footer{font-size:12px;color:#afbdca;margin-top:10px}#detail{margin-top:8px;padding:8px;background:#152232;min-height:48px;overflow-wrap:anywhere;font-size:12px}#detail strong{display:block;color:#9eebee;margin-bottom:3px}
@media(max-width:600px){.scene{padding:5px}.board{padding:7px}.title{font-size:9px}.status,.number,.points{font-size:8px}.task img{width:24%;height:24%;margin-top:9px}.bonus .title{font-size:10px}}
</style><header><button class="toggle" id="toggle">Tab · 打开 / 关闭</button><p>设计预览：真实目标与图标，示例队伍状态；不是实机截图。游戏中为等比缩放面板。</p></header>
<main class="scene"><section class="board" id="board" aria-label="本局目标面板"><h1>本局目标</h1><div class="summary">@@SUMMARY@@</div>
<div class="grid">''' + cells + '''</div><div class="bonus-label">BONUS · 不计胜利分</div><div class="bonus-grid">''' + bonus_cells + '''</div>
<footer>Tab / Esc 关闭 · 悬停查看规则 · 关闭后 Shift+Tab 查看玩家列表</footer><div id="detail" aria-live="polite">悬停或用键盘选中目标，查看完整名称与规则。</div></section></main>
<script>
const board=document.querySelector('#board');
function toggle(){board.hidden=!board.hidden}
document.querySelector('#toggle').addEventListener('click',toggle);
document.addEventListener('keydown',e=>{if(e.key==='Tab'&&!e.shiftKey&&!e.repeat){e.preventDefault();toggle()}if(e.key==='Escape')board.hidden=true});
document.querySelectorAll('.task').forEach(cell=>{function inspect(){const detail=document.querySelector('#detail');detail.replaceChildren();const title=document.createElement('strong');title.textContent=cell.dataset.title;detail.append(title,document.createTextNode(cell.dataset.rule||'获得对应物品'));}cell.addEventListener('mouseenter',inspect);cell.addEventListener('focus',inspect)});
</script></html>'''
    document = document.replace("@@SUMMARY@@", f"红队　{score} / {(total + 1) // 2} 胜利分　·　总分 {total}")
    output = ROOT / "target/task-ui/board-preview.html"
    output.write_text(document, encoding="utf-8")
    print(json.dumps({"main": len(chosen), "bonus": len(bonuses), "html": str(output)}))


if __name__ == "__main__":
    main()
