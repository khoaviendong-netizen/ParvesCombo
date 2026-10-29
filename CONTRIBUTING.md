# Contributing to ParvesCombo

Small, focused contributions are welcome. Open an issue describing the problem or proposed change before a major rewrite.

## Bug reports

Include app version, Android version, device architecture, steps to reproduce, expected behavior, and actual behavior. Use dummy proxy settings and sample lists. Remove passwords, TOTP secrets, personal numbers, names, IP addresses that identify private infrastructure, and signing information from logs and screenshots.

## Development

Follow the build instructions in README.md and README_PREMIUM_BUILD.md. Build an unsigned APK with `bash manual_build.sh /path/to/android-sdk --unsigned`. Use your own local signing key for device testing and never commit it.

Describe the change, its reason, and validation in each pull request. For functional changes, test the affected behavior on a device when possible and state any untested behavior. Preserve existing copyright headers and third-party license files.

## Current limitation

The native tunnel is a prebuilt ARM64 binary. Its matching source revision and build instructions are not recorded in this repository. Contributions that establish its provenance and provide a reproducible native build are particularly useful. Do not claim the native component was rebuilt or audited without evidence.

## Sensitive reports

Do not post exploitable security details or credentials in public issues. Use GitHub private vulnerability reporting if enabled. Otherwise open an issue asking the maintainer for a private reporting channel without including sensitive details.
