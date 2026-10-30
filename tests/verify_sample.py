#!/usr/bin/env python3
"""Build, patch, and compare the sample fixture on its dedicated emulator."""

import argparse
import os
from pathlib import Path
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
AVD_NAME = "morphe_sample_api36"


def run(*command, timeout=180, env=None):
    return subprocess.run(
        [str(arg) for arg in command], cwd=ROOT, env=env, check=True,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=timeout,
    ).stdout


def sign_apk(build_tools, keystore, unsigned, signed):
    aligned = unsigned.with_name(unsigned.stem + "-aligned.apk")
    run(build_tools / "zipalign", "-f", "-p", "4", unsigned, aligned)
    run(build_tools / "apksigner", "sign", "--ks", keystore,
        "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", signed, aligned)
    run(build_tools / "apksigner", "verify", "--verbose", signed)


def build_fixture(java, build_tools, android_jar, output):
    fixture = ROOT / "tests/sample-app"
    unsigned = output / "original-unsigned.apk"
    # Fresh compiler outputs prevent deleted source files surviving a later run.
    with tempfile.TemporaryDirectory(prefix="compile-", dir=output) as directory:
        temporary = Path(directory)
        classes = temporary / "classes"
        dex = temporary / "dex"
        classes.mkdir()
        dex.mkdir()
        run(java / "javac", "--release", "17", "-classpath", android_jar,
            "-d", classes, *sorted((fixture / "src").rglob("*.java")))
        run(build_tools / "d8", "--min-api", "26", "--lib", android_jar,
            "--output", dex, *sorted(classes.rglob("*.class")))
        run(build_tools / "aapt2", "link", "-I", android_jar,
            "--manifest", fixture / "AndroidManifest.xml", "-o", unsigned)
        with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_DEFLATED) as apk:
            apk.write(dex / "classes.dex", "classes.dex")
    return unsigned


def launch_and_check(adb, apk, ads, helper, output, name):
    expected = f"SAMPLE_RESULT ads={str(ads).lower()} helper={helper}"
    run(*adb, "install", "-r", apk)
    run(*adb, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
    run(*adb, "shell", "wm", "dismiss-keyguard")
    run(*adb, "shell", "am", "force-stop", "com.example.app")
    run(*adb, "logcat", "-c")
    run(*adb, "shell", "am", "start", "-W", "-n", "com.example.app/.MainActivity")
    for _ in range(20):
        log = run(*adb, "logcat", "-v", "raw", "-d", "-s", "SamplePatchFixture:I", "AndroidRuntime:E", "*:S")
        if expected in log.splitlines():
            (output / (name + ".log")).write_text(log)
            # Wait for the real UI, rather than capturing Android's launch splash.
            run(*adb, "shell", "uiautomator", "dump", "/sdcard/morphe-sample-window.xml")
            xml = run(*adb, "shell", "cat", "/sdcard/morphe-sample-window.xml")
            (output / (name + ".xml")).write_text(xml)
            screen = "\n".join(node.get("text", "") for node in ET.fromstring(xml).iter("node")).splitlines()
            if f"Ads: {'visible' if ads else 'hidden'}" not in screen or f"Helper: {helper}" not in screen:
                raise RuntimeError("Fixture UI did not match runtime result: " + repr(screen))
            screenshot = subprocess.run(
                [*adb, "exec-out", "screencap", "-p"], check=True,
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30,
            ).stdout
            (output / (name + ".png")).write_bytes(screenshot)
            print("PASS:", expected, flush=True)
            return
        time.sleep(0.5)
    (output / (name + ".log")).write_text(log)
    raise RuntimeError("Expected runtime result missing; see " + str(output / (name + ".log")))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="ADB serial of morphe_sample_api36")
    args = parser.parse_args()
    for variable in ("JAVA_HOME", "ANDROID_HOME"):
        if not os.environ.get(variable):
            parser.error("Set " + variable + " before running this test")
    java = Path(os.environ["JAVA_HOME"]) / "bin"
    sdk = Path(os.environ["ANDROID_HOME"])
    build_tools = sdk / "build-tools/36.0.0"
    android_jar = sdk / "platforms/android-36/android.jar"
    adb = [str(sdk / "platform-tools/adb"), "-s", args.serial]
    # Never install a fixture or clear logs on a physical device or unrelated AVD.
    if AVD_NAME not in run(*adb, "emu", "avd", "name").splitlines():
        parser.error("The target must be the dedicated " + AVD_NAME + " emulator")
    desktop = ROOT / "tools/morphe-desktop-1.19.1-all.jar"
    if not desktop.is_file():
        parser.error("Download the pinned Morphe Desktop release; see tests/README.md")

    output = ROOT / "build/sample-e2e"
    output.mkdir(parents=True, exist_ok=True)
    print("Building the fixture APK", flush=True)
    unsigned = build_fixture(java, build_tools, android_jar, output)
    keystore = output / "fixture-debug.p12"
    if not keystore.exists():
        run(java / "keytool", "-genkeypair", "-keystore", keystore,
            "-storepass", "android", "-keypass", "android", "-alias", "fixture",
            "-dname", "CN=Sample Patch Fixture", "-keyalg", "RSA",
            "-keysize", "2048", "-validity", "3650", "-noprompt")
    original = output / "original.apk"
    sign_apk(build_tools, keystore, unsigned, original)

    # Rebuild the bundle so a stale artifact cannot hide a patch regression.
    print("Building the patch bundle", flush=True)
    (output / "build.log").write_text(run(ROOT / "gradlew", "buildAndroid", "--no-daemon", "--console=plain"))
    bundle = ROOT / "patches/build/libs/patches-1.0.0.mpp"
    patched_unsigned = output / "patched-unsigned.apk"
    env = dict(os.environ, MORPHE_DATA_DIR=str(output / "morphe-data"))
    print("Applying Example Patch with fingerprint and SDK validation", flush=True)
    patch_log = run(java / "java", "-jar", desktop, "patch", "--patches", bundle,
                    "--exclusive", "-e", "Example Patch", "--validate", "--bytecode-mode", "FULL",
                    "--verify-with-sdk", sdk, "--unsigned", "-o", patched_unsigned, original, env=env)
    (output / "patch.log").write_text(patch_log)
    patched = output / "patched.apk"
    sign_apk(build_tools, keystore, patched_unsigned, patched)
    launch_and_check(adb, original, True, "original", output, "before")
    launch_and_check(adb, patched, False, "dummy.value.overide", output, "after")
    print("End-to-end sample verification passed. Artifacts:", output)


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        print(error.stdout or error.stderr or str(error))
        raise SystemExit(error.returncode)
