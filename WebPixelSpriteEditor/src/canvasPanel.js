// CanvasPanel: renders the active frame (nearest-neighbor zoom, grid, 16x16
// guide, onion skin, selection/line/paste previews, hover highlight) and
// turns pointer/touch/wheel/keyboard input into tool calls + camera moves.
//
// Coordinate spaces:
//  - "client" coords: clientX/clientY from browser events (CSS px, page-relative)
//  - "panel" coords: client coords minus the canvas element's bounding rect (CSS px)
//  - "src" coords: integer pixel coordinates in the sprite's own resolution
// panel = camera.pan + src * camera.zoom
var PSE = window.PSE || (window.PSE = {});

PSE.MIN_ZOOM = 1;
PSE.MAX_ZOOM = 64;

PSE.CanvasPanel = function (state, canvas, statusCoordsEl, statusZoomEl) {
  this.state = state;
  this.canvas = canvas;
  this.ctx = canvas.getContext("2d");
  this.statusCoordsEl = statusCoordsEl;
  this.statusZoomEl = statusZoomEl;

  this.pointers = new Map(); // pointerId -> {x, y, type}
  this.drawingPointerId = null;
  this.panPointerId = null;
  this.panLast = null;
  this.pinch = null; // {startDist, startZoom, startMid, startPanX, startPanY}
  this.multiTouchActive = false;
  this.spaceDown = false;
  this.hoverSrc = null;
  this.hoverVisible = false;
  this.needsCenter = true;

  this._bindEvents();
  this._observeResize();

  var self = this;
  ["pixelsChanged", "structureChanged", "selectionChanged", "toolChanged", "projectReplaced"].forEach(function (ev) {
    state.on(ev, function () { self.render(); });
  });
};

PSE.CanvasPanel.prototype.centerCamera = function () {
  var rect = this.canvas.getBoundingClientRect();
  var w = this.state.project.width, h = this.state.project.height;
  var zoom = this.state.camera.zoom;
  this.state.camera.panX = Math.round((rect.width - w * zoom) / 2);
  this.state.camera.panY = Math.round((rect.height - h * zoom) / 2);
  this.needsCenter = false;
};

PSE.CanvasPanel.prototype._observeResize = function () {
  var self = this;
  var ro = new ResizeObserver(function () { self.render(); });
  ro.observe(this.canvas.parentElement);
  window.addEventListener("resize", function () { self.render(); });
};

PSE.CanvasPanel.prototype.screenToSrc = function (clientX, clientY) {
  var rect = this.canvas.getBoundingClientRect();
  var cam = this.state.camera;
  var px = clientX - rect.left;
  var py = clientY - rect.top;
  return {
    x: Math.floor((px - cam.panX) / cam.zoom),
    y: Math.floor((py - cam.panY) / cam.zoom)
  };
};

PSE.CanvasPanel.prototype._panelXY = function (clientX, clientY) {
  var rect = this.canvas.getBoundingClientRect();
  return { x: clientX - rect.left, y: clientY - rect.top };
};

// ---- Input -------------------------------------------------------------------

PSE.CanvasPanel.prototype._bindEvents = function () {
  var self = this;
  var el = this.canvas;
  el.style.touchAction = "none";

  window.addEventListener("keydown", function (e) {
    if (e.code === "Space" && !self.spaceDown) {
      self.spaceDown = true;
      el.classList.add("space-pan");
    }
  });
  window.addEventListener("keyup", function (e) {
    if (e.code === "Space") {
      self.spaceDown = false;
      el.classList.remove("space-pan");
    }
  });

  el.addEventListener("pointerdown", function (e) { self._onPointerDown(e); });
  el.addEventListener("pointermove", function (e) { self._onPointerMove(e); });
  window.addEventListener("pointerup", function (e) { self._onPointerUp(e); });
  window.addEventListener("pointercancel", function (e) { self._onPointerUp(e); });
  el.addEventListener("contextmenu", function (e) {
    e.preventDefault();
    if (window.PSE.ContextMenu) window.PSE.ContextMenu.showAt(e.clientX, e.clientY);
  });
  el.addEventListener("wheel", function (e) { self._onWheel(e); }, { passive: false });
  el.addEventListener("pointerleave", function () {
    self.hoverVisible = false;
    self.render();
  });
};

PSE.CanvasPanel.prototype._activeTool = function () {
  var name = this.state.tool;
  if (name === "paste") return null;
  return PSE.Tools[name];
};

