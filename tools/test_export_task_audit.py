#!/usr/bin/env python3
"""Unit tests for audit-table layout and HTML escaping; no Minecraft client required."""
import io
from pathlib import Path
import tempfile
import unittest

from PIL import Image, ImageFont
import export_task_audit as audit


class AuditExportTests(unittest.TestCase):
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
        audit.ROOT.joinpath("target").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=audit.ROOT / "target") as directory:
            output = Path(directory) / "audit.html"
            audit.write_html([self.example(requirement)], output, 11)
            document = output.read_text()
            self.assertIn("&lt;测试&gt;&amp;目标", document)
            self.assertNotIn("<测试>", document)
            self.assertIn(requirement, document)
            self.assertIn("data:image/png;base64,", document)
            self.assertNotIn('<script', document)
            self.assertEqual(1, document.count('<tr id='))

    def test_bonus_metadata_is_explicit(self):
        text = self.example().metadata()
        self.assertIn("Bonus 奖励 20 分", text)
        self.assertIn("不计胜利进度", text)
        self.assertIn("ALL", text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
