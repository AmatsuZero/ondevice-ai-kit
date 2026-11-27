#!/usr/bin/env python3
'''本地原型服务器（仅 Python3 标准库）。

运行：
    python3 serve.py --port 8765
    python3 serve.py --port 8765 --library /path/to/libopenvoice.so --assets /path/to/assets

选项：
    --port INTEGER   监听端口，默认 8765，仅绑定 127.0.0.1
    --library PATH   已编译的 OpenVoice CPU 动态库（需同时给出 --assets）
    --assets PATH    OpenVoice 参考音色资源目录

静态资源（GET/HEAD）：
    /  /index.html  /original.wav  /clips/{up,down,voiceA,voiceB,voiceC}.wav
    /app-icon.png  /app.js  /voice-worklet.js  /file-mode.js  /waveform.json

转换接口（POST，Content-Type: audio/wav）：
    /convert/{up,down,voiceA,voiceB,voiceC}

仅接受本机 Host/Origin，无 CORS、无目录列表。
'''
import argparse
import ctypes
import os
import re
import struct
import subprocess
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BASE = os.path.dirname(os.path.abspath(__file__))
RATE = 16000
MAX_TOTAL = 960044
MAX_PAYLOAD = 960000
MIN_PAYLOAD = 1600
TIMEOUT = 15

EFFECTS = ('up', 'down', 'voiceA', 'voiceB', 'voiceC')
NEURAL = ('voiceA', 'voiceB', 'voiceC')

ALLOW = {
    '/': 'index.html',
    '/index.html': 'index.html',
    '/original.wav': 'original.wav',
    '/clips/up.wav': 'clips' + os.sep + 'up.wav',
    '/clips/down.wav': 'clips' + os.sep + 'down.wav',
    '/clips/voiceA.wav': 'clips' + os.sep + 'voiceA.wav',
    '/clips/voiceB.wav': 'clips' + os.sep + 'voiceB.wav',
    '/clips/voiceC.wav': 'clips' + os.sep + 'voiceC.wav',
    '/app-icon.png': 'app-icon.png',
    '/app.js': 'app.js',
    '/voice-worklet.js': 'voice-worklet.js',
    '/file-mode.js': 'file-mode.js',
    '/waveform.json': 'waveform.json',
}

HEADER_FMT = '<IHHIIHH'
NEURAL_RATE = 22050
MAX_NEURAL_TOTAL = 1323044


def build_wav(pcm):
    body = struct.pack(HEADER_FMT, 16, 1, 1, RATE, RATE * 2, 2, 16)
    return b'RIFF' + struct.pack('<I', 36 + len(pcm)) + b'WAVEfmt ' + body + b'data' + struct.pack('<I', len(pcm)) + pcm


def parse_wav(data, rate=RATE):
    if len(data) < 44:
        raise ValueError('too short')
    if data[0:4] != b'RIFF' or data[8:12] != b'WAVE':
        raise ValueError('not wav')
    riff_size = struct.unpack_from('<I', data, 4)[0]
    if data[12:16] != b'fmt ':
        raise ValueError('no fmt')
    fmt_size = struct.unpack_from('<I', data, 16)[0]
    if fmt_size != 16:
        raise ValueError('bad fmt')
    fmt = struct.unpack_from('<HHIIHH', data, 20)
    if data[36:40] != b'data':
        raise ValueError('no data')
    data_size = struct.unpack_from('<I', data, 40)[0]
    if (fmt[0] != 1 or fmt[1] != 1 or fmt[2] != rate or
            fmt[3] != rate * 2 or fmt[4] != 2 or fmt[5] != 16):
        raise ValueError('bad fmt')
    if data_size % 2 != 0 or riff_size != len(data) - 8 or data_size != len(data) - 44:
        raise ValueError('bad size')
    if len(data) > rate * 30 * 2 + 44 or (rate == RATE and data_size < MIN_PAYLOAD):
        raise ValueError('bad duration')
    return data[44:]


