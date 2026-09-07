/* Inert Push hardware presentation shared by the debugger and offline UI catalog. */
(() => {
    "use strict";

    const SVG_NS = "http://www.w3.org/2000/svg";
    const BUTTON_WIDTH = 13.5;
    const BUTTON_HEIGHT = 6;

    function element(name, attributes = {}) {
        const result = document.createElementNS(SVG_NS, name);
        for (const [key, value] of Object.entries(attributes))
            result.setAttribute(key, String(value));
        return result;
    }

    function createRowButton({x, y, width = BUTTON_WIDTH, height = BUTTON_HEIGHT, row = 2}) {
        const group = element("g", {class: "button button-display-row"});
        group.append(element("rect", {class: "control-face", x, y, width, height, rx: 0.25}));
        const lightY = row === 1 ? y + 1.25 : y + height - 1.65;
        group.append(element("rect", {class: "row-light", x: x + 1.25, y: lightY, width: width - 2.5, height: 0.4}));
        return group;
    }

    /** Mount once into a caller-owned group. Each screen in a document needs a unique id. */
    function mountScreen(group, {id = "display", x = 36.5, y = 27.25, width = 120.25, height = 20.041667, href = ""} = {}) {
        const gradientId = `${id}-gradient`;
        const clipId = `${id}-clip`;
        const definitions = element("defs");
        const gradient = element("linearGradient", {id: gradientId, x1: 0, y1: 0, x2: 0, y2: 1});
        gradient.append(element("stop", {offset: 0, "stop-color": "#101011"}), element("stop", {offset: 1, "stop-color": "#080809"}));
        const clip = element("clipPath", {id: clipId});
        clip.append(element("rect", {x, y, width, height}));
        definitions.append(gradient, clip);
        // Preserve the measured default bezel: 35.25,26 × 122.75,22.55.
        const bezel = element("rect", {class: "display-bezel", x: x - 1.25, y: y - 1.25, width: width + 2.5, height: height + 2.508333});
        const screen = element("rect", {class: "display-screen", x, y, width, height, fill: `url(#${gradientId})`});
        group.prepend(definitions, bezel, screen);
        const image = element("image", {id: `${id}-image`, x, y, width, height, preserveAspectRatio: "xMidYMid meet", "clip-path": `url(#${clipId})`});
        if (href) image.setAttribute("href", href);
        group.append(image);
        return image;
    }

    window.PushHardware = Object.freeze({createRowButton, mountScreen,
        ROW_WIDTH: 120.25, BUTTON_WIDTH, BUTTON_HEIGHT, COLUMN_PITCH: 15.25});
})();
