#!/bin/sh
set -eu
out_dir="${1:-artifacts/P7}"
mkdir -p "$out_dir"
ffmpeg -hide_banner -loglevel error -f lavfi -i 'testsrc2=size=1280x720:rate=24' -frames:v 48 -an -c:v ffv1 -pix_fmt yuv420p -y "$out_dir/reference-720p.mkv"
ffmpeg -hide_banner -loglevel error -i "$out_dir/reference-720p.mkv" -vf 'scale=640:360:flags=area' -an -c:v ffv1 -pix_fmt yuv420p -y "$out_dir/input-360p.mkv"
ffmpeg -hide_banner -loglevel error -i "$out_dir/input-360p.mkv" -vf 'scale=1280:720:flags=bicubic' -an -c:v ffv1 -pix_fmt yuv420p -y "$out_dir/bicubic-720p.mkv"
ffmpeg -hide_banner -loglevel error -i "$out_dir/reference-720p.mkv" -vf 'scale=320:180:flags=area' -an -c:v ffv1 -pix_fmt yuv420p -y "$out_dir/input-180p-4x.mkv"
ffmpeg -hide_banner -loglevel error -i "$out_dir/input-180p-4x.mkv" -vf 'scale=1280:720:flags=bicubic' -an -c:v ffv1 -pix_fmt yuv420p -y "$out_dir/bicubic-720p-4x.mkv"
ffmpeg -hide_banner -loglevel error -i "$out_dir/input-180p-4x.mkv" -an -c:v libx264 -preset ultrafast -crf 0 -pix_fmt yuv420p -movflags +faststart -y "$out_dir/input-180p-4x.mp4"
python3 tools/probes/video_sr_validate.py "$out_dir/reference-720p.mkv" "$out_dir/input-360p.mkv" "$out_dir/bicubic-720p.mkv"
python3 tools/probes/video_sr_validate.py --scale=4 "$out_dir/reference-720p.mkv" "$out_dir/input-180p-4x.mkv" "$out_dir/bicubic-720p-4x.mkv"
