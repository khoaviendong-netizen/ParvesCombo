# ParvesCombo

An Android SOCKS5 proxy client with per-app routing, saved proxy profiles, floating controls, number lists, and Quick Copy utilities.

## Project status

This repository contains the Java application, Android resources, a manual APK build script, and a prebuilt ARM64 native tunnel library. It is a customized application built on code credited to hev. Keep the original copyright and third-party license notices when redistributing.

**Native source limitation:** the checked-in `app/src/main/jni/hev-socks5-tunnel/` directories currently contain license files only. The source revision and reproducible build instructions for `manual-prebuilt/arm64-v8a/libhev-socks5-tunnel.so` are not recorded here. The current build packages that binary; it does not rebuild the native tunnel. Publishing this repository does not resolve that source/provenance gap.

## Features present in the code

- SOCKS5 profiles with optional username/password authentication.
- Per-app selection, IPv4/IPv6 and DNS settings.
- Proxy exit-IP check through `api.ipify.org` over HTTPS.
- Floating proxy controls and Quick Copy actions.
- Local number lists and name queues.
- Quick Copy password and TOTP secret storage using Android Keystore-backed AES-GCM, plus six-digit TOTP generation.

These describe implemented code, not a claim of independent security auditing or verified behavior on every device.

## Requirements and build

- Android 7.0+ (API 24), ARM64 device; target API 34.
- Linux or WSL, Bash, full JDK 17, and `zip`.
- Android SDK Platform 34 and Build-Tools 34.0.0.

```bash
git clone https://github.com/khoaviendong-netizen/ParvesCombo.git
cd ParvesCombo
bash manual_build.sh /path/to/android-sdk --unsigned
```

Output: `release/ParvesCombo-Premium-v3.2-unsigned.apk`. An unsigned APK must be signed before installation. See [build and signing instructions](README_PREMIUM_BUILD.md). The GitHub Actions workflow also supports manual builds and pull requests.

To update an installed copy, use its original signing key. Do not uninstall or clear app data to work around a signing mismatch: saved data and encryption keys can be lost.

## Privacy and security notes

- Supply your own authorized SOCKS5 server; this project does not provide a proxy service.
- The exit-IP check contacts ipify through the selected proxy.
- Proxy credentials are stored in app SharedPreferences; they do not use the Quick Copy encrypted store.
- Names and number lists are stored locally in preferences. Quick Copy passwords and TOTP secrets use the encrypted store described above.
- Copying a secret puts it on the Android clipboard. Consider clipboard exposure before using this feature.
- The app requests VPN, notification, overlay, and installed-package visibility permissions for its corresponding features.
- Never commit real credentials, TOTP secrets, signing keys, personal lists, or device-data exports.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Report reproducible bugs through repository issues, using dummy data. Useful next work includes documenting the exact native library source revision and build process, testing on devices, and reviewing credential handling.

## License and attribution

The existing [MIT license](LICENSE) credits hev (2023). Original source headers and bundled third-party licenses remain in place. Components under `app/src/main/jni/hev-socks5-tunnel/` include additional license notices; review those for the component being redistributed.

বাংলা: এটি Android-এর SOCKS5 proxy ও Quick Copy অ্যাপ। নিজের proxy ব্যবহার করতে হবে। বিল্ডে আগে থেকে তৈরি ARM64 native library ব্যবহৃত হয়; সেটির সম্পূর্ণ সোর্স এই রিপোজিটরিতে নেই।
