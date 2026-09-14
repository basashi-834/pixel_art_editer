// EditorState: central app state (project, active tool, camera, colors,
// selection/clipboard, display flags) plus a tiny pub-sub so UI panels can
// react to changes without polling.
var PSE = window.PSE || (window.PSE = {});

PSE.EditorState = function () {
  this.project = new PSE.Project(64, 64);
  this.currentColor = { r: 0, g: 0, b: 0, a: 255 };

  this.tool = "pencil"; // pencil | eraser | fill | eyedropper | line | select
  this.camera = { zoom: 8, panX: 0, panY: 0 };

  this.selection = null;      // {x,y,w,h} in pixel coords, or null
  this.clipboard = null;      // {w,h,imageData}
  this.pasting = null;        // {x,y} cursor position while a paste preview follows

  this.showGrid = true;
  this.showGuide16 = true;
  this.onionSkin = false;
  this.playing = false;

  this._listeners = {};
};

PSE.EditorState.prototype.on = function (event, fn) {
  (this._listeners[event] = this._listeners[event] || []).push(fn);
  return fn;
};

PSE.EditorState.prototype.off = function (event, fn) {
  var list = this._listeners[event];
  if (!list) return;
  var i = list.indexOf(fn);
  if (i >= 0) list.splice(i, 1);
};

PSE.EditorState.prototype.emit = function (event, payload) {
  var list = this._listeners[event];
  if (!list) return;
  list.slice().forEach(function (fn) { fn(payload); });
};

// Convenience: fired whenever pixels change, so canvas/layers/frames panels
// all know to redraw thumbnails/composite.
PSE.EditorState.prototype.notifyPixelsChanged = function () {
  this.emit("pixelsChanged");
};

PSE.EditorState.prototype.notifyStructureChanged = function () {
  this.emit("structureChanged");
};

PSE.EditorState.prototype.setTool = function (tool) {
  this.tool = tool;
  this.cancelPaste();
  this.emit("toolChanged", tool);
};

PSE.EditorState.prototype.setCurrentColor = function (color) {
  this.currentColor = { r: color.r, g: color.g, b: color.b, a: color.a };
  this.emit("colorChanged", this.currentColor);
};

PSE.EditorState.prototype.commitCurrentColorToRecent = function () {
  this.project.addRecentColor(this.currentColor);
  this.emit("recentColorsChanged");
};

PSE.EditorState.prototype.setSelection = function (rect) {
  this.selection = rect;
  this.emit("selectionChanged", rect);
};

PSE.EditorState.prototype.clearSelection = function () {
  this.setSelection(null);
};

PSE.EditorState.prototype.cancelPaste = function () {
  if (this.pasting) {
    this.pasting = null;
    this.emit("pixelsChanged");
  }
};

PSE.EditorState.prototype.loadProject = function (project) {
  this.project = project;
  this.selection = null;
  this.clipboard = null;
  this.pasting = null;
  this.camera.panX = 0;
  this.camera.panY = 0;
  this.emit("projectReplaced");
  this.emit("structureChanged");
  this.emit("pixelsChanged");
};
