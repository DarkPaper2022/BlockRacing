#!/usr/bin/env python3
"""Export the custom-goal icons as a two-column audit table (PNG and standalone HTML).

Uses the actual generated resource-pack PNGs, not approximate redraws. Includes every
enabled goal in CSV order; ordinary block/item tasks keep native icons and are excluded.
"""
import argparse
import base64
import csv
from dataclasses import dataclass
import html
import io
import json
from pathlib import Path
import subprocess
import zipfile

from PIL import Image, ImageDraw, ImageFont
from task_icon_animation import MANIFEST_PATH, decode_texture, gif_bytes, spec_digest

ROOT = Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class Row:
    id: str
    chinese: str
    english: str
    score: int
    bonus: bool
    requirement: str
    material: str
    action: str
    badge: str
    png: bytes
    frames: tuple[bytes, ...] = ()
    durations: tuple[int, ...] = ()
    frame_labels: tuple[str, ...] = ()
    frame_subjects: tuple[tuple[str, ...], ...] = ()

    def pictures(self):
        return self.frames or (self.png,)

    def metadata(self):
        score = f"Bonus 奖励 {self.score} 分（不计胜利进度）" if self.bonus else f"普通任务 · {self.score} 分"
        return f"{score} | 对象 {self.material} | 动作 {self.action} | 数量标识 {self.badge or '无'}"


def load_rows(targets, catalog_path, pack_path, bonus_threshold):
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    rows, seen = [], set()
    with targets.open(encoding="utf-8-sig", newline="") as source, zipfile.ZipFile(pack_path) as pack:
        manifest = json.loads(pack.read(MANIFEST_PATH)) if MANIFEST_PATH in pack.namelist() else None
        if manifest is not None and manifest.get("format") != 1:
            raise ValueError("Unsupported icon audit manifest")
        for target in csv.DictReader(source):
            if target["type"] != "goal" or int(target["score"]) < 1:
                continue
            goal = target["id"]
            if goal in seen:
                raise ValueError(f"Duplicate goal ID: {goal}")
            seen.add(goal)
            spec = catalog.get(goal)
            if spec is None or spec["requirement"] != target["requirement"].strip():
                raise ValueError(f"Icon catalog is missing/stale for {goal}; update it before auditing")
            score = int(target["score"])
            bonus = score >= bonus_threshold
            key = "blockracing:task/" + goal.lower()
            model = json.loads(pack.read(f"assets/minecraft/items/{spec['icon'].lower()}.json"))["model"]
            case = next((c for c in model["cases"] if c["when"] == key), None)
            if case is None:
                raise ValueError(f"Resource pack does not contain the model selection for {goal}")
            branch = case["model"]["on_true" if bonus else "on_false"]["model"]
            expected = f"blockracing:item/task/{goal.lower()}" + ("_bonus" if bonus else "")
            if branch != expected:
                raise ValueError(f"Unexpected resource-pack model for {goal}: {branch}")
            texture = f"assets/blockracing/textures/item/task/{goal.lower()}" + ("_bonus" if bonus else "") + ".png"
            physical, order, durations = decode_texture(pack, texture)
            if manifest is None:
                if len(physical) > 1:
                    raise ValueError("Animated pack needs an audit manifest to identify all candidates")
                plan = [{"label": "静态图标（旧版资源包）", "subjects": spec["subjects"]}]
            else:
                entry = manifest["goals"][goal]
                if entry["spec_sha256"] != spec_digest(spec):
                    raise ValueError(f"Resource pack is stale for {goal}; rebuild the pack")
                plan = entry["frames"]
                if len(plan) != len(physical):
                    raise ValueError(f"Frame manifest/texture count mismatch: {goal}")
                if sorted(s for f in plan for s in f["subjects"]) != sorted(spec["subjects"]):
                    raise ValueError(f"Frame manifest omits or duplicates a candidate: {goal}")
                if set(order) != set(range(len(physical))):
                    raise ValueError(f"Playback omits physical frames: {goal}")
            frames = tuple(physical[index] for index in order)
            rows.append(Row(goal, target["中文显示"], target["display_name"], score, bonus,
                            target["requirement"], spec["icon"], spec["action"], spec["badge"], frames[0], frames, durations,
                            tuple(plan[index]["label"] for index in order),
                            tuple(tuple(plan[index]["subjects"]) for index in order)))
    if not rows:
        raise ValueError("No enabled custom goals found")
    return rows


