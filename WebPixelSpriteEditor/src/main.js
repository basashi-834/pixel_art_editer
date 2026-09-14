// main.js: wires up EditorState + all panels, the File/Edit menus, toolbar,
// mobile panel tabs, and keyboard shortcuts. Runs after DOMContentLoaded.
var PSE = window.PSE || (window.PSE = {});

document.addEventListener("DOMContentLoaded", function () {
  var state = new PSE.EditorState();
  window.__PSE_STATE__ = state; // handy for debugging / smoke tests

  var canvasEl = document.getElementById("main-canvas");
  var statusCoords = document.getElementById("status-coords");
  var statusZoom = document.getElementById("status-zoom");
  var canvasPanel = new PSE.CanvasPanel(state, canvasEl, statusCoords, statusZoom);

  new PSE.ColorPanel(state, document.getElementById("color-panel"));
  new PSE.LayersPanel(state, document.getElementById("layers-panel"));
  new PSE.FramesPanel(state, document.getElementById("frames-panel"));
  PSE.ContextMenu.init(state);

  state.on("canvasResized", function () { canvasPanel.centerCamera(); canvasPanel.render(); });
  state.on("projectReplaced", function () { canvasPanel.centerCamera(); canvasPanel.render(); });

  // ---- Toolbar: tool selection --------------------------------------------------

  var toolButtons = document.querySelectorAll("#toolbar [data-tool]");
  function refreshToolButtons() {
    toolButtons.forEach(function (btn) {
      btn.classList.toggle("active", btn.getAttribute("data-tool") === state.tool);
    });
  }
  toolButtons.forEach(function (btn) {
    btn.addEventListener("click", function () {
      state.setTool(btn.getAttribute("data-tool"));
    });
  });
  state.on("toolChanged", refreshToolButtons);
  refreshToolButtons();

  // ---- Dropdown menus (File / Edit) ----------------------------------------------

  function setupDropdown(buttonId, menuId) {
    var button = document.getElementById(buttonId);
    var menu = document.getElementById(menuId);
    button.addEventListener("click", function (e) {
      e.stopPropagation();
      var isOpen = menu.classList.contains("open");
      closeAllDropdowns();
      if (!isOpen) menu.classList.add("open");
    });
    menu.addEventListener("click", function (e) { e.stopPropagation(); });
  }
  function closeAllDropdowns() {
    document.querySelectorAll(".dropdown-menu.open").forEach(function (m) { m.classList.remove("open"); });
  }
  document.addEventListener("click", closeAllDropdowns);
  setupDropdown("btn-menu-file", "menu-file");
  setupDropdown("btn-menu-edit", "menu-edit");

  function bindAction(id, fn) {
    var el = document.getElementById(id);
    if (!el) return;
    el.addEventListener("click", function () { closeAllDropdowns(); fn(); });
  }

  bindAction("action-new", function () { PSE.Dialogs.newCanvas(state); });
  bindAction("action-open-png", function () { PSE.FileIO.openPNG(state); });
  bindAction("action-open-project", function () { PSE.FileIO.openProject(state); });
  bindAction("action-save-png", function () { PSE.FileIO.savePNG(state); });
  bindAction("action-save-project", function () { PSE.FileIO.saveProject(state); });
  bindAction("action-resize-canvas", function () { PSE.Dialogs.resizeCanvas(state); });
  bindAction("action-export-sheet", function () { PSE.Dialogs.spriteSheetExport(state); });

  bindAction("action-undo", function () { undo(); });
  bindAction("action-redo", function () { redo(); });
  bindAction("action-copy", function () { PSE.SelectionOps.copy(state); });
  bindAction("action-cut", function () { PSE.SelectionOps.cut(state); });
  bindAction("action-paste", function () { PSE.SelectionOps.startPaste(state); });
  bindAction("action-delete-selection", function () { PSE.SelectionOps.deleteSelection(state); });
  bindAction("action-fill-selection", function () { PSE.SelectionOps.fillSelection(state); });
  bindAction("action-flip-h", function () { PSE.SelectionOps.flip(state, true); });
  bindAction("action-flip-v", function () { PSE.SelectionOps.flip(state, false); });
  bindAction("action-select-all", function () { PSE.SelectionOps.selectAll(state); });
  bindAction("action-deselect", function () { PSE.SelectionOps.deselect(state); });

  // ---- Undo / redo (per active layer) --------------------------------------------

  function undo() {
    var layer = state.project.activeLayer();
    var restored = layer.history.undo(layer.imageData);
    if (!restored) return;
    layer.setImageData(restored);
    state.notifyPixelsChanged();
    state.notifyStructureChanged();
  }
  function redo() {
    var layer = state.project.activeLayer();
    var restored = layer.history.redo(layer.imageData);
    if (!restored) return;
    layer.setImageData(restored);
    state.notifyPixelsChanged();
    state.notifyStructureChanged();
  }

  // ---- Mobile bottom-sheet panel tabs ----------------------------------------------

  var panelTabs = document.querySelectorAll("#mobile-panel-tabs [data-panel]");
  var backdrop = document.getElementById("mobile-backdrop");
  function closeAllMobilePanels() {
    document.querySelectorAll(".side-panel.open, #frames-panel.open").forEach(function (p) { p.classList.remove("open"); });
    panelTabs.forEach(function (t) { t.classList.remove("active"); });
    backdrop.classList.remove("open");
  }
  panelTabs.forEach(function (tab) {
    tab.addEventListener("click", function () {
      var name = tab.getAttribute("data-panel");
      var target = document.getElementById(name === "colors" ? "color-panel" : name === "layers" ? "layers-panel" : "frames-panel");
      var willOpen = !target.classList.contains("open");
      closeAllMobilePanels();
      if (willOpen) {
        target.classList.add("open");
        tab.classList.add("active");
        backdrop.classList.add("open");
      }
    });
  });
  backdrop.addEventListener("click", closeAllMobilePanels);
  document.querySelectorAll("[data-close-panel]").forEach(function (btn) {
    btn.addEventListener("click", closeAllMobilePanels);
  });

  // ---- Keyboard shortcuts -----------------------------------------------------------

  window.addEventListener("keydown", function (e) {
    var tag = document.activeElement && document.activeElement.tagName;
    var inField = tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT";

    if (e.key === "Escape") {
      if (state.pasting) PSE.SelectionOps.cancelPasteToPencil(state);
      else if (state.linePreview) { state.linePreview = null; state.notifyPixelsChanged(); }
      document.activeElement && document.activeElement.blur && document.activeElement.blur();
      return;
    }
    if (inField) return;

    var ctrl = e.ctrlKey || e.metaKey;
    if (ctrl && e.key.toLowerCase() === "z" && !e.shiftKey) { e.preventDefault(); undo(); return; }
    if (ctrl && (e.key.toLowerCase() === "y" || (e.key.toLowerCase() === "z" && e.shiftKey))) { e.preventDefault(); redo(); return; }
    if (ctrl && e.key.toLowerCase() === "c") { e.preventDefault(); PSE.SelectionOps.copy(state); return; }
    if (ctrl && e.key.toLowerCase() === "x") { e.preventDefault(); PSE.SelectionOps.cut(state); return; }
    if (ctrl && e.key.toLowerCase() === "v") { e.preventDefault(); PSE.SelectionOps.startPaste(state); return; }
    if (ctrl && e.key.toLowerCase() === "a") { e.preventDefault(); PSE.SelectionOps.selectAll(state); return; }
    if (ctrl && e.key.toLowerCase() === "n") { e.preventDefault(); PSE.Dialogs.newCanvas(state); return; }
    if (ctrl && e.key.toLowerCase() === "o") { e.preventDefault(); PSE.FileIO.openPNG(state); return; }
    if (ctrl && e.key.toLowerCase() === "s") { e.preventDefault(); PSE.FileIO.savePNG(state); return; }
    if (e.key === "Delete" || e.key === "Backspace") {
      if (state.selection) { e.preventDefault(); PSE.SelectionOps.deleteSelection(state); }
      return;
    }
  });

  canvasPanel.render();
});
