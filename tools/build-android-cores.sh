#!/usr/bin/env bash
set -euo pipefail

NDK_VERSION=28.0.13004108
CMAKE_VERSION=3.22.1
ABIS=(arm64-v8a armeabi-v7a x86 x86_64)
JOBS="${JOBS:-4}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CORE_SOURCE_ROOT="${CORE_SOURCE_ROOT:-$SCRIPT_DIR/../native-sources}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_SDK_ROOT:+$ANDROID_SDK_ROOT/ndk/$NDK_VERSION}}"

if [[ -z "$ANDROID_NDK_HOME" || ! -d "$ANDROID_NDK_HOME" ]]; then
    printf 'Android NDK %s not found. Set ANDROID_NDK_HOME.\n' "$NDK_VERSION" >&2
    exit 1
fi

NDK_BUILD="$ANDROID_NDK_HOME/ndk-build"
[[ -f "$NDK_BUILD.cmd" ]] && NDK_BUILD="$NDK_BUILD.cmd"
CMAKE="${CMAKE:-${ANDROID_SDK_ROOT:+$ANDROID_SDK_ROOT/cmake/$CMAKE_VERSION/bin/cmake}}"
NINJA="${NINJA:-${ANDROID_SDK_ROOT:+$ANDROID_SDK_ROOT/cmake/$CMAKE_VERSION/bin/ninja}}"
[[ -f "$CMAKE.exe" ]] && CMAKE="$CMAKE.exe"
[[ -f "$NINJA.exe" ]] && NINJA="$NINJA.exe"
STRINGS="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strings.exe"
[[ -f "$STRINGS" ]] || STRINGS="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strings"

for tool in "$NDK_BUILD" "$CMAKE" "$NINJA" "$STRINGS"; do
    if [[ ! -f "$tool" && ! -x "$tool" ]]; then
        printf 'Required tool not found: %s\n' "$tool" >&2
        exit 1
    fi
done