PSE.CanvasPanel.prototype._onPointerDown = function (e) {
  var state = this.state;
  this.canvas.setPointerCapture(e.pointerId);
  var panelPos = this._panelXY(e.clientX, e.clientY);
  this.pointers.set(e.pointerId, { x: panelPos.x, y: panelPos.y, type: e.pointerType });

  if (this.pointers.size === 2) {
    this._cancelDrawing();
    this.multiTouchActive = true;
    this._startPinch();
    return;
  }
  if (this.pointers.size > 2) return;
  if (this.multiTouchActive) return; // ignore stray remaining finger after a pinch

  var wantsPan = this.spaceDown || e.button === 1;
  if (wantsPan) {
    this.panPointerId = e.pointerId;
    this.panLast = panelPos;
    return;
  }
  if (e.button === 2) return; // right click handled by contextmenu event

  var src = this.screenToSrc(e.clientX, e.clientY);
  if (state.pasting) {
    PSE.SelectionOps.updatePastePosition(state, src.x, src.y);
    PSE.SelectionOps.confirmPaste(state);
    return;
  }
  var tool = this._activeTool();
  if (!tool) return;
  this.drawingPointerId = e.pointerId;
  tool.onDown(state, src.x, src.y);
};

PSE.CanvasPanel.prototype._onPointerMove = function (e) {
  var state = this.state;
  var panelPos = this._panelXY(e.clientX, e.clientY);
  if (this.pointers.has(e.pointerId)) {
    this.pointers.set(e.pointerId, { x: panelPos.x, y: panelPos.y, type: e.pointerType });
  }

  if (this.pinch && this.pointers.size === 2) {
    this._updatePinch();
    return;
  }

  if (this.panPointerId === e.pointerId && this.panLast) {
    state.camera.panX += panelPos.x - this.panLast.x;
    state.camera.panY += panelPos.y - this.panLast.y;
    this.panLast = panelPos;
    this.render();
    return;
  }

  var src = this.screenToSrc(e.clientX, e.clientY);
  if (e.pointerType !== "touch") {
    this.hoverSrc = src;
    this.hoverVisible = true;
    this._updateStatus(src);
  }

  if (state.pasting) {
    PSE.SelectionOps.updatePastePosition(state, src.x, src.y);
    return;
  }
  if (this.drawingPointerId === e.pointerId) {
    var tool = this._activeTool();
    if (tool) tool.onMove(state, src.x, src.y);
  } else if (e.pointerType !== "touch") {
    this.render();
  }
};

PSE.CanvasPanel.prototype._onPointerUp = function (e) {
  var state = this.state;
  var had = this.pointers.has(e.pointerId);
  this.pointers.delete(e.pointerId);

  if (this.pinch && this.pointers.size < 2) {
    this.pinch = null;
  }
  if (this.pointers.size === 0) {
    this.multiTouchActive = false;
  }

  if (this.panPointerId === e.pointerId) {
    this.panPointerId = null;
    this.panLast = null;
    return;
  }
  if (this.drawingPointerId === e.pointerId) {
    var src = this.screenToSrc(e.clientX, e.clientY);
    var tool = this._activeTool();
    if (tool) tool.onUp(state, src.x, src.y);
    this.drawingPointerId = null;
  }
  if (!had) return;
};

PSE.CanvasPanel.prototype._cancelDrawing = function () {
  if (this.drawingPointerId !== null) {
    var tool = this._activeTool();
    if (tool) tool.onCancel(this.state);
    this.drawingPointerId = null;
  }
};

PSE.CanvasPanel.prototype._startPinch = function () {
  var pts = Array.from(this.pointers.values());
  var a = pts[0], b = pts[1];
  var dist = Math.hypot(b.x - a.x, b.y - a.y) || 1;
  var mid = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
  this.pinch = {
    startDist: dist,
    startZoom: this.state.camera.zoom,
    startMid: mid,
    startPanX: this.state.camera.panX,
    startPanY: this.state.camera.panY
  };
};

