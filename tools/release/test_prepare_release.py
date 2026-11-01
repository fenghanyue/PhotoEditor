"""用临时 Git 仓库检查触发条件，用模拟响应检查 GitHub 查询；不创建真实 Release。"""

from contextlib import contextmanager
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError, URLError

import prepare_release as release


def published(version="0.4.1"):
    return {
        "draft": False,
        "prerelease": False,
        "assets": [{"name": f"PhotoEditor-{version}.apk", "state": "uploaded", "size": 123}],
    }


class ReleasePreparationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "repo"
        self.root.mkdir()
        previous = Path.cwd()
        os.chdir(self.root)
        self.addCleanup(os.chdir, previous)
        self.env_file = self.root.parent / "github-env"
        self.event_file = self.root.parent / "event.json"
        env = patch.dict(os.environ, {
            "GITHUB_EVENT_NAME": "push",
            "GITHUB_REF": "refs/heads/main",
            "GITHUB_EVENT_PATH": str(self.event_file),
            "GITHUB_ENV": str(self.env_file),
            "GITHUB_REPOSITORY": "example/PhotoEditor",
            "GH_TOKEN": "test-token",
            "RELEASE_INPUT": "",
            "RELEASE_VERSION": "0.4.1",
        })
        env.start()
        self.addCleanup(env.stop)
        self.git("init", "--initial-branch=main", "--quiet")
        self.git("config", "user.name", "Release test")
        self.git("config", "user.email", "test@example.invalid")
        release.VERSION_FILE.parent.mkdir()
        release.VERSION_FILE.write_text("0.4.0\n", encoding="utf-8")
        Path("app").mkdir()
        Path("app/build.gradle.kts").write_text('val appVersion = "0.4.1"\n', encoding="utf-8")
        Path("CHANGELOG.md").write_text(
            "# 更新记录\n\n## 0.4.1（2026-11-01）\n\n新功能。\n\n## 0.4.0\n\n旧功能。\n",
            encoding="utf-8",
        )
        self.before = self.commit("initial")
        self.set_push(self.before, self.before)

    def git(self, *args):
        return subprocess.check_output(["git", *args], text=True, stderr=subprocess.PIPE).strip()

    def commit(self, message):
        self.git("add", ".")
        self.git("-c", "commit.gpgsign=false", "commit", "--quiet", "-m", message)
        return self.git("rev-parse", "HEAD")

    def set_push(self, before, after):
        self.event_file.write_text(json.dumps({"before": before}), encoding="utf-8")
        os.environ["GITHUB_SHA"] = after

    def request_new_release(self):
        release.VERSION_FILE.write_text("0.4.1\n", encoding="utf-8")
        self.set_push(self.before, self.commit("request release"))

    def test_ordinary_push_only_builds_without_querying_github(self):
        Path("README.md").write_text("普通修改\n", encoding="utf-8")
        self.set_push(self.before, self.commit("ordinary change"))
        with patch.object(release, "github_json") as api:
            release.prepare()
        api.assert_not_called()
        self.assertEqual("RELEASE_VERSION=\n", self.env_file.read_text())
        self.assertFalse(release.NOTES_FILE.exists())

    def test_main_push_prepares_release_across_multiple_commits(self):
        self.request_new_release()
        Path("README.md").write_text("后续普通修改\n", encoding="utf-8")
        self.set_push(self.before, self.commit("follow-up"))
        with patch.object(release, "github_json", return_value=None) as api:
            release.prepare()
        self.assertEqual("RELEASE_VERSION=0.4.1\n", self.env_file.read_text())
        notes = release.NOTES_FILE.read_text(encoding="utf-8")
        self.assertIn("新功能。", notes)
        self.assertNotIn("旧功能。", notes)
        self.assertEqual(2, api.call_count)

    def test_development_branch_never_releases(self):
        self.request_new_release()
        os.environ["GITHUB_REF"] = "refs/heads/codex/test"
        with patch.object(release, "github_json") as api:
            release.prepare()
        api.assert_not_called()
        self.assertEqual("RELEASE_VERSION=\n", self.env_file.read_text())

    def test_initial_push_and_deleted_marker(self):
        self.set_push("0" * 40, self.before)
        self.assertEqual("0.4.0", release.requested_version())
        release.VERSION_FILE.unlink()
        self.set_push(self.before, self.commit("remove release request"))
        self.assertEqual("", release.requested_version())

    def test_blank_marker_is_an_error(self):
        release.VERSION_FILE.write_text(" \n", encoding="utf-8")
        self.set_push(self.before, self.commit("blank version"))
        with self.assertRaisesRegex(ValueError, "不能为空"):
            release.requested_version()

    def test_git_comparison_failure_stops_release(self):
        self.set_push("a" * 40, self.before)
        with patch.object(release.subprocess, "run", return_value=subprocess.CompletedProcess([], 128)):
            with self.assertRaisesRegex(ValueError, "无法比较"):
                release.requested_version()

    def test_manual_entry_is_retained(self):
        os.environ["GITHUB_EVENT_NAME"] = "workflow_dispatch"
        self.assertEqual("", release.requested_version())
        os.environ["RELEASE_INPUT"] = "0.4.1"
        with patch.object(release, "github_json", return_value=None):
            release.prepare()
        self.assertEqual("RELEASE_VERSION=0.4.1\n", self.env_file.read_text())
        os.environ["GITHUB_REF"] = "refs/heads/codex/test"
        with self.assertRaisesRegex(ValueError, "只能从 main"):
            release.requested_version()

    def test_invalid_version_or_app_version_mismatch_stops_release(self):
        for version in ["v0.4.1", "0.4", "0.4.1\nRELEASE_VERSION=bad", "0.5.0"]:
            with self.subTest(version=version), self.assertRaises(ValueError):
                release.release_notes(version)

    def test_missing_empty_or_similar_changelog_version_stops_release(self):
        for changelog in ["## 0.4.10\nwrong", "## 0.4.1.1\nwrong", "## 0.4.1\n\n## 0.4.0\nold"]:
            Path("CHANGELOG.md").write_text(changelog, encoding="utf-8")
            with self.subTest(changelog=changelog), self.assertRaisesRegex(ValueError, "更新说明"):
                release.release_notes("0.4.1")

    def test_existing_complete_release_is_skipped(self):
        self.request_new_release()
        with patch.object(release, "github_json", return_value=published()) as api:
            release.prepare()
        api.assert_called_once_with("releases/tags/v0.4.1")
        self.assertEqual("RELEASE_VERSION=\n", self.env_file.read_text())
        self.assertFalse(release.NOTES_FILE.exists())

    def test_partial_draft_or_prerelease_is_not_overwritten(self):
        cases = [
            {**published(), "draft": True},
            {**published(), "prerelease": True},
            {**published(), "assets": []},
            {**published(), "assets": [{"name": "PhotoEditor-0.4.1.apk", "state": "starter", "size": 0}]},
            published("0.4.0"),
        ]
        for existing in cases:
            with self.subTest(existing=existing), patch.object(release, "github_json", return_value=existing):
                with self.assertRaisesRegex(ValueError, "不是带完整 APK"):
                    release.needs_release("0.4.1")

    def test_tag_without_release_stops_publication(self):
        with patch.object(release, "github_json", side_effect=[None, {"ref": "refs/tags/v0.4.1"}]):
            with self.assertRaisesRegex(ValueError, "标签.*已存在"):
                release.needs_release("0.4.1")

    def test_recheck_skips_release_that_finished_during_build(self):
        with patch.object(release, "github_json", return_value=published()):
            release.prepare(check_existing=True)
        self.assertEqual("RELEASE_VERSION=\n", self.env_file.read_text())

    def test_recheck_keeps_unpublished_version(self):
        with patch.object(release, "github_json", return_value=None):
            release.prepare(check_existing=True)
        self.assertFalse(self.env_file.exists())

    def test_github_response_and_only_404_means_absent(self):
        @contextmanager
        def response(*args, **kwargs):
            yield io.StringIO(json.dumps(published()))

        with patch.object(release, "urlopen", side_effect=response):
            self.assertEqual(published(), release.github_json("releases/tags/v0.4.1"))
        for code in [404, 401, 403, 500]:
            error = HTTPError("https://api.github.com/test", code, "test error", None, None)
            with self.subTest(code=code), patch.object(release, "urlopen", side_effect=error):
                if code == 404:
                    self.assertIsNone(release.github_json("releases/tags/v0.4.1"))
                else:
                    with self.assertRaisesRegex(ValueError, f"HTTP {code}"):
                        release.github_json("releases/tags/v0.4.1")
        with patch.object(release, "urlopen", side_effect=URLError("offline")):
            with self.assertRaisesRegex(ValueError, "无法连接"):
                release.github_json("releases/tags/v0.4.1")


if __name__ == "__main__":
    unittest.main()
