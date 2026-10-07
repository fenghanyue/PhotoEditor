"""下载阿里云 DataV 的全国行政区边界，压成 App 离线查地区用的 regions.bin。

用法（只用 Python 标准库）：
    python3 tools/regions/build_regions.py app/src/main/assets/regions.bin tools/fonts/extra_chars.txt

可选 --cache 目录：下载过的文件存在这里，重跑时不再下载（默认 tools/regions/.cache/，不进仓库）。

数据来自 DataV.GeoAtlas（https://datav.aliyun.com/portal/school/atlas/area_selector），
边界是 GCJ-02 坐标。层级不是处处一样：
- 一般是 省级 → 地级 → 县级；
- 直辖市、香港、澳门：省级下面直接是区；
- 东莞、中山、嘉峪关这类不设区的市，仙桃、天门这类省直辖县级市：没有下一级；
- 台湾省：没有下级数据。
只有最底层的地区存边界，上级地区只存名字，供手动选择时逐级点选。
全国数据里还有一个"九段线"图形（代码 100000_JD），它不是行政区，跳过。

regions.bin 的格式（大端序）：
    "RGN1"，地区个数 u16，然后按代码从小到大排列每个地区：
        代码 i32，上级代码 i32（没有上级为 0），层级 u8（1 省级、2 地级、3 县级），
        名字长度 u8 + UTF-8 名字，边界字节数 u32（没有边界为 0）；
        有边界时接着是外接矩形（minLon minLat maxLon maxLat，各 i32），然后是边界：
        环数 varint；每个环：点数 varint，再接每个点相对上一个点的 (dLon, dLat)，
        用 zigzag + varint 编码；第一个点相对外接矩形的左下角。
    坐标单位是 1/100000 度（约 1 米）。环不重复首点。

extra_chars.txt 是地名用到的全部字符，tools/fonts/subset_noto_sc.py 会把它们收进字体。
"""

import argparse
import json
import struct
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

URL = "https://geo.datav.aliyun.com/areas_v3/bound/{}_full.json"
COUNTRY = 100000
SCALE = 100_000
LEVELS = {"province": 1, "city": 2, "district": 3}
RETRIES = 6
WORKERS = 6


class NotFound(Exception):
    pass


def fetch(code: int, cache: Path) -> dict:
    cached = cache / f"{code}_full.json"
    if cached.exists():
        return json.loads(cached.read_text(encoding="utf-8"))
    request = urllib.request.Request(URL.format(code), headers={"User-Agent": "PhotoEditor-build-regions/1.0"})
    for attempt in range(RETRIES):
        try:
            body = urllib.request.urlopen(request, timeout=60).read()
            data = json.loads(body)
            break
        except urllib.error.HTTPError as e:
            # 不存在的文件，OSS 返回 403 或 404
            if e.code in (403, 404):
                raise NotFound(code) from e
            error = e
        except (OSError, ValueError) as e:
            # 连接被断开、超时、下载不完整导致 JSON 解析失败，都重试
            error = e
        if attempt < RETRIES - 1:
            time.sleep(2**attempt)
    else:
        raise RuntimeError(f"{code}：重试 {RETRIES} 次仍然失败（{error}）")
    cached.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return data


def features(data: dict) -> list[dict]:
    """去掉九段线这类没有数字代码的图形。"""
    return [f for f in data["features"] if isinstance(f["properties"].get("adcode"), int)]


def rings(geometry: dict) -> list[list[tuple[int, int]]]:
    """把 Polygon / MultiPolygon 拆成一个个环，坐标换成整数，去掉重复点。"""
    if geometry["type"] == "Polygon":
        polygons = [geometry["coordinates"]]
    elif geometry["type"] == "MultiPolygon":
        polygons = geometry["coordinates"]
    else:
        raise ValueError(f"不认识的图形类型 {geometry['type']}")
    result = []
    for polygon in polygons:
        for ring in polygon:
            points = []
            for lon, lat in ring:
                point = (round(lon * SCALE), round(lat * SCALE))
                if not points or points[-1] != point:
                    points.append(point)
            while len(points) > 1 and points[0] == points[-1]:
                points.pop()
            if len(points) >= 3:
                result.append(points)
    return result


def varint(value: int) -> bytes:
    out = bytearray()
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            return bytes(out)


def zigzag(value: int) -> int:
    return value * 2 if value >= 0 else -value * 2 - 1