PSE.CanvasPanel.prototype._updatePinch = function () {
  var pts = Array.from(this.pointers.values());
  var a = pts[0], b = pts[1];
  var dist = Math.hypot(b.x - a.x, b.y - a.y) || 1;
  var mid = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
  var p = this.pinch;
  var zoomFactor = dist / p.startDist;
  var newZoom = Math.min(PSE.MAX_ZOOM, Math.max(PSE.MIN_ZOOM, p.startZoom * zoomFactor));
  var srcAtStartX = (p.startMid.x - p.startPanX) / p.startZoom;
  var srcAtStartY = (p.startMid.y - p.startPanY) / p.startZoom;
  this.state.camera.zoom = newZoom;
  this.state.camera.panX = mid.x - srcAtStartX * newZoom;
  this.state.camera.panY = mid.y - srcAtStartY * newZoom;
  this.render();
};

PSE.CanvasPanel.prototype._onWheel = function (e) {
  if (!e.ctrlKey && !e.metaKey) return; // README: Ctrl+wheel only, plain wheel does nothing
  e.preventDefault();
  var cam = this.state.camera;
  var panelPos = this._panelXY(e.clientX, e.clientY);
  var factor = Math.exp(-e.deltaY * 0.01);
  var newZoom = Math.min(PSE.MAX_ZOOM, Math.max(PSE.MIN_ZOOM, cam.zoom * factor));
  var srcX = (panelPos.x - cam.panX) / cam.zoom;
  var srcY = (panelPos.y - cam.panY) / cam.zoom;
  cam.zoom = newZoom;
  cam.panX = panelPos.x - srcX * newZoom;
  cam.panY = panelPos.y - srcY * newZoom;
  this.render();
};

PSE.CanvasPanel.prototype._updateStatus = function (src) {
  if (!this.statusCoordsEl) return;
  var w = this.state.project.width, h = this.state.project.height;
  if (src.x >= 0 && src.y >= 0 && src.x < w && src.y < h) {
    this.statusCoordsEl.textContent = "(" + src.x + ", " + src.y + ")";
  } else {
    this.statusCoordsEl.textContent = "";
  }
};

// ---- Rendering -----------------------------------------------------------------

PSE.CanvasPanel.prototype.render = function () {
  var canvas = this.canvas;
  var rect = canvas.parentElement.getBoundingClientRect();
  var dpr = window.devicePixelRatio || 1;
  var cssW = Math.max(1, Math.round(rect.width));
  var cssH = Math.max(1, Math.round(rect.height));
  if (canvas.width !== Math.round(cssW * dpr) || canvas.height !== Math.round(cssH * dpr)) {
    canvas.width = Math.round(cssW * dpr);
    canvas.height = Math.round(cssH * dpr);
    canvas.style.width = cssW + "px";
    canvas.style.height = cssH + "px";
  }
  if (this.needsCenter) this.centerCamera();

  var ctx = this.ctx;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.imageSmoothingEnabled = false;
  ctx.clearRect(0, 0, cssW, cssH);

  var state = this.state;
  var cam = state.camera;
  var project = state.project;
  var w = project.width, h = project.height;
  var ox = cam.panX, oy = cam.panY, z = cam.zoom;

  this._drawCheckerboard(ox, oy, w * z, h * z);

  if (state.onionSkin) {
    var idx = project.activeFrameIndex - 1;
    if (idx >= 0) {
      var prevCanvas = project.frames[idx].compositeToCanvas();
      ctx.globalAlpha = 0.35;
      ctx.drawImage(prevCanvas, ox, oy, w * z, h * z);
      ctx.globalAlpha = 1;
    }
  }

  var activeCanvas = project.activeFrame().compositeToCanvas();
  ctx.drawImage(activeCanvas, ox, oy, w * z, h * z);

  if (state.pasting && state.clipboard) {
    var tmp = document.createElement("canvas");
    tmp.width = state.clipboard.w;
    tmp.height = state.clipboard.h;
    tmp.getContext("2d").putImageData(state.clipboard.imageData, 0, 0);
    ctx.globalAlpha = 0.65;
    ctx.drawImage(tmp, ox + state.pasting.x * z, oy + state.pasting.y * z, state.clipboard.w * z, state.clipboard.h * z);
    ctx.globalAlpha = 1;
  }

  if (state.linePreview) {
    this._drawLinePreview(state.linePreview, ox, oy, z);
  }

  if (z >= 4 && state.showGrid) this._drawGrid(ox, oy, w, h, z, "rgba(120,120,120,0.35)", 1);
  if (state.showGuide16) this._drawGuide(ox, oy, w, h, z, 16, "rgba(255,90,90,0.55)");

  if (state.selection) {
    this._drawSelectionRect(state.selection, ox, oy, z);
  }

  if (this.hoverVisible && this.hoverSrc && !state.pasting) {
    var hx = this.hoverSrc.x, hy = this.hoverSrc.y;
    if (hx >= 0 && hy >= 0 && hx < w && hy < h) {
      this._drawHover(ox + hx * z, oy + hy * z, z);
    }
  }

  if (this.statusZoomEl) this.statusZoomEl.textContent = Math.round(z * 100) + "%";
};

