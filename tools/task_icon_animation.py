"""Shared animation planning/decoding for the resource pack and offline audit."""
import hashlib
import io
import json

from PIL import Image

FRAME_TICKS = 16  # 0.8 seconds; Minecraft texture animations use 20 ticks/second.
MANIFEST_PATH = "blockracing/icon-audit.json"


def spec_digest(spec):
    data = json.dumps(spec, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(data).hexdigest()


def frame_plan(spec):
    """Cover every listed candidate; large sets get four-object frames, never truncation."""
    subjects = spec["subjects"]
    if not subjects:
        raise ValueError("An icon needs at least one subject")
    kind = spec["requirement"].partition(":")[0]
    collections = {"item", "item-unique", "item-total", "equipment-all", "equipment-any", "consume-all"}
    if len(subjects) == 1 or kind not in collections:
        return [{"subjects": subjects, "label": " / ".join(subjects), "phase": ""}]
    if spec["badge"] == "ALL" and spec["icon"] == "WAXED_COPPER_BLOCK":
        frames = []
        # Keep wax state and oxidation stage together so 120 candidates remain auditable.
        for waxed in (False, True):
            for stage, name in enumerate(("初始", "轻微锈蚀", "半锈蚀", "完全锈蚀")):
                members = []
                for subject in subjects:
                    has_wax = subject.startswith("WAXED_")
                    bare = subject.removeprefix("WAXED_")
                    oxidation = next((i for i, prefix in enumerate(
                        ("EXPOSED_", "WEATHERED_", "OXIDIZED_"), 1) if bare.startswith(prefix)), 0)
                    if has_wax == waxed and oxidation == stage:
                        members.append(subject)
                for offset in range(0, len(members), 4):
                    frames.append({"subjects": members[offset:offset + 4],
                                   "label": f"{'已涂蜡' if waxed else '未涂蜡'} · {name} · {offset // 4 + 1}/{(len(members) + 3) // 4}",
                                   "phase": ("W" if waxed else "U") + str(stage)})
        if sorted(s for f in frames for s in f["subjects"]) != sorted(subjects):
            raise ValueError("Copper animation lost candidates")
        return frames
    size = 1 if len(subjects) <= 12 else 4
    return [{"subjects": subjects[i:i + size], "label": " / ".join(subjects[i:i + size]), "phase": ""}
            for i in range(0, len(subjects), size)]


def png_bytes(image):
    result = io.BytesIO()
    image.save(result, format="PNG")
    return result.getvalue()


def sprite_strip(pictures):
    if not pictures or any(p.size != (32, 32) for p in pictures):
        raise ValueError("All animation frames must be 32x32")
    result = Image.new("RGBA", (32, 32 * len(pictures)))
    for index, picture in enumerate(pictures):
        result.paste(picture, (0, index * 32))
    return png_bytes(result)


def decode_texture(pack, texture):
    """Return physical PNG frames, playback indices, and durations read from .mcmeta."""
    with Image.open(io.BytesIO(pack.read(texture))) as image:
        if image.format != "PNG" or image.width != 32 or image.height % 32 or not 1 <= image.height // 32 <= 512:
            raise ValueError(f"Expected a 32-wide PNG strip with 1..512 frames: {texture}")
        frames = tuple(png_bytes(image.crop((0, y, 32, y + 32)).convert("RGBA"))
                       for y in range(0, image.height, 32))
    metadata_path = texture + ".mcmeta"
    if metadata_path not in pack.namelist():
        if len(frames) > 1:
            raise ValueError(f"Animation strip has no .mcmeta: {texture}")
        return frames, (0,), (FRAME_TICKS * 50,)
    metadata = json.loads(pack.read(metadata_path))["animation"]
    if metadata.get("width", 32) != 32 or metadata.get("height", 32) != 32:
        raise ValueError(f"Unsupported animation frame size: {texture}")
    if metadata.get("interpolate", False):
        raise ValueError("Audit GIF does not approximate interpolated frames; disable interpolation")
    indices, durations = [], []
    for entry in metadata.get("frames", list(range(len(frames)))):
        index = entry.get("index") if isinstance(entry, dict) else entry
        ticks = entry.get("time", metadata.get("frametime", 1)) if isinstance(entry, dict) else metadata.get("frametime", 1)
        if type(index) is not int or not 0 <= index < len(frames) or type(ticks) is not int or not 1 <= ticks <= 1200:
            raise ValueError(f"Invalid frame index/duration: {texture}")
        indices.append(index)
        durations.append(ticks * 50)
    if not indices:
        raise ValueError(f"Empty animation sequence: {texture}")
    return frames, tuple(indices), tuple(durations)


def gif_bytes(frames, durations):
    """Flatten to a fixed dark backing: no GIF transparency trails or palette-index ghosts."""
    if len(frames) != len(durations) or not frames:
        raise ValueError("Every GIF frame needs a duration")
    pictures = []
    for png in frames:
        with Image.open(io.BytesIO(png)) as source:
            rgba = source.convert("RGBA")
        image = Image.new("RGB", (32, 32), "#111827")
        image.paste(rgba, mask=rgba.getchannel("A"))
        pictures.append(image)
    output = io.BytesIO()
    pictures[0].save(output, format="GIF", save_all=True, append_images=pictures[1:],
                     duration=list(durations), loop=0, disposal=2, optimize=False)
    return output.getvalue()
