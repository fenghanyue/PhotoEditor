"""决定本次 Actions 是否发正式版；只读 GitHub，发布仍由工作流负责。"""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


VERSION_FILE = Path(".github/release-version")
NOTES_FILE = Path("dist/release-notes.md")


def requested_version():
    event_name = os.environ["GITHUB_EVENT_NAME"]
    ref = os.environ["GITHUB_REF"]
    if event_name == "workflow_dispatch":
        version = os.environ.get("RELEASE_INPUT", "").strip()
        if version and ref != "refs/heads/main":
            raise ValueError("正式版只能从 main 发")
        return version
    if event_name != "push" or ref != "refs/heads/main" or not VERSION_FILE.exists():
        return ""

    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    before = event["before"]
    # checkout 取完整历史，比较整次推送前后，不能只比较最后一个提交。
    if before != "0" * 40:
        result = subprocess.run(
            ["git", "diff", "--quiet", before, os.environ["GITHUB_SHA"], "--", str(VERSION_FILE)],
            check=False,
        )
        if result.returncode == 0:
            return ""
        if result.returncode != 1:
            raise ValueError("无法比较发布版本文件，停止发布")
    version = VERSION_FILE.read_text(encoding="utf-8").strip()
    if not version:
        raise ValueError("发布版本文件不能为空")
    return version


def release_notes(version):
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version):
        raise ValueError(f"版本号要写成 0.4.1 这样，现在是 {version!r}")
    gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")
    match = re.search(r'^val appVersion = "([^"]+)"', gradle, re.MULTILINE)
    if not match or match.group(1) != version:
        raise ValueError(f"要发的版本 {version} 和 app/build.gradle.kts 里的 appVersion 对不上")

    # 精确匹配版本号，不把 0.4.01 或 0.4.0.1 当成 0.4.0。
    sections = re.split(r"^## ", Path("CHANGELOG.md").read_text(encoding="utf-8"), flags=re.MULTILINE)
    for section in sections[1:]:
        heading, _, body = section.partition("\n")
        if re.match(re.escape(version) + r"(?![0-9.])", heading):
            if body.strip():
                return body.strip() + "\n\n用手机浏览器下载下面的 APK，直接安装就能覆盖之前装的版本。\n"
            break
    raise ValueError(f"CHANGELOG.md 里没有 {version} 的更新说明")


def github_json(endpoint):
    request = Request(
        f"https://api.github.com/repos/{os.environ['GITHUB_REPOSITORY']}/{endpoint}",
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {os.environ['GH_TOKEN']}",
        },
    )
    try:
        with urlopen(request, timeout=30) as response:
            return json.load(response)
    except HTTPError as error:
        if error.code == 404:
            return None
        # 权限或网络失败不能当作“版本不存在”，否则会误触发发布。
        raise ValueError(f"查询 GitHub {endpoint} 失败（HTTP {error.code}），停止发布") from error
    except URLError as error:
        raise ValueError(f"无法连接 GitHub 查询 {endpoint}，停止发布") from error


def needs_release(version):
    release = github_json(f"releases/tags/v{version}")
    if release is not None:
        apk = f"PhotoEditor-{version}.apk"
        complete = (
            not release["draft"]
            and not release["prerelease"]
            and any(
                asset["name"] == apk and asset["state"] == "uploaded" and asset["size"] > 0
                for asset in release["assets"]
            )
        )
        if not complete:
            raise ValueError(f"v{version} 已存在，但不是带完整 APK 的正式版；停止发布，请检查 Releases")
        print(f"v{version} 已发布且 APK 完整，跳过重复发布")
        return False
    if github_json(f"git/ref/tags/v{version}") is not None:
        raise ValueError(f"标签 v{version} 已存在但没有完整正式版，停止发布，避免标签指向错误的代码")
    return True


def export_version(version):
    with Path(os.environ["GITHUB_ENV"]).open("a", encoding="utf-8") as output:
        output.write(f"RELEASE_VERSION={version}\n")


def prepare(check_existing=False):
    if check_existing:
        version = os.environ["RELEASE_VERSION"]
        if not needs_release(version):
            export_version("")
        return

    version = requested_version()
    if version:
        notes = release_notes(version)
        if needs_release(version):
            NOTES_FILE.parent.mkdir(exist_ok=True)
            NOTES_FILE.write_text(notes, encoding="utf-8")
            print(f"准备发布 v{version}；编译、测试和代码检查通过后才会上传")
        else:
            version = ""
    else:
        print("本次只编译，不发正式版")
    export_version(version)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check-existing", action="store_true", help="上传前再次检查版本，避免重复发布")
    args = parser.parse_args()
    try:
        prepare(args.check_existing)
    except (ValueError, KeyError, OSError) as error:
        print(f"发布准备失败：{error}", file=sys.stderr)
        sys.exit(1)
