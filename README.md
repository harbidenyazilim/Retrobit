# Retrobit

Retrobit is an Android and Android TV frontend for organizing and running game files selected by the user. It does not include, host, or link to ROMs or BIOS files.

## Build

Requirements: Java 17, Android SDK 36, Android NDK 28.0.13004108, CMake 3.22.1, and Ninja.

```bash
git clone https://github.com/harbidenyazilim/Retrobit.git
cd Retrobit
git submodule update --init --jobs 4
./gradlew :app:assemblePlayDebug
```

Release signing is enabled only when all four variables are set: `RETROBIT_STORE_FILE`, `RETROBIT_STORE_PASSWORD`, `RETROBIT_KEY_ALIAS`, and `RETROBIT_KEY_PASSWORD`. Keys and passwords must not be committed.

The bundled libretro cores are pinned as Git submodules. Their exact revisions, licenses, binary SHA-256 values, and build adjustments are recorded in `app/src/main/assets/licenses/EMULATOR_COMPONENTS.txt`. Rebuild all four Play ABIs with:

```bash
./tools/build-android-cores.sh
```

See `docs/DEVELOPER.md` for architecture and detailed setup.

## Privacy

The Turkish and English privacy policy is published at https://harbidenyazilim.github.io/Retrobit/ (source: `docs/PRIVACY_POLICY.md`). Retrobit has no ads, analytics, tracking SDK, developer account system, or developer-operated data server.

## License

Retrobit application source is licensed under GPL-3.0-or-later. Third-party components retain their own licenses; see `app/src/main/assets/licenses/` and each pinned submodule.
