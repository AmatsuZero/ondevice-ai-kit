#!/bin/sh
set -eu
out_dir="${1:-artifacts/P7}"
mkdir -p "$out_dir"
ffmpeg -hide_banner -loglevel error -f lavfi -i 'testsrc2=size=1280x720:rate=24' -frames:v 48 -an -c:v ffv1 -pix_fmt yuv420p -y "$out_dir/reference-720p.mkv"
ffmpeg -hide_banner -loglevel error -i "$out_dir/reference-720p.mkv" -vf 'scale=640:360:flags=area' -an -c:v ffv1 -pix_fmt yuv420p -y "$out_dir/input-360p.mkv"
ffmpeg -hide_banner -loglevel error -i "$out_dir/input-360p.mkv" -vf 'scale=1280:720:flags=bicubic' -an -c:v ffv1 -pix_fmt yuv420p -y "$out_dir/bicubic-720p.mkv"
python3 tools/probes/video_sr_validate.py "$out_dir/reference-720p.mkv" "$out_dir/input-360p.mkv" "$out_dir/bicubic-720p.mkv"