if [[ "$(uname -s)" == MINGW* && ${#CORE_SOURCE_ROOT} -gt 45 ]]; then
    printf 'CORE_SOURCE_ROOT must use a short Windows path, for example CORE_SOURCE_ROOT=R:/.\n' >&2
    exit 1
fi

verify_revision() {
    local repo="$1" expected="$2" actual
    actual="$(git -C "$CORE_SOURCE_ROOT/$repo" rev-parse HEAD)"
    if [[ "$actual" != "$expected" ]]; then
        printf '%s revision mismatch: expected %s, found %s\n' "$repo" "$expected" "$actual" >&2
        exit 1
    fi
    # This upstream revision contains an unused nested gitlink without root metadata.
    [[ "$repo" == "mupen64plus-next" ]] && return
    while IFS= read -r submodule; do
        if [[ "$submodule" == [-+]* ]]; then
            printf '%s has an uninitialized or mismatched submodule: %s\n' "$repo" "$submodule" >&2
            exit 1
        fi
    done < <(git -C "$CORE_SOURCE_ROOT/$repo" submodule status --recursive)
}

apply_patch() {
    local repo="$1" patch="$2"
    if git -C "$CORE_SOURCE_ROOT/$repo" apply --check "$patch"; then
        git -C "$CORE_SOURCE_ROOT/$repo" apply "$patch"
    elif ! git -C "$CORE_SOURCE_ROOT/$repo" apply --reverse --check "$patch"; then
        printf 'Patch does not apply cleanly to %s: %s\n' "$repo" "$patch" >&2
        exit 1
    fi
}

verify_revision picodrive 1890c2932234c9d30f4cd3851d02228baae8f09e
verify_revision fceumm 8997c2bf9503f6d50585b11a1fd7207af53a2426
verify_revision bsnes-mercury 79d7f9de218b6ffa65a80bbdc5828532bc239232
verify_revision mupen64plus-next 15bada85906593376e8fd697f1b0b3af3a20f512
verify_revision gambatte 77f2567d718b8973d02caa2daa8dce4953faf811
verify_revision mgba 6c062d1407aa4bfec3235cebb12d229abf781779
verify_revision melonds 4081e8358e66f1ea11879560be6b813dfeaab75a
verify_revision gearsystem f61994f36f443d338f8660b6ff210ad1ea84054a
verify_revision pcsx-rearmed 7b0e8f8fadfbce3d170d195b9d9eecaabfa08c8e
verify_revision ppsspp 65181eea1deb7361beb3be6062ea98e8427f2786
verify_revision stella da5a002c318843bea1d233932508b9439798de0d

apply_patch melonds "$SCRIPT_DIR/../native-patches/melonds-android-jit.patch"
apply_patch ppsspp "$SCRIPT_DIR/../native-patches/ppsspp-android-arm64-png.patch"
apply_patch stella "$SCRIPT_DIR/../native-patches/stella-android-float-parse.patch"

grep -Fq 'ifneq ($(strip $(JIT_ARCH)),)' "$CORE_SOURCE_ROOT/melonds/Makefile.common"
grep -Fq '$(EXTDIR)/libpng17/arm/arm_init.c' "$CORE_SOURCE_ROOT/ppsspp/libretro/Makefile.common"
grep -Fq 'std::strtof' "$CORE_SOURCE_ROOT/stella/src/common/Variant.hxx"
printf '%s\n' \
    'const char *PPSSPP_GIT_VERSION = "v1.20.4-4913-g65181eea1d";' \
    '#define PPSSPP_GIT_VERSION_NO_UPDATE 0' \
    > "$CORE_SOURCE_ROOT/ppsspp/git-version.cpp"

build_root_jni() {
    local repo="$1" abi="$2" dir="$CORE_SOURCE_ROOT/$repo"
    "$NDK_BUILD" -C "$dir" \
        NDK_PROJECT_PATH="$dir" \
        APP_BUILD_SCRIPT="$dir/jni/Android.mk" \
        NDK_APPLICATION_MK="$dir/jni/Application.mk" \
        APP_ABI="$abi" APP_SHORT_COMMANDS=true -j"$JOBS"
}

build_nested_jni() {
    local repo="$1" jni="$2" abi="$3" out_tag="$4"
    shift 4
    local dir="$CORE_SOURCE_ROOT/$repo"
    "$NDK_BUILD" -C "$dir/$jni" \
        APP_ABI="$abi" APP_SHORT_COMMANDS=true \
        NDK_LIBS_OUT="$dir/android-libs" \
        NDK_OUT="$dir/android-obj/$out_tag" \
        "$@" -j"$JOBS"
}

for abi in "${ABIS[@]}"; do
    build_root_jni picodrive "$abi"
    build_root_jni fceumm "$abi"
    build_root_jni melonds "$abi"
    build_root_jni pcsx-rearmed "$abi"
    build_nested_jni bsnes-mercury target-libretro/jni "$abi" "$abi" APP_PLATFORM=android-26 PROFILE=performance
    build_nested_jni gambatte libgambatte/libretro/jni "$abi" "$abi"
    build_nested_jni gearsystem platforms/libretro/jni "$abi" "$abi"
    build_nested_jni stella src/os/libretro/jni "$abi" "${abi}-patched" APP_PLATFORM=android-26
    build_nested_jni ppsspp libretro/jni "$abi" "${abi}-rtti" APP_PLATFORM=android-26 APP_CPPFLAGS="-frtti -fexceptions"
done

for abi in "${ABIS[@]}"; do
    build_dir="$CORE_SOURCE_ROOT/mgba/build-android-$abi"
    "$CMAKE" -S "$CORE_SOURCE_ROOT/mgba" -B "$build_dir" -G Ninja \
        -DCMAKE_MAKE_PROGRAM="$NINJA" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$abi" -DANDROID_PLATFORM=android-21 \
        -DBUILD_LIBRETRO=ON -DBUILD_QT=OFF -DBUILD_SDL=OFF \
        -DBUILD_TEST=OFF -DBUILD_STATIC=OFF
    "$CMAKE" --build "$build_dir" --parallel "$JOBS"
done

build_mupen() {
    local abi="$1" dir="$CORE_SOURCE_ROOT/mupen64plus-next"
    local out="$dir/android-obj/${abi}-gles3"
    local args=(-C "$dir/libretro/jni" APP_ABI="$abi" APP_PLATFORM=android-26 GLES3=1
        APP_SHORT_COMMANDS=true STRINGS="$STRINGS" NDK_LIBS_OUT="$dir/android-libs"
        NDK_OUT="$out" -j"$JOBS")
    local first_status=0
    "$NDK_BUILD" "${args[@]}" || first_status=$?
    if (( first_status != 0 )); then
        local object destination
        object="$(find "$out" -type f -path '*/asm_defines/asm_defines.o' -print -quit)"
        if [[ -z "$object" ]]; then
            printf 'Mupen64Plus offset object was not produced for %s.\n' "$abi" >&2
            exit "$first_status"
        fi
        destination="$out/local/$abi/asm_defines"
        mkdir -p "$destination"
        "$STRINGS" "$object" | tr -d '\r' | awk \
            -v dest_dir="$destination/" \
            -f "$dir/mupen64plus-core/tools/gen_asm_defines.awk"
        "$NDK_BUILD" "${args[@]}"
    fi
}

for abi in "${ABIS[@]}"; do
    build_mupen "$abi"
done

for abi in "${ABIS[@]}"; do
    sha256sum \
        "$CORE_SOURCE_ROOT/picodrive/libs/$abi/libretro.so" \
        "$CORE_SOURCE_ROOT/fceumm/libs/$abi/libretro.so" \
        "$CORE_SOURCE_ROOT/bsnes-mercury/android-libs/$abi/libretro.so" \
        "$CORE_SOURCE_ROOT/mupen64plus-next/android-libs/$abi/libretro.so" \
        "$CORE_SOURCE_ROOT/gambatte/android-libs/$abi/libretro.so" \
        "$CORE_SOURCE_ROOT/mgba/build-android-$abi/mgba_libretro.so" \
        "$CORE_SOURCE_ROOT/melonds/libs/$abi/libretro.so" \
        "$CORE_SOURCE_ROOT/gearsystem/android-libs/$abi/libretro.so" \
        "$CORE_SOURCE_ROOT/pcsx-rearmed/libs/$abi/libretro.so" \
        "$CORE_SOURCE_ROOT/ppsspp/android-libs/$abi/libretro.so" \
        "$CORE_SOURCE_ROOT/stella/android-libs/$abi/libretro.so"
done
