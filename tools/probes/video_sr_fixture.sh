#!/bin/sh
set -eu
bt709=1
case "${1:-}" in
    --bt709) shift ;;
    --untagged) bt709=0; shift ;;
    --*) echo "usage: $0 [--bt709|--untagged] [output-directory]" >&2; exit 2 ;;
esac
out_dir="${1:-artifacts/P7}"
mkdir -p "$out_dir"
color_flags=""
source_filter=""
color_filter_suffix=""
if [ "$bt709" -eq 1 ]; then
    color_flags="-color_range tv -colorspace bt709 -color_trc bt709 -color_primaries bt709"
    source_filter="-vf setparams=range=tv:color_primaries=bt709:color_trc=bt709:colorspace=bt709"
    color_filter_suffix=",setparams=range=tv:color_primaries=bt709:color_trc=bt709:colorspace=bt709"
fi
ffmpeg -hide_banner -loglevel error -f lavfi -i 'testsrc2=size=1280x720:rate=24' -frames:v 48 $source_filter -an -c:v ffv1 -pix_fmt yuv420p $color_flags -y "$out_dir/reference-720p.mkv"
ffmpeg -hide_banner -loglevel error -i "$out_dir/reference-720p.mkv" -vf "scale=640:360:flags=area$color_filter_suffix" -an -c:v ffv1 -pix_fmt yuv420p $color_flags -y "$out_dir/input-360p.mkv"
ffmpeg -hide_banner -loglevel error -i "$out_dir/input-360p.mkv" -vf "scale=1280:720:flags=bicubic$color_filter_suffix" -an -c:v ffv1 -pix_fmt yuv420p $color_flags -y "$out_dir/bicubic-720p.mkv"
ffmpeg -hide_banner -loglevel error -i "$out_dir/reference-720p.mkv" -vf "scale=320:180:flags=area$color_filter_suffix" -an -c:v ffv1 -pix_fmt yuv420p $color_flags -y "$out_dir/input-180p-4x.mkv"
ffmpeg -hide_banner -loglevel error -i "$out_dir/input-180p-4x.mkv" -vf "scale=1280:720:flags=bicubic$color_filter_suffix" -an -c:v ffv1 -pix_fmt yuv420p $color_flags -y "$out_dir/bicubic-720p-4x.mkv"
ffmpeg -hide_banner -loglevel error -i "$out_dir/input-180p-4x.mkv" -an -c:v libx264 -preset ultrafast -crf 0 -pix_fmt yuv420p -movflags +faststart $color_flags -y "$out_dir/input-180p-4x.mp4"
python3 tools/probes/video_sr_validate.py "$out_dir/reference-720p.mkv" "$out_dir/input-360p.mkv" "$out_dir/bicubic-720p.mkv"
python3 tools/probes/video_sr_validate.py --scale=4 "$out_dir/reference-720p.mkv" "$out_dir/input-180p-4x.mkv" "$out_dir/bicubic-720p-4x.mkv"