def mime_for(path):
    if path.endswith('.html'):
        return 'text/html; charset=utf-8'
    if path.endswith('.js'):
        return 'application/javascript; charset=utf-8'
    if path.endswith('.json'):
        return 'application/json; charset=utf-8'
    if path.endswith('.wav'):
        return 'audio/wav'
    if path.endswith('.png'):
        return 'image/png'
    return 'application/octet-stream'


class OpenVoice:
    def __init__(self, library, assets):
        self.fn = ctypes.CDLL(library).openvoice_render
        self.fn.argtypes = [ctypes.c_char_p] * 4
        self.fn.restype = ctypes.c_int
        self.assets = assets.encode('utf-8')

    def render(self, pcm, voice):
        with tempfile.TemporaryDirectory(prefix='ov-') as d:
            inp = os.path.join(d, 'in.wav')
            out = os.path.join(d, 'out.wav')
            with open(inp, 'wb') as f:
                f.write(build_wav(pcm))
            rc = self.fn(self.assets, inp.encode('utf-8'), voice.encode('utf-8'), out.encode('utf-8'))
            if rc != 0:
                raise RuntimeError('render failed')
            with open(out, 'rb') as f:
                data = f.read(MAX_NEURAL_TOTAL + 1)
            parse_wav(data, NEURAL_RATE)
            return data


def shift(pcm, effect):
    semitones = 4 if effect == 'up' else -4
    k = pow(2.0, semitones / 12.0)
    rate = int(round(RATE * k))
    tempo = 1.0 / k
    count = len(pcm) // 2
    af = ('asetrate=' + str(rate) + ',aresample=' + str(RATE) +
          ',atempo=' + str(tempo) + ',apad,atrim=end_sample=' + str(count))
    with tempfile.TemporaryDirectory(prefix='conv-') as d:
        src = os.path.join(d, 'in.wav')
        with open(src, 'wb') as f:
            f.write(build_wav(pcm))
        cmd = ['ffmpeg', '-nostdin', '-hide_banner', '-loglevel', 'error',
               '-i', src, '-af', af, '-f', 's16le', '-acodec', 'pcm_s16le',
               '-ar', str(RATE), '-ac', '1', 'pipe:1']
        proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=TIMEOUT)
    if proc.returncode != 0 or len(proc.stdout) != count * 2:
        raise RuntimeError('ffmpeg failed')
    parse_wav(build_wav(proc.stdout))
    return proc.stdout


PORT = 8765
OV = None
ADMIT = threading.BoundedSemaphore(1)


