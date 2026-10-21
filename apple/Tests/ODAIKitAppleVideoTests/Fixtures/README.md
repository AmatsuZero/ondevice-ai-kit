Synthetic fixtures generated locally with ffmpeg (no downloads/private media).

`ffmpeg -v error -nostdin -f lavfi -i color=c=blue:size=320x180:rate=24:duration=2 -f lavfi -i sine=frequency=440:sample_rate=48000:duration=2 -c:v libx264 -pix_fmt yuv420p -color_primaries bt709 -color_trc bt709 -colorspace bt709 -c:a aac -b:a 64k apple/Tests/ODAIKitAppleVideoTests/Fixtures/aac.mp4`

`ffmpeg -v error -nostdin -f lavfi -i color=c=blue:size=1280x720:rate=24:duration=2 -c:v libx265 -x265-params log-level=error:pools=1 -tag:v hvc1 -pix_fmt yuv420p -color_primaries bt709 -color_trc bt709 -colorspace bt709 apple/Tests/ODAIKitAppleVideoTests/Fixtures/video-only.mov`

`ffmpeg -v error -nostdin -f lavfi -i sine=frequency=440:sample_rate=48000:duration=2 -c:a pcm_s16le apple/Tests/ODAIKitAppleVideoTests/Fixtures/pcm.wav`

`ffmpeg -v error -nostdin -f lavfi -i sine=frequency=440:sample_rate=48000:duration=3 -c:a aac -b:a 64k apple/Tests/ODAIKitAppleVideoTests/Fixtures/long-aac.m4a`

`ffmpeg -v error -nostdin -f lavfi -i sine=frequency=440:sample_rate=48000:duration=2 -map 0:a -map 0:a -c:a aac -b:a 64k apple/Tests/ODAIKitAppleVideoTests/Fixtures/two-aac.m4a`

`ffmpeg -v error -nostdin -f lavfi -i sine=frequency=440:sample_rate=48000:duration=1 -af asetpts=PTS+0.25/TB -c:a aac -b:a 64k apple/Tests/ODAIKitAppleVideoTests/Fixtures/offset-aac.mov`
