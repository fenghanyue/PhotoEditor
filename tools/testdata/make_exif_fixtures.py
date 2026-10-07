"""生成单元测试用的小图片（app/src/test/resources/exif/）。

用法：python3 tools/testdata/make_exif_fixtures.py app/src/test/resources/exif

- camera_portrait.jpg：模拟尼康竖拍原图的拍摄信息（方向 = 6，带 GPS）。
  时间和坐标是虚构的（坐标取天安门附近），不是真实拍摄记录。
- no_exif.jpg：没有任何拍摄信息，模拟被聊天软件压缩过的图。
"""

import sys
from pathlib import Path

from PIL import Image
from PIL.TiffImagePlugin import IFDRational


def camera_portrait(path: Path) -> None:
    img = Image.new("RGB", (60, 40), (200, 120, 80))
    exif = Image.Exif()
    exif[0x010F] = "NIKON CORPORATION"  # Make
    exif[0x0110] = "NIKON Z5_2"  # Model
    exif[0x0112] = 6  # Orientation：需要顺时针转 90° 显示
    exif[0x0132] = "2025:05:20 09:00:00"  # DateTime（修改时间）

    ifd = exif.get_ifd(0x8769)
    ifd[0x829A] = IFDRational(1, 60)  # ExposureTime
    ifd[0x829D] = IFDRational(56, 10)  # FNumber
    ifd[0x8827] = 1400  # PhotographicSensitivity
    ifd[0x9003] = "2025:05:20 08:30:15"  # DateTimeOriginal
    ifd[0x9011] = "+08:00"  # OffsetTimeOriginal
    # ExposureBiasValue 按标准是有符号分数；Pillow 只在值为负时才写成有符号类型，
    # 所以这里用 -1/3，顺便测负数
    ifd[0x9204] = IFDRational(-1, 3)
    ifd[0x9209] = 16  # Flash：没有闪光
    ifd[0x920A] = IFDRational(52, 1)  # FocalLength
    ifd[0xA405] = 52  # FocalLengthIn35mmFilm
    ifd[0xA434] = "NIKKOR Z 24-200mm f/4-6.3 VR"  # LensModel
    # 机身和镜头序列号（虚构），用来测试导出时不会被复制
    ifd[0xA431] = "3012345"  # BodySerialNumber
    ifd[0xA435] = "20012345"  # LensSerialNumber

    gps = exif.get_ifd(0x8825)
    gps[0x0001] = "N"
    gps[0x0002] = (IFDRational(39, 1), IFDRational(54, 1), IFDRational(3132, 100))
    gps[0x0003] = "E"
    gps[0x0004] = (IFDRational(116, 1), IFDRational(23, 1), IFDRational(5100, 100))
    gps[0x0005] = 0
    gps[0x0006] = IFDRational(44, 1)

    img.save(path, "JPEG", quality=90, exif=exif)


def no_exif(path: Path) -> None:
    Image.new("RGB", (40, 60), (80, 120, 200)).save(path, "JPEG", quality=90)


def main() -> None:
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    camera_portrait(out / "camera_portrait.jpg")
    no_exif(out / "no_exif.jpg")


if __name__ == "__main__":
    main()
