# Parves Combo Premium — build handoff

Source prepared for version **3.2**, version code **5**. Application ID remains `com.parves.proxy`; minimum Android API **24**, target API **34**, **ARM64 (`arm64-v8a`) only**.

## What changed

The native UI uses the approved dark navy/cyan design across Proxy, Numbers, Apps, Quick Copy and floating controls. Existing stores, native proxy code and functional handlers are preserved. Quick Copy retains all seven actions and real saved/remaining counts. Numbers still supports bulk addition and long-press deletion; its scrolling form header is excluded from deletion indexes. App selection and search continue saving through the original preferences.

Before the first remote build, validation covered syntax and static checks. The private GitHub repository contains the complete Java and resource inputs, original prebuilt ARM64 library, licenses, and manual build script required for this APK. The full project archive also contains native source and Gradle project files. Refer to the pull request checks for the current build result. Device behavior still requires verification on a phone.

## Build an unsigned APK locally

Use Bash on Linux or WSL, a full JDK (17 recommended), `zip`, and an Android SDK containing **Android SDK Platform 34** and **Build-Tools 34.0.0**. The manual build uses the bundled ARM64 native library and does not require an NDK rebuild.

From the project directory:

```bash
bash manual_build.sh /path/to/android-sdk --unsigned
```

Expected output after a successful build:

```text
release/ParvesCombo-Premium-v3.2-unsigned.apk
```

An unsigned APK cannot be installed. Signing keys and signing credentials are excluded from the exported source ZIP.

## Sign using the original local key

Use the **same original signing key/certificate as the installed app**. Replace the paths and alias below with your local values; `apksigner` prompts for passwords. Do not upload the key or include passwords in commands, screenshots or source control.

```bash
/path/to/android-sdk/build-tools/34.0.0/apksigner sign \
  --ks /path/to/original-signing-key.jks \
  --ks-key-alias ORIGINAL_KEY_ALIAS \
  --out release/ParvesCombo-Premium-v3.2.apk \
  release/ParvesCombo-Premium-v3.2-unsigned.apk

/path/to/android-sdk/build-tools/34.0.0/apksigner verify \
  --verbose --print-certs release/ParvesCombo-Premium-v3.2.apk
```

Compare the printed signer certificate with the original APK before installing. Keeping the same application ID alone is insufficient: updates and future builds also require the same signing identity and an acceptable version code. The certificate of the app installed on your phone has not been verified here.

Install as an update over the existing app. **Do not uninstall the old app or clear its data**; that can remove saved data and its encryption keys. If Android rejects the update, stop and check the signing certificate/version instead of uninstalling.

## Optional GitHub build

The included GitHub Actions workflow runs on pull requests or a manual trigger and produces an **unsigned APK** and the official Android SDK `apksigner.jar` as separate artifacts. Signing keys remain local and are excluded from this repository. Sign the downloaded build locally using the original key as described above.

## বাংলায় জরুরি কথা

এখানে নতুন ডিজাইনের সোর্স কোড দেওয়া হয়েছে। GitHub build-এর বর্তমান ফল pull request-এর checks-এ দেখা যাবে। unsigned APK আগের অ্যাপের একই আসল signing key দিয়ে sign করতে হবে। ফোনে চালিয়ে আচরণ যাচাই করা এখনো বাকি।

পুরোনো অ্যাপ **মুছবেন না, Clear data-ও করবেন না**। একই package name ও signing certificate মিলিয়ে পুরোনো অ্যাপের ওপর update দিন। ইনস্টল না হলে আগে কারণ যাচাই করুন—আনইনস্টল করলে সেভ করা তথ্য ও encryption key হারাতে পারে। ভবিষ্যতের আপডেটের জন্যও একই signing key নিজের কাছে রাখতে হবে।
