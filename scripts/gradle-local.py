#!/usr/bin/env python3
"""Local convenience: use JAVA_HOME and existing HTTP(S)_PROXY with Gradle."""
import os
from pathlib import Path
import subprocess
import sys
from urllib.parse import urlparse
root = Path(__file__).resolve().parents[1]
env = os.environ.copy()
local_jdk = root / "artifacts/toolchains/jdk-21.0.2.jdk/Contents/Home"
if "JAVA_HOME" not in env and local_jdk.exists():
    env["JAVA_HOME"] = str(local_jdk)
args = [env.get("ODAI_GRADLE_BIN", str(root / "gradlew"))]
proxy = urlparse(env.get("https_proxy") or env.get("HTTPS_PROXY") or "")
if proxy.hostname:
    if proxy.username or proxy.password:
        sys.exit("Authenticated proxy: configure Gradle credentials locally; helper does not expose secrets")
    for scheme in ("http", "https"):
        args.extend([f"-D{scheme}.proxyHost={proxy.hostname}", f"-D{scheme}.proxyPort={proxy.port or 80}"])
args.extend(sys.argv[1:])
sys.exit(subprocess.call(args, cwd=root, env=env))
