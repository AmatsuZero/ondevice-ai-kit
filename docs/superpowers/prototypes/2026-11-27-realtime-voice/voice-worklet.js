// voice-worklet.js — 独立 AudioWorklet 处理器，无外部依赖。
(function () {
  'use strict';

  var VALID = { original: 1, uncle: 1, girl: 1, announcer: 1 };

  function wrap(x, m) {
    var r = x % m;
    return r < 0 ? r + m : r;
  }
  function clamp(v, lo, hi) {
    return v < lo ? lo : (v > hi ? hi : v);
  }

  class RoleVoiceProcessor extends AudioWorkletProcessor {
    static get parameterDescriptors() { return []; }

    constructor(options) {
      super();
      var opts = (options && options.processorOptions) || {};
      var sr = (typeof sampleRate === 'number' && isFinite(sampleRate) && sampleRate > 0)
        ? sampleRate : 44100;
      this.sr = sr;

      // 连续双读头的 sawtooth 跨度（约 32ms，取偶数）；4 倍缓冲为读头留出余量。
      var span = Math.round(sr * 0.032);
      if (span < 16) span = 16;
      if (span & 1) span += 1;
      this.span = span;
      this.half = span >> 1;
      this.invSpan = 1 / span;
      this.B = span * 4;
      this.buf = new Float32Array(this.B);
      this.win = new Float32Array(span);
      for (var i = 0; i < span; i++) {
        this.win[i] = 0.5 - 0.5 * Math.cos((2 * Math.PI * i) / span);
      }

      // 一阶极点的增量形式必须是 1 - exp(...)。原来直接用 exp(...) 作
      // z += a*(x - z) 会让滤波器几乎瞬时跟随，高通后信号被抵消，
      // announcer 因此近乎无声。
      this.aLow = 1 - Math.exp((-2 * Math.PI * Math.min(380, 0.4 * sr)) / sr);
      this.aWarmHi = 1 - Math.exp((-2 * Math.PI * Math.min(3200, 0.45 * sr)) / sr);
      this.aGirlHi = 1 - Math.exp((-2 * Math.PI * Math.min(2400, 0.45 * sr)) / sr);
      this.aAir = 1 - Math.exp((-2 * Math.PI * Math.min(6000, 0.45 * sr)) / sr);
      this.aRumble = 1 - Math.exp((-2 * Math.PI * Math.min(110, 0.4 * sr)) / sr);
      this.aPres = 1 - Math.exp((-2 * Math.PI * Math.min(2800, 0.45 * sr)) / sr);
      this.atk = 1 - Math.exp(-1 / (0.006 * sr));
      this.rel = 1 - Math.exp(-1 / (0.16 * sr));
      this.aGain = 1 - Math.exp(-1 / (0.03 * sr));

      this.setRole(opts.role);

      if (this.port) {
        this.port.onmessage = (function (self) {
          return function (e) {
            var d = e && e.data;
            if (d && typeof d.role === 'string') self.setRole(d.role);
          };
        })(this);
      }
    }

    setRole(role) {
      if (!VALID[role]) role = 'original';
      this.role = role;
      var semi = (role === 'uncle') ? -4 : (role === 'girl') ? 4 : 0;
      this.ratio = Math.pow(2, semi / 12);
      this.shift = (semi !== 0);
      this.resetState();
    }

    resetState() {
      this.writeIdx = 0;
      if (this.buf) this.buf.fill(0);
      this.phIdx = 0;

      // sawtooth 延迟：每跨度变化 D = (1 - ratio) * span；base 保证读头
      // 始终落后于写头，复位点恰好落在 Hann 窗权为 0 处。
      var D = (1 - (this.ratio || 1)) * this.span;
      this.D = D;
      this.base = this.span + (D > 0 ? 0 : -D);

      this.env = 0;
      this.gain = 1;
      this.zLow = this.zWarmHi = this.zGirlHi = 0;
      this.zAir = this.zRumble = this.zPres = 0;
    }

    // 环形缓冲区浮点位置线性插值读取。
    tap(p) {
      var x = wrap(p, this.B);
      var i0 = x | 0;
      var i1 = i0 + 1; if (i1 >= this.B) i1 = 0;
      var f = x - i0;
      var b = this.buf;
      return b[i0] + f * (b[i1] - b[i0]);
    }

    process(inputs, outputs) {
      var out = outputs && outputs[0];
      if (!out || !out[0]) return true;
      var o = out[0], n = o.length;
      var inp = inputs && inputs[0] && inputs[0][0] ? inputs[0][0] : null;

      // original：精确直通，不滤波、不加权、不分配。
      if (this.role === 'original') {
        for (var k0 = 0; k0 < n; k0++) o[k0] = inp ? inp[k0] : 0;
        return true;
      }

      if (this.role === 'announcer') {
        var zRumble = this.zRumble, env = this.env, gain = this.gain, zPres = this.zPres;
        var aRumble = this.aRumble, atk = this.atk, rel = this.rel;
        var aGain = this.aGain, aPres = this.aPres;
        for (var k1 = 0; k1 < n; k1++) {
          var x1 = inp ? inp[k1] : 0;
          if (x1 !== x1 || x1 < -1e12 || x1 > 1e12) x1 = 0;
          zRumble += aRumble * (x1 - zRumble);
          var v1 = x1 - zRumble;
          var av1 = v1 < 0 ? -v1 : v1;
          env += (av1 > env ? atk : rel) * (av1 - env);
          var target = 0.22 / (env > 0.02 ? env : 0.02);
          if (target > 2.4) target = 2.4; else if (target < 0.45) target = 0.45;
          gain += aGain * (target - gain);
          var y1 = v1 * gain;
          zPres += aPres * (y1 - zPres);
          y1 += 0.20 * (y1 - zPres);
          o[k1] = clamp(y1, -0.99, 0.99);
        }
        this.zRumble = zRumble; this.env = env; this.gain = gain; this.zPres = zPres;
        return true;
      }

      // uncle / girl：连续双读头 + 互补 Hann 交叉淡化。读头速率恒为 ratio，
      // sawtooth 延迟以 (1 - ratio) 每跨度线性变化，不再按颗粒重启，
      var ratio = this.ratio, span = this.span, half = this.half, B = this.B;
      var D = this.D, base = this.base, invSpan = this.invSpan;
      var buf = this.buf, win = this.win;
      var w = this.writeIdx, ph = this.phIdx;
      var girl = (this.role === 'girl');
      var zLow = this.zLow, zHi = girl ? this.zGirlHi : this.zWarmHi, zAir = this.zAir;
      var aLow = this.aLow, aWarmHi = this.aWarmHi;
      var aGirlHi = this.aGirlHi, aAir = this.aAir;

      for (var k2 = 0; k2 < n; k2++) {
        var x2 = inp ? inp[k2] : 0;
        if (x2 !== x2 || x2 < -1e12 || x2 > 1e12) x2 = 0;
        buf[w] = x2;
        w++; if (w >= B) w = 0;

        var fa = ph * invSpan;
        var rpa = w - (base + D * fa);
        var sa = this.tap(rpa) * win[ph];

        var pb = ph + half; if (pb >= span) pb -= span;
        var fb = pb * invSpan;
        var rpb = w - (base + D * fb);
        var sb = this.tap(rpb) * win[pb];

        var y2 = sa + sb;

        if (girl) {
          zHi += aGirlHi * (y2 - zHi);
          zAir += aAir * (y2 - zAir);
          y2 += 0.30 * (y2 - zHi) + 0.14 * (y2 - zAir);
        } else {
          zLow += aLow * (y2 - zLow);
          zHi += aWarmHi * (y2 - zHi);
          y2 = 0.80 * y2 + 0.28 * zLow + 0.12 * zHi;
        }
        o[k2] = clamp(y2, -1, 1);

        ph++; if (ph >= span) ph = 0;
      }

      this.writeIdx = w; this.phIdx = ph;
      this.zLow = zLow; this.zAir = zAir;
      if (girl) this.zGirlHi = zHi; else this.zWarmHi = zHi;
      return true;
    }
  }

  registerProcessor('role-voice', RoleVoiceProcessor);
})();
