(function () {
  'use strict';

  var $ = function (sel) { return document.querySelector(sel); };
  var fileAudio = $('#fileAudio');
  if (!fileAudio) return;

  var filePanel = $('#filePanel');
  var fileDemo = $('#fileDemo');
  var fileInput = $('#fileInput');
  var fileRecord = $('#fileRecord');
  var fileSource = $('#fileSource');
  var fileDownload = $('#fileDownload');
  var fileStatus = $('#fileStatus');
  var effectButtons = document.querySelectorAll('[data-effect]');

  var EFFECT_LABELS = {
    original: '原声', up: '升调', down: '降调',
    voiceA: '音色 A', voiceB: '音色 B', voiceC: '音色 C'
  };
  var MAX_IMPORT_BYTES = 64 * 1024 * 1024;
  var SR = 16000;
  var MAX_SAMPLES = 30 * SR;

  var source = null;
  var sourceSeq = 0;
  var cache = new Map();
  var sourceUrls = [];
  var generation = 0;
  var activeController = null;
  var selectedEffect = 'original';
  var readyEffect = null;
  var readyUrl = null;
  var savedPos = null;
  var decodeCtx = null;
  var recorder = null;
  var recStream = null;
  var recChunks = [];
  var recTimer = null;
  var recGen = 0;
  var recCancelled = false;
  var recPending = false;
  var pendingConversion = false;
  var audioToken = 0;
  var audioHandler = null;

  function isVisible(el) {
    if (!el) return false;
    if (el.hidden) return false;
    var st = window.getComputedStyle ? getComputedStyle(el) : null;
    return !st || st.display !== 'none';
  }
  var active = isVisible(filePanel) && !fileAudio.hidden && !filePanel.hidden;

  function setStatus(msg) {
    if (fileStatus) fileStatus.textContent = msg;
  }
  function label(e) { return EFFECT_LABELS[e] || e; }

  function trackedUrl(blob) {
    var u = URL.createObjectURL(blob);
    sourceUrls.push(u);
    return u;
  }
  function revokeUrls(list) {
    (list || []).forEach(function (u) { try { URL.revokeObjectURL(u); } catch (e) {} });
  }

  function capturePlayback() {
    var d = fileAudio.duration, t = fileAudio.currentTime;
    var ratio = (isFinite(d) && d > 0) ? Math.max(0, Math.min(1, t / d)) : 0;
    return {
      ratio: ratio,
      shouldPlay: !fileAudio.paused && !fileAudio.ended,
      volume: fileAudio.volume,
      muted: fileAudio.muted,
      rate: fileAudio.playbackRate
    };
  }

  function removeAudioHandler() {
    if (audioHandler) {
      try { fileAudio.removeEventListener('loadedmetadata', audioHandler); } catch (e) {}
      audioHandler = null;
    }
  }
  function cancelAudioAutoPlay() {
    audioToken++;
    removeAudioHandler();
  }
  function playAudio() {
    try {
      var p = fileAudio.play();
      if (p && p.catch) p.catch(function () {});
    } catch (e) {}
  }
  function setAudioSrc(url, pos) {
    var token = ++audioToken;
    removeAudioHandler();
    fileAudio.preload = 'metadata';
    fileAudio.src = url;
    var requested = !!(pos && pos.shouldPlay && active && !document.hidden);
    var started = false;
    var apply = function () {
      if (token !== audioToken) return;
      if (!active || document.hidden) return;
      removeAudioHandler();
      if (pos) {
        if (typeof pos.ratio === 'number' && isFinite(fileAudio.duration) && fileAudio.duration > 0) {
          try { fileAudio.currentTime = pos.ratio * fileAudio.duration; } catch (e) {}
        }
        if (pos.volume !== undefined) { try { fileAudio.volume = pos.volume; } catch (e) {} }
        if (pos.muted !== undefined) { try { fileAudio.muted = pos.muted; } catch (e) {} }
        if (pos.rate !== undefined) { try { fileAudio.playbackRate = pos.rate; } catch (e) {} }
      }
      if (requested && !started) { started = true; playAudio(); }
    };
    if (fileAudio.readyState >= 1) apply();
    else {
      audioHandler = apply;
      fileAudio.addEventListener('loadedmetadata', apply);
      fileAudio.load();
      if (requested && !started) { started = true; playAudio(); }
    }
  }

  function safeName(s) {
    var str = String(s || ''), out = '';
    for (var i = 0; i < str.length; i++) {
      var c = str.charAt(i), code = str.charCodeAt(i);
      var ok = (code >= 48 && code <= 57) || (code >= 65 && code <= 90) ||
        (code >= 97 && code <= 122) || c === '.' || c === '_' || c === '-';
      out += ok ? c : '_';
    }
    return out;
  }
  function downloadName(effect) {
    if (!source || source.kind === 'builtin') return effect + '.wav';
    return effect + '-' + safeName(source.label) + '.wav';
  }

  function setReady(effect, url, blob) {
    readyEffect = effect;
    readyUrl = url;
    if (fileDownload) {
      if (url) fileDownload.href = url;
      else fileDownload.removeAttribute('href');
      fileDownload.setAttribute('download', downloadName(effect));
      fileDownload.removeAttribute('aria-disabled');
    }
  }

  function updateEffectButtons() {
    Array.prototype.forEach.call(effectButtons, function (b) {
      b.setAttribute('aria-pressed', b.getAttribute('data-effect') === selectedEffect ? 'true' : 'false');
    });
  }

  function abortConversion() {
    pendingConversion = false;
    if (activeController) {
      try { activeController.abort(); } catch (e) {}
      activeController = null;
    }
  }

  function builtinSource() {
    return { id: ++sourceSeq, kind: 'builtin', label: '内置示例', originalBlob: null, canonicalBlob: null };
  }

  function adoptSource(src) {
    generation++;
    abortConversion();
    cancelRecording();
    cancelAudioAutoPlay();
    pendingConversion = false;
    var old = sourceUrls;
    sourceUrls = [];
    cache.clear();
    source = src;
    if (fileSource) fileSource.textContent = src.label;
    if (src.originalBlob) src.originalUrl = trackedUrl(src.originalBlob);
    if (src.canonicalBlob && src.canonicalBlob !== src.originalBlob) src.canonicalUrl = trackedUrl(src.canonicalBlob);
    selectedEffect = 'original';
    readyEffect = null;
    readyUrl = null;
    updateEffectButtons();
    return old;
  }

  function isLoopbackHost() {
    var h = ((window.location && window.location.hostname) || '').toLowerCase();
    return h === 'localhost' || h === '127.0.0.1' || h === '::1' || h === '[::1]';
  }

  function clearForPending() {
    pendingConversion = true;
    try { fileAudio.pause(); } catch (e) {}
    fileAudio.removeAttribute('src');
    try { fileAudio.load(); } catch (e) {}
    cancelAudioAutoPlay();
    if (fileDownload) {
      fileDownload.removeAttribute('href');
      fileDownload.setAttribute('aria-disabled', 'true');
    }
  }

  function restoreReady(pos) {
    pendingConversion = false;
    var prev = readyEffect || 'original';
    var prevUrl = readyUrl;
    if (!prevUrl && source && source.kind === 'builtin') prevUrl = 'original.wav';
    selectedEffect = prev;
    updateEffectButtons();
    setReady(prev, prevUrl, null);
    if (prevUrl) {
      setAudioSrc(prevUrl, {
        shouldPlay: false,
        ratio: pos && pos.ratio,
        volume: pos && pos.volume,
        muted: pos && pos.muted,
        rate: pos && pos.rate
      });
    }
  }

  function selectEffect(effect) {
    var gen = ++generation;
    selectedEffect = effect;
    updateEffectButtons();
    abortConversion();
    cancelRecording();
    var pos = capturePlayback();

    if (!source || source.kind === 'builtin') {
      var url = effect === 'original' ? 'original.wav' : 'clips/' + effect + '.wav';
      setReady(effect, url, null);
      setAudioSrc(url, pos);
      setStatus('已选择：' + label(effect));
      return;
    }
    if (effect === 'original') {
      var ourl = source.originalUrl || trackedUrl(source.originalBlob);
      setReady(effect, ourl, source.originalBlob);
      setAudioSrc(ourl, pos);
      setStatus('已选择：' + label(effect));
      return;
    }
    var key = source.id + '|' + effect;
    var cached = cache.get(key);
    if (cached) {
      setReady(effect, cached.url, cached.blob);
      setAudioSrc(cached.url, pos);
      setStatus('已选择：' + label(effect) + '（已缓存）');
      return;
    }
    if (!isLoopbackHost()) {
      restoreReady(pos);
      setStatus('自定义转换仅支持本地服务（localhost/127.0.0.1）');
      return;
    }
    clearForPending();
    setStatus('正在生成 ' + label(effect) + '…');
    var controller = new AbortController();
    activeController = controller;
    fetch('/convert/' + effect, {
      method: 'POST',
      headers: { 'Content-Type': 'audio/wav' },
      body: source.canonicalBlob,
      signal: controller.signal
    }).then(function (res) {
      if (gen !== generation) throw new Error('stale');
      if (!res.ok) {
        return res.text().then(function (t) { throw new Error(cleanError(t) || ('HTTP ' + res.status)); });
      }
      var ct = (res.headers.get('content-type') || '').toLowerCase();
      if (ct.indexOf('text/html') !== -1) throw new Error('后端不可用（返回了页面内容）');
      return res.blob();
    }).then(function (blob) {
      if (gen !== generation) throw new Error('stale');
      if (!blob || !blob.size) throw new Error('后端返回了空音频');
      return isWav(blob).then(function (ok) {
        if (!ok) throw new Error('后端返回了非 WAV 内容');
        if (gen !== generation) throw new Error('stale');
        var url = trackedUrl(blob);
        cache.set(key, { url: url, blob: blob });
        pendingConversion = false;
        setReady(effect, url, blob);
        var next = (active && !document.hidden) ? pos : {
          shouldPlay: false,
          ratio: pos && pos.ratio,
          volume: pos && pos.volume,
          muted: pos && pos.muted,
          rate: pos && pos.rate
        };
        setAudioSrc(url, next);
        setStatus('已生成：' + label(effect));
      });
    }).catch(function (err) {
      if (gen !== generation) return;
      if (err && err.name === 'AbortError') return;
      if (activeController === controller) activeController = null;
      pendingConversion = false;
      var msg = err && err.message ? err.message : String(err);
      if (/fetch|network|failed/i.test(msg)) msg = '后端不可用（无法连接服务）';
      setStatus('转换失败：' + msg);
      restoreReady(pos);
    }).then(function () {
      if (activeController === controller) activeController = null;
    });
  }

  function cleanError(text) {
    if (!text) return '';
    var first = String(text).split(String.fromCharCode(10))[0].trim();
    if (first.charAt(0) === '<') return '后端返回了页面内容';
    return first.slice(0, 160);
  }

  function isWav(blob) {
    return blob.slice(0, 12).arrayBuffer().then(function (ab) {
      var v = new DataView(ab);
      var a = String.fromCharCode(v.getUint8(0), v.getUint8(1), v.getUint8(2), v.getUint8(3));
      var b = String.fromCharCode(v.getUint8(8), v.getUint8(9), v.getUint8(10), v.getUint8(11));
      return a === 'RIFF' && b === 'WAVE';
    }).catch(function () { return false; });
  }

  function writeString(view, offset, s) {
    for (var i = 0; i < s.length; i++) view.setUint8(offset + i, s.charCodeAt(i));
  }
  function encodeWav(samples, sampleRate) {
    var dataBytes = samples.length * 2;
    var buffer = new ArrayBuffer(44 + dataBytes);
    var view = new DataView(buffer);
    writeString(view, 0, 'RIFF');
    view.setUint32(4, 36 + dataBytes, true);
    writeString(view, 8, 'WAVE');
    writeString(view, 12, 'fmt ');
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true);
    view.setUint16(22, 1, true);
    view.setUint32(24, sampleRate, true);
    view.setUint32(28, sampleRate * 2, true);
    view.setUint16(32, 2, true);
    view.setUint16(34, 16, true);
    writeString(view, 36, 'data');
    view.setUint32(40, dataBytes, true);
    var off = 44;
    for (var i = 0; i < samples.length; i++, off += 2) {
      var s = Math.max(-1, Math.min(1, samples[i]));
      view.setInt16(off, s < 0 ? s * 0x8000 : s * 0x7fff, true);
    }
    return new Blob([buffer], { type: 'audio/wav' });
  }

  function decodeAndCanonicalize(blob, isRecording) {
    return blob.arrayBuffer().then(function (ab) {
      var AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) throw new Error('浏览器不支持 Web Audio');
      if (!decodeCtx) decodeCtx = new AC();
      return decodeCtx.decodeAudioData(ab);
    }).then(function (buf) {
      if (!buf || !isFinite(buf.duration) || buf.duration <= 0) throw new Error('音频内容无效');
      if (buf.duration < 0.05) throw new Error('音频太短（至少 0.05 秒）');
      if (!isRecording && buf.duration > 30.02) throw new Error('音频过长（最多 30 秒）');
      var OAC = window.OfflineAudioContext || window.webkitOfflineAudioContext;
      if (!OAC) throw new Error('浏览器不支持离线音频渲染');
      var len = Math.min(MAX_SAMPLES, Math.ceil(buf.duration * SR));
      var offline = new OAC(1, len, SR);
      var s = offline.createBufferSource();
      s.buffer = buf;
      s.connect(offline.destination);
      if (isRecording && buf.duration > 30) s.start(0, 0, 30);
      else s.start();
      return offline.startRendering();
    }).then(function (rendered) {
      return encodeWav(rendered.getChannelData(0), SR);
    });
  }

  function handleFile(file) {
    if (!file) return;
    cancelAll(false);
    var gen = ++generation;
    abortConversion();
    if (file.size <= 0) { setStatus('导入失败：文件为空'); return; }
    if (file.size > MAX_IMPORT_BYTES) { setStatus('导入失败：文件过大（上限 64MB）'); return; }
    setStatus('正在读取文件…');
    isWav(file).then(function (ok) {
      if (!ok) throw new Error('不是 RIFF/WAVE 音频文件');
      if (gen !== generation) throw new Error('stale');
      return decodeAndCanonicalize(file, false);
    }).then(function (canonical) {
      if (gen !== generation) return;
      var src = { id: ++sourceSeq, kind: 'imported', label: file.name || '导入文件', originalBlob: file, canonicalBlob: canonical };
      var old = adoptSource(src);
      selectEffect('original');
      revokeUrls(old);
      setStatus('已导入：' + src.label);
    }).catch(function (err) {
      if (gen !== generation) return;
      setStatus('导入失败：' + (err && err.message ? err.message : err));
    });
  }

  function stopTracks(stream) {
    if (!stream || !stream.getTracks) return;
    stream.getTracks().forEach(function (t) { try { t.stop(); } catch (e) {} });
  }
  function pickMime() {
    if (!window.MediaRecorder || !MediaRecorder.isTypeSupported) return undefined;
    var types = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4'];
    for (var i = 0; i < types.length; i++) if (MediaRecorder.isTypeSupported(types[i])) return types[i];
    return undefined;
  }
  function setRecordLabel(text) { if (fileRecord) fileRecord.textContent = text; }

  function cancelRecording() {
    recGen++;
    recCancelled = true;
    recPending = false;
    if (recTimer) { clearTimeout(recTimer); recTimer = null; }
    var rec = recorder;
    recorder = null;
    if (rec && rec.state !== 'inactive') { try { rec.stop(); } catch (e) {} }
    recChunks = [];
    stopTracks(recStream);
    recStream = null;
    setRecordLabel('开始录音');
  }

  function startRecording() {
    if (recPending) return;
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia || !window.MediaRecorder) {
      setStatus('当前浏览器不支持录音');
      setRecordLabel('开始录音');
      return;
    }
    cancelRecording();
    try { fileAudio.pause(); } catch (e) {}
    var gen = ++recGen;
    recCancelled = false;
    recPending = true;
    setRecordLabel('获取权限中…');
    setStatus('正在请求麦克风权限…');
    navigator.mediaDevices.getUserMedia({ audio: true }).then(function (stream) {
      recPending = false;
      if (gen !== recGen || recCancelled || !active) { stopTracks(stream); return; }
      recStream = stream;
      var mime = pickMime();
      var rec;
      try {
        rec = mime ? new MediaRecorder(stream, { mimeType: mime }) : new MediaRecorder(stream);
      } catch (e) {
        stopTracks(stream); recStream = null;
        setRecordLabel('开始录音');
        setStatus('录音不可用：' + e.message);
        return;
      }
      recorder = rec;
      recChunks = [];
      rec.ondataavailable = function (e) {
        if (gen !== recGen || recCancelled || recorder !== rec) return;
        if (e.data && e.data.size) recChunks.push(e.data);
      };
      rec.onstop = function () {
        if (gen !== recGen || recorder !== rec) return;
        onRecordingStopped(gen, rec);
      };
      rec.onerror = function () {
        if (gen !== recGen || recorder !== rec) return;
        setStatus('录音出错');
        cancelRecording();
      };
      try { rec.start(); } catch (e) {
        setRecordLabel('开始录音');
        setStatus('无法开始录音：' + e.message);
        cancelRecording();
        return;
      }
      setRecordLabel('停止录音');
      setStatus('录音中…（最长 30 秒）');
      recTimer = setTimeout(function () {
        if (gen !== recGen || recorder !== rec) return;
        if (rec.state !== 'inactive') { try { rec.stop(); } catch (e) {} }
      }, 30000);
    }).catch(function (err) {
      recPending = false;
      if (gen !== recGen) return;
      setRecordLabel('开始录音');
      setStatus('麦克风不可用：' + (err && err.message ? err.message : err));
    });
  }

  function onRecordingStopped(gen, rec) {
    if (gen !== recGen) return;
    if (recTimer) { clearTimeout(recTimer); recTimer = null; }
    recPending = false;
    recorder = null;
    stopTracks(recStream);
    recStream = null;
    if (recCancelled) { recChunks = []; setRecordLabel('开始录音'); return; }
    var blob = new Blob(recChunks, { type: (rec && rec.mimeType) || 'audio/webm' });
    recChunks = [];
    if (!blob.size) { setStatus('录音为空'); setRecordLabel('开始录音'); return; }
    setStatus('正在处理录音…');
    setRecordLabel('处理中…');
    decodeAndCanonicalize(blob, true).then(function (canonical) {
      if (gen !== recGen || recCancelled) return;
      var src = { id: ++sourceSeq, kind: 'recording', label: '麦克风录音', originalBlob: canonical, canonicalBlob: canonical };
      var old = adoptSource(src);
      selectEffect('original');
      revokeUrls(old);
      setRecordLabel('开始录音');
      setStatus('录音已载入（最长 30 秒）');
    }).catch(function (err) {
      if (gen !== recGen) return;
      setRecordLabel('开始录音');
      setStatus('录音处理失败：' + (err && err.message ? err.message : err));
    });
  }

  function toggleRecord() {
    if (recorder && recorder.state !== 'inactive') {
      setRecordLabel('处理中…');
      try { recorder.stop(); } catch (e) { cancelRecording(); }
      return;
    }
    startRecording();
  }

  function cancelAll(revert) {
    generation++;
    abortConversion();
    cancelRecording();
    cancelAudioAutoPlay();
    try { fileAudio.pause(); } catch (e) {}
    if (revert) {
      if (pendingConversion || selectedEffect !== readyEffect) {
        restoreReady(savedPos);
      }
    }
  }

  function deactivate() {
    if (!active) return;
    active = false;
    savedPos = capturePlayback();
    savedPos.shouldPlay = false;
    try { fileAudio.pause(); } catch (e) {}
    cancelAll(true);
    setStatus('文件模式已暂停');
  }
  function activate() {
    if (active) return;
    active = true;
    if (readyUrl) setAudioSrc(readyUrl, savedPos || { shouldPlay: false });
    setStatus('文件模式已就绪');
  }

  if (fileDemo) fileDemo.addEventListener('click', function () {
    cancelAll(false);
    var old = adoptSource(builtinSource());
    selectEffect('original');
    revokeUrls(old);
    setStatus('已恢复内置示例');
  });
  if (fileInput) fileInput.addEventListener('change', function (e) {
    var f = e.target.files && e.target.files[0];
    try { e.target.value = ''; } catch (err) {}
    handleFile(f);
  });
  if (fileRecord) fileRecord.addEventListener('click', toggleRecord);
  Array.prototype.forEach.call(effectButtons, function (b) {
    b.addEventListener('click', function () { selectEffect(b.getAttribute('data-effect')); });
  });
  fileAudio.addEventListener('error', function () {
    if (active && fileAudio.error && !pendingConversion) setStatus('音频加载失败：' + (fileAudio.currentSrc || fileAudio.src));
  });
  window.addEventListener('voice-mode', function (e) {
    var m = e.detail && e.detail.mode;
    if (m === 'file') { if (!fileAudio.hidden) activate(); }
    else deactivate();
  });
  window.addEventListener('voice-file-kind', function (e) {
    var kind = e && e.detail && e.detail.kind;
    if (kind === 'reference') { deactivate(); return; }
    if (kind === 'fixed') {
      var ex = document.getElementById('panel-explore');
      if (isVisible(filePanel) && ex && !ex.hidden && !document.hidden) activate();
    }
  });
  window.addEventListener('prototype-nav', function () {
    try { fileAudio.pause(); } catch (e) {}
    cancelAll(true);
  });
  document.addEventListener('visibilitychange', function () {
    if (document.hidden) {
      try { fileAudio.pause(); } catch (e) {}
      cancelAll(true);
    }
  });
  window.addEventListener('pagehide', function (e) {
    savedPos = capturePlayback();
    if (e.persisted) {
      savedPos.shouldPlay = false;
      cancelAll(true);
    } else {
      cancelAll(false);
    }
    if (!e.persisted) {
      revokeUrls(sourceUrls);
      sourceUrls = [];
    }
  });
  window.addEventListener('pageshow', function (e) {
    if (!e.persisted) return;
    active = isVisible(filePanel);
    if (readyUrl) setAudioSrc(readyUrl, savedPos || { shouldPlay: false });
    updateEffectButtons();
  });

  var initial = builtinSource();
  adoptSource(initial);
  setReady('original', 'original.wav', null);
  setAudioSrc('original.wav', { shouldPlay: false });
  setStatus('就绪：内置示例');
})();
