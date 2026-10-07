"""下载思源黑体（Noto Sans SC）常规和粗体，裁成常用字子集，放进 App 的 assets。

用法（需要先 pip install fonttools）：
    python3 tools/fonts/subset_noto_sc.py app/src/main/assets/fonts

保留的字符：英文、数字、常用符号，以及 GB2312 里的全部字符（6763 个汉字）。
没收进来的生僻字，在手机上会自动用系统字体补上。
"""

import re
import sys
import urllib.request
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

CSS_URL = "https://fonts.googleapis.com/css2?family=Noto+Sans+SC:wght@400;700"
OUTPUT_NAMES = {400: "NotoSansSC-Regular.ttf", 700: "NotoSansSC-Bold.ttf"}

# 英文、数字、标点、度分秒、希腊字母（索尼的 α）、罗马数字、几何符号等
EXTRA_RANGES = [
    (0x0020, 0x007E),
    (0x00A0, 0x00FF),
    (0x0391, 0x03C9),
    (0x2010, 0x2027),
    (0x2030, 0x205E),
    (0x2160, 0x216B),
    (0x2190, 0x2193),
    (0x25A0, 0x25FF),
    (0x3000, 0x303F),
    (0xFF01, 0xFF5E),
]


def font_urls() -> dict[int, str]:
    # 用 curl 的 User-Agent 请求，Google Fonts 会返回完整的 TTF，而不是给网页用的分片
    request = urllib.request.Request(CSS_URL, headers={"User-Agent": "curl/8.5.0"})
    css = urllib.request.urlopen(request, timeout=60).read().decode()
    found = re.findall(r"font-weight: (\d+);.*?src: url\((\S+?)\)", css, re.S)
    urls = {int(weight): url for weight, url in found}
    missing = set(OUTPUT_NAMES) - set(urls)
    if missing:
        raise SystemExit(f"Google Fonts 没有返回这些字重：{sorted(missing)}")
    return urls


def characters() -> list[int]:
    codepoints = set()
    for start, end in EXTRA_RANGES:
        codepoints.update(range(start, end + 1))
    for high in range(0xA1, 0xF8):
        for low in range(0xA1, 0xFF):
            try:
                codepoints.add(ord(bytes([high, low]).decode("gb2312")))
            except UnicodeDecodeError:
                pass
    return sorted(codepoints)


def subset_font(source: Path, target: Path, codepoints: list[int]) -> None:
    options = subset.Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.hinting = False
    options.notdef_outline = True
    font = TTFont(source)
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=codepoints)
    subsetter.subset(font)
    font.save(target)


def main() -> None:
    out_dir = Path(sys.argv[1])
    out_dir.mkdir(parents=True, exist_ok=True)
    work = out_dir / ".download"
    work.mkdir(exist_ok=True)
    codepoints = characters()
    for weight, url in font_urls().items():
        if weight not in OUTPUT_NAMES:
            continue
        full = work / f"full-{weight}.ttf"
        urllib.request.urlretrieve(url, full)
        target = out_dir / OUTPUT_NAMES[weight]
        subset_font(full, target, codepoints)
        print(f"{target.name}: {full.stat().st_size // 1024} KB -> {target.stat().st_size // 1024} KB")
        full.unlink()
    work.rmdir()


if __name__ == "__main__":
    main()