def write_html(rows, output, threshold):
    def data_url(data, kind="png"):
        return f"data:image/{kind};base64," + base64.b64encode(data).decode("ascii")

    entries = []
    for number, row in enumerate(rows, 1):
        frames = row.pictures()
        animated = len(frames) > 1
        gif = data_url(gif_bytes(frames, row.durations), "gif") if animated else ""
        strip, controls = "", ""
        if animated:
            cards = []
            for index, frame in enumerate(frames):
                label = row.frame_labels[index] if row.frame_labels else f"帧 {index + 1}"
                subjects = " / ".join(row.frame_subjects[index]) if row.frame_subjects else ""
                cards.append(f'''<button type="button" class="frame-select" data-frame="{index}" aria-label="查看第 {index + 1} 帧">
<img width="64" height="64" alt="" src="{data_url(frame)}">
<span>#{index + 1} · {row.durations[index]} ms</span><span>{html.escape(label)}</span>
{('<small>' + html.escape(subjects) + '</small>') if subjects != label else ''}</button>''')
            strip = f'''<details open class="all-frames"><summary>全部 {len(frames)} 帧 · {len(set(s for f in row.frame_subjects for s in f))} 个对象 · 点击帧可定格核对</summary>
<div class="frame-grid">{''.join(cards)}</div></details>'''
            controls = '''<div class="row-controls"><button type="button" data-action="previous" aria-label="上一帧">←</button>
<button type="button" data-action="toggle">播放</button><button type="button" data-action="next" aria-label="下一帧">→</button></div>
<span class="frame-status" aria-live="polite">第 1 帧</span>'''
        timing = f" · {len(frames)} 帧 · 一轮 {sum(row.durations) / 1000:g} 秒" if animated else " · 静态"
        entries.append(f'''<tr id="{html.escape(row.id, quote=True)}" data-animated="{str(animated).lower()}" data-playing="false" data-current="0">
<td class="icon"><span>{number:03d}</span><img class="hero" width="96" height="96" alt="{html.escape(row.id, quote=True)}"
src="{data_url(row.png)}" data-first="{data_url(row.png)}" data-gif="{gif}">{controls}</td>
<td><h2>{html.escape(row.chinese)}</h2><p class="english">{html.escape(row.english)}</p>
<p class="meta">{html.escape(row.metadata() + timing)}</p><code>{html.escape(row.id)}</code>
{strip}<p class="rule">规则：{html.escape(row.requirement)}</p></td></tr>''')
    controls = '''<div class="toolbar" hidden><input id="search" type="search" placeholder="搜索名称、ID、候选物品…" aria-label="搜索目标">
<label><input id="animated-only" type="checkbox">只看轮播目标</label>
<button id="pause-all" type="button">全部暂停</button><button id="play-all" type="button">全部播放</button>
<button id="collapse-all" type="button">收起全部帧</button><button id="expand-all" type="button">展开全部帧</button>
<output id="result-count" aria-live="polite"></output></div>'''
    script = r"""<script>
(() => {
  const rows = Array.from(document.querySelectorAll('tbody tr'));
  const moving = rows.filter(row => row.dataset.animated === 'true');
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  function setPlaying(row, playing) {
    const hero = row.querySelector('.hero');
    const frames = row.querySelectorAll('.frame-select img');
    row.dataset.playing = String(playing);
    hero.src = playing ? hero.dataset.gif : frames[Number(row.dataset.current)].src;
    row.querySelector('[data-action="toggle"]').textContent = playing ? '暂停' : '播放';
    row.querySelector('.frame-status').textContent = playing ? '自动轮播' : '第 ' + (Number(row.dataset.current) + 1) + ' 帧';
    row.querySelectorAll('.frame-select').forEach((button, i) => {
      button.setAttribute('aria-pressed', String(!playing && i === Number(row.dataset.current)));
    });
  }
  function select(row, index) {
    const count = row.querySelectorAll('.frame-select').length;
    row.dataset.current = String((index + count) % count);
    setPlaying(row, false);
  }
  moving.forEach(row => {
    row.querySelector('.row-controls').classList.add('active');
    row.addEventListener('click', event => {
      const frame = event.target.closest('.frame-select');
      if (frame) { select(row, Number(frame.dataset.frame)); return; }
      const control = event.target.closest('[data-action]');
      if (!control) return;
      const action = control.dataset.action;
      if (action === 'toggle') setPlaying(row, row.dataset.playing !== 'true');
      else select(row, Number(row.dataset.current) + (action === 'next' ? 1 : -1));
    });
    setPlaying(row, !reduceMotion.matches);
  });
  document.querySelector('.toolbar').hidden = false;
  function filter() {
    const query = document.querySelector('#search').value.toLocaleLowerCase().trim();
    const onlyAnimated = document.querySelector('#animated-only').checked;
    let visible = 0;
    rows.forEach(row => {
      row.hidden = (onlyAnimated && row.dataset.animated !== 'true') || !row.textContent.toLocaleLowerCase().includes(query);
      if (!row.hidden) visible++;
    });
    document.querySelector('#result-count').textContent = visible + ' / ' + rows.length + ' 个目标';
  }
  document.querySelector('#search').addEventListener('input', filter);
  document.querySelector('#animated-only').addEventListener('change', filter);
  document.querySelector('#pause-all').addEventListener('click', () => moving.forEach(row => setPlaying(row, false)));
  document.querySelector('#play-all').addEventListener('click', () => moving.forEach(row => setPlaying(row, true)));
  document.querySelector('#collapse-all').addEventListener('click', () => document.querySelectorAll('details').forEach(d => d.open = false));
  document.querySelector('#expand-all').addEventListener('click', () => document.querySelectorAll('details').forEach(d => d.open = true));
  reduceMotion.addEventListener('change', event => { if (event.matches) moving.forEach(row => setPlaying(row, false)); });
  window.addEventListener('beforeprint', () => moving.forEach(row => setPlaying(row, false)));
  filter();
})();
</script>"""
    output.write_text('''<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>BlockRacing 目标图标审计</title>
<style>
body{margin:24px;background:#0b1220;color:#e2e8f0;font:16px/1.55 system-ui,sans-serif}
main{max-width:1280px;margin:auto}h1{font-size:26px}table{width:100%;border-collapse:collapse;table-layout:fixed}
th{text-align:left;background:#20304b;padding:12px}th:first-child{width:124px}
td{padding:16px;border-bottom:1px solid #334155;vertical-align:top}tr:nth-child(odd){background:#172235}
.icon{text-align:center}.icon>span{display:block;color:#94a3b8;margin-bottom:8px}img{image-rendering:pixelated}
.hero{background:#111827}h2{font-size:21px;margin:0;color:#f8fafc}p{margin:5px 0}.english{color:#cbd5e1}.meta{color:#67e8f9}
code,.rule{font:13px/1.6 ui-monospace,monospace;overflow-wrap:anywhere;white-space:pre-wrap}.rule{color:#94a3b8}
button,input[type=search]{font:inherit;background:#20304b;color:#e2e8f0;border:1px solid #475569;border-radius:5px;padding:6px}
button{cursor:pointer}button:focus-visible,input:focus-visible{outline:2px solid #22d3ee;outline-offset:2px}
.toolbar:not([hidden]){display:flex;gap:10px;flex-wrap:wrap;align-items:center;position:sticky;top:0;background:#0b1220;padding:12px 0;z-index:2}
.toolbar input[type=search]{min-width:220px}.row-controls{display:none}.row-controls.active{display:flex;justify-content:center;gap:3px;margin-top:8px}
.frame-status{font-size:12px}summary{cursor:pointer;color:#a5f3fc}.all-frames{margin:12px 0}
.frame-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(145px,1fr));gap:8px;margin-top:8px}
.frame-select{display:flex;flex-direction:column;align-items:center;gap:4px;background:#111827;font-size:11px;overflow-wrap:anywhere;min-width:0}
.frame-select span,.frame-select small{display:block;max-width:100%}.frame-select[aria-pressed=true]{border:2px solid #22d3ee}
@media print{body{background:white;color:black;margin:0}tr:nth-child(odd),th{background:#eee}h2,.meta,.rule,.english{color:black}
.toolbar,.row-controls{display:none!important}tr{break-inside:avoid}.frame-grid{display:grid}}
</style><main><h1>BlockRacing 目标图标审计</h1>'''
        + f"<p>{len(rows)} 个启用的行为/组合目标 · {sum(len(r.pictures()) > 1 for r in rows)} 个轮播目标 · Bonus 阈值 {threshold} 分。普通物品原版图标不在本表。</p>"
        + "<p>左侧播放资源包实际帧（含 Bonus 角标），右侧完整展开全部帧、对象名称和规则；可暂停、逐帧查看、搜索。所有素材内嵌，离线可用。</p>"
        + "<p>铜方块按涂蜡/氧化程度分组：U0–U3 未涂蜡，W0–W3 已涂蜡。动作与需求角标始终不变；轮播不是任务切换。</p>"
        + "<p>这是图标审计表，不是游戏截图；附魔闪光、叠放数字由客户端另行绘制。自动遵循系统减少动画设置；GIF 暂停后可从所选静态帧审计。</p>"
        + controls + '<table><thead><tr><th>图标</th><th>描述 / 审计信息</th></tr></thead><tbody>'
        + "\n".join(entries) + "</tbody></table></main>" + script + "</html>", encoding="utf-8")


