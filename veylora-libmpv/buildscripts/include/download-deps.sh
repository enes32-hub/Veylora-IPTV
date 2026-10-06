#!/bin/bash -e

. ./include/depinfo.sh

[ -z "$WGET" ] && WGET=wget

mkdir -p deps && cd deps

# mbedtls
[ ! -d mbedtls ] && git clone --depth 1 --branch v$v_mbedtls --recurse-submodules https://github.com/Mbed-TLS/mbedtls.git mbedtls

# dav1d
[ ! -d dav1d ] && git clone --depth 1 --branch $v_dav1d https://code.videolan.org/videolan/dav1d.git dav1d

# ffmpeg
[ ! -d ffmpeg ] && git clone --depth 1 --branch n$v_ffmpeg https://github.com/FFmpeg/FFmpeg.git ffmpeg

# freetype2
[ ! -d freetype ] && git clone --depth 1 --branch VER-$v_freetype https://gitlab.freedesktop.org/freetype/freetype.git freetype

# fribidi
[ ! -d fribidi ] && git clone --depth 1 --branch v$v_fribidi https://github.com/fribidi/fribidi.git fribidi

# harfbuzz
[ ! -d harfbuzz ] && git clone --depth 1 --branch $v_harfbuzz https://github.com/harfbuzz/harfbuzz.git harfbuzz

# libunibreak
if [ ! -d libunibreak ]; then
	mkdir libunibreak
	$WGET https://github.com/adah1972/libunibreak/releases/download/libunibreak_${v_libunibreak}/libunibreak-${v_libunibreak//_/.}.tar.gz -O - | \
		tar -xz -C libunibreak --strip-components=1
fi

# libxml2
[ ! -d libxml2 ] && git clone --depth 1 --branch v$v_libxml2 https://gitlab.gnome.org/GNOME/libxml2.git libxml2

# fontconfig
[ ! -d fontconfig ] && git clone --depth 1 --branch $v_fontconfig https://gitlab.freedesktop.org/fontconfig/fontconfig.git fontconfig

# libass
[ ! -d libass ] && git clone --depth 1 --branch $v_libass https://github.com/libass/libass.git libass

# lua
if [ ! -d lua ]; then
	mkdir lua
	$WGET http://www.lua.org/ftp/lua-$v_lua.tar.gz -O - | \
		tar -xz -C lua --strip-components=1
fi

[ ! -d libplacebo ] && git clone --depth 1 --branch v$v_libplacebo --recurse-submodules https://code.videolan.org/videolan/libplacebo.git libplacebo

# mpv — a pinned master commit. Blobless rather than shallow, so the tags come along and mpv's
# version string reads "v0.41.0-1072-g2a4eb8067" instead of a bare hash.
if [ ! -d mpv ]; then
	git clone --filter=blob:none https://github.com/mpv-player/mpv.git mpv
	git -C mpv checkout -q $v_mpv
fi

cd ..