class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.0'
    server_version = 'LocalVoice/1.0'

    def setup(self):
        super().setup()
        self.connection.settimeout(TIMEOUT)

    def log_message(self, fmt, *args):
        return

    def reply(self, code, body, ctype):
        if isinstance(body, str):
            body = body.encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', ctype)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.end_headers()
        if self.command != 'HEAD':
            self.wfile.write(body)

    def text(self, code, msg):
        self.close_connection = True
        self.reply(code, msg, 'text/plain; charset=utf-8')

    def local_host(self):
        return self.headers.get('Host', '') in ('localhost:' + str(PORT), '127.0.0.1:' + str(PORT))

    def do_GET(self):
        self.static()

    def do_HEAD(self):
        self.static()

    def reply_wav(self, data, ctype):
        total = len(data)
        extra = [('Accept-Ranges', 'bytes')]
        rng = self.headers.get('Range', '').strip()
        code = 200
        if rng:
            m = re.fullmatch(r'bytes=(\d*)-(\d*)', rng)
            start = None
            if m and (m.group(1) or m.group(2)):
                first, last = m.group(1), m.group(2)
                if first:
                    start = int(first)
                    end = int(last) if last else total - 1
                    if start >= total or end < start:
                        start = None
                    else:
                        end = min(end, total - 1)
                else:
                    n = int(last)
                    if n > 0:
                        start = max(0, total - n)
                        end = total - 1
            if start is None:
                self.send_response(416)
                self.send_header('Content-Type', ctype)
                self.send_header('Content-Length', '0')
                self.send_header('Content-Range', 'bytes */' + str(total))
                self.send_header('Accept-Ranges', 'bytes')
                self.send_header('Cache-Control', 'no-store')
                self.send_header('X-Content-Type-Options', 'nosniff')
                self.end_headers()
                return
            code = 206
            data = data[start:end + 1]
            extra.append(('Content-Range', 'bytes %d-%d/%d' % (start, end, total)))
        self.send_response(code)
        self.send_header('Content-Type', ctype)
        self.send_header('Content-Length', str(len(data)))
        for key, value in extra:
            self.send_header(key, value)
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.end_headers()
        if self.command != 'HEAD':
            self.wfile.write(data)

    def static(self):
        if not self.local_host():
            return self.text(403, '仅允许本机访问')
        path = self.path.split('?', 1)[0].split('#', 1)[0]
        rel = ALLOW.get(path)
        if rel is None:
            return self.text(404, '未找到资源')
        full = os.path.join(BASE, *rel.split(os.sep))
        try:
            with open(full, 'rb') as f:
                data = f.read()
        except OSError:
            return self.text(404, '未找到资源')
        ctype = mime_for(rel)
        if ctype == 'audio/wav':
            return self.reply_wav(data, ctype)
        return self.reply(200, data, ctype)

    def do_POST(self):
        if not self.local_host():
            return self.text(403, '仅允许本机访问')
        if self.headers.get('Origin', '') != 'http://' + self.headers.get('Host', ''):
            return self.text(403, '仅允许本机访问')
        path = self.path.split('?', 1)[0]
        if not path.startswith('/convert/'):
            return self.text(404, '未找到资源')
        effect = path[len('/convert/'):]
        if effect not in EFFECTS:
            return self.text(404, '未找到资源')
        ctype = self.headers.get('Content-Type', '').split(';', 1)[0].strip()
        if ctype != 'audio/wav':
            return self.text(415, '仅支持 audio/wav')
        try:
            size = int(self.headers.get('Content-Length', ''))
        except ValueError:
            return self.text(400, '请求无效')
        if size <= 0 or size > MAX_TOTAL:
            return self.text(413, '音频过大')
        try:
            data = self.rfile.read(size)
        except OSError:
            return self.text(400, '请求无效')
        if len(data) != size:
            return self.text(400, '请求无效')
        try:
            pcm = parse_wav(data)
        except ValueError:
            return self.text(422, 'WAV 格式无效')
        if not ADMIT.acquire(timeout=TIMEOUT):
            return self.text(503, '服务繁忙，请稍后再试')
        try:
            try:
                if effect in NEURAL:
                    if OV is None:
                        return self.text(503, 'OpenVoice 不可用')
                    wav = OV.render(pcm, effect)
                else:
                    wav = build_wav(shift(pcm, effect))
            except (subprocess.SubprocessError, OSError, RuntimeError, ValueError):
                return self.text(500, '转换失败')
            return self.reply(200, wav, 'audio/wav')
        finally:
            ADMIT.release()


def main():
    global PORT, OV
    ap = argparse.ArgumentParser(description='本地语音原型服务器')
    ap.add_argument('--port', type=int, default=8765)
    ap.add_argument('--library', default=None)
    ap.add_argument('--assets', default=None)
    args = ap.parse_args()
    PORT = args.port
    if not (1 <= PORT <= 65535):
        ap.error('端口必须在 1 到 65535 之间')
    if bool(args.library) != bool(args.assets):
        ap.error('--library 与 --assets 必须同时提供')
    if args.library:
        if not os.path.isfile(args.library):
            ap.error('动态库文件不存在')
        if not os.path.isdir(args.assets):
            ap.error('资源目录不存在')
        need = ('ref_encoder.onnx', 'converter.onnx', 'voiceA.f32', 'voiceB.f32', 'voiceC.f32')
        if any(not os.path.isfile(os.path.join(args.assets, n)) for n in need):
            ap.error('资源文件不完整')
        try:
            OV = OpenVoice(args.library, args.assets)
        except Exception:
            ap.error('动态库加载失败')
    srv = ThreadingHTTPServer(('127.0.0.1', PORT), Handler)
    host, bound = srv.server_address[:2]
    print('bound http://' + host + ':' + str(bound), flush=True)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        srv.server_close()


if __name__ == '__main__':
    main()