def find_font(explicit):
    if explicit:
        return str(explicit)
    try:
        result = subprocess.run(["fc-match", "-f", "%{file}", "Noto Sans CJK SC:lang=zh-cn"],
                                capture_output=True, text=True, check=True, timeout=10)
        if result.stdout.strip():
            return result.stdout.strip()
    except (OSError, subprocess.SubprocessError):
        pass
    raise ValueError("Chinese font not found; pass --font /path/to/chinese-font.ttf or use --format html")


def wrap_text(text, font, max_width):
    """Pixel-width wrapping, including CJK and long technical identifiers; never ellipsize."""
    lines = []
    for paragraph in text.split("\n"):
        line = ""
        for character in paragraph:
            if line and font.getlength(line + character) > max_width:
                lines.append(line)
                line = ""
            line += character
        lines.append(line)
    return lines


def layout_rows(rows, font_path, width):
    fonts = {"title": ImageFont.truetype(font_path, 23), "normal": ImageFont.truetype(font_path, 16),
             "small": ImageFont.truetype(font_path, 13)}
    layout = []
    for row in rows:
        content = []
        for text, style, color in [(row.chinese, "title", "#f8fafc"), (row.english, "normal", "#cbd5e1"),
                                   (row.metadata(), "small", "#67e8f9"), (row.id, "small", "#cbd5e1"),
                                   ("规则：" + row.requirement, "small", "#94a3b8")]:
            for line in wrap_text(text, fonts[style], width - 210):
                content.append((line, style, color, fonts[style].size + 8))
        frame_height = 34 + ((len(row.pictures()) + 1) // 2) * 72 if len(row.pictures()) > 1 else 150
        height = max(frame_height, 28 + sum(part[3] for part in content))
        layout.append((row, content, height))
    return fonts, layout


def write_png(rows, output, font_path, width, threshold):
    fonts, layout = layout_rows(rows, font_path, width)
    header = 150
    height = header + sum(row[2] for row in layout) + 16
    if width * height > 80_000_000:
        raise ValueError(f"Audit image would be {width}x{height}; use --format html to avoid excessive memory use")
    image = Image.new("RGB", (width, height), "#0b1220")
    draw = ImageDraw.Draw(image)
    draw.text((24, 16), "BlockRacing · 目标图标审计", font=fonts["title"], fill="#f8fafc")
    draw.text((24, 54), f"{len(rows)} 个行为/组合目标 · CSV 原顺序 · Bonus 阈值 {threshold} 分 · 普通物品图标不在本表",
              font=fonts["normal"], fill="#cbd5e1")
    draw.text((24, 81), "资源包实际纹理；轮播目标左侧列出全部帧。不模拟附魔闪光/叠放数字，可配合 HTML 播放审计。",
              font=fonts["small"], fill="#94a3b8")
    draw.rectangle((16, 114, width - 16, 146), fill="#20304b")
    draw.text((36, 118), "图标", font=fonts["normal"], fill="#f8fafc")
    draw.text((174, 118), "描述 / 审计信息", font=fonts["normal"], fill="#f8fafc")
    top = header
    for number, (row, content, row_height) in enumerate(layout, 1):
        draw.rectangle((16, top, width - 16, top + row_height - 1), fill="#172235" if number % 2 else "#101a2b")
        draw.line((156, top, 156, top + row_height), fill="#334155")
        draw.text((35, top + 10), f"{number:03d}", font=fonts["small"], fill="#94a3b8")
        frames = row.pictures()
        for index, frame in enumerate(frames):
            size = 48 if len(frames) > 1 else 96
            x = 30 + (index % 2) * 62 if len(frames) > 1 else 36
            y = top + 34 + (index // 2) * 72
            with Image.open(io.BytesIO(frame)) as source:
                sprite = source.convert("RGBA").resize((size, size), Image.Resampling.NEAREST)
            image.paste(sprite, (x, y), sprite)
            if len(frames) > 1:
                draw.text((x, y + size), f"#{index + 1}", font=fonts["small"], fill="#94a3b8")
        y = top + 10
        for line, style, color, line_height in content:
            draw.text((174, y), line, font=fonts[style], fill=color)
            y += line_height
        top += row_height
        draw.line((16, top - 1, width - 16, top - 1), fill="#334155")
    image.save(output)
    image.close()
    return width, height


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pack", type=Path, default=ROOT / "target/task-ui/BlockRacing-TaskIcons-26.2.zip")
    parser.add_argument("--targets", type=Path, default=ROOT / "src/main/resources/Targets.csv")
    parser.add_argument("--catalog", type=Path, default=ROOT / "src/main/resources/target-icons.json")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "target/task-ui/audit")
    parser.add_argument("--format", choices=["both", "png", "html"], default="html")
    parser.add_argument("--font", type=Path, help="CJK TTF/OTF/TTC; auto-detect with fontconfig by default")
    parser.add_argument("--width", type=int, default=1280)
    parser.add_argument("--bonus-threshold", type=int, default=11)
    args = parser.parse_args()
    if not 800 <= args.width <= 2400 or args.bonus_threshold < 1:
        parser.error("Use width 800..2400 and a positive Bonus threshold")
    try:
        rows = load_rows(args.targets, args.catalog, args.pack, args.bonus_threshold)
        args.output_dir.mkdir(parents=True, exist_ok=True)
        outputs = {"rows": len(rows)}
        if args.format in ("both", "html"):
            path = args.output_dir / "task-audit.html"
            write_html(rows, path, args.bonus_threshold)
            outputs["html"] = str(path)
        if args.format in ("both", "png"):
            path = args.output_dir / "task-audit.png"
            outputs["dimensions"] = write_png(rows, path, find_font(args.font), args.width, args.bonus_threshold)
            outputs["png"] = str(path)
        print(json.dumps(outputs, ensure_ascii=False))
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Audit export failed: {error}\nBuild the resource pack first if it is missing.\n")


if __name__ == "__main__":
    main()
