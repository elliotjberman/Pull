"use strict";
(() => {
  const stories = Array.from(document.querySelectorAll(".story"));
  const byId = new Map(stories.map(story => [story.id, story]));
  const links = Array.from(document.querySelectorAll("[data-story-link]"));
  const groups = Array.from(document.querySelectorAll(".nav-group"));
  const sections = Array.from(document.querySelectorAll("[data-section]"));
  const search = document.querySelector("#story-search");
  const workspace = document.querySelector("#story-workspace");
  const colorControls = document.querySelector("#color-controls");
  const remembered = {};
  let section = "components";
  let activeId;
  let stopPlayback = () => {};
  const aliases = new Map(["start", "middle", "clear"].map(state => ["playback-ripple-" + state, "playback-ripple"]));

  function filterNavigation() {
    const query = search.value.trim().toLowerCase();
    let count = 0;
    for (const link of links) {
      link.hidden = link.dataset.kind !== section || !link.dataset.search.includes(query);
      if (!link.hidden) count++;
    }
    for (const group of groups) group.hidden = !Array.from(group.querySelectorAll("[data-story-link]")).some(link => !link.hidden);
    for (const button of sections) button.setAttribute("aria-pressed", String(button.dataset.section === section));
    document.querySelector("#no-results").hidden = count !== 0;
  }

  function closeNavigation() {
    document.body.classList.remove("nav-open");
    document.querySelector("#nav-toggle").setAttribute("aria-expanded", "false");
    document.querySelector("#nav-backdrop").hidden = true;
  }

  function showStory(id) {
    id = aliases.get(id) || id;
    const requestedSection = id === "views" || id === "components" ? id : null;
    const story = requestedSection
      ? byId.get(remembered[requestedSection]) || stories.find(item => item.dataset.kind === requestedSection)
      : byId.get(id) || stories[0];
    if (!story) return;
    section = story.dataset.kind;
    remembered[section] = story.id;
    for (const other of stories) other.hidden = other !== story;
    for (const link of links) {
      if (link.dataset.storyLink === story.id) link.setAttribute("aria-current", "page");
      else link.removeAttribute("aria-current");
    }
    document.querySelector("#story-title").textContent = story.dataset.title;
    document.querySelector("#story-group").textContent = (section === "components" ? "Components" : "Views") + " / " + story.dataset.group;
    document.querySelector("#story-permalink").href = "#" + story.id;
    document.title = story.dataset.title + " · Pull UI library";
    colorControls.hidden = section !== "components";
    if (activeId !== story.id) { stopPlayback(); workspace.scrollTop = 0; }
    activeId = story.id;
    const activeLink = links.find(link => link.dataset.storyLink === story.id);
    if (activeLink && !activeLink.dataset.search.includes(search.value.trim().toLowerCase())) search.value = "";
    filterNavigation();
    if (activeLink) activeLink.scrollIntoView({block: "nearest"});
    closeNavigation();
  }

  function selectStory(id) {
    if (location.hash.slice(1) === id) showStory(id);
    else location.hash = id;
  }

  for (const button of sections) button.addEventListener("click", () => {
    section = button.dataset.section;
    search.value = "";
    filterNavigation();
    const last = links.find(link => !link.hidden && link.dataset.storyLink === remembered[section]);
    const next = last || links.find(link => !link.hidden);
    if (next) selectStory(next.dataset.storyLink);
  });
  for (const link of links) link.addEventListener("click", () => { if (link.dataset.storyLink === activeId) closeNavigation(); });
  search.addEventListener("input", filterNavigation);
  search.addEventListener("keydown", event => {
    if (event.key === "Enter") {
      const first = links.find(link => !link.hidden);
      if (first) { selectStory(first.dataset.storyLink); search.blur(); }
    }
  });
  document.querySelector("#nav-toggle").addEventListener("click", () => {
    const open = document.body.classList.toggle("nav-open");
    document.querySelector("#nav-toggle").setAttribute("aria-expanded", String(open));
    document.querySelector("#nav-backdrop").hidden = !open;
    if (open) search.focus();
  });
  document.querySelector("#nav-backdrop").addEventListener("click", closeNavigation);
  document.addEventListener("keydown", event => {
    if (event.key === "Escape") {
      if (document.body.classList.contains("nav-open")) closeNavigation();
      else if (document.activeElement === search) { search.value = ""; filterNavigation(); search.blur(); }
    }
    if (event.key === "/" && !["INPUT", "TEXTAREA"].includes(document.activeElement.tagName)) {
      event.preventDefault();
      if (matchMedia("(max-width:760px)").matches) {
        document.body.classList.add("nav-open");
        document.querySelector("#nav-toggle").setAttribute("aria-expanded", "true");
        document.querySelector("#nav-backdrop").hidden = false;
      }
      search.focus();
    }
  });
  function readHash() {
    try { return decodeURIComponent(location.hash.slice(1)); } catch { return ""; }
  }
  window.addEventListener("hashchange", () => showStory(readHash()));

  // Use the same inert hardware presentation as the debugger, without its input/runtime code.
  for (const row of document.querySelectorAll("svg[data-colors]")) {
    const colors = row.dataset.colors.split(",");
    row.setAttribute("viewBox", `0 0 ${colors.length === 1 ? PushHardware.BUTTON_WIDTH : PushHardware.ROW_WIDTH} ${PushHardware.BUTTON_HEIGHT}`);
    colors.forEach((color, index) => {
      const button = PushHardware.createRowButton({x: index * PushHardware.COLUMN_PITCH, y: 0, row: Number(row.dataset.row)});
      button.dataset.lit = String(Boolean(color));
      button.style.setProperty("--light", color || "transparent");
      button.classList.toggle("unowned", !color);
      const title = document.createElementNS("http://www.w3.org/2000/svg", "title");
      title.textContent = `Button ${index + 1}: ${color || "no light state from this view"}`;
      button.append(title);
      row.append(button);
    });
  }
  for (const image of document.querySelectorAll(".screen img")) {
    const screen = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    const width = PushHardware.ROW_WIDTH;
    const height = width * Number(image.getAttribute("height")) / Number(image.getAttribute("width"));
    screen.setAttribute("viewBox", `0 0 ${width} ${height}`);
    screen.setAttribute("role", "img");
    screen.setAttribute("aria-label", image.alt);
    PushHardware.mountScreen(screen, {id: image.closest(".story").id + "-screen", x: 0, y: 0, width, height, href: image.getAttribute("src")});
    image.replaceWith(screen);
  }

  for (const button of document.querySelectorAll("[data-animation]")) {
    const story = button.closest(".story");
    const image = story.querySelector(".screen image");
    const status = story.querySelector(".animation-status");
    const restingFrame = image.getAttribute("href");
    const frames = button.dataset.frames.split(",");
    let ready;
    button.addEventListener("click", async () => {
      stopPlayback();
      let cancelled = false;
      let request;
      stopPlayback = () => {
        cancelled = true;
        cancelAnimationFrame(request);
        image.setAttribute("href", restingFrame);
        status.textContent = "";
      };
      status.classList.add("sr-only");
      status.textContent = "Loading preview";
      try {
        ready ||= Promise.all(frames.map(src => {
          const frame = new Image();
          frame.src = src;
          return frame.decode().then(() => frame);
        }));
        await ready;
      } catch {
        ready = null;
        if (!cancelled) {
          status.classList.remove("sr-only");
          status.textContent = "Preview could not load. Try again.";
        }
        return;
      }
      if (cancelled) return;
      status.textContent = "Playing preview";
      const start = performance.now();
      function draw(now) {
        if (cancelled) return;
        const progress = Math.min(1, (now - start) / Number(button.dataset.duration));
        image.setAttribute("href", frames[Math.floor(progress * (frames.length - 1))]);
        if (progress < 1) request = requestAnimationFrame(draw);
        else status.textContent = "Preview complete";
      }
      draw(start);
    });
  }

  const picker = document.querySelector("#component-color");
  const hex = document.querySelector("#component-color-hex");
  const error = document.querySelector("#color-error");
  const defaultColor = picker.value;
  const exportFont = new Blob([document.querySelector("#display-font").innerHTML]);
  const exports = Array.from(document.querySelectorAll(".story[data-kind=components] figure"), figure => ({
    svg: figure.querySelector("svg").cloneNode(true), link: figure.querySelector("a"), url: null
  }));
  function applyColor(color) {
    picker.value = color;
    document.querySelector("#stories").style.setProperty("--component-color", color);
    hex.removeAttribute("aria-invalid");
    error.hidden = true;
    for (const specimen of exports) {
      specimen.svg.style.setProperty("--component-color", color);
      const source = new XMLSerializer().serializeToString(specimen.svg);
      const end = source.lastIndexOf("</svg>");
      const previous = specimen.url;
      specimen.url = URL.createObjectURL(new Blob([source.slice(0, end), exportFont, source.slice(end)], {type: "image/svg+xml"}));
      specimen.link.href = specimen.url;
      if (previous) URL.revokeObjectURL(previous);
    }
  }
  picker.addEventListener("input", () => { applyColor(picker.value); hex.value = picker.value; });
  hex.addEventListener("input", () => {
    const value = hex.value.trim().replace(/^#/, "");
    if (/^[0-9a-f]{6}$/i.test(value)) applyColor("#" + value.toLowerCase());
  });
  hex.addEventListener("blur", () => {
    const valid = /^#?[0-9a-f]{6}$/i.test(hex.value.trim());
    hex.setAttribute("aria-invalid", String(!valid));
    error.hidden = valid;
    if (valid) hex.value = picker.value;
  });
  document.querySelector("#reset-color").addEventListener("click", () => { applyColor(defaultColor); hex.value = defaultColor; });
  applyColor(defaultColor);
  const initial = readHash();
  if (aliases.has(initial)) history.replaceState(null, "", "#" + aliases.get(initial));
  else if (!byId.has(initial) && !["components", "views"].includes(initial) && stories[0]) history.replaceState(null, "", "#" + stories[0].id);
  showStory(readHash());
})();
