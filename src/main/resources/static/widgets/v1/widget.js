/*
 * Voice widget embed script.
 *
 *   <script src="https://your-host/widgets/v1/widget.js"
 *           data-widget-key="wgt_0123456789abcdef" async></script>
 *
 * What it does, in order: read the widget's public configuration, draw a launcher styled
 * from it, and — once the visitor has consented and pressed the button — book a session
 * and put their microphone through to the agent over WebRTC. The audio path from there is
 * the same one every phone call takes: Asterisk, then Stasis, then the RTP pipeline.
 *
 * Written as one plain IIFE with no build step and no framework, because it is loaded into
 * somebody else's page: it has to be a single file that can be served from static
 * resources, and it must not assume anything about what else is on that page.
 *
 * Everything it renders lives inside a shadow root. That is not tidiness — it is the only
 * way the host page's CSS cannot reach in and break the widget, and the widget's cannot
 * leak out and break the host page.
 */
(function () {
  'use strict';

  var SCRIPT = document.currentScript;
  if (!SCRIPT) {
    console.error('[voice-widget] loaded without document.currentScript; use a plain <script src> tag');
    return;
  }

  var WIDGET_KEY = SCRIPT.getAttribute('data-widget-key');
  if (!WIDGET_KEY) {
    console.error('[voice-widget] the <script> tag needs data-widget-key');
    return;
  }

  /* The API lives wherever this script was served from, unless the page overrides it —
   * which it has to when the script is on a CDN and the API is not. */
  var API_BASE = (SCRIPT.getAttribute('data-api-base') || new URL(SCRIPT.src).origin).replace(/\/+$/, '');

  /*
   * SIP.js is not bundled with this file: it is ~270 KB that most page loads never need,
   * so it is fetched only when a visitor actually starts a call.
   *
   * jsDelivr's `+esm` build rather than the package's own `lib/index.js`, which is ESM
   * split across a hundred relative imports — a hundred requests before the first ring.
   * Pinned to an exact version, because an embed script running on someone else's site
   * must not change behaviour because a CDN tag moved. A deployment that cannot reach a
   * public CDN overrides the URL with data-sip-js and self-hosts the file.
   */
  var SIP_JS_URL = SCRIPT.getAttribute('data-sip-js')
      || 'https://cdn.jsdelivr.net/npm/sip.js@0.21.2/+esm';

  var LANGUAGE = SCRIPT.getAttribute('data-language') || null;

  /* One widget per page. A second tag is a copy-paste mistake, and two launchers sitting
   * on top of each other is a worse way to find out than a line in the console. */
  if (window.__voiceWidgetLoaded) {
    console.warn('[voice-widget] already loaded on this page; ignoring the second tag');
    return;
  }
  window.__voiceWidgetLoaded = true;

  var STATE = {
    IDLE: 'idle',
    CONSENT: 'consent',
    CONNECTING: 'connecting',
    LIVE: 'live',
    ENDED: 'ended',
    ERROR: 'error'
  };

  var config = null;
  var theme = null;
  var state = STATE.IDLE;
  var errorText = null;
  var open = false;

  var ua = null;
  var session = null;
  var audioEl = null;

  var host = document.createElement('div');
  host.setAttribute('data-voice-widget', WIDGET_KEY);
  var root = host.attachShadow({ mode: 'open' });

  /* ---------------------------------------------------------------- configuration */

  function loadConfig() {
    return fetch(API_BASE + '/api/public/widgets/' + encodeURIComponent(WIDGET_KEY) + '/config', {
      method: 'GET',
      credentials: 'omit',
      headers: { Accept: 'application/json' }
    }).then(readResponse);
  }

  function startSession() {
    var url = API_BASE + '/api/public/widgets/' + encodeURIComponent(WIDGET_KEY) + '/session';
    if (LANGUAGE) {
      url += '?language=' + encodeURIComponent(LANGUAGE);
    }
    return fetch(url, {
      method: 'POST',
      credentials: 'omit',
      headers: { Accept: 'application/json' }
    }).then(readResponse);
  }

  /*
   * Every endpoint answers in the same envelope, including its failures, so the message a
   * visitor sees comes from the server rather than from an HTTP status this script would
   * have to translate itself.
   */
  function readResponse(response) {
    return response.json().catch(function () {
      throw new Error('Server javobi tushunarsiz (' + response.status + ')');
    }).then(function (body) {
      if (!response.ok || !body || body.accept === false) {
        throw new Error((body && body.message) || ('Xatolik (' + response.status + ')'));
      }
      return body.data;
    });
  }

  /* ------------------------------------------------------------------------- call */

  var sipModule = null;

  function loadSipJs() {
    if (sipModule) {
      return Promise.resolve(sipModule);
    }
    return import(/* webpackIgnore: true */ SIP_JS_URL).then(function (module) {
      sipModule = module;
      return module;
    }).catch(function () {
      throw new Error("Ovozli aloqa kutubxonasi yuklanmadi. Internetni tekshirib, qayta urinib ko'ring.");
    });
  }

  function startCall() {
    setState(STATE.CONNECTING);

    /* Permission is asked for before anything is booked: a visitor who declines has not
     * started a call, and booking one first would spend the agent's daily allowance on a
     * conversation that never happens.
     *
     * The probe stream is released immediately — SIP.js opens its own when it builds the
     * peer connection, and holding this one too would leave the browser's recording
     * indicator on with a second, unused microphone capture behind it. Releasing it costs
     * nothing: permission is granted per origin, so the second request does not prompt. */
    return navigator.mediaDevices.getUserMedia({ audio: true, video: false })
        .then(function (stream) {
          stream.getTracks().forEach(function (track) { track.stop(); });
        })
        .catch(function () {
          throw new Error('Mikrofonga ruxsat berilmadi. Brauzer sozlamalaridan ruxsat bering.');
        })
        .then(function () {
          return Promise.all([loadSipJs(), startSession()]);
        })
        .then(function (results) {
          return connect(results[0], results[1]);
        })
        .catch(function (error) {
          fail(error);
        });
  }

  function connect(SIP, credentials) {
    var wsHost = new URL(credentials.wsUrl).host;
    var uri = SIP.UserAgent.makeURI('sip:' + credentials.sipUser + '@' + wsHost);
    if (!uri) {
      throw new Error('Server manzili noto‘g‘ri sozlangan.');
    }

    ua = new SIP.UserAgent({
      uri: uri,
      transportOptions: { server: credentials.wsUrl },
      authorizationUsername: credentials.sipUser,
      authorizationPassword: credentials.sipPassword,
      /* Audio only, and explicitly no video: a widget that asked for the camera would be
       * refused by half the visitors who would happily have talked. */
      sessionDescriptionHandlerFactoryOptions: {
        constraints: { audio: true, video: false }
      },
      logBuilder: function () {
        return function () { /* the host page's console is not ours to fill */ };
      }
    });

    return ua.start().then(function () {
      var target = SIP.UserAgent.makeURI('sip:' + credentials.dialNumber + '@' + wsHost);
      var inviter = new SIP.Inviter(ua, target, {
        extraHeaders: [credentials.sessionHeader + ': ' + credentials.sessionId],
        sessionDescriptionHandlerOptions: { constraints: { audio: true, video: false } }
      });
      session = inviter;

      inviter.stateChange.addListener(function (next) {
        if (next === 'Established') {
          attachRemoteAudio(inviter);
          setState(STATE.LIVE);
        } else if (next === 'Terminated') {
          cleanUpCall();
          /* A call that never reached Established was refused or dropped on the way in;
           * saying "ended" there would tell the visitor a conversation happened. */
          if (state === STATE.CONNECTING) {
            fail(new Error("Aloqa o‘rnatilmadi. Birozdan so‘ng qayta urinib ko‘ring."));
          } else if (state === STATE.LIVE) {
            setState(STATE.ENDED);
          }
        }
      });

      return inviter.invite();
    });
  }

  function attachRemoteAudio(inviter) {
    var handler = inviter.sessionDescriptionHandler;
    if (!handler || !handler.peerConnection) {
      fail(new Error("Audio oqimi ochilmadi. Sahifani yangilab, qayta urinib ko‘ring."));
      return;
    }
    var remote = new MediaStream();
    var receivers = handler.peerConnection.getReceivers();
    receivers.forEach(function (receiver) {
      if (receiver.track) {
        remote.addTrack(receiver.track);
      }
    });
    audioEl = document.createElement('audio');
    audioEl.autoplay = true;
    audioEl.srcObject = remote;
    /* Kept out of the shadow root: some browsers will not play audio from a detached or
     * hidden subtree, and this element is never meant to be seen anyway. */
    audioEl.style.display = 'none';
    document.body.appendChild(audioEl);
    /* Autoplay can still be refused if the visitor has not interacted enough; they have
     * pressed a button by now, so this is a fallback, not the normal path. */
    var played = audioEl.play();
    if (played && played.catch) {
      played.catch(function () {
        console.warn('[voice-widget] the browser blocked audio playback');
      });
    }
  }

  function endCall() {
    if (!session) {
      setState(STATE.ENDED);
      return;
    }
    try {
      if (session.state === 'Established') {
        session.bye();
      } else {
        session.cancel();
      }
    } catch (error) {
      console.warn('[voice-widget] could not end the call cleanly', error);
    }
    cleanUpCall();
    setState(STATE.ENDED);
  }

  function cleanUpCall() {
    if (audioEl) {
      audioEl.srcObject = null;
      if (audioEl.parentNode) {
        audioEl.parentNode.removeChild(audioEl);
      }
      audioEl = null;
    }
    if (ua) {
      /* Stopping the transport is what actually closes the WebSocket; without it a page
       * that is left open holds a connection to Asterisk for as long as it lives. */
      try {
        var stopping = ua.stop();
        if (stopping && stopping.catch) {
          stopping.catch(function () { /* already gone */ });
        }
      } catch (error) {
        console.warn('[voice-widget] could not close the transport', error);
      }
      ua = null;
    }
    session = null;
  }

  function fail(error) {
    errorText = (error && error.message) || 'Kutilmagan xatolik.';
    console.error('[voice-widget]', error);
    setState(STATE.ERROR);
  }

  /* ------------------------------------------------------------------------- view */

  function setState(next) {
    state = next;
    render();
  }

  function t(key, fallback) {
    var value = theme && theme[key];
    return value === null || value === undefined || value === '' ? fallback : value;
  }

  /*
   * A theme value on its way into the stylesheet. The theme is written by the company's
   * own admin, so this is not about a hostile actor — it is about a stray brace or
   * semicolon in a colour field silently breaking the widget's layout on a live site,
   * with nothing in the console to say why. Anything that is not plainly a colour or a
   * keyword falls back to the default.
   */
  function css(key, fallback) {
    var value = t(key, fallback);
    return /^[#a-zA-Z0-9(),.%\s-]*$/.test(String(value)) ? value : fallback;
  }

  /** A number a stylesheet can carry: theme sizes arrive as JSON and may be anything. */
  function px(key, fallback) {
    var value = parseInt(t(key, fallback), 10);
    return isNaN(value) || value < 0 ? fallback : value;
  }

  function styles() {
    var radius = px('borderRadius', 16);
    var width = px('panelWidth', 340);
    var position = t('position', 'bottom-right');
    var side = position.indexOf('left') >= 0 ? 'left' : 'right';
    var launcher = t('launcherSize', 'comfortable') === 'compact' ? 52 : 60;

    return [
      ':host{all:initial}',
      '*{box-sizing:border-box;font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}',
      '.wrap{position:fixed;bottom:24px;' + side + ':24px;z-index:2147483000;',
      '  display:flex;flex-direction:column;align-items:' + (side === 'left' ? 'flex-start' : 'flex-end') + ';gap:12px}',
      '.launcher{width:' + launcher + 'px;height:' + launcher + 'px;border-radius:50%;border:0;cursor:pointer;',
      '  background:' + css('primaryColor', '#002FA7') + ';color:' + css('buttonTextColor', '#FFFFFF') + ';',
      '  box-shadow:0 8px 24px rgba(0,0,0,.22);display:flex;align-items:center;justify-content:center;',
      '  transition:transform .15s ease}',
      '.launcher:hover{transform:scale(1.06)}',
      '.launcher svg{width:52%;height:52%;fill:currentColor}',
      '.panel{width:' + width + 'px;max-width:calc(100vw - 32px);border-radius:' + radius + 'px;overflow:hidden;',
      '  background:' + css('surfaceColor', '#FFFFFF') + ';color:' + css('textColor', '#111827') + ';',
      '  border:1px solid ' + css('borderColor', '#DADDE3') + ';box-shadow:0 18px 48px rgba(0,0,0,.20)}',
      '.head{display:flex;align-items:center;gap:12px;padding:16px;',
      '  background:' + css('primaryColor', '#002FA7') + ';color:' + css('buttonTextColor', '#FFFFFF') + '}',
      '.orb{width:40px;height:40px;border-radius:50%;flex:0 0 auto;background:radial-gradient(circle at 30% 30%,'
          + css('avatarOrbColor1', '#002FA7') + ',' + css('avatarOrbColor2', '#00F0FF') + ')}',
      '.orb.live{animation:pulse 1.6s ease-in-out infinite}',
      '@keyframes pulse{0%,100%{transform:scale(1);opacity:.95}50%{transform:scale(1.12);opacity:1}}',
      '.avatar{width:40px;height:40px;border-radius:50%;object-fit:cover;flex:0 0 auto}',
      '.title{font-size:15px;font-weight:600;line-height:1.2}',
      '.subtitle{font-size:12px;opacity:.85;margin-top:2px}',
      '.close{margin-inline-start:auto;background:transparent;border:0;color:inherit;cursor:pointer;',
      '  font-size:20px;line-height:1;padding:4px;opacity:.85}',
      '.close:hover{opacity:1}',
      '.body{padding:16px;display:flex;flex-direction:column;gap:12px}',
      '.welcome{font-size:14px;line-height:1.45}',
      '.consent{font-size:12px;line-height:1.5;color:' + css('mutedTextColor', '#667085') + '}',
      '.check{display:flex;gap:8px;align-items:flex-start;font-size:12px;line-height:1.5;cursor:pointer}',
      '.check input{margin-top:2px;flex:0 0 auto}',
      '.status{display:flex;align-items:center;gap:8px;font-size:13px;',
      '  color:' + css('mutedTextColor', '#667085') + '}',
      '.dot{width:8px;height:8px;border-radius:50%;background:' + css('accentColor', '#0F172A') + '}',
      '.dot.live{background:#12B76A;animation:pulse 1.4s ease-in-out infinite}',
      '.dot.error{background:#D92D20}',
      '.btn{border:0;border-radius:10px;padding:12px 16px;font-size:14px;font-weight:600;cursor:pointer;width:100%}',
      '.btn[disabled]{opacity:.5;cursor:not-allowed}',
      '.btn.start{background:' + css('primaryColor', '#002FA7') + ';color:' + css('buttonTextColor', '#FFFFFF') + '}',
      '.btn.end{background:#D92D20;color:#FFFFFF}',
      '.error{font-size:13px;line-height:1.45;color:#B42318}',
      '.brand{font-size:11px;text-align:center;padding:0 16px 14px;color:' + css('mutedTextColor', '#667085') + '}'
    ].join('');
  }

  var MIC_ICON = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 15a3 3 0 0 0 3-3V6a3 3 0 1 0-6 0v6a3 3 0 0 0 3 3z"/>'
      + '<path d="M19 11a1 1 0 1 0-2 0 5 5 0 0 1-10 0 1 1 0 1 0-2 0 7 7 0 0 0 6 6.92V21a1 1 0 1 0 2 0v-3.08A7 7 0 0 0 19 11z"/></svg>';

  function statusText() {
    switch (state) {
      case STATE.CONNECTING: return t('connectingText', 'Ulanmoqda');
      case STATE.LIVE: return t('listeningText', 'Tinglayapman');
      case STATE.ENDED: return t('endedText', "Qo‘ng‘iroq tugadi");
      default: return null;
    }
  }

  function render() {
    if (!config) {
      root.innerHTML = '';
      return;
    }

    var style = document.createElement('style');
    style.textContent = styles();

    var wrap = document.createElement('div');
    wrap.className = 'wrap';

    if (open) {
      wrap.appendChild(renderPanel());
    }
    wrap.appendChild(renderLauncher());

    root.innerHTML = '';
    root.appendChild(style);
    root.appendChild(wrap);
  }

  function renderLauncher() {
    var button = document.createElement('button');
    button.className = 'launcher';
    button.type = 'button';
    button.setAttribute('aria-label', t('actionText', 'Biz bilan gaplashing'));
    button.innerHTML = open ? '<svg viewBox="0 0 24 24" aria-hidden="true">'
        + '<path d="M18.3 5.71 12 12.01l-6.3-6.3-1.4 1.42 6.29 6.3-6.3 6.3 1.42 1.4 6.3-6.29 6.3 6.3 1.4-1.42-6.29-6.3 6.3-6.3z"/></svg>'
        : MIC_ICON;
    button.addEventListener('click', function () {
      open = !open;
      render();
    });
    return button;
  }

  function renderPanel() {
    var panel = document.createElement('div');
    panel.className = 'panel';
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-label', t('brandName', 'AI yordamchi'));

    panel.appendChild(renderHead());
    panel.appendChild(renderBody());

    if (!t('whiteLabel', false)) {
      var brand = document.createElement('div');
      brand.className = 'brand';
      brand.textContent = t('brandName', 'AI yordamchi');
      panel.appendChild(brand);
    }
    return panel;
  }

  function renderHead() {
    var head = document.createElement('div');
    head.className = 'head';

    if (t('showAvatar', true)) {
      var avatarUrl = t('avatarImageUrl', null);
      if (avatarUrl) {
        var img = document.createElement('img');
        img.className = 'avatar';
        img.src = avatarUrl;
        img.alt = '';
        head.appendChild(img);
      } else {
        var orb = document.createElement('div');
        orb.className = 'orb' + (state === STATE.LIVE ? ' live' : '');
        head.appendChild(orb);
      }
    }

    var titles = document.createElement('div');
    var title = document.createElement('div');
    title.className = 'title';
    title.textContent = t('actionText', 'Biz bilan gaplashing');
    titles.appendChild(title);

    if (config.agentName) {
      var subtitle = document.createElement('div');
      subtitle.className = 'subtitle';
      subtitle.textContent = config.agentName;
      titles.appendChild(subtitle);
    }
    head.appendChild(titles);

    var close = document.createElement('button');
    close.className = 'close';
    close.type = 'button';
    close.setAttribute('aria-label', 'Yopish');
    close.innerHTML = '&times;';
    close.addEventListener('click', function () {
      open = false;
      render();
    });
    head.appendChild(close);
    return head;
  }

  function renderBody() {
    var body = document.createElement('div');
    body.className = 'body';

    var status = statusText();
    if (status) {
      var line = document.createElement('div');
      line.className = 'status';
      var dot = document.createElement('span');
      dot.className = 'dot' + (state === STATE.LIVE ? ' live' : '');
      line.appendChild(dot);
      line.appendChild(document.createTextNode(status));
      body.appendChild(line);
    }

    if (state === STATE.ERROR) {
      var error = document.createElement('div');
      error.className = 'error';
      error.textContent = errorText;
      body.appendChild(error);
    }

    if (state === STATE.LIVE) {
      body.appendChild(button('end', t('endButtonText', 'Tugatish'), function () {
        endCall();
      }));
      return body;
    }

    if (state === STATE.CONNECTING) {
      var connecting = button('start', t('connectingText', 'Ulanmoqda') + '…', null);
      connecting.disabled = true;
      body.appendChild(connecting);
      return body;
    }

    var welcome = document.createElement('div');
    welcome.className = 'welcome';
    welcome.textContent = t('welcomeText', 'Savolingizni ovozli bering.');
    body.appendChild(welcome);

    /* Consent gates the button rather than appearing after it: recording starts the
     * moment the call connects, so agreeing afterwards would be agreeing to something
     * that already happened. */
    var consented = !config.consentRequired;
    var startButton = button('start', t('startButtonText', "Qo‘ng‘iroqni boshlash"), function () {
      startCall();
    });

    if (config.consentRequired) {
      var label = document.createElement('label');
      label.className = 'check';
      var box = document.createElement('input');
      box.type = 'checkbox';
      var text = document.createElement('span');
      text.className = 'consent';
      text.textContent = config.consentText || '';
      label.appendChild(box);
      label.appendChild(text);
      body.appendChild(label);

      startButton.disabled = true;
      box.addEventListener('change', function () {
        consented = box.checked;
        startButton.disabled = !consented;
      });
    }

    body.appendChild(startButton);
    return body;
  }

  function button(kind, label, onClick) {
    var element = document.createElement('button');
    element.className = 'btn ' + kind;
    element.type = 'button';
    element.textContent = label;
    if (onClick) {
      element.addEventListener('click', onClick);
    }
    return element;
  }

  /* ------------------------------------------------------------------------- boot */

  /* A page navigated away from mid-call leaves Asterisk holding a channel; BYE on unload
   * is what closes it while the tab still exists to send one. */
  window.addEventListener('pagehide', function () {
    if (session) {
      endCall();
    }
  });

  function boot() {
    loadConfig().then(function (data) {
      config = data;
      theme = data.theme || {};
      open = !!theme.defaultOpen;
      document.body.appendChild(host);
      render();
    }).catch(function (error) {
      /* A widget that is disabled, deleted, or embedded on an origin it was not issued
       * for renders nothing at all. The page keeps working; the console says why. */
      console.error('[voice-widget] could not load the widget configuration:', error.message);
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
