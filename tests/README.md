# Sample patch end to end

This fixture supplies the fictional `com.example.app` version `1.0.2` targeted by the template. It exercises both the Example Patch and its internal dependency, without downloading a third-party APK.

The original app reports `ads=true helper=original`. After patching, the extension returns false and the internal patch overrides a helper argument, producing `ads=false helper=dummy.value.overide`. The misspelling is the template's existing literal.

The sample fingerprint uses `IGET_OBJECT` for its `Map` field. The template's `IGET` instruction cannot match a valid object-field access.

## Installed tools

The dedicated AVD is named `morphe_sample_api36`. It uses the stable Android 36 AOSP ARM64 image, with no Google account required. The emulator and image are installed in the Android SDK.

Morphe Desktop 1.19.1 is saved at `tools/morphe-desktop-1.19.1-all.jar` and ignored by Git. Its release SHA-256 was verified against GitHub:

```text
a51472252cd63b2eac635dbb5a154ff918bfc15268da89679feb3d760230c573
```

To restore these tools on another Mac:

```sh
sdkmanager "emulator" "system-images;android-36;default;arm64-v8a"
avdmanager create avd --name morphe_sample_api36 \
  --package "system-images;android-36;default;arm64-v8a" --device pixel_6
mkdir -p tools
curl -fL https://github.com/MorpheApp/morphe-desktop/releases/download/v1.19.1/morphe-desktop-1.19.1-all.jar \
  -o tools/morphe-desktop-1.19.1-all.jar
printf '%s  %s\n' a51472252cd63b2eac635dbb5a154ff918bfc15268da89679feb3d760230c573 \
  tools/morphe-desktop-1.19.1-all.jar | shasum -a 256 -c -
```

## Run the test

Use the Java, SDK, and GitHub credential environment from the [project build instructions](../README.md#build). Start the dedicated emulator on a free console port, for example 5556:

```sh
"$ANDROID_HOME/emulator/emulator" -avd morphe_sample_api36 -port 5556 \
  -gpu auto -no-audio -no-snapshot-save -no-boot-anim
```

Wait until `adb -s emulator-5556 shell getprop sys.boot_completed` returns `1`, then run:

```sh
python3 tests/verify_sample.py --serial emulator-5556
```

The runner builds a fresh fixture APK, rebuilds the patch bundle, applies only Example Patch with `--validate`, `--bytecode-mode FULL`, and SDK verification, and signs both APKs with the same local debug key. It wakes and unlocks the dedicated emulator, then installs and launches each variant, checking the exact runtime result through logcat and exact visible text lines through UI Automator before capturing a screenshot. It refuses to target a device whose AVD name differs from `morphe_sample_api36`.

APKs, the debug key, logs, Morphe runtime data, and before/after screenshots are stored in the Git-ignored `build/sample-e2e/` directory. The patched app remains installed after a successful run. Keep this directory's debug key to update the fixture on later runs; if it is deleted, uninstall only this fixture from the dedicated AVD before rerunning. The runner clears logcat on that dedicated emulator for each launch.

Stop the emulator with `adb -s emulator-5556 emu kill`. This fixture verifies the sample on Android 36; other apps and Android releases need their own tests.
