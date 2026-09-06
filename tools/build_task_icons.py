#!/usr/bin/env python3
"""Describe task icons, or build an optional pack from an owned Minecraft 26.2 client.

No network, credentials, AI service, or Draftout website artwork is used by this tool.
Only build artifacts go into target/. Run --catalog to print the bundled UI catalog.
"""
import argparse
import csv
import io
import json
from pathlib import Path
import zipfile
from task_icon_animation import FRAME_TICKS, MANIFEST_PATH, frame_plan, spec_digest, sprite_strip

ROOT = Path(__file__).resolve().parents[1]

ADVANCEMENTS = {
    "story/enter_the_nether": "OBSIDIAN", "end/root": "END_STONE",
    "story/follow_ender_eye": "ENDER_EYE", "adventure/trade": "EMERALD",
    "nether/distract_piglin": "GOLD_INGOT", "adventure/sniper_duel": "BOW",
    "adventure/bullseye": "TARGET", "adventure/summon_iron_golem": "IRON_GOLEM_SPAWN_EGG",
    "nether/ride_strider": "WARPED_FUNGUS_ON_A_STICK", "nether/charge_respawn_anchor": "RESPAWN_ANCHOR",
    "husbandry/complete_catalogue": "CAT_SPAWN_EGG", "husbandry/whole_pack": "WOLF_SPAWN_EGG",
    "husbandry/froglights": "OCHRE_FROGLIGHT", "nether/create_full_beacon": "BEACON",
    "nether/ride_strider_in_overworld_lava": "STRIDER_SPAWN_EGG", "nether/netherite_armor": "NETHERITE_CHESTPLATE",
    "story/cure_zombie_villager": "GOLDEN_APPLE", "adventure/trade_at_world_height": "EMERALD_BLOCK",
    "adventure/revaulting": "OMINOUS_TRIAL_KEY", "nether/explore_nether": "NETHERRACK",
    "adventure/hero_of_the_village": "TOTEM_OF_UNDYING", "husbandry/leash_all_frog_variants": "FROG_SPAWN_EGG",
}
EFFECTS = {
    "glowing": ("GLOW_INK_SAC", "#94a061"), "levitation": ("SHULKER_SHELL", "#ceffff"),
    "mining_fatigue": ("PRISMARINE_SHARD", "#4a4217"), "nausea": ("PUFFERFISH", "#551d4a"),
    "poison": ("SPIDER_EYE", "#4e9331"), "weakness": ("FERMENTED_SPIDER_EYE", "#484d48"),
}
ACTION_COLORS = {
    "collect": "#22d3ee", "mine": "#38bdf8", "kill": "#fb7185", "breed": "#f472b6",
    "tame": "#f9a8d4", "wear": "#a78bfa", "advance": "#facc15", "eat": "#86efac",
    "craft": "#fdba74", "use": "#67e8f9", "effect": "#c084fc", "death": "#ef4444",
    "level": "#a3e635", "travel": "#7dd3fc", "fish": "#60a5fa", "spy": "#5eead4",
    "damage": "#f87171", "trade": "#34d399", "hunger": "#d6a875", "enchant": "#d8b4fe",
}


