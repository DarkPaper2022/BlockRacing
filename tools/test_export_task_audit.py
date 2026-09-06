#!/usr/bin/env python3
"""Unit tests for audit-table layout and HTML escaping; no Minecraft client required."""
import io
import json
from dataclasses import replace
from pathlib import Path
import tempfile
import unittest
import zipfile

from PIL import Image, ImageFont
import export_task_audit as audit
from task_icon_animation import decode_texture, png_bytes, sprite_strip


class AuditExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        audit.ROOT.joinpath("target").mkdir(exist_ok=True)

    def example(self, requirement="item:STONE"):
        output = io.BytesIO()
        Image.new("RGBA", (32, 32), "#22d3ee").save(output, format="PNG")
        return audit.Row("TEST_ID", "<测试>&目标", "A <goal>", 20, True,
                         requirement, "STONE", "collect", "ALL", output.getvalue())

    def test_wrap_keeps_every_character_in_long_requirements(self):
        text = "item:" + ",".join("WAXED_WEATHERED_CUT_COPPER_STAIRS" for _ in range(120))
        font = ImageFont.load_default(size=13)
        lines = audit.wrap_text(text, font, 300)
        self.assertEqual(text, "".join(lines))
        self.assertGreater(len(lines), 10)
        self.assertTrue(all(font.getlength(line) <= 300 for line in lines))

    def test_chinese_wrap_does_not_drop_characters(self):
        text = "获得所有可被涂蜡的方块的全部变种" * 8
        font = ImageFont.load_default(size=16)
        self.assertEqual(text, "".join(audit.wrap_text(text, font, 150)))

    def test_html_is_self_contained_escaped_and_untruncated(self):
        requirement = "item:" + "LONG_IDENTIFIER," * 300
        with tempfile.TemporaryDirectory(dir=audit.ROOT / "target") as directory:
            output = Path(directory) / "audit.html"
            audit.write_html([self.example(requirement)], output, 11)
            document = output.read_text()
            self.assertIn("&lt;测试&gt;&amp;目标", document)
            self.assertNotIn("<测试>", document)
            self.assertIn(requirement, document)
            self.assertIn("data:image/png;base64,", document)
            self.assertEqual(1, document.count('<script>'))
            self.assertNotIn('<script src=', document)
            self.assertNotIn('https://', document)
            self.assertEqual(1, document.count('<tr id='))

    def test_bonus_metadata_is_explicit(self):
        text = self.example().metadata()
        self.assertIn("Bonus 奖励 20 分", text)
        self.assertIn("不计胜利进度", text)
        self.assertIn("ALL", text)

    def test_native_icon_metadata_does_not_claim_quantity_overlay(self):
        row = replace(self.example(), material="SPYGLASS", badge="", vanilla=True)
        self.assertIn("原版物品图标（无角标）", row.metadata())
        self.assertIn("数量标识 无", row.metadata())

    def test_animated_html_includes_gif_and_all_inspectable_static_frames(self):
        row = self.example()
        other = png_bytes(Image.new("RGBA", (32, 32), "red"))
        row = replace(row, frames=(row.png, other), durations=(800, 400),
                      frame_labels=("first <unsafe>", "last"), frame_subjects=(("STONE",), ("DIRT",)))
        with tempfile.TemporaryDirectory(dir=audit.ROOT / "target") as directory:
            output = Path(directory) / "audit.html"
            audit.write_html([row], output, 11)
            document = output.read_text()
        self.assertIn('data:image/gif;base64,', document)
        self.assertEqual(2, document.count('class="frame-select"'))
        self.assertIn('first &lt;unsafe&gt;', document)
        self.assertIn('400 ms', document)
        self.assertIn('一轮 1.2 秒', document)
        self.assertIn('<details open', document)
        self.assertIn('prefers-reduced-motion', document)
        self.assertIn('id="pause-all"', document)

    def test_decoder_uses_mcmeta_frame_order_and_per_frame_time(self):
        raw = io.BytesIO()
        with zipfile.ZipFile(raw, "w") as pack:
            pack.writestr("icon.png", sprite_strip([Image.new("RGBA", (32, 32), color) for color in ("red", "blue")]))
            pack.writestr("icon.png.mcmeta", json.dumps({"animation": {"frametime": 16, "frames": [1, {"index": 0, "time": 4}]}}))
        with zipfile.ZipFile(raw) as pack:
            frames, order, durations = decode_texture(pack, "icon.png")
        self.assertEqual(2, len(frames))
        self.assertEqual((1, 0), order)
        self.assertEqual((800, 200), durations)

    def test_decoder_rejects_missing_or_invalid_animation_metadata(self):
        for metadata in (None, {"frames": []}, {"frames": [2]}, {"frametime": 0}, {"interpolate": True}):
            with self.subTest(metadata=metadata):
                raw = io.BytesIO()
                with zipfile.ZipFile(raw, "w") as pack:
                    pack.writestr("icon.png", sprite_strip([Image.new("RGBA", (32, 32), "red")] * 2))
                    if metadata is not None:
                        pack.writestr("icon.png.mcmeta", json.dumps({"animation": metadata}))
                with zipfile.ZipFile(raw) as pack, self.assertRaises(ValueError):
                    decode_texture(pack, "icon.png")


if __name__ == "__main__":
    unittest.main(verbosity=2)
