#!/usr/bin/env python3
"""Offline pack checks. Build the pack first; pass --client-jar to verify vanilla fallback identity."""
import argparse
import io
import json
import unittest
import zipfile
from PIL import Image
import build_task_icons as icons

CLIENT_JAR = None


class TaskIconTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.specs = icons.catalog()
        cls.pack = zipfile.ZipFile(icons.ROOT / "target/task-ui/BlockRacing-TaskIcons-26.2.zip")
        cls.client = zipfile.ZipFile(CLIENT_JAR)

    @classmethod
    def tearDownClass(cls):
        cls.pack.close()
        cls.client.close()

    def test_catalog_matches_checked_in_plugin_resource(self):
        bundled = json.loads((icons.ROOT / "src/main/resources/target-icons.json").read_text())
        self.assertEqual(self.specs, bundled)

    def test_all_actions_and_quantity_glyphs_are_supported(self):
        for goal, spec in self.specs.items():
            with self.subTest(goal=goal):
                self.assertIn(spec["action"], icons.GLYPHS)
                self.assertIn(spec["action"], icons.ACTION_COLORS)
                self.assertTrue(all(c in icons.FONT for c in spec["badge"]))

    def test_all_models_keep_original_vanilla_fallback_and_properties(self):
        count = 0
        for name in self.pack.namelist():
            if not name.startswith("assets/minecraft/items/"): continue
            original = json.loads(self.client.read(name))
            changed = json.loads(self.pack.read(name))
            self.assertEqual(changed["model"]["fallback"], original["model"], name)
            self.assertEqual({k: v for k, v in changed.items() if k != "model"},
                             {k: v for k, v in original.items() if k != "model"}, name)
            self.assertEqual(changed["model"]["property"], "minecraft:custom_model_data")
            count += 1
        self.assertEqual(count, len({s["icon"] for s in self.specs.values()}))

    def test_every_goal_has_matching_normal_and_bonus_models(self):
        actual = set()
        for name in self.pack.namelist():
            if not name.startswith("assets/minecraft/items/"): continue
            for case in json.loads(self.pack.read(name))["model"]["cases"]:
                actual.add(case["when"])
                condition = case["model"]
                self.assertEqual(condition["property"], "minecraft:custom_model_data")
                for key in ("on_true", "on_false"):
                    model = condition[key]["model"].split(":", 1)[1]
                    document = json.loads(self.pack.read("assets/blockracing/models/" + model + ".json"))
                    texture = document["textures"]["layer0"].split(":", 1)[1]
                    self.assertIn("assets/blockracing/textures/" + texture + ".png", self.pack.namelist())
        self.assertEqual(actual, {"blockracing:task/" + goal.lower() for goal in self.specs})

    def test_sprites_are_small_transparent_and_distinguish_critical_variants(self):
        for name in self.pack.namelist():
            if not name.endswith(".png"): continue
            picture = Image.open(io.BytesIO(self.pack.read(name)))
            self.assertEqual((32, 32), picture.size)
            self.assertEqual("RGBA", picture.mode)
            self.assertEqual(0, picture.getchannel("A").getextrema()[0])
        for left, right in [("obtain_5_unique_discs", "obtain_8_unique_discs"),
                            ("kill_warden", "die_to_warden"), ("craft_20_unique_items", "craft_100_unique_items")]:
            prefix = "assets/blockracing/textures/item/task/"
            self.assertNotEqual(self.pack.read(prefix + left + ".png"), self.pack.read(prefix + right + ".png"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client-jar", required=True)
    args = parser.parse_args()
    CLIENT_JAR = args.client_jar
    unittest.main(argv=[__file__], verbosity=2)
