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

    def metadata(self):
        score = f"Bonus 奖励 {self.score} 分（不计胜利进度）" if self.bonus else f"普通任务 · {self.score} 分"
        return f"{score} | 对象 {self.material} | 动作 {self.action} | 数量标识 {self.badge or '无'}"


def load_rows(targets, catalog_path, pack_path, bonus_threshold):
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    rows, seen = [], set()
    with targets.open(encoding="utf-8-sig", newline="") as source, zipfile.ZipFile(pack_path) as pack:
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
            picture = pack.read(texture)
            with Image.open(io.BytesIO(picture)) as image:
                if image.size != (32, 32) or image.format != "PNG":
                    raise ValueError(f"Expected a 32x32 PNG: {texture}")
            rows.append(Row(goal, target["中文显示"], target["display_name"], score, bonus,
                            target["requirement"], spec["icon"], spec["action"], spec["badge"], picture))
    if not rows:
        raise ValueError("No enabled custom goals found")
    return rows


def write_html(rows, output, threshold):
    entries = []
    for number, row in enumerate(rows, 1):
        picture = base64.b64encode(row.png).decode("ascii")
        entries.append(f'''<tr id="{html.escape(row.id, quote=True)}">
<td class="icon"><span>{number:03d}</span><img width="96" height="96" alt="{html.escape(row.id, quote=True)}" src="data:image/png;base64,{picture}"></td>
<td><h2>{html.escape(row.chinese)}</h2><p class="english">{html.escape(row.english)}</p>
<p class="meta">{html.escape(row.metadata())}</p><code>{html.escape(row.id)}</code>
<p class="rule">规则：{html.escape(row.requirement)}</p></td></tr>''')
    output.write_text('''<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>BlockRacing 目标图标审计</title>
<style>
body{margin:24px;background:#0b1220;color:#e2e8f0;font:16px/1.55 system-ui,sans-serif}
main{max-width:1280px;margin:auto}h1{font-size:26px}table{width:100%;border-collapse:collapse;table-layout:fixed}
th{text-align:left;background:#20304b;padding:12px;position:sticky;top:0}th:first-child{width:124px}
td{padding:16px;border-bottom:1px solid #334155;vertical-align:top}tr:nth-child(odd){background:#172235}
.icon{text-align:center}.icon span{display:block;color:#94a3b8;margin-bottom:8px}img{image-rendering:pixelated}
h2{font-size:21px;margin:0;color:#f8fafc}p{margin:5px 0}.english{color:#cbd5e1}.meta{color:#67e8f9}
code,.rule{font:13px/1.6 ui-monospace,monospace;overflow-wrap:anywhere;white-space:pre-wrap}.rule{color:#94a3b8}
@media print{body{background:white;color:black;margin:0}tr:nth-child(odd),th{background:#eee}h2,.meta,.rule,.english{color:black}th{position:static}tr{break-inside:avoid}}
</style><main><h1>BlockRacing 目标图标审计</h1>'''
        + f"<p>{len(rows)} 个启用的行为/组合目标 · CSV 原顺序 · Bonus 阈值 {threshold} 分。普通物品原版图标不在本表。</p>"
        + "<p>左侧为资源包实际纹理（含 Bonus 角标），右侧描述与规则完整显示。浏览器 Ctrl+F 可搜索；离线可用。</p>"
        + "<p>这是图标审计表，不是游戏截图；游戏中的附魔闪光、叠放数字、悬浮提示由客户端另行绘制。</p>"
        + '<table><thead><tr><th>图标</th><th>描述 / 审计信息</th></tr></thead><tbody>'
        + "\n".join(entries) + "</tbody></table></main></html>", encoding="utf-8")


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
        height = max(150, 28 + sum(part[3] for part in content))
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
    draw.text((24, 81), "资源包实际纹理；不模拟游戏附魔闪光/叠放数字。完整描述不截断，可配合 HTML 搜索审计。",
              font=fonts["small"], fill="#94a3b8")
    draw.rectangle((16, 114, width - 16, 146), fill="#20304b")
    draw.text((36, 118), "图标", font=fonts["normal"], fill="#f8fafc")
    draw.text((174, 118), "描述 / 审计信息", font=fonts["normal"], fill="#f8fafc")
    top = header
    for number, (row, content, row_height) in enumerate(layout, 1):
        draw.rectangle((16, top, width - 16, top + row_height - 1), fill="#172235" if number % 2 else "#101a2b")
        draw.line((156, top, 156, top + row_height), fill="#334155")
        draw.text((35, top + 10), f"{number:03d}", font=fonts["small"], fill="#94a3b8")
        with Image.open(io.BytesIO(row.png)) as source:
            sprite = source.convert("RGBA").resize((96, 96), Image.Resampling.NEAREST)
        image.paste(sprite, (36, top + 34), sprite)
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
    parser.add_argument("--format", choices=["both", "png", "html"], default="both")
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
