(function () {
  'use strict';

  var ROLES = ['original', 'uncle', 'girl', 'announcer'];
  var ROLE_NAMES = {
    original: '原声',
    uncle: '大叔',
    girl: '少女',
    announcer: '播音员'
  };
  var INITIAL_ROLE = 'uncle';
  var INITIAL_DURATION = 9.72;
  var WAVE_POINTS = 96;
  var WAVE_URL = 'waveform.json';
  var SAMPLE_URL = 'original.wav';
  var WORKLET_URL = 'voice-worklet.js';
  var MIC_CONSTRAINTS = {
    audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: false },
    video: false
  };
  var LIVE_GAIN = 0.12;
  var HP_GAIN = 0.8;
  var SAMPLE_GAIN = 0.85;
  var RAMP_UP = 0.08;
  var ROUTE_POLL_MS = 400;

  var els = {};
  var talkText = '';
  var listenToggleText = '';
  var role = INITIAL_ROLE;
  var waveCache = null;

  var ctx = null;
  var workletReady = null;

  var decoded = null;
  var sampleSource = null;
  var sampleNode = null;
  var sampleGain = null;
  var sampleLimiter = null;
  var sampleStartedAt = 0;
  var sampleOffset = 0;
  var samplePlaying = false;
  var sampleLoadGen = 0;

  var liveGen = 0;
  var live = null;
  var pressing = false;
  var permissionPending = false;
  var holdMode = '';
  var captureId = -1;

  var continuousMode = false;
  var headphoneMode = false;
  var captureKind = 'unknown';
  var captureToken = '';
  var captureLabel = '';
  var routePollTimer = 0;
  var routePollBusy = false;

  var rafId = 0;
  var fbBuf = null;
  var rawBuf = null;
  var fbHot = 0;

  function $(id) { return document.getElementById(id); }
  function setStatus(s) { if (els.status) els.status.textContent = s; }
  function msg(err) {
    if (err == null) return '未知错误';
    if (typeof err === 'string') return err;
    return err.message || err.name || String(err);
  }
  function resetTalk() { if (els.talk) els.talk.textContent = talkText; }
  function setTalkHold() { if (els.talk) els.talk.textContent = '松开结束'; }

  function setRolesDisabled(disabled) {
    var btns = document.querySelectorAll('[data-role]');
    for (var i = 0; i < btns.length; i++) btns[i].disabled = !!disabled;
  }

  function monitorHelper() { return window.voiceMonitor; }

  function invalidatePending() {
    var h = monitorHelper();
    if (h && typeof h.invalidate === 'function') {
      try { h.invalidate(); } catch (_) {}
    }
  }

  function freshRoute() {
    var h = monitorHelper();
    if (!h || typeof h.route !== 'function') {
      return Promise.resolve({ kind: 'unknown', token: '' });
    }
    try {
      var p = h.route();
      return p && typeof p.then === 'function'
        ? p
        : Promise.resolve(p || { kind: 'unknown', token: '' });
    } catch (_) {
      return Promise.resolve({ kind: 'unknown', token: '' });
    }
  }

  function showRoute(route) {
    var h = monitorHelper();
    if (!h || typeof h.showRoute !== 'function' || !route || !route.token) return;
    try { h.showRoute(route); } catch (_) {}
  }

  function constraintsFor(hp) {
    var h = monitorHelper();
    if (h && typeof h.constraints === 'function') {
      try { return h.constraints(hp); } catch (_) {}
    }
    if (hp) {
      return { audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false }, video: false };
    }
    return MIC_CONSTRAINTS;
  }

  function defaultSink() {
    try { return !ctx || ctx.sinkId === undefined || ctx.sinkId === ''; } catch (_) { return false; }
  }

  function verifiedHeadphones(route) {
    return !!route && defaultSink() && route.kind === 'headphones' &&
      typeof route.token === 'string' && route.token.length > 0;
  }

  function routeStillMatches(route) {
    if (!route) return false;
    if (!captureToken) {
      if (headphoneMode || continuousMode) return false;
      if (captureKind !== 'unknown' || route.kind !== 'unknown' || route.token) return false;
      return true;
    }
    if (route.token !== captureToken) return false;
    if (route.kind !== captureKind) return false;
    if (headphoneMode && !verifiedHeadphones(route)) return false;
    return true;
  }

  function reportLevel(rms, label) {
    var h = monitorHelper();
    if (!h || typeof h.level !== 'function') return;
    var v = Number(rms);
    if (!isFinite(v) || v < 0) v = 0;
    if (v > 1) v = 1;
    try { h.level(v, label || ''); } catch (_) {}
  }

  function resetMeter() { reportLevel(0, ''); }

  function setToggleActive(on) {
    var b = els.listenToggle;
    if (!b) return;
    b.textContent = on ? '停止连续监听' : (listenToggleText || '连续监听');
    b.setAttribute('aria-pressed', on ? 'true' : 'false');
    b.setAttribute('aria-label', on ? '点击停止连续监听' : '点击开始连续监听（需已验证耳机）');
  }

  function resetToggle() { setToggleActive(false); }

  function stopRoutePolling() {
    if (routePollTimer) { clearInterval(routePollTimer); routePollTimer = 0; }
    routePollBusy = false;
  }

  function startRoutePolling(gen) {
    stopRoutePolling();
    routePollTimer = setInterval(function () {
      if (routePollBusy || !live || gen !== liveGen || !live.hp) return;
      routePollBusy = true;
      freshRoute().then(function (route) {
        routePollBusy = false;
        if (!live || gen !== liveGen || !live.hp) return;
        if (route && route.token && route.token === live.token) return;
        showRoute(route);
        endLive();
        setStatus('音频路由已变化，连续监听已停止');
      }).catch(function () {
        routePollBusy = false;
        if (!live || gen !== liveGen) return;
        endLive();
        setStatus('无法确认音频路由，连续监听已停止');
      });
    }, ROUTE_POLL_MS);
  }

  function describeMicError(err) {
    var n = err && (err.name || err.code);
    if (n === 'NotAllowedError' || n === 'PermissionDeniedError' || n === 'SecurityError') {
      return '麦克风权限被拒绝，请在浏览器中允许麦克风后重试';
    }
    if (n === 'NotFoundError' || n === 'DevicesNotFoundError' || n === 'OverconstrainedError') {
      return '未找到可用的麦克风或指定的输入设备';
    }
    if (n === 'NotReadableError' || n === 'TrackStartError') {
      return '麦克风被其他应用占用，无法启动';
    }
    return '麦克风访问失败：' + msg(err);
  }

  function createVoiceNode(context, r, gen) {
    var node = new AudioWorkletNode(context, 'role-voice', {
      numberOfInputs: 1,
      numberOfOutputs: 1,
      outputChannelCount: [1],
      processorOptions: { role: r }
    });
    node.onprocessorerror = function () { onProcessorError(gen); };
    return node;
  }
  function onProcessorError(gen) {
    if (live && gen === liveGen) {
      pressing = false;
      holdMode = '';
      resetTalk();
      setRolesDisabled(false);
      stopLive();
      setStatus('音频处理出错，监听已停止，请重试');
      return;
    }
    if (samplePlaying && gen === sampleLoadGen) {
      ++sampleLoadGen;
      stopSample();
      setStatus('音频处理出错，示例播放已停止，请重试');
    }
  }
  function postRole(node, r) { try { node.port.postMessage({ role: r }); } catch (_) {} }
  function makeLimiter(context) {
    var c = context.createDynamicsCompressor();
    c.threshold.value = -3;
    c.knee.value = 0;
    c.ratio.value = 20;
    c.attack.value = 0.002;
    c.release.value = 0.1;
    return c;
  }
  function stopTracks(stream) {
    if (!stream) return;
    var tracks = [];
    try { tracks = stream.getTracks ? stream.getTracks() : []; } catch (_) {}
    for (var i = 0; i < tracks.length; i++) { try { tracks[i].stop(); } catch (_) {} }
  }

  function ensureContext() {
    if (ctx) return ctx.resume().then(function () { return ctx; });
    var AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return Promise.reject(new Error('浏览器不支持 Web Audio'));
    ctx = new AC();
    return ctx.resume().then(function () { return ctx; }).catch(function (err) {
      ctx = null;
      throw err;
    });
  }
  function ensureWorklet() {
    if (workletReady) return workletReady;
    if (!ctx || !ctx.audioWorklet || typeof AudioWorkletNode === 'undefined') {
      return Promise.reject(new Error('浏览器不支持 AudioWorklet'));
    }
    workletReady = ctx.audioWorklet.addModule(WORKLET_URL).catch(function (err) {
      workletReady = null;
      throw err;
    });
    return workletReady;
  }

  function applyRole(next) {
    if (pressing && !continuousMode) return;
    if (ROLES.indexOf(next) === -1) return;
    role = next;
    if (els.roleName) els.roleName.textContent = ROLE_NAMES[role] || role;
    var btns = document.querySelectorAll('[data-role]');
    for (var i = 0; i < btns.length; i++) {
      var on = btns[i].getAttribute('data-role') === role;
      if (on) btns[i].classList.add('active'); else btns[i].classList.remove('active');
      btns[i].setAttribute('aria-pressed', on ? 'true' : 'false');
    }
    if (sampleNode) postRole(sampleNode, role);
    if (live && live.node) postRole(live.node, role);
    if (samplePlaying) setStatus('示例播放中（' + (ROLE_NAMES[role] || role) + '）');
    updateRouteNote();
  }
  function updateRouteNote() {
    if (!els.routeNote) return;
    els.routeNote.textContent = continuousMode
      ? '声音跟随系统输出；连续监听需手动停止。'
      : '声音跟随系统输出；松开即停。';
  }

  function normalizeWave(arr) {
    var out = new Array(WAVE_POINTS);
    for (var i = 0; i < WAVE_POINTS; i++) {
      var v = arr[Math.min(arr.length - 1, Math.floor(i / WAVE_POINTS * arr.length))];
      v = Number(v);
      if (!isFinite(v) || v < 0) v = 0;
      if (v > 1) v = 1;
      out[i] = v;
    }
    return out;
  }
  function loadWaveformJson() {
    if (typeof fetch !== 'function') return;
    fetch(WAVE_URL).then(function (r) {
      if (!r.ok) throw new Error('waveform.json HTTP ' + r.status);
      return r.json();
    }).then(function (arr) {
      if (!Array.isArray(arr) || !arr.length || decoded) return;
      waveCache = normalizeWave(arr);
      drawWave();
    }).catch(function () {});
  }
  function cacheWaveFromBuffer(buf) {
    var data = buf.getChannelData(0);
    var len = data.length;
    var out = new Array(WAVE_POINTS);
    var per = len / WAVE_POINTS;
    for (var i = 0; i < WAVE_POINTS; i++) {
      var s = Math.floor(i * per);
      var e = Math.floor((i + 1) * per);
      if (e <= s) e = s + 1;
      if (s < 0) s = 0;
      if (s > len) s = len;
      if (e > len) e = len;
      var mx = 0;
      var step = Math.max(1, Math.floor((e - s) / 64));
      for (var j = s; j < e; j += step) {
        var v = Math.abs(data[j]);
        if (v > mx) mx = v;
      }
      out[i] = mx > 1 ? 1 : mx;
    }
    return out;
  }

  function fmt(t) {
    if (!isFinite(t) || t < 0) t = 0;
    return t.toFixed(2);
  }
  function updateTime(pos, dur) {
    if (!els.time) return;
    els.time.textContent = fmt(pos) + ' / ' + fmt(dur) + ' s';
  }

  function drawWave() {
    var canvas = els.wave;
    if (!canvas) return;
    var rect = canvas.getBoundingClientRect();
    var cssW = Math.max(1, Math.floor(rect.width || 1));
    var cssH = Math.max(1, Math.floor(rect.height || 1));
    var dpr = Math.min(2, window.devicePixelRatio || 1);
    var pw = Math.max(1, Math.floor(cssW * dpr));
    var ph = Math.max(1, Math.floor(cssH * dpr));
    if (canvas.width !== pw || canvas.height !== ph) { canvas.width = pw; canvas.height = ph; }
    var g = canvas.getContext('2d');
    if (!g) return;
    g.clearRect(0, 0, pw, ph);
    var mid = ph / 2;
    g.strokeStyle = 'rgba(255,255,255,0.15)';
    g.lineWidth = 1;
    g.beginPath(); g.moveTo(0, Math.floor(mid) + 0.5); g.lineTo(pw, Math.floor(mid) + 0.5); g.stroke();

    var pts = waveCache;
    if (pts && pts.length) {
      g.strokeStyle = '#7fd1ff';
      g.lineWidth = Math.max(1, Math.floor(dpr));
      g.beginPath();
      var n = pts.length;
      for (var x = 0; x < pw; x++) {
        var idx = Math.floor(x / pw * n);
        if (idx >= n) idx = n - 1;
        var a = pts[idx];
        if (!isFinite(a) || a < 0) a = 0;
        if (a > 1) a = 1;
        var h = a * mid * 0.9;
        g.moveTo(x + 0.5, mid - h);
        g.lineTo(x + 0.5, mid + h);
      }
      g.stroke();
    } else {
      g.fillStyle = 'rgba(255,255,255,0.5)';
      g.font = (12 * dpr) + 'px sans-serif';
      g.fillText('波形加载中…', 8 * dpr, mid - 8 * dpr);
    }

    if (samplePlaying) {
      var pos = samplePosition();
      var dur = decoded && decoded.duration > 0 ? decoded.duration : INITIAL_DURATION;
      var frac = dur > 0 ? Math.max(0, Math.min(1, pos / dur)) : 0;
      var px = frac * pw;
      g.strokeStyle = '#ffcf5c';
      g.lineWidth = Math.max(1, Math.floor(dpr));
      g.beginPath(); g.moveTo(px, 0); g.lineTo(px, ph); g.stroke();
    }
  }

  function samplePosition() {
    if (!samplePlaying || !decoded || !ctx) return 0;
    var d = decoded.duration;
    if (!(d > 0)) return 0;
    var p = ctx.currentTime - sampleStartedAt + sampleOffset;
    if (!isFinite(p) || p < 0) return 0;
    if (p > d) p = d;
    return p;
  }

  function stopSample() {
    samplePlaying = false;
    var s = sampleSource; sampleSource = null;
    if (s) { try { s.stop(0); } catch (_) {} try { s.disconnect(); } catch (_) {} }
    if (sampleNode) { try { sampleNode.port.close(); } catch (_) {} try { sampleNode.disconnect(); } catch (_) {} sampleNode = null; }
    if (sampleGain) { try { sampleGain.disconnect(); } catch (_) {} sampleGain = null; }
    if (sampleLimiter) { try { sampleLimiter.disconnect(); } catch (_) {} sampleLimiter = null; }
    if (els.playLabel) els.playLabel.textContent = '播放示例';
    updateTime(0, decoded ? decoded.duration : INITIAL_DURATION);
    drawWave();
  }

  function startSample() {
    if (!ctx || !decoded || !workletReady) return;
    stopSample();
    try {
      var src = ctx.createBufferSource();
      src.buffer = decoded;
      var node = createVoiceNode(ctx, role, sampleLoadGen);
      var gain = ctx.createGain();
      gain.gain.value = SAMPLE_GAIN;
      var limiter = makeLimiter(ctx);
      src.connect(node);
      node.connect(gain);
      gain.connect(limiter);
      limiter.connect(ctx.destination);
      sampleSource = src;
      sampleNode = node;
      sampleGain = gain;
      sampleLimiter = limiter;
      sampleStartedAt = ctx.currentTime;
      sampleOffset = 0;
      samplePlaying = true;
      src.start(0, 0);
      if (els.playLabel) els.playLabel.textContent = '停止';
      setStatus('示例播放中（' + (ROLE_NAMES[role] || role) + '）');
      ensureLoop();
    } catch (err) {
      stopSample();
      setStatus('示例播放失败：' + msg(err));
    }
  }

  function onPlay() {
    if (samplePlaying) { stopSample(); setStatus('示例已停止'); return; }

    if (pressing || live) {
      ++liveGen;
      pressing = false;
      permissionPending = false;
      resetTalk();
      setRolesDisabled(false);
      stopLive();
    }

    if (decoded) { startSample(); return; }

    var gen = ++sampleLoadGen;
    var STALE = { stale: true };
    function guard() { if (gen !== sampleLoadGen) throw STALE; }

    setStatus('正在加载示例…');
    ensureContext()
      .then(function () { guard(); return ensureWorklet(); })
      .then(function () { guard(); return fetch(SAMPLE_URL); })
      .then(function (r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        guard();
        return r.arrayBuffer();
      })
      .then(function (ab) { guard(); return ctx.decodeAudioData(ab); })
      .then(function (buf) {
        guard();
        if (!buf || !(buf.length > 0)) throw new Error('示例音频为空');
        decoded = buf;
        waveCache = cacheWaveFromBuffer(buf);
        updateTime(0, decoded.duration);
        drawWave();
        startSample();
      })
      .catch(function (err) {
        if (err === STALE || gen !== sampleLoadGen) return;
        setStatus('示例加载失败：' + msg(err));
      });
  }

  function failSession(gen, text) {
    if (gen !== liveGen) return;
    pressing = false;
    permissionPending = false;
    holdMode = '';
    continuousMode = false;
    updateRouteNote();
    headphoneMode = false;
    captureKind = 'unknown';
    captureToken = '';
    captureLabel = '';
    resetTalk();
    setRolesDisabled(false);
    resetToggle();
    resetMeter();
    stopRoutePolling();
    setStatus(text);
  }

  function connectStream(stream, gen) {
    var tracks = [];
    try { tracks = stream.getAudioTracks(); } catch (_) {}
    var track = tracks && tracks[0];
    if (!track) {
      stopTracks(stream);
      failSession(gen, '未获得音频轨道');
      return null;
    }
    var settings = {};
    try { settings = track.getSettings ? track.getSettings() : {}; } catch (_) {}
    if (!headphoneMode && settings.echoCancellation !== true) {
      stopTracks(stream);
      failSession(gen, '浏览器未开启回声消除，已关闭输出');
      return null;
    }
    if (gen !== liveGen || !pressing) { stopTracks(stream); return null; }
    try {
      var src = ctx.createMediaStreamSource(stream);
      var node = createVoiceNode(ctx, role, gen);
      var raw = ctx.createAnalyser();
      raw.fftSize = 1024;
      var analyser = ctx.createAnalyser();
      analyser.fftSize = 1024;
      var gain = ctx.createGain();
      gain.gain.value = 0;
      var limiter = makeLimiter(ctx);
      src.connect(raw);
      raw.connect(node);
      node.connect(analyser);
      analyser.connect(gain);
      gain.connect(limiter);
      limiter.connect(ctx.destination);
      var now = ctx.currentTime;
      var target = headphoneMode ? HP_GAIN : LIVE_GAIN;
      gain.gain.setValueAtTime(0, now);
      gain.gain.linearRampToValueAtTime(target, now + RAMP_UP);
      captureLabel = track.label || '当前麦克风';
      live = {
        stream: stream, src: src, node: node, raw: raw, analyser: analyser,
        gain: gain, limiter: limiter, hp: headphoneMode,
        token: captureToken, label: captureLabel, track: track
      };
      if (!rawBuf) rawBuf = new Float32Array(raw.fftSize);
      if (!fbBuf) fbBuf = new Float32Array(analyser.fftSize);
      fbHot = 0;
      if (track.addEventListener) {
        track.addEventListener('ended', function () {
          if (gen === liveGen && live) {
            endLive();
            setStatus('麦克风已断开，监听已停止');
          }
        });
      }
      if (headphoneMode) startRoutePolling(gen);
      if (continuousMode) {
        setToggleActive(true);
        setStatus('连续监听中（' + (ROLE_NAMES[role] || role) + '）');
      } else {
        setStatus('讲话中（' + (ROLE_NAMES[role] || role) + '）— 松开结束');
      }
      ensureLoop();
      return live;
    } catch (err) {
      stopTracks(stream);
      failSession(gen, '麦克风连接失败：' + msg(err));
      return null;
    }
  }

  function stopLive() {
    ++liveGen;
    var l = live;
    live = null;
    continuousMode = false;
    updateRouteNote();
    headphoneMode = false;
    captureKind = 'unknown';
    captureToken = '';
    captureLabel = '';
    permissionPending = false;
    stopRoutePolling();
    resetMeter();
    resetToggle();
    if (!l) return;
    try {
      if (ctx) l.gain.gain.cancelScheduledValues(ctx.currentTime);
      l.gain.gain.value = 0;
    } catch (_) {}
    stopTracks(l.stream);
    try { l.src.disconnect(); } catch (_) {}
    try { l.node.port.close(); } catch (_) {}
    try { l.node.disconnect(); } catch (_) {}
    try { l.raw.disconnect(); } catch (_) {}
    try { l.analyser.disconnect(); } catch (_) {}
    try { l.gain.disconnect(); } catch (_) {}
    try { l.limiter.disconnect(); } catch (_) {}
  }

  function beginLive(mode) {
    if (pressing || live) return;
    ++sampleLoadGen;
    if (samplePlaying) stopSample();
    invalidatePending();
    pressing = true;
    permissionPending = false;
    holdMode = mode || 'pointer';
    captureId = -1;
    continuousMode = holdMode === 'continuous';
    headphoneMode = false;
    captureKind = 'unknown';
    captureToken = '';
    captureLabel = '';
    if (continuousMode) {
      setToggleActive(true);
      setRolesDisabled(false);
    } else {
      setTalkHold();
      setRolesDisabled(true);
    }
    updateRouteNote();
    var gen = ++liveGen;

    function cancelled() { return gen !== liveGen || !pressing; }
    function fail(text) { failSession(gen, text); }
    function staleStream(stream) {
      if (gen === liveGen && pressing) return false;
      stopTracks(stream);
      return true;
    }

    setStatus(continuousMode ? '正在确认耳机路由…' : '正在准备音频…');
    var ctxPromise;
    try { ctxPromise = ensureContext(); }
    catch (err) { fail('音频初始化失败：' + msg(err)); return; }

    var routePromise = freshRoute();

    ctxPromise
      .then(function () {
        if (cancelled()) return null;
        return routePromise.then(function (route) {
          if (cancelled()) return null;
          showRoute(route);
          captureKind = route && route.kind ? route.kind : 'unknown';
          captureToken = route && typeof route.token === 'string' ? route.token : '';
          headphoneMode = verifiedHeadphones(route);
          if (continuousMode && !headphoneMode) {
            fail('连续监听仅支持已验证的耳机输出：请先连接耳机，确认系统输出与页面路由均显示为耳机后再开启。');
            return null;
          }
          return ensureWorklet();
        });
      })
      .then(function () {
        if (cancelled()) return null;
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
          throw new Error('浏览器不支持麦克风采集');
        }
        permissionPending = true;
        setStatus('正在请求麦克风…');
        return navigator.mediaDevices.getUserMedia(constraintsFor(headphoneMode));
      })
      .then(function (stream) {
        if (staleStream(stream)) return null;
        permissionPending = false;
        return freshRoute().then(function (route2) {
          if (staleStream(stream)) return null;
          showRoute(route2);
          if (!routeStillMatches(route2)) {
            stopTracks(stream);
            fail('音频路由已变化，已关闭输出');
            return null;
          }
          return connectStream(stream, gen);
        });
      })
      .catch(function (err) {
        if (gen !== liveGen) return;
        permissionPending = false;
        fail(describeMicError(err));
      });
  }

  function endLive() {
    if (!pressing && !live) return;
    var wasPending = permissionPending;
    var wasContinuous = continuousMode;
    pressing = false;
    permissionPending = false;
    holdMode = '';
    continuousMode = false;
    if (captureId >= 0 && els.talk && els.talk.releasePointerCapture) {
      try { els.talk.releasePointerCapture(captureId); } catch (_) {}
      captureId = -1;
    }
    resetTalk();
    setRolesDisabled(false);
    stopLive();
    if (wasContinuous) setStatus('连续监听已停止');
    else setStatus(wasPending ? '请允许麦克风后重新按住讲话' : '麦克风已关闭');
  }

  function monitorFeedback() {
    if (!live || !pressing) return;
    var rawRms = 0;
    var r = live.raw;
    try {
      if (r.getFloatTimeDomainData && rawBuf) {
        r.getFloatTimeDomainData(rawBuf);
        var rs = 0;
        for (var m = 0; m < rawBuf.length; m++) { var rv = rawBuf[m]; rs += rv * rv; }
        rawRms = Math.sqrt(rs / rawBuf.length);
      } else if (r.getByteTimeDomainData) {
        var rb = new Uint8Array(r.fftSize);
        r.getByteTimeDomainData(rb);
        var rs2 = 0;
        for (var n = 0; n < rb.length; n++) { var xv = (rb[n] - 128) / 128; rs2 += xv * xv; }
        rawRms = Math.sqrt(rs2 / rb.length);
      }
    } catch (_) {}
    reportLevel(rawRms, live.label);

    var a = live.analyser;
    var rms = 0;
    try {
      if (a.getFloatTimeDomainData && fbBuf) {
        a.getFloatTimeDomainData(fbBuf);
        var sum = 0;
        for (var i = 0; i < fbBuf.length; i++) { var v = fbBuf[i]; sum += v * v; }
        rms = Math.sqrt(sum / fbBuf.length);
      } else if (a.getByteTimeDomainData) {
        var b = new Uint8Array(a.fftSize);
        a.getByteTimeDomainData(b);
        var s2 = 0;
        for (var j = 0; j < b.length; j++) { var x = (b[j] - 128) / 128; s2 += x * x; }
        rms = Math.sqrt(s2 / b.length);
      }
    } catch (_) { return; }
    if (rms > 0.98) fbHot++; else fbHot = Math.max(0, fbHot - 1);
    if (fbHot > 30) {
      fbHot = 0;
      pressing = false;
      permissionPending = false;
      resetTalk();
      setRolesDisabled(false);
      setStatus('检测到持续高电平，已关闭输出');
      stopLive();
    }
  }

  function ensureLoop() { if (!rafId) rafId = requestAnimationFrame(tick); }
  function tick() {
    rafId = 0;
    if (samplePlaying) {
      var pos = samplePosition();
      updateTime(pos, decoded ? decoded.duration : INITIAL_DURATION);
      drawWave();
      if (decoded && decoded.duration > 0 && pos >= decoded.duration - 0.005) {
        stopSample();
        setStatus('示例播放结束');
      }
    }
    if (pressing && live) monitorFeedback();
    if (samplePlaying || live) rafId = requestAnimationFrame(tick);
  }

  function hardStopAll() {
    ++sampleLoadGen;
    pressing = false;
    permissionPending = false;
    holdMode = '';
    continuousMode = false;
    captureId = -1;
    resetTalk();
    setRolesDisabled(false);
    invalidatePending();
    stopLive();
    stopSample();
    setStatus('已停止全部音频');
  }

  function isTyping(t) {
    return !!(t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable));
  }
  function isVisible(el) {
    if (!el) return false;
    var rects = el.getClientRects();
    if (!rects || !rects.length) return false;
    var r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  }
  function talkFocused() {
    return document.activeElement === els.talk && isVisible(els.talk) && !document.hidden;
  }

  function init() {
    els.play = $('play');
    els.playLabel = $('playLabel');
    els.time = $('time');
    els.wave = $('wave');
    els.talk = $('talk');
    els.status = $('status');
    els.roleName = $('roleName');
    els.routeNote = $('routeNote');
    els.listenToggle = $('listenToggle');
    els.micSource = $('micSource');
    if (els.talk) talkText = els.talk.textContent;
    if (els.listenToggle) listenToggleText = els.listenToggle.textContent || '连续监听';

    if (els.play) els.play.addEventListener('click', onPlay);

    if (els.talk) {
      els.talk.addEventListener('pointerdown', function (e) {
        if (e.pointerType === 'mouse' && e.button !== 0) return;
        if (pressing || live) return;
        beginLive('pointer');
        if (pressing && els.talk.setPointerCapture) {
          try { els.talk.setPointerCapture(e.pointerId); captureId = e.pointerId; } catch (_) {}
        }
      });
      els.talk.addEventListener('lostpointercapture', function () {
        if (pressing && holdMode === 'pointer') endLive();
      });
      els.talk.addEventListener('focusout', function () {
        if (holdMode === 'key' && pressing) endLive();
      });
    }

    if (els.listenToggle) {
      els.listenToggle.addEventListener('click', function (e) {
        if (e.button && e.button !== 0) return;
        if (pressing || live) { endLive(); return; }
        beginLive('continuous');
      });
    }

    window.addEventListener('pointerup', function (e) {
      if (!pressing || holdMode === 'continuous') return;
      if (holdMode === 'pointer' && captureId >= 0 && e.pointerId !== captureId) return;
      endLive();
    });
    window.addEventListener('pointercancel', function (e) {
      if (!pressing || holdMode === 'continuous') return;
      if (holdMode === 'pointer' && captureId >= 0 && e.pointerId !== captureId) return;
      endLive();
    });

    window.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' || e.code === 'Escape') {
        if (pressing || live) { e.preventDefault(); endLive(); }
        return;
      }
      if (e.code !== 'Space' && e.key !== ' ') return;
      if (e.repeat || isTyping(e.target) || !talkFocused()) return;
      e.preventDefault();
      beginLive('key');
    });
    window.addEventListener('keyup', function (e) {
      if (e.code !== 'Space' && e.key !== ' ') return;
      if (e.repeat || isTyping(e.target)) return;
      if (holdMode === 'continuous') return;
      if (holdMode === 'key' && pressing) {
        e.preventDefault();
        endLive();
        return;
      }
      if (!talkFocused()) return;
      e.preventDefault();
      endLive();
    });

    var roleBtns = document.querySelectorAll('[data-role]');
    for (var i = 0; i < roleBtns.length; i++) {
      (function (b) {
        b.addEventListener('click', function () { applyRole(b.getAttribute('data-role')); });
      })(roleBtns[i]);
    }

    window.addEventListener('blur', function () { hardStopAll(); });
    document.addEventListener('visibilitychange', function () { if (document.hidden) hardStopAll(); });
    if (navigator.mediaDevices && navigator.mediaDevices.addEventListener) {
      navigator.mediaDevices.addEventListener('devicechange', hardStopAll);
    }
    window.addEventListener('pagehide', hardStopAll);
    window.addEventListener('prototype-nav', hardStopAll);
    window.addEventListener('resize', drawWave);
    window.addEventListener('voice-mode', (e) => {
      if (e.detail.mode === 'file') hardStopAll();
      else if (e.detail.mode === 'live') requestAnimationFrame(drawWave);
    });
    window.addEventListener('orientationchange', drawWave);
    window.addEventListener('monitor-input-change', function () {
      if (pressing || live) endLive();
      invalidatePending();
    });

    applyRole(INITIAL_ROLE);
    setToggleActive(false);
    resetMeter();
    setStatus('就绪：按住讲话或点击播放示例');
    updateTime(0, INITIAL_DURATION);
    drawWave();
    loadWaveformJson();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
