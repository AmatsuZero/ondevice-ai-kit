(function () {
  'use strict';

  var ROUTE_TIMEOUT = 3000;
  var INPUT_THRESHOLD = 0.005;
  var NO_INPUT_MS = 2000;
  var PAINT_MS = 300;
  var DEFAULT_LABEL = '系统默认麦克风';

  var ROUTE_TEXT = {
    headphones: '已检测到耳机输出，可开启监听',
    other: '检测到其他音频输出，监听时请佩戴耳机',
    unknown: '无法确认音频输出，使用扬声器时请佩戴耳机或调低音量'
  };

  function $(id) { return document.querySelector(id); }

  var prepToken = 0;
  var prep = null;
  var navigated = false;
  var levelState = { label: '', noInputSince: 0, lastText: '', lastPaint: 0 };

  function setNote(text) {
    var el = $('#setupNote');
    if (el) el.textContent = text || '';
  }

  function setPrepBusy(busy) {
    var b = $('#micPrepare');
    if (b) b.disabled = !!busy;
  }

  function normalizeRoute(data) {
    data = data || {};
    var kind = data.kind;
    if (kind !== 'headphones' && kind !== 'other' && kind !== 'unknown') kind = 'unknown';
    var token = typeof data.token === 'string' ? data.token : '';
    return { kind: kind, token: token };
  }

  function route() {
    return new Promise(function (resolve) {
      var unknown = { kind: 'unknown', token: '' };
      if (typeof fetch !== 'function') { resolve(unknown); return; }
      var ctrl = null;
      try { ctrl = new AbortController(); } catch (e) {}
      var timer = null;
      if (ctrl) timer = setTimeout(function () { try { ctrl.abort(); } catch (e) {} }, ROUTE_TIMEOUT);
      var opts = { method: 'GET', cache: 'no-store', credentials: 'same-origin' };
      if (ctrl) opts.signal = ctrl.signal;
      fetch('/audio-route', opts).then(function (resp) {
        if (!resp || !resp.ok) throw new Error('bad response');
        return resp.json();
      }).then(function (data) {
        resolve(normalizeRoute(data));
      }).catch(function () {
        resolve(unknown);
      }).then(function () {
        if (timer) clearTimeout(timer);
      });
    });
  }

  function currentDeviceId() {
    var sel = $('#micSource');
    return sel && sel.value ? sel.value : '';
  }

  function constraints(headphoneBool) {
    var hp = !!headphoneBool;
    var audio = {
      echoCancellation: !hp,
      noiseSuppression: !hp,
      autoGainControl: false
    };
    var id = currentDeviceId();
    if (id) audio.deviceId = { exact: id };
    return { audio: audio, video: false };
  }

  function showRoute(r) {
    var kind = r && r.kind ? r.kind : 'unknown';
    var text = ROUTE_TEXT[kind] || ROUTE_TEXT.unknown;
    var el = $('#monitorRoute');
    if (el && el.textContent !== text) el.textContent = text;
    var t = $('#listenToggle');
    if (t && t.getAttribute('aria-description') !== text) t.setAttribute('aria-description', text);
  }

  function resetLevel() {
    var m = $('#micMeter');
    var r = $('#micReading');
    if (m) m.value = 0;
    if (r) r.textContent = '';
    levelState.label = '';
    levelState.noInputSince = 0;
    levelState.lastText = '';
    levelState.lastPaint = 0;
  }

  function level(rms, trackLabel) {
    var m = $('#micMeter');
    var r = $('#micReading');
    var value = typeof rms === 'number' && isFinite(rms) ? Math.max(0, Math.min(1, rms)) : 0;
    if (m && m.value !== value) m.value = value;
    if (!trackLabel) { resetLevel(); return; }
    if (levelState.label !== trackLabel) {
      levelState.label = trackLabel;
      levelState.noInputSince = 0;
      levelState.lastText = '';
    }
    var now = Date.now();
    var hasInput = value > INPUT_THRESHOLD;
    if (hasInput) levelState.noInputSince = 0;
    else if (!levelState.noInputSince) levelState.noInputSince = now;

    var text;
    if (levelState.noInputSince && now - levelState.noInputSince >= NO_INPUT_MS) {
      text = '连续无输入，请切换麦克风';
    } else if (hasInput) {
      text = '麦克风有输入：' + trackLabel;
    } else {
      text = '麦克风已连接：' + trackLabel;
    }
    if (text !== levelState.lastText && now - levelState.lastPaint >= PAINT_MS) {
      if (r) r.textContent = text;
      levelState.lastText = text;
      levelState.lastPaint = now;
    }
  }

  function notifyInputChange(deviceId) {
    var label = '';
    var sel = $('#micSource');
    if (sel && sel.selectedIndex >= 0) label = sel.options[sel.selectedIndex].textContent || '';
    var detail = { deviceId: deviceId || '', label: label };
    var evt;
    try {
      evt = new CustomEvent('monitor-input-change', { detail: detail, bubbles: true });
    } catch (e) {
      evt = document.createEvent('CustomEvent');
      evt.initCustomEvent('monitor-input-change', true, false, detail);
    }
    document.dispatchEvent(evt);
  }

  function buildDeviceOptions(devices) {
    var sel = $('#micSource');
    if (!sel) return;
    var prev = sel.value || '';
    var inputs = [];
    var seen = {};
    (devices || []).forEach(function (d) {
      if (!d || d.kind !== 'audioinput' || !d.label || !d.deviceId) return;
      if (seen[d.deviceId]) return;
      seen[d.deviceId] = 1;
      inputs.push(d);
    });
    while (sel.firstChild) sel.removeChild(sel.firstChild);
    var def = document.createElement('option');
    def.value = '';
    def.textContent = DEFAULT_LABEL;
    sel.appendChild(def);
    inputs.forEach(function (d) {
      var o = document.createElement('option');
      o.value = d.deviceId;
      o.textContent = d.label;
      sel.appendChild(o);
    });
    var found = false;
    for (var i = 0; i < sel.options.length; i++) {
      if (sel.options[i].value === prev) { found = true; break; }
    }
    if (prev && found) sel.value = prev;
    else {
      sel.value = '';
      if (prev) notifyInputChange('');
    }
  }

  function enumerateDevices() {
    if (!navigator.mediaDevices || typeof navigator.mediaDevices.enumerateDevices !== 'function') {
      return Promise.resolve([]);
    }
    return navigator.mediaDevices.enumerateDevices().catch(function () { return []; });
  }

  function refreshDeviceList() {
    return enumerateDevices().then(function (devices) {
      buildDeviceOptions(devices);
      return devices;
    });
  }

  function stopPrep(p) {
    if (p.stream) {
      try { p.stream.getTracks().forEach(function (t) { t.stop(); }); } catch (e) {}
      p.stream = null;
    }
    p.active = false;
    if (prep === p) setPrepBusy(false);
  }

  function cancelPreparation() {
    prepToken++;
    if (prep) {
      prep.cancelled = true;
      stopPrep(prep);
      prep = null;
    }
  }

  function handlePermissionError(err) {
    var name = err && err.name ? err.name : '';
    if (name === 'NotAllowedError' || name === 'PermissionDeniedError' || name === 'SecurityError') {
      setNote('麦克风权限被拒绝。请在浏览器地址栏的网站设置中允许麦克风后重试。');
    } else if (name === 'NotFoundError' || name === 'DevicesNotFoundError') {
      setNote('未找到麦克风设备，请连接麦克风后重试。');
    } else {
      setNote('无法获取麦克风权限：' + (name || '未知错误') + '。请检查设备与浏览器设置。');
    }
  }

  function prepare() {
    if (prep && prep.active) return;
    cancelPreparation();
    var gen = prepToken;
    var p = { gen: gen, active: true, cancelled: false, stream: null };
    prep = p;
    setPrepBusy(true);
    setNote('正在请求麦克风权限…');
    var gum;
    try {
      gum = navigator.mediaDevices.getUserMedia({ audio: true, video: false });
    } catch (e) {
      p.active = false;
      setPrepBusy(false);
      handlePermissionError(e);
      return;
    }
    Promise.resolve(gum).then(function (stream) {
      p.stream = stream;
      if (p.cancelled || gen !== prepToken || navigated || document.hidden) {
        stopPrep(p);
        if (prep === p) prep = null;
        return;
      }
      return enumerateDevices().then(function (devices) {
        if (p.cancelled || gen !== prepToken || navigated || document.hidden) {
          stopPrep(p);
          if (prep === p) prep = null;
          return;
        }
        buildDeviceOptions(devices);
        stopPrep(p);
        if (prep === p) prep = null;
        setNote('麦克风权限已就绪，设备列表已更新。');
      });
    }).catch(function (err) {
      stopPrep(p);
      if (prep === p) prep = null;
      if (p.cancelled || gen !== prepToken) return;
      handlePermissionError(err);
    });
  }

  function liveVisible() {
    var live = $('#livePanel');
    var explore = $('#panel-explore');
    return !!(live && !live.hidden && explore && !explore.hidden);
  }

  function handleLiveGate() {
    if (liveVisible()) onLiveEntry();
    else cancelPreparation();
  }

  function onLiveEntry() {
    route().then(function (r) { showRoute(r); });
    refreshDeviceList();
  }

  function onLeave() {
    navigated = true;
    cancelPreparation();
  }

  function capturedTrackValid(p) {
    if (!p || !p.stream) return false;
    try {
      var tracks = p.stream.getTracks();
      for (var i = 0; i < tracks.length; i++) {
        if (tracks[i].readyState === 'live') return true;
      }
    } catch (e) {}
    return false;
  }

  function onDeviceChange() {
    if (prep && prep.active && !capturedTrackValid(prep)) cancelPreparation();
    if (liveVisible()) {
      route().then(function (r) { showRoute(r); });
      refreshDeviceList();
    }
  }

  function onSourceChange() {
    var sel = $('#micSource');
    notifyInputChange(sel && sel.value ? sel.value : '');
  }

  function invalidate() {
    cancelPreparation();
  }

  function init() {
    var t = $('#listenToggle');
    if (t) t.disabled = false;
    var r = $('#micReading');
    if (r) r.setAttribute('aria-live', 'off');
    var m = $('#micMeter');
    if (m) {
      if (!m.hasAttribute('min')) m.setAttribute('min', '0');
      if (!m.hasAttribute('max')) m.setAttribute('max', '1');
    }
    var sel = $('#micSource');
    if (sel) sel.addEventListener('change', onSourceChange);
    var pb = $('#micPrepare');
    if (pb) pb.addEventListener('click', prepare);
    window.addEventListener('pagehide', onLeave);
    window.addEventListener('beforeunload', onLeave);
    window.addEventListener('pageshow', function (e) {
      if (e && e.persisted) navigated = false;
    });
    document.addEventListener('visibilitychange', function () {
      if (document.hidden) cancelPreparation();
    });
    if (navigator.mediaDevices && typeof navigator.mediaDevices.addEventListener === 'function') {
      navigator.mediaDevices.addEventListener('devicechange', onDeviceChange);
    }
    window.addEventListener('voice-mode', function (e) {
      if (e && e.detail && e.detail.mode === 'live') handleLiveGate();
      else cancelPreparation();
    });
    window.addEventListener('prototype-nav', function (e) {
      if (e && e.detail && e.detail.tab === 'explore') handleLiveGate();
      else cancelPreparation();
    });
    if (/(?:^|[?&])mode=live(?:[&#]|$)/.test(window.location.search || '')) handleLiveGate();
  }

  var api = window.voiceMonitor || {};
  api.route = route;
  api.constraints = constraints;
  api.showRoute = showRoute;
  api.level = level;
  api.invalidate = invalidate;
  window.voiceMonitor = api;

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
}());