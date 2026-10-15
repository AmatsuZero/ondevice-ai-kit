import os, subprocess, tempfile, unittest
from pathlib import Path
SCRIPT = Path(__file__).resolve().parents[2] / "scripts/sync_remotes.py"
def git(directory, *args):
    return subprocess.run(["git", "-C", str(directory), *args], check=True, capture_output=True, text=True).stdout.strip()
class SyncTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name); self.repo = self.root / "repo"; self.repo.mkdir()
        git(self.repo, "init", "-b", "main"); git(self.repo, "config", "user.name", "Fixture"); git(self.repo, "config", "user.email", "fixture@example.invalid")
        for name in ["origin", "jihu"]:
            target = self.root / name; target.mkdir(); git(target, "init", "--bare"); git(self.repo, "remote", "add", name, str(target))
        self.commit("one")
    def commit(self, text):
        (self.repo / "file").write_text(text); git(self.repo, "add", "file"); git(self.repo, "commit", "-m", text)
    def run_sync(self):
        return subprocess.run([os.sys.executable, str(SCRIPT), "main"], cwd=self.repo, capture_output=True, text=True)
    def testFastForwardAndIdempotency(self):
        first = self.run_sync(); self.assertEqual(first.returncode, 0, first.stderr)
        self.commit("two"); result = self.run_sync(); self.assertEqual(result.returncode, 0, result.stderr)
        head = git(self.repo, "rev-parse", "HEAD")
        for name in ["origin", "jihu"]: self.assertEqual(git(self.root/name, "rev-parse", "refs/heads/main"), head)
        self.assertEqual(self.run_sync().returncode, 0)
    def testDivergenceDoesNotTouchEitherRemote(self):
        self.assertEqual(self.run_sync().returncode, 0)
        alternate = self.root / "alternate"; git(self.root, "clone", str(self.root/"jihu"), str(alternate))
        git(alternate, "config", "user.name", "Fixture"); git(alternate, "config", "user.email", "fixture@example.invalid")
        (alternate/"remote-only").write_text("keep"); git(alternate, "add", "."); git(alternate, "commit", "-m", "remote-only"); git(alternate, "push", "origin", "main")
        before = [git(self.root/name, "rev-parse", "refs/heads/main") for name in ["origin", "jihu"]]
        self.commit("local-only"); result = self.run_sync(); self.assertNotEqual(result.returncode, 0)
        self.assertEqual(before, [git(self.root/name, "rev-parse", "refs/heads/main") for name in ["origin", "jihu"]])
    def testMirrorFailureCanRetryWithoutRewritingPrimary(self):
        self.assertEqual(self.run_sync().returncode, 0)
        self.commit("two")
        git(self.repo, "remote", "set-url", "--push", "jihu", str(self.root/"missing"))
        failed = self.run_sync(); self.assertNotEqual(failed.returncode, 0)
        self.assertIn("pending", failed.stderr)
        git(self.repo, "remote", "set-url", "--push", "jihu", str(self.root/"jihu"))
        self.assertEqual(self.run_sync().returncode, 0)
if __name__ == "__main__": unittest.main()