PSE.CanvasPanel.prototype._drawCheckerboard = function (x, y, w, h) {
  var ctx = this.ctx;
  ctx.save();
  ctx.beginPath();
  ctx.rect(x, y, w, h);
  ctx.clip();
  var size = 8;
  for (var yy = y; yy < y + h; yy += size) {
    for (var xx = x; xx < x + w; xx += size) {
      var even = (Math.floor((xx - x) / size) + Math.floor((yy - y) / size)) % 2 === 0;
      ctx.fillStyle = even ? "#ffffff" : "#c9c9c9";
      ctx.fillRect(xx, yy, size, size);
    }
  }
  ctx.restore();
};

PSE.CanvasPanel.prototype._drawGrid = function (ox, oy, w, h, z, color, width) {
  var ctx = this.ctx;
  ctx.save();
  ctx.strokeStyle = color;
  ctx.lineWidth = width;
  ctx.beginPath();
  for (var x = 0; x <= w; x++) {
    var sx = Math.round(ox + x * z) + 0.5;
    ctx.moveTo(sx, oy);
    ctx.lineTo(sx, oy + h * z);
  }
  for (var y = 0; y <= h; y++) {
    var sy = Math.round(oy + y * z) + 0.5;
    ctx.moveTo(ox, sy);
    ctx.lineTo(ox + w * z, sy);
  }
  ctx.stroke();
  ctx.restore();
};

PSE.CanvasPanel.prototype._drawGuide = function (ox, oy, w, h, z, step, color) {
  var ctx = this.ctx;
  ctx.save();
  ctx.strokeStyle = color;
  ctx.lineWidth = 1;
  ctx.beginPath();
  for (var x = 0; x <= w; x += step) {
    var sx = Math.round(ox + x * z) + 0.5;
    ctx.moveTo(sx, oy);
    ctx.lineTo(sx, oy + h * z);
  }
  for (var y = 0; y <= h; y += step) {
    var sy = Math.round(oy + y * z) + 0.5;
    ctx.moveTo(ox, sy);
    ctx.lineTo(ox + w * z, sy);
  }
  ctx.stroke();
  ctx.restore();
};

PSE.CanvasPanel.prototype._drawSelectionRect = function (rect, ox, oy, z) {
  var ctx = this.ctx;
  var x = ox + rect.x * z, y = oy + rect.y * z, w = rect.w * z, h = rect.h * z;
  ctx.save();
  ctx.lineWidth = 1;
  ctx.strokeStyle = "#000000";
  ctx.setLineDash([4, 4]);
  ctx.strokeRect(x + 0.5, y + 0.5, w - 1, h - 1);
  ctx.strokeStyle = "#ffffff";
  ctx.lineDashOffset = 4;
  ctx.strokeRect(x + 0.5, y + 0.5, w - 1, h - 1);
  ctx.restore();
};

PSE.CanvasPanel.prototype._drawHover = function (x, y, z) {
  var ctx = this.ctx;
  ctx.save();
  ctx.lineWidth = 1;
  ctx.strokeStyle = "#000000";
  ctx.strokeRect(x + 0.5, y + 0.5, z - 1, z - 1);
  ctx.strokeStyle = "#ffffff";
  ctx.strokeRect(x + 1.5, y + 1.5, z - 3, z - 3);
  ctx.restore();
};

PSE.CanvasPanel.prototype._drawLinePreview = function (line, ox, oy, z) {
  var pts = PSE.LineUtil.bresenham(line.x0, line.y0, line.x1, line.y1);
  var ctx = this.ctx;
  var color = this.state.currentColor;
  ctx.save();
  ctx.globalAlpha = Math.max(0.35, color.a / 255);
  ctx.fillStyle = "rgb(" + color.r + "," + color.g + "," + color.b + ")";
  for (var i = 0; i < pts.length; i++) {
    ctx.fillRect(ox + pts[i].x * z, oy + pts[i].y * z, z, z);
  }
  ctx.restore();
};