def describe(row):
    raw = row["requirement"]
    kind, _, value = raw.partition(":")
    spec = {"requirement": raw, "icon": "PAPER", "action": "collect", "badge": "",
            "count": 1, "glint": False, "subjects": [], "zh": row["中文显示"], "en": row["display_name"]}

    def use(icon, action, badge="", count=1, subjects=None):
        spec.update(icon=icon, action=action, badge=badge, count=count, subjects=subjects or [icon])

    def items(text):
        return [i.split("*")[0] for i in text.split(",")]

    def representative(values):
        return next((i for i in values if i.endswith("_PICKAXE") or i.endswith("_CHESTPLATE")), values[0])

    if kind in ("item", "item-unique", "item-total", "equipment-all", "equipment-any", "consume-all"):
        values = items(value.split(":", 1)[1] if kind in ("item-unique", "item-total") else value)
        count = int(value.split(":", 1)[0]) if kind in ("item-unique", "item-total") else len(values)
        if kind == "item" and len(values) == 1:
            count = int(value.split("*", 1)[1]) if "*" in value else 1
        if kind == "equipment-any": count = 1
        action = "wear" if kind.startswith("equipment") else "eat" if kind == "consume-all" else "collect"
        badge = "ANY" if (kind == "item-unique" and count == 1) or kind == "equipment-any" else str(count)
        use(representative(values), action, badge, count, values)
        if row["id"] == "COLLECT_ALL_COPPER_VARIANTS":
            use("WAXED_COPPER_BLOCK", action, "ALL", count, values)
    elif kind == "actions":
        icon, action = {"MILK_CLEANSE": ("MILK_BUCKET", "eat"), "LOOM_CRAFT": ("LOOM", "craft"),
                        "CAULDRON_CLEAN": ("CAULDRON", "use"), "COMPOST_FILL,COMPOST_COLLECT": ("COMPOSTER", "use"),
                        "JUKEBOX_PLAY": ("JUKEBOX", "use")}[value]
        use(icon, action)
    elif kind in ("kill", "breed", "tame", "death-attacker"):
        use(value + "_SPAWN_EGG", "death" if kind == "death-attacker" else kind)
    elif kind in ("break", "use-block", "consume"):
        use(items(value)[0], {"break": "mine", "use-block": "use", "consume": "eat"}[kind])
    elif kind == "enchanted-item":
        use(value.split(":")[0], "enchant", "MAX")
        spec["glint"] = True
    elif kind == "advancement":
        use(ADVANCEMENTS[value], "advance")
    elif kind == "effect":
        icon, tint = EFFECTS[value]
        use(icon, "effect")
        spec["effect_color"] = tint
    elif kind == "consume-potion":
        use("POTION", "eat", "H2O" if value == "WATER" else "")
    elif kind == "location":
        use({"height-limit": "SCAFFOLDING", "bedrock": "BEDROCK", "nether-roof": "NETHERRACK"}[value],
            "travel", {"height-limit": "UP", "bedrock": "LOW", "nether-roof": "TOP"}[value])
    elif kind == "wear-continuous":
        material, seconds = value.split(":")
        minutes = int(seconds) // 60
        use(material, "wear", str(minutes) + "M", minutes)
    elif kind == "death-cause":
        use({"DROWNING": "WATER_BUCKET", "CONTACT": "CACTUS", "MAGIC": "SPLASH_POTION",
             "VOID": "END_PORTAL_FRAME", "FREEZE": "POWDER_SNOW_BUCKET"}[value], "death")
    elif kind == "death-projectile":
        use(value, "death")
    elif kind == "hunger": use("ROTTEN_FLESH", "hunger", "0")
    elif kind == "fish-treasure": use("FISHING_ROD", "fish", "", subjects=["FISHING_ROD", "NAUTILUS_SHELL"])
    elif kind == "villager-max-level": use("VILLAGER_SPAWN_EGG", "trade", "MAX")
    else:
        count_types = {
            "breed-unique": ("COW_SPAWN_EGG", "breed"), "kill-unique-hostile": ("ZOMBIE_SPAWN_EGG", "kill"),
            "kill-undead": ("ZOMBIE_HEAD", "kill"), "kill-arthropod": ("SPIDER_EYE", "kill"),
            "kill-total": ("DIAMOND_SWORD", "kill"), "consume-unique": ("COOKED_BEEF", "eat"),
            "advancement-count": ("KNOWLEDGE_BOOK", "advance"), "level": ("EXPERIENCE_BOTTLE", "level"),
            "craft-unique": ("CRAFTING_TABLE", "craft"), "spy-unique": ("SPYGLASS", "spy"),
            "damage-taken": ("SHIELD", "damage"), "damage-dealt": ("IRON_SWORD", "damage"),
            "equipment-unique-leather-colors": ("LEATHER_CHESTPLATE", "wear"),
        }
        icon, action = count_types[kind]  # Missing coverage fails the build, never silently produces a book.
        use(icon, action, value, int(value))
        if kind == "equipment-unique-leather-colors": spec["rainbow"] = True
    return spec


def catalog():
    with (ROOT / "src/main/resources/Targets.csv").open(encoding="utf-8") as source:
        return {row["id"]: describe(row) for row in csv.DictReader(source)
                if row["type"] == "goal" and row["score"] != "-1"}