def encode_shape(shape: list[list[tuple[int, int]]]) -> tuple[tuple[int, int, int, int], bytes]:
    lons = [lon for ring in shape for lon, _ in ring]
    lats = [lat for ring in shape for _, lat in ring]
    bbox = (min(lons), min(lats), max(lons), max(lats))
    out = bytearray(varint(len(shape)))
    last_lon, last_lat = bbox[0], bbox[1]
    for ring in shape:
        out += varint(len(ring))
        for lon, lat in ring:
            out += varint(zigzag(lon - last_lon)) + varint(zigzag(lat - last_lat))
            last_lon, last_lat = lon, lat
    return bbox, bytes(out)


def collect(cache: Path) -> tuple[list[dict], list[str]]:
    """逐级下载，返回全部地区（最底层的带边界）和下载失败的说明。"""
    regions: list[dict] = []
    failures: list[str] = []

    def add(feature: dict, parent: int, leaf: bool) -> None:
        props = feature["properties"]
        regions.append(
            {
                "code": props["adcode"],
                "parent": parent,
                "level": LEVELS[props["level"]],
                "name": props["name"],
                "shape": rings(feature["geometry"]) if leaf else None,
            }
        )

    def expand(feature: dict, parent: int) -> list[tuple[dict, int]]:
        """有下级的地区：下载下级并返回；没有下级的作为最底层加入。"""
        props = feature["properties"]
        if props.get("childrenNum", 0) == 0 or props["level"] == "district":
            add(feature, parent, leaf=True)
            return []
        try:
            children = features(fetch(props["adcode"], cache))
        except Exception as e:  # noqa: BLE001 记下来，最后统一报告
            failures.append(f"{props['adcode']} {props['name']}：{e}")
            return []
        if len(children) != props["childrenNum"]:
            print(f"注意：{props['name']} 标注有 {props['childrenNum']} 个下级，实际 {len(children)} 个", file=sys.stderr)
        add(feature, parent, leaf=False)
        return [(child, props["adcode"]) for child in children]

    level = [(f, 0) for f in features(fetch(COUNTRY, cache))]
    with ThreadPoolExecutor(WORKERS) as pool:
        while level:
            print(f"处理 {len(level)} 个地区…", file=sys.stderr)
            level = [item for batch in pool.map(lambda item: expand(*item), level) for item in batch]
    regions.sort(key=lambda r: r["code"])
    return regions, failures


def write_regions(regions: list[dict], path: Path) -> int:
    out = bytearray(b"RGN1")
    out += struct.pack(">H", len(regions))
    points = 0
    for region in regions:
        name = region["name"].encode("utf-8")
        out += struct.pack(">iiBB", region["code"], region["parent"], region["level"], len(name)) + name
        shape = region["shape"]
        if shape is None:
            out += struct.pack(">I", 0)
            continue
        bbox, geometry = encode_shape(shape)
        out += struct.pack(">I4i", len(geometry), *bbox) + geometry
        points += sum(len(ring) for ring in shape)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(out)
    return points


def check(regions: list[dict]) -> list[str]:
    problems = []
    codes = {r["code"] for r in regions}
    has_children = {r["parent"] for r in regions}
    for r in regions:
        if r["parent"] and r["parent"] not in codes:
            problems.append(f"{r['code']} {r['name']} 的上级 {r['parent']} 不存在")
        leaf = r["code"] not in has_children
        if leaf and not r["shape"]:
            problems.append(f"{r['code']} {r['name']} 是最底层，却没有边界")
        if not leaf and r["shape"] is not None:
            problems.append(f"{r['code']} {r['name']} 有下级，不该存边界")
    if sum(1 for r in regions if r["level"] == 1) != 34:
        problems.append("省级地区不是 34 个")
    return problems


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("output", type=Path, help="regions.bin 的路径")
    parser.add_argument("chars", type=Path, help="extra_chars.txt 的路径")
    parser.add_argument("--cache", type=Path, default=Path(__file__).with_name(".cache"))
    args = parser.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)

    regions, failures = collect(args.cache)
    problems = failures + check(regions)
    if problems:
        print("\n".join(problems), file=sys.stderr)
        raise SystemExit("数据不完整，没有生成文件")

    points = write_regions(regions, args.output)
    chars = sorted({c for r in regions for c in r["name"]})
    args.chars.write_text(
        "# 由 tools/regions/build_regions.py 生成：地名用到的全部字符\n" + "".join(chars) + "\n",
        encoding="utf-8",
    )
    counts = {level: sum(1 for r in regions if r["level"] == level) for level in (1, 2, 3)}
    leaves = sum(1 for r in regions if r["shape"])
    print(
        f"{len(regions)} 个地区（省级 {counts[1]}，地级 {counts[2]}，县级 {counts[3]}），"
        f"{leaves} 个带边界，共 {points} 个点；"
        f"{args.output.name} {args.output.stat().st_size // 1024} KB，地名用字 {len(chars)} 个"
    )


if __name__ == "__main__":
    main()
