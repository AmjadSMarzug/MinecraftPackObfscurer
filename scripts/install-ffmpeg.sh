#!/usr/bin/env bash
# Installs ffmpeg with Vorbis support (libvorbis), which PackObfuscator needs for junk sounds
# and for removing Vorbis tags. Works on Debian/Ubuntu, Fedora/RHEL, Arch, openSUSE and macOS (Homebrew).
#
#   scripts/install-ffmpeg.sh          install if missing, then verify
#   scripts/install-ffmpeg.sh --check  only check, install nothing
set -euo pipefail

CHECK_ONLY=0
[ "${1:-}" = "--check" ] && CHECK_ONLY=1

has_vorbis() {
    command -v ffmpeg >/dev/null 2>&1 && ffmpeg -hide_banner -encoders 2>/dev/null | grep -q "libvorbis"
}

verify() {
    if has_vorbis; then
        echo "OK: $(command -v ffmpeg) supports libvorbis."
        ffmpeg -hide_banner -version | head -1
        return 0
    fi
    echo "ffmpeg with libvorbis was not found on the PATH." >&2
    return 1
}

if has_vorbis; then
    verify
    exit 0
fi
if [ "$CHECK_ONLY" = "1" ]; then
    verify || exit 1
fi

# Run a command as root when we are not already root.
as_root() {
    if [ "$(id -u)" -eq 0 ]; then
        "$@"
    elif command -v sudo >/dev/null 2>&1; then
        sudo "$@"
    else
        echo "This needs root to install packages. Run it as root or install sudo." >&2
        exit 1
    fi
}

OS="$(uname -s)"
case "$OS" in
    Darwin)
        if ! command -v brew >/dev/null 2>&1; then
            echo "Homebrew is required on macOS. Install it from https://brew.sh first." >&2
            exit 1
        fi
        brew install ffmpeg
        ;;
    Linux)
        if command -v apt-get >/dev/null 2>&1; then
            as_root apt-get update
            as_root apt-get install -y ffmpeg
        elif command -v dnf >/dev/null 2>&1; then
            # Fedora's ffmpeg is in RPM Fusion on some releases. Plain "ffmpeg" works on current Fedora.
            as_root dnf install -y ffmpeg || {
                echo "dnf could not install ffmpeg. Enable RPM Fusion (https://rpmfusion.org) and run this again." >&2
                exit 1
            }
        elif command -v pacman >/dev/null 2>&1; then
            as_root pacman -S --needed --noconfirm ffmpeg
        elif command -v zypper >/dev/null 2>&1; then
            as_root zypper --non-interactive install ffmpeg
        else
            echo "No supported package manager found (apt, dnf, pacman, zypper)." >&2
            echo "Install ffmpeg yourself from https://ffmpeg.org/download.html, then run this script with --check." >&2
            exit 1
        fi
        ;;
    *)
        echo "Unsupported system: $OS. On Windows use scripts/install-ffmpeg.ps1." >&2
        exit 1
        ;;
esac

hash -r 2>/dev/null || true
verify
