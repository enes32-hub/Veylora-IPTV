#!/bin/bash -e

. ../../include/depinfo.sh
. ../../include/path.sh

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	rm -rf _build$ndk_suffix
	exit 0
else
	exit 255
fi

mkdir -p _build$ndk_suffix
cd _build$ndk_suffix

cpu=armv7-a
[[ "$ndk_triple" == "aarch64"* ]] && cpu=armv8-a
[[ "$ndk_triple" == "x86_64"* ]] && cpu=generic
[[ "$ndk_triple" == "i686"* ]] && cpu="i686 --disable-asm"

cpuflags=
[[ "$ndk_triple" == "arm"* ]] && cpuflags="$cpuflags -mfpu=neon -mcpu=cortex-a8"

# Filters are an allowlist, not all-or-nothing. Without any, mpv's `--deinterlace` and every `af=lavfi`
# fail silently (the option is accepted, the filter never runs). tools/inspect_aar.py checks each one
# below is really registered in the built libavfilter.
filters=(
	# graph plumbing mpv's lavfi bridge creates by name (filters/f_lavfi.c)
	buffer buffersink abuffer abuffersink
	# format converters libavfilter inserts on its own when two filters disagree
	scale aresample format aformat null anull
	# inserted by mpv itself (filters/f_auto_filters.c): deinterlace, rotation, flip
	bwdif yadif rotate transpose hflip vflip
	# sound: night mode, volume levelling, loudness, EQ, dialogue
	acompressor dynaudnorm loudnorm alimiter volume pan
	equalizer anequalizer bass treble highpass lowpass dialoguenhance
)
filter_list=$(IFS=,; echo "${filters[*]}")
# Two muxers and still no encoders: mpv's `--stream-record` / `dump-cache` can then write what it is
# already playing to disk untouched (a local timeshift option), but nothing is ever re-encoded.

../configure \
	--target-os=android --enable-cross-compile --cross-prefix=$ndk_triple- --cc=$CC \
	--arch=${ndk_triple%%-*} --cpu=$cpu --pkg-config=pkg-config --nm=llvm-nm \
	--extra-cflags="-I$prefix_dir/include $cpuflags" --extra-ldflags="-L$prefix_dir/lib" \
	--enable-{jni,mediacodec,mbedtls,libdav1d,libxml2} --disable-vulkan \
	--disable-static --enable-shared --enable-{gpl,version3} \
	--disable-{stripping,doc,programs} \
	--disable-{muxers,encoders,devices,filters} \
	--enable-filter=$filter_list \
	--enable-muxer=mpegts,matroska \
	--disable-v4l2-m2m

make -j$cores
make DESTDIR="$prefix_dir" install

ln -sf "$prefix_dir"/lib/libswresample.so "$native_dir"
ln -sf "$prefix_dir"/lib/libpostproc.so "$native_dir"
ln -sf "$prefix_dir"/lib/libavutil.so "$native_dir"
ln -sf "$prefix_dir"/lib/libavcodec.so "$native_dir"
ln -sf "$prefix_dir"/lib/libavformat.so "$native_dir"
ln -sf "$prefix_dir"/lib/libswscale.so "$native_dir"
ln -sf "$prefix_dir"/lib/libavfilter.so "$native_dir"
ln -sf "$prefix_dir"/lib/libavdevice.so "$native_dir"