# Original, deliberately simple pixel badges. Shapes supplement color for accessibility.
GLYPHS = {
    "collect": [".......", "...#...", "...#...", ".#####.", "...#...", "...#...", "......."],
    "mine": [".#####.", "##.#.##", "...#...", "...#...", "...#...", "...#...", "......."],
    "kill": ["##...##", ".##.##.", "..###..", "...#...", "..###..", ".#...#.", "#.....#"],
    "breed": [".##.##.", "#######", "#######", ".#####.", "..###..", "...#...", "......."],
    "tame": [".......", "..###..", ".#...#.", ".#...#.", "..###..", "...#...", "..###.."],
    "wear": ["##...##", "#######", ".#####.", ".#####.", ".#####.", ".#####.", "......."],
    "advance": ["...#...", "...#...", "#######", ".#####.", "..###..", ".##.##.", ".#...#."],
    "eat": ["#.#.#..", "#.#.#.#", "#####.#", "..#...#", "..#...#", "..#...#", "..#...#"],
    "craft": [".#####.", ".#####.", "...#...", "...#...", "...#...", "...#...", "......."],
    "use": ["#......", "##.....", "###....", "####...", "#####..", "#.#....", "...#..."],
    "effect": ["..###..", "...#...", "..#.#..", ".#...#.", ".#####.", ".#####.", "..###.."],
    "death": [".#####.", "#######", "#.#.#.#", "#######", ".#####.", "..#.#..", "..#.#.."],
    "level": ["...#...", "..###..", ".#####.", "...#...", "...#...", ".#####.", "......."],
    "travel": ["...#...", "..###..", ".#.#.#.", "...#...", "...#...", "...#...", "......."],
    "fish": ["....#..", "....#..", "....#..", "#...#..", "#...#..", ".###...", "......."],
    "spy": [".......", "..###..", ".#...#.", "#..#..#", ".#...#.", "..###..", "......."],
    "damage": ["....##.", "...##..", "..##...", ".#####.", "...##..", "..##...", ".##...."],
    "trade": ["..###..", ".#...#.", "#.....#", "#.....#", ".#...#.", "..###..", "......."],
    "hunger": [".......", ".#####.", "#......", ".#####.", "......#", ".#####.", "......."],
    "enchant": [".#.....", "###..#.", ".#..###", ".....#.", "..#....", ".###...", "..#...."],
}
FONT = dict(zip("0123456789AHLMNOPUWXY", [
    "111101101101111", "010110010010111", "111001111100111", "111001111001111",
    "101101111001001", "111100111001111", "111100111101111", "111001010010010",
    "111101111101111", "111101111001111", "010101111101101", "101101111101101",
    "100100100100111", "101111111101101", "101111111111101", "111101101101111",
    "110101110100100", "101101101101111", "101101111111101", "101101010101101",
    "101101010010010",
]))
FONT["T"] = "111010010010010"


