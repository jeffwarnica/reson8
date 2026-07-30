        const DEV_TIER_STORAGE_KEY = 'reson8-dev-tier';
        const DEV_TIER_HEADER = 'X-Reson8-Dev-Tier';
        const DEV_TIER_COOKIE = 'reson8-dev-tier';

        function readCookie(name) {
            const needle = name + '=';
            const parts = document.cookie ? document.cookie.split(';') : [];
            for (const part of parts) {
                const p = part.trim();
                if (p.startsWith(needle)) {
                    return decodeURIComponent(p.slice(needle.length));
                }
            }
            return '';
        }

        function setDevTierCookie(tier) {
            if (tier) {
                document.cookie = `${DEV_TIER_COOKIE}=${encodeURIComponent(tier)}; Path=/; SameSite=Lax`;
            } else {
                document.cookie = `${DEV_TIER_COOKIE}=; Path=/; Max-Age=0; SameSite=Lax`;
            }
        }

        function getDevTierSelection() {
            const fromSession = sessionStorage.getItem(DEV_TIER_STORAGE_KEY);
            if (fromSession) {
                return fromSession;
            }
            return readCookie(DEV_TIER_COOKIE);
        }

        function apiFetch(url, options) {
            const opts = options || {};
            const headers = new Headers(opts.headers || {});
            if (document.getElementById('reson8-dev-toolbar')) {
                const tier = getDevTierSelection();
                if (tier) {
                    headers.set(DEV_TIER_HEADER, tier);
                }
            }
            return fetch(url, { ...opts, headers });
        }

        let isStreaming = false;
        let syncTimer = null;

        const audio = document.getElementById('audioPlayer');
        const k8sCheck = document.getElementById('k8sActive');
        const startBtn = document.getElementById('startBtn');
        const stopBtn = document.getElementById('stopBtn');
        const status = document.getElementById('connectionStatus');
        let activeSliders = new Set(); // Tracks sliders the user is currently touching

        // --- STREAM LOGIC ---

        function audioStreamUrl() {
            return '/audio/stream?t=' + Date.now();
        }

        startBtn.onclick = () => {
            if (window.__reson8Cap && !window.__reson8Cap.canStream) {
                return;
            }
            status.innerText = "Status: Connecting...";
            audio.src = '';
            audio.load();
            audio.src = audioStreamUrl();
            audio.play().then(() => {
                isStreaming = true;
                startBtn.disabled = true;
                stopBtn.disabled = false;
                status.innerText = "Status: Streaming Live Assets...";
                status.style.color = "green";
            }).catch(e => {
                console.error("Error:", e);
                status.innerText = "Status: Error";
                status.style.color = "red";
            });
        };

        stopBtn.onclick = () => {
            isStreaming = false;
            audio.pause();
            audio.removeAttribute('src');
            audio.load();
            startBtn.disabled = false;
            stopBtn.disabled = true;
            if (window.__reson8Cap && !window.__reson8Cap.canStream) {
                startBtn.disabled = true;
            }
            status.innerText = "Status: Disconnected";
            status.style.color = "#666";
        };

        // --- CORE SYNC LOGIC ---

        async function syncState() {
            try {
                const res = await apiFetch('/audio/control/state');
                const state = await res.json();

                
                // We only update it if the user isn't clicking it (standard safety)
                k8sCheck.checked = state.k8sSyncActive;
                document.getElementById('k8sStatusText').innerText = state.k8sSyncActive ? "ACTIVE" : "PAUSED";
                document.getElementById('k8sStatusText').style.color = state.k8sSyncActive ? "var(--k8s)" : "var(--accent)"

                // 1. Update Global Master
                if (!activeSliders.has('serverMasterVol')) {
                    document.getElementById('serverMasterVol').value = state.masterVolume;
                    document.getElementById('serverMasterVolVal').innerText = state.masterVolume.toFixed(2);
                }

                // 2. Update Channels
                state.channels.forEach(ch => {
                    updateChannelUI(ch, state.k8sSyncActive);
                });
            } catch (e) { console.error("Sync failed", e); }
        }

        function updateChannelUI(ch, k8sActive) {
            const row = document.getElementById(`ch-${ch.name}`);
            if (!row) return;

            const currentPct = Math.min(100, Math.max(0, ch.currentIntensity ?? 0));

            const badge = row.querySelector('.current-intensity-badge');
            if (badge) badge.textContent = 'Live ' + Math.round(currentPct) + '%';

            const intensitySlider = row.querySelector('.intensity-slider');
            if (intensitySlider && !activeSliders.has(intensitySlider.id)) {
                const target = Math.min(100, Math.max(0, ch.targetIntensity ?? 0));
                intensitySlider.value = target;
                const tv = row.querySelector('.intensity-target-val');
                if (tv) tv.textContent = target.toFixed(1);
            }

            // Update Faders if not being touched - both chanVol and mixVol are 0-100
            const chFader = row.querySelector('.ch-fader');
            if (!activeSliders.has(chFader.id)) {
                const chanVolPercent = Math.min(100, Math.max(0, ch.chanVol));
                chFader.value = chanVolPercent;
                row.querySelector('.chFader-val').innerText = chanVolPercent.toFixed(2);
            }
            const mixFader = row.querySelector('.mix-fader');
            if (!activeSliders.has(mixFader.id)) {
                const mixerVolPercent = Math.min(100, Math.max(0, ch.mixerVol)); // mixVol is already 0-100
                mixFader.value = mixerVolPercent;
                row.querySelector('.mixFader-val').innerText = mixerVolPercent.toFixed(2);
            }

            // Update pipeline flow
            const pipEl = row.querySelector('.pipeline-flow');
            if (pipEl) {
                updatePipelineStyles(pipEl, k8sActive);
                updatePipelineValues(pipEl, ch, channelCurveData[ch.name]);
            }

            // Update curve live dots
            if (channelCurveData[ch.name]) {
                const cd = channelCurveData[ch.name];
                const rawApprox = (cd.curveSamples && cd.curveSamples.length >= 2)
                    ? curveInverse(cd.curveSamples, currentPct)
                    : currentPct;
                updateCurveDot(`ch-${ch.name}`, rawApprox, currentPct);
            }
            // VolumeScaler dot: use currentIntensity as x, cubic as y
            const gstVal = Math.pow(currentPct / 100, 3);
            updateCurveDot('vol', currentPct, gstVal);
        }

        // --- INTERACTION ---

        async function postControl(path, body) {
            if (!window.__reson8Cap || !window.__reson8Cap.canMutate) {
                return;
            }
            await apiFetch(path, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
        }

        async function patchTargetIntensity(channelName, targetIntensity) {
            if (!window.__reson8Cap || !window.__reson8Cap.canMutate) {
                return;
            }
            await apiFetch('/audio/control/channels/' + encodeURIComponent(channelName) + '/target-intensity', {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ targetIntensity })
            });
        }

        // Global K8s Toggle
        k8sCheck.onchange = async () => {
            if (!window.__reson8Cap || !window.__reson8Cap.canMutate) {
                return;
            }
            const active = k8sCheck.checked;
            document.getElementById('k8sStatusText').innerText = active ? "ACTIVE" : "PAUSED";
            await apiFetch(`/audio/control/k8s-sync/${active}`, { method: 'POST' });
        };

        // Local browser volume: slider is 0–100 (percent); HTMLMediaElement.volume is 0.0–1.0
        function applyLocalVolumeFromSlider() {
            const slider = document.getElementById('localVol');
            const volumePercent = parseFloat(slider.value);
            const clamped = Math.min(100, Math.max(0, volumePercent));
            if (clamped !== volumePercent) {
                slider.value = String(clamped);
            }
            audio.volume = clamped / 100;
            document.getElementById('localVolVal').innerText = clamped.toFixed(2);
        }

        document.getElementById('localVol').oninput = () => applyLocalVolumeFromSlider();
        applyLocalVolumeFromSlider();

        // --- SVG CURVE HELPERS ---

        const SVG_W = 230, SVG_H = 130, SVG_PAD = { t: 8, r: 8, b: 28, l: 36 };

        function makeSvgNS(tag, attrs) {
            const el = document.createElementNS('http://www.w3.org/2000/svg', tag);
            Object.entries(attrs).forEach(([k, v]) => el.setAttribute(k, v));
            return el;
        }

        function buildCurveSvg(id, title, samples, rawSamples, xLabel, yLabel, xMin, xMax, yMin, yMax) {
            const iw = SVG_W - SVG_PAD.l - SVG_PAD.r;
            const ih = SVG_H - SVG_PAD.t - SVG_PAD.b;

            // Expand y range to fit unclamped samples if they go outside [yMin, yMax]
            const rawYVals = (rawSamples || []).map(s => s[1]).filter(v => isFinite(v));
            const allYMin = rawYVals.length ? Math.min(yMin, ...rawYVals) : yMin;
            const allYMax = rawYVals.length ? Math.max(yMax, ...rawYVals) : yMax;
            const hasOscillation = rawSamples && (allYMin < yMin - 0.5 || allYMax > yMax + 0.5);

            const svg = makeSvgNS('svg', { width: SVG_W, height: SVG_H, class: 'curve-svg', 'aria-label': title });

            const toSvgX = x => SVG_PAD.l + ((x - xMin) / (xMax - xMin)) * iw;
            const toSvgY = y => SVG_PAD.t + ih - ((y - allYMin) / (allYMax - allYMin)) * ih;

            // Zero-reference line if the chart includes negative values
            if (allYMin < 0) {
                const y0 = toSvgY(0);
                const refLine = makeSvgNS('line', { x1: SVG_PAD.l, y1: y0, x2: SVG_PAD.l + iw, y2: y0, stroke: '#ef9a9a', 'stroke-width': '1', 'stroke-dasharray': '3,2' });
                svg.appendChild(refLine);
            }

            // Axes
            const axes = makeSvgNS('g', { stroke: '#90a4ae', 'stroke-width': '1' });
            axes.appendChild(makeSvgNS('line', { x1: SVG_PAD.l, y1: SVG_PAD.t, x2: SVG_PAD.l, y2: SVG_PAD.t + ih }));
            axes.appendChild(makeSvgNS('line', { x1: SVG_PAD.l, y1: SVG_PAD.t + ih, x2: SVG_PAD.l + iw, y2: SVG_PAD.t + ih }));
            svg.appendChild(axes);

            // Axis labels
            const xLbl = makeSvgNS('text', { x: SVG_PAD.l + iw / 2, y: SVG_H - 4, 'text-anchor': 'middle', 'font-size': '9', fill: '#78909c' });
            xLbl.textContent = xLabel;
            svg.appendChild(xLbl);
            const yLbl = makeSvgNS('text', { x: 9, y: SVG_PAD.t + ih / 2, 'text-anchor': 'middle', 'font-size': '9', fill: '#78909c', transform: `rotate(-90, 9, ${SVG_PAD.t + ih / 2})` });
            yLbl.textContent = yLabel;
            svg.appendChild(yLbl);

            // Tick labels — always show the clamped range boundary values
            const addTick = (x, y, txt, anchor) => {
                const t = makeSvgNS('text', { x, y, 'text-anchor': anchor, 'font-size': '8', fill: '#90a4ae' });
                t.textContent = txt;
                svg.appendChild(t);
            };
            addTick(SVG_PAD.l - 2, toSvgY(allYMin) + 4, fmt(allYMin), 'end');
            addTick(SVG_PAD.l - 2, toSvgY(allYMax) + 4, fmt(allYMax), 'end');
            if (allYMin < 0 && yMin === 0) {
                // extra tick at 0 (the clamping floor)
                addTick(SVG_PAD.l - 2, toSvgY(0) + 4, '0', 'end');
            }
            addTick(SVG_PAD.l, SVG_PAD.t + ih + 10, fmt(xMin), 'middle');
            addTick(SVG_PAD.l + iw, SVG_PAD.t + ih + 10, fmt(xMax), 'middle');

            // Raw (unclamped) spline — dashed grey, drawn first so clamped sits on top
            if (hasOscillation && rawSamples) {
                const rawPts = rawSamples.map(([x, y]) => `${toSvgX(x).toFixed(1)},${toSvgY(y).toFixed(1)}`).join(' ');
                const rawPoly = makeSvgNS('polyline', { points: rawPts, fill: 'none', stroke: '#b0bec5', 'stroke-width': '1.5', 'stroke-dasharray': '4,3', 'stroke-linejoin': 'round' });
                svg.appendChild(rawPoly);

                // Legend hint
                const warn = makeSvgNS('text', { x: SVG_PAD.l + 2, y: SVG_PAD.t + 10, 'font-size': '8', fill: '#e53935' });
                warn.textContent = '⚠ clamping active';
                svg.appendChild(warn);
            }

            // Effective (clamped) curve — solid blue
            const pts = samples.map(([x, y]) => `${toSvgX(x).toFixed(1)},${toSvgY(y).toFixed(1)}`).join(' ');
            const poly = makeSvgNS('polyline', { points: pts, fill: 'none', stroke: 'var(--k8s)', 'stroke-width': '2', 'stroke-linejoin': 'round' });
            svg.appendChild(poly);

            // Live dot (initially hidden, updated by syncState)
            const dot = makeSvgNS('circle', { id: `dot-${id}`, r: '5', fill: 'var(--accent)', stroke: 'white', 'stroke-width': '1.5', cx: '-20', cy: '-20' });
            svg.appendChild(dot);
            const dotLbl = makeSvgNS('text', { id: `dotlbl-${id}`, 'font-size': '9', fill: 'var(--accent)', 'font-weight': 'bold' });
            svg.appendChild(dotLbl);

            svg._toSvgX = toSvgX;
            svg._toSvgY = toSvgY;
            svg._xMin = xMin; svg._xMax = xMax;
            svg._yMin = allYMin; svg._yMax = allYMax;
            return svg;
        }

        function fmt(v) {
            if (Math.abs(v) >= 1000) return Math.round(v).toString();
            if (Math.abs(v) >= 10)   return v.toFixed(1);
            return v.toFixed(2);
        }

        function updateCurveDot(svgId, x, y) {
            const dot = document.getElementById(`dot-${svgId}`);
            const lbl = document.getElementById(`dotlbl-${svgId}`);
            const svg = dot && dot.closest('svg');
            if (!dot || !svg) return;
            const cx = svg._toSvgX(x);
            const cy = svg._toSvgY(y);
            dot.setAttribute('cx', cx.toFixed(1));
            dot.setAttribute('cy', cy.toFixed(1));
            lbl.setAttribute('x', (cx + 6).toFixed(1));
            lbl.setAttribute('y', (cy - 4).toFixed(1));
            lbl.textContent = fmt(y);
        }

        // Channel curve data keyed by channel name
        const channelCurveData = {};

        function buildChannelCurvePanel(ch) {
            if (!ch.curveSamples || ch.curveSamples.length < 2) return null;
            channelCurveData[ch.name] = ch;

            const yVals = ch.curveSamples.map(s => s[1]);
            const yMin = Math.min(...yVals);
            const yMax = Math.max(...yVals);

            const panel = document.createElement('div');
            panel.className = 'curve-panel';
            const title = document.createElement('h4');
            title.textContent = `k8s metric → intensity`;
            panel.appendChild(title);

            const svg = buildCurveSvg(
                `ch-${ch.name}`, title.textContent,
                ch.curveSamples, ch.curveRawSamples || null,
                `metric (${fmt(ch.curveMinInput)}–${fmt(ch.curveMaxInput)})`, 'intensity (0–100)',
                ch.curveMinInput, ch.curveMaxInput,
                yMin, Math.max(yMax, 0.001)
            );
            panel.appendChild(svg);
            return panel;
        }

        function buildVolumeCurvePanel() {
            const volPanel = document.createElement('div');
            volPanel.className = 'curve-panel';
            const volTitle = document.createElement('h4');
            volTitle.textContent = 'VolumeScaler — intensity (0–100) → GStreamer (0–1)';
            volPanel.appendChild(volTitle);
            const volSamples = [];
            for (let i = 0; i <= 40; i++) {
                const h = i * 2.5; // 0..100
                volSamples.push([h, Math.pow(h / 100, 3)]);
            }
            const volSvg = buildCurveSvg('vol', volTitle.textContent, volSamples, null, 'intensity (0–100)', 'GStreamer (0–1)', 0, 100, 0, 1);
            volPanel.appendChild(volSvg);
            return volPanel;
        }

        // --- PIPELINE RENDERING ---

        function buildPipelineHTML(ch) {
            const soundLabel = ch.soundName
                ? `${ch.soundName} · ${(ch.soundType || '').toLowerCase()}`
                : (ch.isDrop ? 'drop' : 'channel');

            const k8sBoxLabel = {
                PROMETHEUS: 'Prometheus',
                KUBERNETES_STATS: 'k8s Stats',
                KUBERNETES_EVENT: 'k8s Events'
            }[ch.sourceType] || 'Signal';

            // Target → Easing → Current → Ceiling are the input channel internals
            const inputChannelNodes = `
                <div class="pipe-node" data-seg="flow">
                    <span class="node-label">Target</span>
                    <span class="node-value" data-pipe-val="target">—</span>
                </div>
                <span class="pipe-arrow" data-seg="flow">▶</span>
                <div class="pipe-node" data-seg="flow">
                    <span class="node-label">Easing</span>
                    <span class="node-value" style="font-size:0.85em; color:inherit;">~ smooth</span>
                </div>
                <span class="pipe-arrow" data-seg="flow">▶</span>
                <div class="pipe-node" data-seg="flow">
                    <span class="node-label">Current</span>
                    <span class="node-value" data-pipe-val="current">—</span>
                </div>
                <span class="pipe-arrow" data-seg="flow">▶</span>
                <div class="pipe-node" data-seg="flow">
                    <span class="node-label">Ceiling</span>
                    <span class="node-value" data-pipe-val="ceiling">—</span>
                </div>`;

            // Fader → VolumeScaler x³ → GStreamer in a Mixer box
            const mixerOutputNodes = `
                <span class="pipe-arrow" data-seg="flow">▶</span>
                <div class="pipeline-input-group">
                    <span class="pipeline-input-group-label">Mixer</span>
                    <div class="pipe-node" data-seg="flow">
                        <span class="node-label">Fader</span>
                        <span class="node-value" data-pipe-val="fader">—</span>
                    </div>
                    <span class="pipe-arrow" data-seg="flow">▶</span>
                    <div class="pipe-node pipe-node-xform" data-seg="flow">
                        <span class="node-label">VolumeScaler</span>
                        <span class="node-value">x³</span>
                    </div>
                    <span class="pipe-arrow" data-seg="flow">▶</span>
                    <div class="pipe-node" data-seg="flow">
                        <span class="node-label">GStreamer</span>
                        <span class="node-value" data-pipe-val="gst">—</span>
                    </div>
                </div>`;

            if (ch.isDrop) {
                return `
                <div class="pipeline-flow" id="pipe-${ch.name}">
                    <div class="pipeline-input-group">
                        <span class="pipeline-input-group-label">${k8sBoxLabel}</span>
                        <div class="pipe-node pipe-node-drop" data-seg="drop">
                            <span class="node-label">Event</span>
                            <button class="btn-simulate-event" data-channel="${ch.name}"
                                title="Simulate a k8s event triggering this drop">▶ Simulate Event</button>
                        </div>
                    </div>
                    <span class="pipe-arrow" data-seg="flow">▶</span>
                    <div class="pipeline-input-group">
                        <span class="pipeline-input-group-label">${soundLabel}</span>
                        ${inputChannelNodes}
                    </div>
                    ${mixerOutputNodes}
                </div>`;
            }

            const curveMax = (ch.curveMaxInput != null && ch.curveMaxInput > 0) ? ch.curveMaxInput : 1000;
            return `
            <div class="pipeline-flow" id="pipe-${ch.name}">
                <div class="pipeline-input-group">
                    <span class="pipeline-input-group-label">${k8sBoxLabel}</span>
                    <div class="pipe-node" data-seg="k8s">
                        <span class="node-label">K8s Metric</span>
                        <span class="node-value" data-pipe-val="rawMetric">—</span>
                        <input type="number" class="metric-sim-input" data-channel="${ch.name}"
                            min="0" max="${curveMax}" step="any"
                            placeholder="sim value"
                            title="Enter a raw metric value to simulate (applies when k8s sync is paused)">
                    </div>
                    <span class="pipe-arrow" data-seg="k8s">▶</span>
                    <div class="pipe-node" data-seg="curvemap">
                        <span class="node-label">Curve Map</span>
                        <span class="node-value" style="font-size:0.85em; color:inherit;">→ intensity</span>
                    </div>
                </div>
                <span class="pipe-arrow" data-seg="k8s">▶</span>
                <div class="pipeline-input-group">
                    <span class="pipeline-input-group-label">${soundLabel}</span>
                    ${inputChannelNodes}
                </div>
                ${mixerOutputNodes}
            </div>`;
        }

        function updatePipelineStyles(pipEl, k8sActive) {
            pipEl.querySelectorAll('[data-seg]').forEach(el => {
                const seg = el.dataset.seg;
                el.classList.remove('active-k8s', 'active-flow');
                if (seg === 'k8s')       el.classList.toggle('active-k8s', k8sActive);
                else if (seg === 'flow') el.classList.add('active-flow');
                // 'curvemap' and 'drop' never change styling
            });
            // Show the metric sim input only when k8s sync is paused
            const simInput = pipEl.querySelector('.metric-sim-input');
            if (simInput) simInput.style.display = k8sActive ? 'none' : '';
        }

        function updatePipelineValues(pipEl, ch, curveData) {
            const set = (key, val) => {
                const el = pipEl.querySelector(`[data-pipe-val="${key}"]`);
                if (el) el.textContent = val;
            };
            // Raw metric: derive via inverse of currentIntensity if curve data available
            if (curveData && curveData.curveSamples && curveData.curveSamples.length >= 2) {
                const rawApprox = curveInverse(curveData.curveSamples, ch.currentIntensity);
                set('rawMetric', fmt(rawApprox));
            }
            set('target',  ch.targetIntensity.toFixed(1));
            set('current', ch.currentIntensity.toFixed(1));
            set('ceiling', ch.chanVol.toFixed(1));
            set('fader',   ch.mixerVol.toFixed(1));
            // GStreamer: apply cubic scaling to currentIntensity
            const gstVal = Math.pow(Math.min(100, Math.max(0, ch.currentIntensity)) / 100, 3);
            set('gst', gstVal.toFixed(3));
        }

        /** Approximate inverse lookup by finding the closest sample pair. */
        function curveInverse(samples, intensity) {
            let best = samples[0][0];
            let bestDist = Math.abs(samples[0][1] - intensity);
            for (const [x, y] of samples) {
                const d = Math.abs(y - intensity);
                if (d < bestDist) { bestDist = d; best = x; }
            }
            return best;
        }

        // --- INITIALIZATION ---

        async function initChannels() {
            const res = await apiFetch('/audio/control/channels');
            if (!res.ok) {
                console.error('channels load failed', res.status);
                return;
            }
            const channels = await res.json();
            const list = document.getElementById('chList');
            
            channels.forEach(ch => {
                const div = document.createElement('div');
                div.id = `ch-${ch.name}`;
                div.className = 'channel-strip';

                const sourceLabel = (ch.sourceType || ch.mapType || '').toString();
                const mapTypeDisplay = sourceLabel
                    ? `<span class="map-type" style="background: #e8f5e8; color: #2e7d32; padding: 2px 6px; border-radius: 3px; font-size: 0.7em; font-weight: bold;">${sourceLabel.replace(/_/g, ' ').toUpperCase()}</span>`
                    : '';
                const headerAction = ch.isDrop
                    ? `<span class="status-tag" style="background:#ede7f6;color:#673ab7">DROP EVENT</span>`
                    : `<span class="status-tag">CONTINUOUS</span>`;

                div.innerHTML = `
                <div class="channel-header">
                    <span class="channel-name">${ch.name.toUpperCase()}</span>
                    ${mapTypeDisplay}
                    ${headerAction}
                </div>
                
                ${buildPipelineHTML(ch)}

                <div class="channel-body">
                    <div class="channel-sliders">
                        <div class="slider-row">
                            <label>Signal intensity (target)<abbr class="info-hint" title="Desired signal level (0–100%). This is what you or k8s sync ask for; the sound engine may ramp smoothly toward it.">i</abbr></label>
                            <div class="intensity-slider-wrap">
                                <abbr class="current-intensity-badge" title="Current signal level (0–100%) the channel is using right now. It can differ from target while easing or when metrics update.">Live ${Math.round(Math.min(100, Math.max(0, ch.currentIntensity ?? 0)))}%</abbr>
                                <input type="range" class="intensity-slider" id="int-${ch.name}"
                                    min="0" max="100" step="0.01" value="${Math.min(100, Math.max(0, ch.targetIntensity ?? 0))}">
                            </div>
                            <span class="intensity-target-val">${(Math.min(100, Math.max(0, ch.targetIntensity ?? 0))).toFixed(1)}</span>
                        </div>

                        <div class="slider-row">
                            <label>Channel ceiling<abbr class="info-hint" title="Output ceiling (0–100) — caps this channel's contribution to the mix">i</abbr></label>
                            <input type="range" class="fader-slider ch-fader" id="chanFade-${ch.name}" 
                                min="0" max="100" step="0.01" value="${Math.min(100, Math.max(0, ch.chanVol))}">
                            <span class="chFader-val">${Math.min(100, Math.max(0, ch.chanVol)).toFixed(2)}</span>
                        </div>

                        <div class="slider-row">
                            <label>Mixer fader</label>
                            <input type="range" class="fader-slider mix-fader" id="mixerFade-${ch.name}" 
                                min="0" max="100" step="0.01" value="${Math.min(100, Math.max(0, ch.mixerVol))}">
                            <span class="mixFader-val">${ch.mixerVol.toFixed(2)}</span>
                        </div>
                    </div>
                </div>
                `;
                // Inline curve panel — placed alongside sliders in the channel-body flex row
                const curvePanel = buildChannelCurvePanel(ch);
                if (curvePanel) {
                    div.querySelector('.channel-body').appendChild(curvePanel);
                }

                list.appendChild(div);

                // Wire up pipeline initial state (k8s status unknown until first poll; default active)
                const pipEl = div.querySelector('.pipeline-flow');
                updatePipelineStyles(pipEl, true);
                updatePipelineValues(pipEl, ch, ch);

                // Drop channels: wire the Simulate Event button
                const simEventBtn = div.querySelector('.btn-simulate-event');
                if (simEventBtn) {
                    simEventBtn.onclick = () => playDrop(ch.name);
                }

                // Event Listeners for Sliders
                const chfader = div.querySelector('.ch-fader');
                chfader.onmousedown = () => activeSliders.add(chfader.id);
                chfader.onmouseup = () => activeSliders.delete(chfader.id);
                chfader.oninput = (e) => postControl('/audio/control/fader', { channel: ch.name, mixVol: parseFloat(div.querySelector('.mix-fader').value), chVol: parseFloat(e.target.value) });

                const mixfader = div.querySelector('.mix-fader');
                mixfader.onmousedown = () => activeSliders.add(mixfader.id);
                mixfader.onmouseup = () => activeSliders.delete(mixfader.id);
                mixfader.oninput = (e) => postControl('/audio/control/fader', { channel: ch.name, mixVol: parseFloat(e.target.value), chVol: parseFloat(div.querySelector('.ch-fader').value )});

                const intensity = div.querySelector('.intensity-slider');
                intensity.onmousedown = () => activeSliders.add(intensity.id);
                intensity.onmouseup = () => activeSliders.delete(intensity.id);
                intensity.onmouseleave = () => activeSliders.delete(intensity.id);
                intensity.oninput = (e) => {
                    const v = parseFloat(e.target.value);
                    div.querySelector('.intensity-target-val').textContent = v.toFixed(1);
                    patchTargetIntensity(ch.name, v);
                };

                // Metric sim input: only present on non-drop channels
                const simInput = div.querySelector('.metric-sim-input');
                if (simInput) {
                    simInput.style.display = 'none'; // hidden until k8s sync is paused
                    const fireSimulate = async () => {
                        if (!window.__reson8Cap || !window.__reson8Cap.canMutate) {
                            return;
                        }
                        const raw = parseFloat(simInput.value);
                        if (isNaN(raw)) return;
                        try {
                            const res = await apiFetch(
                                '/audio/control/channels/' + encodeURIComponent(ch.name) + '/simulate-metric',
                                { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ rawMetricValue: raw }) }
                            );
                            if (res.ok) {
                                const data = await res.json();
                                const pipEl = div.querySelector('.pipeline-flow');
                                const targetEl = pipEl && pipEl.querySelector('[data-pipe-val="target"]');
                                if (targetEl) targetEl.textContent = data.mappedIntensity.toFixed(1);
                            }
                        } catch (e) { console.error('simulate-metric failed', e); }
                    };
                    simInput.addEventListener('keydown', e => { if (e.key === 'Enter') fireSimulate(); });
                    simInput.addEventListener('change', fireSimulate);
                }
            });

            // VolumeScaler curve shown once after all channels
            list.appendChild(buildVolumeCurvePanel());
        }

        async function playDrop(name) {
            await postControl('/audio/drop', { drop: name });
        }

        async function loadCapabilities() {
            const res = await apiFetch('/api/capabilities');
            if (!res.ok) {
                throw new Error('capabilities HTTP ' + res.status);
            }
            return res.json();
        }

        function applyOperatorVisibility(cap) {
            const op = document.getElementById('operatorControlsCard');
            const chOuter = document.getElementById('channelsOuter');
            if (!cap.canViewControlState) {
                if (op) op.style.display = 'none';
                if (chOuter) chOuter.style.display = 'none';
            } else {
                if (op) op.style.display = '';
                if (chOuter) chOuter.style.display = '';
            }
        }

        function applyStreamControls(cap) {
            if (!cap.canStream) {
                startBtn.disabled = true;
                status.innerText = 'Status: Streaming not permitted for your access tier';
                status.style.color = '#c62828';
            } else {
                status.innerText = 'Status: Disconnected';
                status.style.color = '#666';
                startBtn.disabled = false;
            }
        }

        function applyLoginCard(cap) {
            const card = document.getElementById('loginStubCard');
            const hint = document.getElementById('loginHint');
            if (!card) {
                return;
            }
            if (cap.loginAvailable && (cap.tier === 'STREAM' || cap.tier === 'NONE')) {
                card.style.display = '';
                hint.textContent = cap.tier === 'NONE'
                    ? 'Sign in for full operator access.'
                    : 'Need more access? Sign in with an operator account.';
            } else {
                card.style.display = 'none';
            }
        }

        function setOperatorAndChannelInputsEnabled(enabled) {
            const op = document.getElementById('operatorControlsCard');
            if (op) {
                op.querySelectorAll('input, button').forEach(el => { el.disabled = !enabled; });
            }
            document.querySelectorAll('#channelsOuter input, #channelsOuter button').forEach(el => { el.disabled = !enabled; });
        }

        function applyCapabilities(cap) {
            window.__reson8Cap = cap;
            applySessionBar(cap);
            applyOperatorVisibility(cap);
            applyStreamControls(cap);
            applyLoginCard(cap);
            const roHint = document.getElementById('viewerReadonlyHint');
            if (roHint) {
                roHint.style.display = (cap.canViewControlState && !cap.canMutate) ? '' : 'none';
            }
        }

        function setupDevToolbar() {
            const toolbar = document.getElementById('reson8-dev-toolbar');
            const sel = document.getElementById('devTierSelect');
            if (!toolbar || !sel) {
                return;
            }
            const saved = getDevTierSelection();
            if (saved) {
                sel.value = saved;
                setDevTierCookie(saved);
            }
            sel.addEventListener('change', () => {
                const v = sel.value;
                if (v) {
                    sessionStorage.setItem(DEV_TIER_STORAGE_KEY, v);
                } else {
                    sessionStorage.removeItem(DEV_TIER_STORAGE_KEY);
                }
                setDevTierCookie(v);
                window.location.reload();
            });
        }

        function setupLoginStub() {
            const btn = document.getElementById('loginBtn');
            if (!btn) {
                return;
            }
            btn.addEventListener('click', () => {
                // Same-origin path only — avoids malformed URLs when base/context or caching breaks relative resolution.
                window.location.assign(new URL('/login', window.location.href).href);
            });
        }

        function setupLogoutButton() {
            const btn = document.getElementById('logoutBtn');
            if (!btn) {
                return;
            }
            btn.addEventListener('click', () => {
                // q_session is HttpOnly — cannot be deleted from JS. The server-side /logout handler
                // clears the cookie and redirects to / (configured via quarkus.oidc.logout.*).
                window.location.assign(new URL('/logout', window.location.href).href);
            });
        }

        function applySessionBar(cap) {
            const bar = document.getElementById('sessionBar');
            const badge = document.getElementById('sessionTierBadge');
            if (!bar) {
                return;
            }
            const authenticated = cap.tier === 'ADMIN' || cap.tier === 'VIEWER';
            bar.style.display = authenticated ? '' : 'none';
            if (badge) {
                badge.textContent = authenticated ? cap.tier.charAt(0) + cap.tier.slice(1).toLowerCase() + ' access' : '';
            }
        }

        async function bootstrap() {
            setupDevToolbar();
            setupLoginStub();
            setupLogoutButton();
            const cap = await loadCapabilities();
            applyCapabilities(cap);
            if (syncTimer) {
                clearInterval(syncTimer);
                syncTimer = null;
            }
            if (cap.canViewControlState) {
                document.getElementById('chList').innerHTML = '';
                await initChannels();
                setOperatorAndChannelInputsEnabled(!!cap.canMutate);
                syncTimer = setInterval(syncState, 500);
            }
        }

        bootstrap().catch(err => console.error('bootstrap failed', err));
