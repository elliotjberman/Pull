(() => {
    "use strict";

    const SVG_NS = "http://www.w3.org/2000/svg";
    const XLINK_NS = "http://www.w3.org/1999/xlink";
    const controlsRoot = document.querySelector("#surface-controls");
    const status = document.querySelector("#event-status");
    const displayImage = document.querySelector("#display-image");
    const displayPlaceholder = document.querySelector("#display-placeholder");
    const pressureSlider = document.querySelector("#pressure-slider");
    const pressureValue = document.querySelector("#pressure-value");
    const pressureTarget = document.querySelector("#pressure-target");
    const activePointers = new Map();
    const activeInjectedEdges = new Map();
    const touchedControls = new Set();
    const liveState = {
        pressed: new Set(),
        revision: -1,
        displayRevision: -1,
        eventSequence: -1,
        inputSession: "",
        inputStatusRequest: "",
        lastInputAt: 0,
        displayGeneration: 0,
        displayLoadActive: false,
        displayObjectUrl: "",
        pendingDisplay: null,
        stateFetchActive: false,
        stateFetchPending: false
    };
    let inputRequestChain = Promise.resolve();
    let selectedPressurePad = 1;
    let pressureTimer = null;
    let pendingPressure = 0;

    const grid = Object.freeze({
        x: 33.25,
        y: 59.25,
        columns: 8,
        rows: 8,
        cellWidth: 12.75,
        cellHeight: 10,
        columnGap: 2.75,
        rowGap: 1.75
    });
    const gridColumnX = column => grid.x + column * (grid.cellWidth + grid.columnGap);
    const gridRowY = row => grid.y + row * (grid.cellHeight + grid.rowGap);

    const buttons = [
        ["PLAY", "Play", 4.75, 142.75, 10, 8.5],
        ["RECORD", "Record", 4.75, 132.25, 10, 8.5],
        ["NEW", "New", 4.75, 121.75, 10, 8.5],
        ["DUPLICATE", "Duplicate", 4.75, 111.25, 10, 8.5],
        ["AUTOMATION", "Automate", 4.75, 100.75, 10, 8.5],
        ["FIXED_LENGTH", "Fixed length", 4.75, 90.25, 10, 8.5],
        ["QUANTIZE", "Quantize", 4.75, 79.75, 10, 8.5],
        ["DOUBLE", "Double loop", 4.75, 69.25, 10, 8.5],
        ["CONVERT", "Convert", 4.75, 58.75, 10, 8.5],
        ["UNDO", "Undo", 4.5, 39.5, 10, 10],
        ["DELETE", "Delete", 4.5, 28.25, 10, 10],
        ["TAP_TEMPO", "Tap tempo", 4.5, 20.25, 11.25, 5.5],
        ["METRONOME", "Metronome", 17.5, 20.25, 11.25, 5.5],
        ["MUTE", "Mute", 4.5, 51, 8.25, 5.5],
        ["SOLO", "Solo", 12.5, 51, 8.25, 5.5],
        ["STOP_CLIP", "Stop clip", 21, 51, 8.25, 5.5],

        ["SETUP", "Setup", 173.5, 20.5, 10, 6.25],
        ["USER", "User", 185.5, 20.5, 10, 6.25],
        ["ADD_EFFECT", "Add device", 159.75, 30, 10, 8.75],
        ["DEVICE", "Device", 173.5, 30, 10, 8.75],
        ["TRACK", "Mix", 185.5, 30, 10, 8.75],
        ["ADD_TRACK", "Add track", 159.75, 40.75, 10, 8.75],
        ["BROWSE", "Browse", 173.5, 40.75, 10, 8.75],
        ["CLIP", "Clip", 185.5, 40.75, 10, 8.75],
        ["MASTERTRACK", "Master", 159.75, 51.5, 10, 5],
        ["REPEAT", "Repeat", 173.5, 93.75, 10, 6.25],
        ["ACCENT", "Accent", 185.5, 93.75, 10, 6.25],
        ["SCALES", "Scale", 173.5, 105.75, 10, 6.25],
        ["LAYOUT", "Layout", 185.5, 105.75, 10, 6.25],
        ["NOTE", "Note", 173.5, 113.75, 10, 6.25],
        ["SESSION", "Session", 185.5, 113.75, 10, 6.25],
        ["SHIFT", "Shift", 173.5, 145, 10, 6],
        ["SELECT", "Select", 185.5, 145, 10, 6]
    ];

    const directionalClusters = [
        {
            x: 173.5,
            y: 51.5,
            size: 22,
            icon: "chevron",
            controls: {
                up: ["ARROW_UP", "Arrow up"],
                right: ["ARROW_RIGHT", "Arrow right"],
                down: ["ARROW_DOWN", "Arrow down"],
                left: ["ARROW_LEFT", "Arrow left"]
            }
        },
        {
            x: 173.5,
            y: 122,
            size: 22,
            icon: "triangle",
            controls: {
                up: ["OCTAVE_UP", "Octave up"],
                right: ["PAGE_RIGHT", "Page right"],
                down: ["OCTAVE_DOWN", "Octave down"],
                left: ["PAGE_LEFT", "Page left"]
            }
        }
    ];

    const buttonRows = [
        {prefix: "ROW2_", y: 20.25, label: "Upper"},
        {prefix: "ROW1_", y: 51.25, label: "Lower"}
    ];

    const encoders = [
        ["TEMPO", "Tempo", 4, 5.75, 10, 10],
        ["PLAY_POSITION", "Swing", 17.75, 5.75, 10, 10],
        ...Array.from({length: 8}, (_, index) => [
            `KNOB${index + 1}`,
            `${index + 1}`,
            gridColumnX(index) + (grid.cellWidth - 10) / 2,
            5.75,
            10,
            10
        ]),
        ["MASTER_KNOB", "Master", 179.5, 5.75, 10, 10]
    ];

    function svgElement(name, attributes = {}) {
        const element = document.createElementNS(SVG_NS, name);
        for (const [key, value] of Object.entries(attributes))
            element.setAttribute(key, String(value));
        return element;
    }

    function buttonAddress(symbolicName) {
        return `push.button.${symbolicName.toLowerCase().replaceAll("_", "-")}`;
    }

    function continuousAddress(symbolicName) {
        return `push.continuous.${symbolicName.toLowerCase().replaceAll("_", "-")}`;
    }

    function registerControl(group, address, debugName, label, inputKind) {
        group.classList.add("control");
        group.dataset.controlId = address;
        group.dataset.debugName = debugName;
        group.dataset.label = label;
        group.dataset.inputKind = inputKind;
        group.dataset.lit = "false";
        group.setAttribute("role", "button");
        group.setAttribute("tabindex", "0");
        group.setAttribute("aria-label", `${label} (${address})`);

        if (inputKind === "BUTTON" || inputKind === "PAD") {
            group.addEventListener("pointerdown", event => {
                event.preventDefault();
                if (inputKind === "PAD")
                    selectPressurePad(address);
                group.setPointerCapture(event.pointerId);
                activePointers.set(event.pointerId, {address, inputKind});
                beginEdge(address, inputKind);
            });
            group.addEventListener("pointerup", event => releasePointer(event.pointerId));
            group.addEventListener("pointercancel", event => releasePointer(event.pointerId));
            group.addEventListener("keydown", event => {
                if ((event.key === " " || event.key === "Enter") && !event.repeat) {
                    event.preventDefault();
                    if (inputKind === "PAD")
                        selectPressurePad(address);
                    beginEdge(address, inputKind);
                }
            });
            group.addEventListener("keyup", event => {
                if (event.key === " " || event.key === "Enter") {
                    event.preventDefault();
                    endEdge(address, inputKind);
                }
            });
        }
        else if (inputKind === "TOUCH") {
            group.addEventListener("pointerenter", () => setTouched(address, true));
            group.addEventListener("pointerleave", () => setTouched(address, false));
        }
        controlsRoot.append(group);
    }

    function releasePointer(pointerId) {
        const active = activePointers.get(pointerId);
        if (!active)
            return;
        activePointers.delete(pointerId);
        endEdge(active.address, active.inputKind);
    }

    function beginEdge(address, inputKind) {
        if (activeInjectedEdges.has(address))
            return;
        activeInjectedEdges.set(address, inputKind);
        setPressed(address, true, "DOWN");
        queueDebugInput(address, inputKind, "BEGIN", 127);
    }

    function endEdge(address, inputKind) {
        if (!activeInjectedEdges.has(address))
            return;
        activeInjectedEdges.delete(address);
        setPressed(address, false, "UP");
        queueDebugInput(address, inputKind, "END", 0);
    }

    function setTouched(address, touched) {
        if (touched === touchedControls.has(address))
            return;
        const control = findControl(address);
        if (!control)
            return;
        control.classList.toggle("is-touched", touched);
        if (touched)
            touchedControls.add(address);
        else
            touchedControls.delete(address);
        status.textContent = `${control.dataset.debugName} · TOUCH ${touched ? "BEGIN" : "END"} · ${address}`;
        liveState.lastInputAt = performance.now();
        queueDebugInput(address, "TOUCH", touched ? "BEGIN" : "END", touched ? 127 : 0);
    }

    function createButton([symbolicName, label, x, y, width, height]) {
        const group = svgElement("g", {class: "button"});
        const face = svgElement("rect", {
            class: "control-face",
            x,
            y,
            width,
            height
        });
        group.append(face);
        if (symbolicName === "RECORD")
            group.append(svgElement("circle", {
                class: "transport-icon record-icon",
                cx: x + width / 2,
                cy: y + height / 2,
                r: Math.min(width, height) * 0.2
            }));
        else if (symbolicName === "PLAY")
        {
            const centerX = x + width / 2 + 0.2;
            const centerY = y + height / 2;
            const radius = Math.min(width, height) * 0.27;
            group.append(svgElement("path", {
                class: "transport-icon play-icon",
                d: `M ${centerX - radius * 0.75} ${centerY - radius} L ${centerX + radius} ${centerY} L ${centerX - radius * 0.75} ${centerY + radius} Z`
            }));
        }
        else
        {
            const text = svgElement("text", {
                class: `button-label ${label.length > 9 ? "micro" : label.length > 6 ? "small" : ""}`,
                x: x + width / 2,
                y: y + height / 2 + 0.55
            });
            text.textContent = label;
            group.append(text);
        }
        registerControl(group, buttonAddress(symbolicName), symbolicName, label, "BUTTON");
    }

    function createDirectionalCluster({x, y, size, icon, controls}) {
        const centerX = x + size / 2;
        const centerY = y + size / 2;
        const faces = {
            up: `M ${x} ${y} L ${x + size} ${y} L ${centerX} ${centerY} Z`,
            right: `M ${x + size} ${y} L ${x + size} ${y + size} L ${centerX} ${centerY} Z`,
            down: `M ${x + size} ${y + size} L ${x} ${y + size} L ${centerX} ${centerY} Z`,
            left: `M ${x} ${y + size} L ${x} ${y} L ${centerX} ${centerY} Z`
        };
        const vectors = {
            up: [0, -1],
            right: [1, 0],
            down: [0, 1],
            left: [-1, 0]
        };
        for (const direction of ["up", "right", "down", "left"]) {
            const [symbolicName, label] = controls[direction];
            const group = svgElement("g", {class: "direction-pad button"});
            group.append(svgElement("path", {class: "control-face", d: faces[direction]}));
            const [dx, dy] = vectors[direction];
            const iconX = centerX + dx * size * 0.28;
            const iconY = centerY + dy * size * 0.28;
            const radius = size * 0.075;
            if (icon === "chevron")
                group.append(svgElement("path", {
                    class: "direction-icon direction-chevron",
                    d: chevronPath(direction, iconX, iconY, radius)
                }));
            else
                group.append(svgElement("path", {
                    class: "direction-icon direction-triangle",
                    d: trianglePath(direction, iconX, iconY, radius)
                }));
            registerControl(group, buttonAddress(symbolicName), symbolicName, label, "BUTTON");
        }
    }

    function chevronPath(direction, x, y, radius) {
        if (direction === "up")
            return `M ${x - radius} ${y + radius * 0.55} L ${x} ${y - radius * 0.55} L ${x + radius} ${y + radius * 0.55}`;
        if (direction === "right")
            return `M ${x - radius * 0.55} ${y - radius} L ${x + radius * 0.55} ${y} L ${x - radius * 0.55} ${y + radius}`;
        if (direction === "down")
            return `M ${x - radius} ${y - radius * 0.55} L ${x} ${y + radius * 0.55} L ${x + radius} ${y - radius * 0.55}`;
        return `M ${x + radius * 0.55} ${y - radius} L ${x - radius * 0.55} ${y} L ${x + radius * 0.55} ${y + radius}`;
    }

    function trianglePath(direction, x, y, radius) {
        if (direction === "up")
            return `M ${x} ${y - radius} L ${x + radius} ${y + radius * 0.72} L ${x - radius} ${y + radius * 0.72} Z`;
        if (direction === "right")
            return `M ${x + radius} ${y} L ${x - radius * 0.72} ${y + radius} L ${x - radius * 0.72} ${y - radius} Z`;
        if (direction === "down")
            return `M ${x} ${y + radius} L ${x + radius} ${y - radius * 0.72} L ${x - radius} ${y - radius * 0.72} Z`;
        return `M ${x - radius} ${y} L ${x + radius * 0.72} ${y + radius} L ${x + radius * 0.72} ${y - radius} Z`;
    }

    function createButtonRows() {
        for (const row of buttonRows) {
            Array.from({length: grid.columns}, (_, index) => gridColumnX(index)).forEach((x, index) => createButton([
                `${row.prefix}${index + 1}`,
                `${index + 1}`,
                x,
                row.y,
                grid.cellWidth,
                5.5
            ]));
        }
    }

    function createScenes() {
        Array.from({length: grid.rows}, (_, index) => gridRowY(index)).forEach((position, index) => createButton([
            `SCENE${index + 1}`,
            `Scene ${index + 1}`,
            159.75,
            position,
            10,
            10
        ]));
    }

    function createPads() {
        for (let row = 0; row < 8; row++) {
            for (let column = 0; column < 8; column++) {
                const index = row * 8 + column + 1;
                const group = svgElement("g", {class: "pad"});
                const face = svgElement("rect", {
                    class: "control-face",
                    x: gridColumnX(column),
                    y: gridRowY(grid.rows - row - 1),
                    width: grid.cellWidth,
                    height: grid.cellHeight
                });
                const number = svgElement("text", {
                    class: "pad-number",
                    x: gridColumnX(column) + grid.cellWidth / 2,
                    y: gridRowY(grid.rows - row - 1) + grid.cellHeight / 2 + 0.45
                });
                number.textContent = String(index);
                group.append(face, number);
                registerControl(group, `push.pad.${index}`, `PAD${index}`, `Pad ${index}`, "PAD");
            }
        }
    }

    function createEncoder([symbolicName, label, x, y, width, height]) {
        const group = svgElement("g", {class: "encoder"});
        const centerX = x + width / 2;
        const centerY = y + height / 2;
        group.append(
            svgElement("circle", {class: "encoder-ring", cx: centerX, cy: centerY, r: 4.55}),
            svgElement("circle", {class: "control-face", cx: centerX, cy: centerY, r: 3.82}),
            svgElement("line", {
                class: "encoder-tick",
                x1: centerX,
                y1: centerY - 2.65,
                x2: centerX,
                y2: centerY - 3.55
            })
        );
        const text = svgElement("text", {
            class: "encoder-label",
            x: centerX,
            y: y + height + 2.2
        });
        text.textContent = label;
        group.append(text);
        registerControl(group, continuousAddress(symbolicName), symbolicName, label, "TOUCH");
    }

    function createTouchStrip() {
        const group = svgElement("g", {class: "touchstrip"});
        const x = 17.75;
        const y = grid.y;
        const width = 12;
        const height = grid.rows * grid.cellHeight + (grid.rows - 1) * grid.rowGap;
        group.append(
            svgElement("rect", {class: "touch-track", x, y, width, height}),
            svgElement("rect", {class: "touch-fill", x: x + 1.2, y: y + height / 2, width: width - 2.4, height: height / 2 - 1.2}),
            svgElement("rect", {class: "touch-position", x: x + 1.2, y: y + height / 2 - 0.6, width: width - 2.4, height: 1.2})
        );
        const text = svgElement("text", {class: "touch-label", x: x + width / 2 - 0.4, y: y + 10});
        text.textContent = "TOUCH STRIP";
        group.append(text);
        registerControl(group, continuousAddress("TOUCHSTRIP"), "TOUCHSTRIP", "Touch strip", "TOUCH");
    }

    function resolveAddress(reference) {
        if (typeof reference !== "string")
            return "";
        if (reference.startsWith("push."))
            return reference.toLowerCase();
        if (/^PAD([1-9]|[1-5][0-9]|6[0-4])$/.test(reference))
            return `push.pad.${reference.slice(3)}`;
        if (/^(KNOB[1-8]|TEMPO|PLAY_POSITION|MASTER_KNOB|TOUCHSTRIP)$/.test(reference))
            return continuousAddress(reference);
        return buttonAddress(reference);
    }

    function findControl(reference) {
        const address = resolveAddress(reference);
        return [...controlsRoot.querySelectorAll(".control")].find(control => control.dataset.controlId === address);
    }

    function queueDebugInput(reference, kind, phase, value) {
        const control = findControl(reference);
        const address = control?.dataset.controlId ?? resolveAddress(reference);
        const run = async () => {
            if (!liveState.inputSession) {
                status.textContent = location.protocol === "file:"
                    ? "Static preview · run tools/push-debug-surface for input"
                    : "Waiting for Bitwig debug input";
                return false;
            }
            try {
                const response = await fetch("/api/input", {
                    method: "POST",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({
                        session: liveState.inputSession,
                        control: address,
                        kind,
                        phase,
                        value
                    })
                });
                const result = await response.json().catch(() => ({}));
                if (!response.ok) {
                    status.textContent = `Input rejected · ${result.error ?? response.status}`;
                    liveState.lastInputAt = performance.now();
                    return false;
                }
                return true;
            }
            catch (_error) {
                status.textContent = "Waiting for local debug input bridge";
                liveState.lastInputAt = performance.now();
                return false;
            }
        };
        const request = inputRequestChain.then(run);
        inputRequestChain = request.then(() => undefined, () => undefined);
        return request;
    }

    function selectPressurePad(reference) {
        const address = resolveAddress(reference);
        const match = /^push\.pad\.([1-9]|[1-5][0-9]|6[0-4])$/.exec(address);
        if (!match)
            return false;
        const nextPad = Number(match[1]);
        if (nextPad !== selectedPressurePad) {
            if (pendingPressure !== 0)
                queueDebugInput(`PAD${selectedPressurePad}`, "POLY_PRESSURE", "CHANGE", 0);
            selectedPressurePad = nextPad;
            pendingPressure = 0;
            pressureSlider.value = "0";
            pressureValue.value = "0";
            pressureValue.textContent = "0";
        }
        pressureTarget.textContent = `Pad ${selectedPressurePad}`;
        controlsRoot.querySelectorAll(".pad").forEach(pad => pad.classList.toggle(
            "is-pressure-target",
            pad.dataset.controlId === `push.pad.${selectedPressurePad}`));
        return true;
    }

    function schedulePressure(value, immediate = false) {
        pendingPressure = Math.max(0, Math.min(127, Number(value)));
        pressureValue.value = String(pendingPressure);
        pressureValue.textContent = String(pendingPressure);
        if (pressureTimer !== null) {
            if (!immediate)
                return;
            window.clearTimeout(pressureTimer);
            pressureTimer = null;
        }
        const send = () => {
            pressureTimer = null;
            queueDebugInput(`PAD${selectedPressurePad}`, "POLY_PRESSURE", "CHANGE", pendingPressure);
        };
        if (immediate)
            send();
        else
            pressureTimer = window.setTimeout(send, 40);
    }

    function releaseBrowserInputs() {
        for (const [address, kind] of [...activeInjectedEdges])
            endEdge(address, kind);
        for (const address of [...touchedControls])
            setTouched(address, false);
    }

    function setPressed(reference, pressed, phase = pressed ? "DOWN" : "UP", announce = true) {
        const control = findControl(reference);
        if (!control)
            return false;
        control.classList.toggle("is-pressed", pressed);
        control.setAttribute("aria-pressed", String(pressed));
        if (announce)
        {
            status.textContent = `${control.dataset.debugName} · ${phase} · ${control.dataset.controlId}`;
            window.dispatchEvent(new CustomEvent("push-surface-input", {
                detail: {
                    controlId: control.dataset.controlId,
                    debugName: control.dataset.debugName,
                    phase,
                    pressed
                }
            }));
        }
        return true;
    }

    function setLight(reference, color, options = {}) {
        const control = findControl(reference);
        if (!control)
            return false;
        const lit = Boolean(color) && color !== "transparent";
        control.dataset.lit = String(lit);
        control.dataset.blink = options.blinkColor ? options.fast ? "fast" : "slow" : "none";
        if (lit)
            control.style.setProperty("--light", color);
        else
            control.style.removeProperty("--light");
        if (options.blinkColor)
            control.style.setProperty("--blink-light", options.blinkColor);
        else
            control.style.removeProperty("--blink-light");
        return true;
    }

    function setDisplay(source) {
        liveState.displayGeneration += 1;
        liveState.pendingDisplay = null;
        if (!source) {
            displayImage.removeAttribute("href");
            displayImage.removeAttributeNS(XLINK_NS, "href");
            displayPlaceholder.style.display = "";
            if (liveState.displayObjectUrl) {
                URL.revokeObjectURL(liveState.displayObjectUrl);
                liveState.displayObjectUrl = "";
            }
            return;
        }
        displayPlaceholder.style.display = "none";
        displayImage.setAttribute("href", source);
    }

    function queueLiveDisplay(source, revision) {
        liveState.pendingDisplay = {source, revision, generation: liveState.displayGeneration};
        if (!liveState.displayLoadActive)
            void loadNewestDisplay();
    }

    async function loadNewestDisplay() {
        const requested = liveState.pendingDisplay;
        if (!requested)
            return;
        liveState.pendingDisplay = null;
        liveState.displayLoadActive = true;
        let objectUrl = "";
        try {
            if (requested.generation !== liveState.displayGeneration)
                return;
            const response = await fetch(requested.source, {cache: "no-store"});
            if (!response.ok)
                throw new Error(`display returned ${response.status}`);
            objectUrl = URL.createObjectURL(await response.blob());
            if (requested.generation !== liveState.displayGeneration)
                return;
            await new Promise((resolve, reject) => {
                const loaded = () => {
                    displayImage.removeEventListener("error", failed);
                    resolve();
                };
                const failed = () => {
                    displayImage.removeEventListener("load", loaded);
                    reject(new Error("display image could not be decoded"));
                };
                displayImage.addEventListener("load", loaded, {once: true});
                displayImage.addEventListener("error", failed, {once: true});
                displayImage.setAttribute("href", objectUrl);
            });
            const previous = liveState.displayObjectUrl;
            liveState.displayObjectUrl = objectUrl;
            objectUrl = "";
            displayImage.dataset.revision = String(requested.revision);
            displayPlaceholder.style.display = "none";
            if (previous)
                URL.revokeObjectURL(previous);
        }
        catch (_error) {
            // A later event will retry from the newest atomic frame.
        }
        finally {
            if (objectUrl)
                URL.revokeObjectURL(objectUrl);
            liveState.displayLoadActive = false;
            if (liveState.pendingDisplay)
                void loadNewestDisplay();
        }
    }

    function reset() {
        controlsRoot.querySelectorAll(".control").forEach(control => {
            control.classList.remove("is-pressed");
            control.dataset.lit = "false";
            control.dataset.blink = "none";
            control.style.removeProperty("--light");
            control.style.removeProperty("--blink-light");
            control.setAttribute("aria-pressed", "false");
        });
        setDisplay(null);
        status.textContent = "Preview · ready";
    }

    function showDemo() {
        reset();
        const colors = ["#42e98f", "#40d9ff", "#4f78ff", "#b865ff", "#ff5c8d", "#ff724d", "#ffc642", "#a8eb4b"];
        for (let row = 0; row < 8; row++) {
            for (let column = 0; column < 8; column++) {
                if ((row + column) % 3 !== 1)
                    setLight(`PAD${row * 8 + column + 1}`, colors[column]);
            }
        }
        setLight("PLAY", "#42e98f");
        setLight("RECORD", "#ff4d5f");
        setLight("NOTE", "#4f78ff");
        setLight("ROW1_5", "#b865ff");
        status.textContent = "Preview · demo output";
    }

    function cssColor(rgb) {
        return typeof rgb === "string" && /^[0-9A-Fa-f]{6}$/.test(rgb) ? `#${rgb}` : null;
    }

    function applyDebugState(state) {
        if (!state || typeof state !== "object")
            return;

        const input = state.input && typeof state.input === "object" ? state.input : {};
        liveState.inputSession = input.connected && typeof input.session === "string" ? input.session : "";
        const inputStatus = input.status && typeof input.status === "object" ? input.status : null;
        if (inputStatus?.requestId && inputStatus.requestId !== liveState.inputStatusRequest) {
            liveState.inputStatusRequest = inputStatus.requestId;
            if (inputStatus.state === "FAILED" || inputStatus.state === "RELEASED") {
                status.textContent = `Input ${inputStatus.state.toLowerCase()} · ${inputStatus.message || inputStatus.control}`;
                liveState.lastInputAt = performance.now();
            }
        }

        const lights = state.lights && typeof state.lights === "object" ? state.lights : {};
        controlsRoot.querySelectorAll(".control").forEach(control => {
            const light = lights[control.dataset.controlId];
            setLight(control.dataset.controlId, light ? cssColor(light.rgb) : null, light ? {
                blinkColor: cssColor(light.blinkRgb),
                fast: Boolean(light.fast)
            } : {});
        });

        const pressed = new Set(Array.isArray(state.pressed) ? state.pressed : []);
        for (const controlId of new Set([...liveState.pressed, ...pressed])) {
            const wasPressed = liveState.pressed.has(controlId);
            const isPressed = pressed.has(controlId);
            setPressed(controlId, isPressed, isPressed ? "DOWN" : "UP", false);
            if (wasPressed !== isPressed) {
                const control = findControl(controlId);
                if (control) {
                    status.textContent = `Live · ${control.dataset.debugName} · ${isPressed ? "DOWN" : "UP"}`;
                    liveState.lastInputAt = performance.now();
                }
            }
        }
        liveState.pressed = pressed;

        const events = Array.isArray(state.events) ? state.events : [];
        const newestEventSequence = events.reduce((newest, event) => Math.max(newest, Number(event.sequence ?? -1)), -1);
        if (liveState.eventSequence < 0)
            liveState.eventSequence = newestEventSequence;
        else
        {
            for (const event of events) {
                const sequence = Number(event.sequence ?? -1);
                if (sequence <= liveState.eventSequence)
                    continue;
                const control = findControl(event.control);
                if (control) {
                    control.classList.remove("debug-pulse");
                    void control.getBoundingClientRect();
                    control.classList.add("debug-pulse");
                    window.setTimeout(() => control.classList.remove("debug-pulse"), 480);
                    const value = Number.isFinite(Number(event.value)) ? ` · ${event.value}` : "";
                    status.textContent = `Debug · ${control.dataset.debugName} · ${event.kind ?? "INPUT"} ${event.phase}${value}`;
                    liveState.lastInputAt = performance.now();
                }
                liveState.eventSequence = sequence;
            }
        }

        const displayRevision = Number(state.display?.revision ?? -1);
        if (displayRevision >= 0 && displayRevision !== liveState.displayRevision) {
            queueLiveDisplay(`${state.display.url}?revision=${encodeURIComponent(displayRevision)}`, displayRevision);
            liveState.displayRevision = displayRevision;
        }

        liveState.revision = Number(state.revision ?? liveState.revision);
        if (performance.now() - liveState.lastInputAt > 1200) {
            const lightCount = Object.keys(lights).length;
            status.textContent = state.connected
                ? `Live · output ${liveState.revision} · ${lightCount} lights · input ${liveState.inputSession ? "ready" : "off"}`
                : displayRevision >= 0
                    ? "Live display · lights and input unavailable"
                    : "Waiting for Bitwig debug output";
        }
    }

    async function pollDebugState() {
        try {
            const response = await fetch("/api/state", {cache: "no-store"});
            if (!response.ok)
                throw new Error(`debug state returned ${response.status}`);
            applyDebugState(await response.json());
        }
        catch (_error) {
            status.textContent = "Waiting for local debug bridge";
        }
    }

    function requestDebugState() {
        liveState.stateFetchPending = true;
        if (liveState.stateFetchActive)
            return;
        liveState.stateFetchActive = true;
        void (async () => {
            do {
                liveState.stateFetchPending = false;
                await pollDebugState();
            }
            while (liveState.stateFetchPending);
            liveState.stateFetchActive = false;
        })();
    }

    function startLiveUpdates() {
        if (location.protocol === "http:" || location.protocol === "https:") {
            requestDebugState();
            if ("EventSource" in window) {
                const events = new EventSource("/api/events");
                events.addEventListener("state", requestDebugState);
                window.addEventListener("beforeunload", () => events.close(), {once: true});
                window.setInterval(requestDebugState, 1000);
            }
            else
                window.setInterval(requestDebugState, 100);
            return;
        }
        status.textContent = "Static preview · run tools/push-debug-surface for live output";
    }

    buttons.forEach(createButton);
    directionalClusters.forEach(createDirectionalCluster);
    createButtonRows();
    createScenes();
    createPads();
    encoders.forEach(createEncoder);
    createTouchStrip();
    reset();
    selectPressurePad("PAD1");

    document.querySelector("#demo-button").addEventListener("click", showDemo);
    document.querySelector("#reset-button").addEventListener("click", reset);
    pressureSlider.addEventListener("input", event => schedulePressure(event.target.value));
    pressureSlider.addEventListener("change", event => schedulePressure(event.target.value, true));
    window.addEventListener("blur", releaseBrowserInputs);
    window.setInterval(() => {
        for (const [address, kind] of activeInjectedEdges)
            queueDebugInput(address, kind, "KEEPALIVE", 0);
        for (const address of touchedControls)
            queueDebugInput(address, "TOUCH", "KEEPALIVE", 0);
    }, 1000);

    window.pushSurface = Object.freeze({
        findControl,
        applyDebugState,
        queueDebugInput,
        reset,
        selectPressurePad,
        setDisplay,
        setLight,
        setPressed,
        showDemo
    });
    startLiveUpdates();
})();