def draw_label(image, text, x, y, color="white"):
    from PIL import ImageDraw
    draw = ImageDraw.Draw(image)
    for i, letter in enumerate(text):
        bits = FONT[letter]
        for pos, bit in enumerate(bits):
            if bit == "1": draw.point((x + i * 4 + pos % 3, y + pos // 3), fill=color)


class ClientTextures:
    def __init__(self, archive):
        self.archive = archive
        self.cache = {}

    def read_json(self, path):
        return json.loads(self.archive.read(path))

    def model_textures(self, model, seen=None):
        seen = set() if seen is None else seen
        if model in seen: return {}
        seen.add(model)
        namespace, path = model.split(":", 1) if ":" in model else ("minecraft", model)
        filename = f"assets/{namespace}/models/{path}.json"
        if filename not in self.archive.namelist(): return {}
        data = self.read_json(filename)
        textures = self.model_textures(data["parent"], seen) if "parent" in data else {}
        return textures | data.get("textures", {})

    def model_path(self, node):
        if node.get("type") == "minecraft:model": return node["model"]
        for key in ("fallback", "on_false", "base"):
            if isinstance(node.get(key), dict):
                result = self.model_path(node[key])
                if result: return result
        for key in ("models", "entries", "cases"):
            for part in node.get(key, []):
                result = self.model_path(part.get("model", part))
                if result: return result
        return None

    def icon(self, material):
        from PIL import Image, ImageDraw
        if material in self.cache: return self.cache[material].copy()
        if material == "WITHER_SPAWN_EGG":
            # A three-headed portrait is clearer than the nearly black native spawn egg.
            skin = Image.open(io.BytesIO(self.archive.read("assets/minecraft/textures/entity/wither/wither.png"))).convert("RGBA")
            image = Image.new("RGBA", (16, 16))
            d = ImageDraw.Draw(image)
            d.line([(2, 8), (8, 10), (14, 8)], fill="#74777c", width=2)
            d.line([(8, 7), (8, 15)], fill="#4b5057", width=2)
            image.alpha_composite(skin.crop((8, 8, 16, 16)).resize((7, 7), Image.Resampling.NEAREST), (5, 1))
            side = skin.crop((38, 6, 44, 12)).resize((5, 5), Image.Resampling.NEAREST)
            image.alpha_composite(side, (0, 5))
            image.alpha_composite(side, (11, 5))
            return image
        # A couple of vanilla GUI items are entity renderers, not flat item textures.
        if material == "SHIELD":
            image = Image.new("RGBA", (16, 16))
            d = ImageDraw.Draw(image)
            d.polygon([(3, 1), (12, 1), (12, 10), (8, 15), (3, 10)], fill="#a88a61", outline="#d4d4d8")
            d.line([(8, 2), (8, 12)], fill="#655344", width=2)
            return image
        if material == "ZOMBIE_HEAD": return self.icon("ZOMBIE_SPAWN_EGG")
        node = self.read_json(f"assets/minecraft/items/{material.lower()}.json")["model"]
        if material.endswith("COPPER_CHEST"):
            texture = node["model"]["texture"].split(":", 1)[1]
            skin = Image.open(io.BytesIO(self.archive.read(f"assets/minecraft/textures/entity/chest/{texture}.png"))).convert("RGBA")
            image = Image.new("RGBA", (16, 16))
            image.alpha_composite(skin.crop((14, 14, 28, 19)), (1, 0))
            image.alpha_composite(skin.crop((14, 33, 28, 43)), (1, 5))
            image.alpha_composite(skin.crop((1, 1, 3, 5)), (7, 3))
            self.cache[material] = image
            return image.copy()
        if material.endswith("COPPER_GOLEM_STATUE"):
            texture = node["fallback"]["model"]["texture"].split(":", 1)[1]
            skin = Image.open(io.BytesIO(self.archive.read(f"assets/minecraft/{texture}"))).convert("RGBA")
            image = Image.new("RGBA", (16, 16))
            face = skin.crop((8, 8, 16, 16))
            image.alpha_composite(face, (4, 1))
            # Compact frontal statue silhouette, using the matching oxidation texture.
            body = skin.crop((4, 20, 10, 26))
            image.alpha_composite(body, (5, 8))
            image.alpha_composite(body.resize((2, 5), Image.Resampling.NEAREST), (2, 8))
            image.alpha_composite(body.resize((2, 5), Image.Resampling.NEAREST), (12, 8))
            image.alpha_composite(body.resize((2, 3), Image.Resampling.NEAREST), (5, 13))
            image.alpha_composite(body.resize((2, 3), Image.Resampling.NEAREST), (9, 13))
            self.cache[material] = image
            return image.copy()
        model = self.model_path(node)
        if model is None: raise ValueError(f"No GUI model for {material}")
        textures = self.model_textures(model)

        def texture(name):
            for _ in range(16):
                if not name.startswith("#"): break
                name = textures[name[1:]]
            ns, path = name.split(":", 1) if ":" in name else ("minecraft", name)
            image = Image.open(io.BytesIO(self.archive.read(f"assets/{ns}/textures/{path}.png"))).convert("RGBA")
            # Animated strips: first frame is enough for a stable menu image.
            if image.height > image.width: image = image.crop((0, 0, image.width, image.width))
            return image.resize((16, 16), Image.Resampling.NEAREST)

        layers = [textures[key] for key in sorted(textures) if key.startswith("layer")]
        if layers:
            image = Image.new("RGBA", (16, 16))
            for layer in layers: image.alpha_composite(texture(layer))
        else:
            name = next((textures[k] for k in ("front", "side", "all", "particle", "top", "texture") if k in textures), None)
            if name is None: raise ValueError(f"No usable texture for {material}: {textures}")
            image = texture(name)
        self.cache[material] = image
        return image.copy()


def render_icon(spec, client, bonus=False, phase=""):
    from PIL import Image, ImageDraw, ImageColor
    image = Image.new("RGBA", (32, 32))
    subjects = spec["subjects"]
    if not 1 <= len(subjects) <= 4:
        raise ValueError("Render one planned frame at a time; do not truncate a candidate list")
    if len(subjects) == 1:
        sprite = client.icon(subjects[0]).resize((24, 24), Image.Resampling.NEAREST)
        if spec.get("rainbow"):
            shades = ["#ef4444", "#facc15", "#22d3ee", "#a78bfa"]
            for y in range(24):
                for x in range(24):
                    r, g, b, a = sprite.getpixel((x, y))
                    tint = ImageColor.getrgb(shades[x // 6])
                    sprite.putpixel((x, y), (*(int(c * max(r, g, b) / 255) for c in tint), a))
        image.alpha_composite(sprite, (4, 8))
    else:
        for i, subject in enumerate(subjects):
            sprite = client.icon(subject).resize((14, 14), Image.Resampling.NEAREST)
            image.alpha_composite(sprite, (2 + i % 2 * 14, 5 + i // 2 * 13))
    draw = ImageDraw.Draw(image)
    # The action and exact-quantity badges remain outside the vanilla bottom-right stack counter.
    draw.rectangle((0, 0, 8, 8), fill="#111827")
    for y, line in enumerate(GLYPHS[spec["action"]]):
        for x, pixel in enumerate(line):
            if pixel == "#": draw.point((x + 1, y + 1), fill=ACTION_COLORS[spec["action"]])
    badge = spec["badge"]
    if badge:
        width = len(badge) * 4 - 1
        draw.rectangle((30 - width, 0, 31, 6), fill="#111827")
        draw_label(image, badge, 31 - width, 1)
    if bonus:
        # Corner brackets keep the central object unobscured.
        for x, y, dx, dy in [(0, 0, 1, 1), (31, 0, -1, 1), (0, 31, 1, -1), (31, 31, -1, -1)]:
            draw.line([(x + 4 * dx, y), (x, y), (x, y + 4 * dy)], fill="#fbbf24")
    if phase:
        draw.rectangle((10, 0, 17, 6), fill="#111827")
        draw_label(image, phase, 10, 1, "#facc15" if phase.startswith("W") else "#cbd5e1")
    return image


def build_pack(client_jar, specs):
    from PIL import Image, ImageDraw, ImageFont
    import hashlib
    import textwrap
    output = ROOT / "target/task-ui"
    output.mkdir(parents=True, exist_ok=True)
    files = {}
    previews = {}
    manifest = {"format": 1, "goals": {}}
    with zipfile.ZipFile(client_jar) as archive:
        version = json.loads(archive.read("version.json"))
        if version["id"] != "26.2": raise ValueError("Use a Minecraft 26.2 client, matching the plugin API")
        client = ClientTextures(archive)
        grouped = {}
        for goal, spec in specs.items():
            key = "task/" + goal.lower()
            plan = frame_plan(spec)
            manifest["goals"][goal] = {"spec_sha256": spec_digest(spec), "frames": plan}
            for bonus in (False, True):
                name = key + ("_bonus" if bonus else "")
                pictures = [render_icon(spec | {"subjects": frame["subjects"]}, client, bonus, frame["phase"]) for frame in plan]
                if not bonus: previews[goal] = pictures[0]
                path = f"assets/blockracing/textures/item/{name}.png"
                files[path] = sprite_strip(pictures)
                if len(pictures) > 1:
                    files[path + ".mcmeta"] = {"animation": {"width": 32, "height": 32,
                                                               "frametime": FRAME_TICKS, "interpolate": False}}
                files[f"assets/blockracing/models/item/{name}.json"] = {
                    "parent": "minecraft:item/generated", "gui_light": "front",
                    "textures": {"layer0": f"blockracing:item/{name}"}}
            grouped.setdefault(spec["icon"].lower(), []).append({
                "when": "blockracing:" + key,
                "model": {"type": "minecraft:condition", "property": "minecraft:custom_model_data", "index": 0,
                          "on_true": {"type": "minecraft:model", "model": "blockracing:item/" + key + "_bonus"},
                          "on_false": {"type": "minecraft:model", "model": "blockracing:item/" + key}}})
        for material, cases in grouped.items():
            path = f"assets/minecraft/items/{material}.json"
            original = json.loads(archive.read(path))
            files[path] = original | {"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data",
                                               "index": 0, "cases": cases, "fallback": original["model"]}}
    files["pack.mcmeta"] = {"pack": {"min_format": [88, 0], "max_format": [88, 0],
                                     "description": "BlockRacing task icons | object + action + quantity | 26.2"}}
    files[MANIFEST_PATH] = manifest
    files["LICENSE-NOTICE.txt"] = ("Task composites use assets from your local Minecraft installation. "
                                  "Minecraft assets belong to Mojang/Microsoft. Do not treat them as AGPL artwork. "
                                  "Original badge code and layout are part of the BlockRacing fork. "
                                  "No Draftout website artwork is bundled.").encode()
    pack_path = output / "BlockRacing-TaskIcons-26.2.zip"
    with zipfile.ZipFile(pack_path, "w", compression=zipfile.ZIP_DEFLATED) as pack:
        for path, value in sorted(files.items()):
            if isinstance(value, dict): value = json.dumps(value, separators=(",", ":")).encode()
            info = zipfile.ZipInfo(path, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            pack.writestr(info, value)

    def sheet(ids, name, columns):
        width, height = 200, 166
        canvas = Image.new("RGB", (columns * width, ((len(ids) + columns - 1) // columns) * height), "#0b1220")
        draw = ImageDraw.Draw(canvas)
        font = ImageFont.load_default(size=12)
        for i, goal in enumerate(ids):
            x, y = i % columns * width, i // columns * height
            draw.rounded_rectangle((x + 5, y + 5, x + width - 5, y + height - 5), radius=8, fill="#172235")
            sprite = previews[goal].resize((96, 96), Image.Resampling.NEAREST)
            canvas.paste(sprite, (x + 52, y + 10), sprite)
            label = specs[goal]["en"]
            for line, text in enumerate(textwrap.wrap(label, width=27)[:3]):
                draw.text((x + 10, y + 112 + line * 14), text, fill="#e2e8f0", font=font)
        canvas.save(output / name)

    examples = ["KILL_WITHER", "BREED_TRADER_LLAMA", "TAME_CAT", "DIE_TO_WARDEN", "MINE_DIAMOND_ORE",
                "OBTAIN_WOODEN_TOOLS", "OBTAIN_5_UNIQUE_DISCS", "OBTAIN_8_UNIQUE_DISCS", "USE_STONECUTTER",
                "OBTAIN_ANY_NAUTILUS_ARMOR", "EAT_PUMPKIN_PIE", "WEAR_CARVED_PUMPKIN_5_MINUTES",
                "GET_REVAULTING_ADVANCEMENT", "GET_POISON_STATUS_EFFECT", "COLLECT_ALL_COPPER_VARIANTS", "CRAFT_100_UNIQUE_ITEMS"]
    sheet(examples, "preview.png", 4)
    sheet(list(specs), "all-goals.png", 8)
    print(json.dumps({"goals": len(specs), "animated": sum(len(g["frames"]) > 1 for g in manifest["goals"].values()),
                      "base_models": len(grouped), "pack": str(pack_path.relative_to(ROOT)),
                      "bytes": pack_path.stat().st_size, "sha1": hashlib.sha1(pack_path.read_bytes()).hexdigest()}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", action="store_true", help="Print deterministic goal-icon metadata")
    parser.add_argument("--client-jar", type=Path, help="Owned Minecraft 26.2 client JAR; never downloaded automatically")
    args = parser.parse_args()
    specs = catalog()
    if args.catalog:
        print(json.dumps(specs, ensure_ascii=False, indent=2))
    elif args.client_jar:
        build_pack(args.client_jar, specs)
    else:
        parser.error("Specify --catalog or --client-jar")


if __name__ == "__main__":
    main()
