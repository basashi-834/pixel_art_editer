// Tools: pencil / eraser / fill / eyedropper / line / select.
// Each tool exposes onDown(state, x, y) / onMove(state, x, y) / onUp(state, x, y),
// all called with integer pixel coordinates (already floor()'d by the caller).
// Drawing tools use layer.history.beginStroke()/commitStroke() so each full
// drag is a single Undo/Redo step, matching the Java version's behavior.
var PSE = window.PSE || (window.PSE = {});
PSE.Tools = {};

function paintPixel(layer, x, y, color) {
  layer.setPixel(x, y, color);
}

function drawLineOnLayer(layer, x0, y0, x1, y1, color) {
  var pts = PSE.LineUtil.bresenham(x0, y0, x1, y1);
  for (var i = 0; i < pts.length; i++) paintPixel(layer, pts[i].x, pts[i].y, color);
}

function floodFill(layer, x, y, color) {
  var target = layer.getPixel(x, y);
  if (!target) return;
  if (target.r === color.r && target.g === color.g && target.b === color.b && target.a === color.a) return;
  var stack = [{ x: x, y: y }];
  var w = layer.width, h = layer.height;
  var visited = new Uint8Array(w * h);
  while (stack.length) {
    var p = stack.pop();
    if (p.x < 0 || p.y < 0 || p.x >= w || p.y >= h) continue;
    var idx = p.y * w + p.x;
    if (visited[idx]) continue;
    var px = layer.getPixel(p.x, p.y);
    if (px.r !== target.r || px.g !== target.g || px.b !== target.b || px.a !== target.a) continue;
    visited[idx] = 1;
    paintPixel(layer, p.x, p.y, color);
    stack.push({ x: p.x + 1, y: p.y });
    stack.push({ x: p.x - 1, y: p.y });
    stack.push({ x: p.x, y: p.y + 1 });
    stack.push({ x: p.x, y: p.y - 1 });
  }
}

// ---- Pencil / Eraser --------------------------------------------------------

function makeDrawingTool(colorForState) {
  var lastPoint = null;
  return {
    onDown: function (state, x, y) {
      var layer = state.project.activeLayer();
      layer.history.beginStroke(layer.imageData);
      var color = colorForState(state);
      paintPixel(layer, x, y, color);
      layer.commitPixels();
      lastPoint = { x: x, y: y };
      state.notifyPixelsChanged();
    },
    onMove: function (state, x, y) {
      if (!lastPoint) return;
      var layer = state.project.activeLayer();
      var color = colorForState(state);
      drawLineOnLayer(layer, lastPoint.x, lastPoint.y, x, y, color);
      layer.commitPixels();
      lastPoint = { x: x, y: y };
      state.notifyPixelsChanged();
    },
    onUp: function (state) {
      if (!lastPoint) return;
      var layer = state.project.activeLayer();
      layer.history.commitStroke(layer.imageData);
      lastPoint = null;
      state.notifyStructureChanged();
    },
    onCancel: function () { lastPoint = null; }
  };
}

PSE.Tools.pencil = makeDrawingTool(function (state) { return state.currentColor; });
PSE.Tools.eraser = makeDrawingTool(function () { return { r: 0, g: 0, b: 0, a: 0 }; });

// ---- Fill --------------------------------------------------------------------

PSE.Tools.fill = {
  onDown: function (state, x, y) {
    var layer = state.project.activeLayer();
    layer.history.beginStroke(layer.imageData);
    floodFill(layer, x, y, state.currentColor);
    layer.commitPixels();
    layer.history.commitStroke(layer.imageData);
    state.notifyPixelsChanged();
    state.notifyStructureChanged();
  },
  onMove: function () {},
  onUp: function () {},
  onCancel: function () {}
};

// ---- Eyedropper ----------------------------------------------------------------

PSE.Tools.eyedropper = {
  onDown: function (state, x, y) {
    var layer = state.project.activeLayer();
    var px = layer.getPixel(x, y);
    if (px) state.setCurrentColor(px);
  },
  onMove: function () {},
  onUp: function () {},
  onCancel: function () {}
};

// ---- Line ------------------------------------------------------------------

PSE.Tools.line = (function () {
  var start = null;
  return {
    onDown: function (state, x, y) {
      start = { x: x, y: y };
      state.linePreview = { x0: x, y0: y, x1: x, y1: y };
      state.notifyPixelsChanged();
    },
    onMove: function (state, x, y) {
      if (!start) return;
      state.linePreview = { x0: start.x, y0: start.y, x1: x, y1: y };
      state.notifyPixelsChanged();
    },
    onUp: function (state, x, y) {
      if (!start) return;
      var layer = state.project.activeLayer();
      layer.history.beginStroke(layer.imageData);
      drawLineOnLayer(layer, start.x, start.y, x, y, state.currentColor);
      layer.commitPixels();
      layer.history.commitStroke(layer.imageData);
      start = null;
      state.linePreview = null;
      state.notifyPixelsChanged();
      state.notifyStructureChanged();
    },
    onCancel: function (state) { start = null; state.linePreview = null; }
  };
})();

// ---- Selection -----------------------------------------------------------------

PSE.Tools.select = (function () {
  var start = null;
  function normalize(x0, y0, x1, y1, w, h) {
    var minX = Math.max(0, Math.min(x0, x1));
    var minY = Math.max(0, Math.min(y0, y1));
    var maxX = Math.min(w - 1, Math.max(x0, x1));
    var maxY = Math.min(h - 1, Math.max(y0, y1));
    if (maxX < minX || maxY < minY) return null;
    return { x: minX, y: minY, w: maxX - minX + 1, h: maxY - minY + 1 };
  }
  return {
    onDown: function (state, x, y) {
      start = { x: x, y: y };
      var w = state.project.width, h = state.project.height;
      state.setSelection(normalize(x, y, x, y, w, h));
    },
    onMove: function (state, x, y) {
      if (!start) return;
      var w = state.project.width, h = state.project.height;
      state.setSelection(normalize(start.x, start.y, x, y, w, h));
    },
    onUp: function () { start = null; },
    onCancel: function () { start = null; }
  };
})();

// ---- Selection / clipboard operations (used by Edit menu & context menu) -------

PSE.SelectionOps = {
  activeRect: function (state) {
    if (state.selection) return state.selection;
    return { x: 0, y: 0, w: state.project.width, h: state.project.height };
  },

  copy: function (state) {
    var rect = this.activeRect(state);
    var layer = state.project.activeLayer();
    var data = layer.ctx.getImageData(rect.x, rect.y, rect.w, rect.h);
    state.clipboard = { w: rect.w, h: rect.h, imageData: data };
  },

  cut: function (state) {
    this.copy(state);
    this.deleteSelection(state);
  },

  deleteSelection: function (state) {
    var rect = this.activeRect(state);
    var layer = state.project.activeLayer();
    layer.history.beginStroke(layer.imageData);
    for (var yy = 0; yy < rect.h; yy++) {
      for (var xx = 0; xx < rect.w; xx++) {
        layer.setPixel(rect.x + xx, rect.y + yy, { r: 0, g: 0, b: 0, a: 0 });
      }
    }
    layer.commitPixels();
    layer.history.commitStroke(layer.imageData);
    state.notifyPixelsChanged();
    state.notifyStructureChanged();
  },

  fillSelection: function (state) {
    var rect = this.activeRect(state);
    var layer = state.project.activeLayer();
    var color = state.currentColor;
    layer.history.beginStroke(layer.imageData);
    for (var yy = 0; yy < rect.h; yy++) {
      for (var xx = 0; xx < rect.w; xx++) {
        layer.setPixel(rect.x + xx, rect.y + yy, color);
      }
    }
    layer.commitPixels();
    layer.history.commitStroke(layer.imageData);
    state.notifyPixelsChanged();
    state.notifyStructureChanged();
  },

  flip: function (state, horizontal) {
    var rect = this.activeRect(state);
    var layer = state.project.activeLayer();
    var src = layer.ctx.getImageData(rect.x, rect.y, rect.w, rect.h);
    var dst = layer.ctx.createImageData(rect.w, rect.h);
    for (var yy = 0; yy < rect.h; yy++) {
      for (var xx = 0; xx < rect.w; xx++) {
        var sx = horizontal ? (rect.w - 1 - xx) : xx;
        var sy = horizontal ? yy : (rect.h - 1 - yy);
        var si = (sy * rect.w + sx) * 4;
        var di = (yy * rect.w + xx) * 4;
        dst.data[di] = src.data[si];
        dst.data[di + 1] = src.data[si + 1];
        dst.data[di + 2] = src.data[si + 2];
        dst.data[di + 3] = src.data[si + 3];
      }
    }
    layer.history.beginStroke(layer.imageData);
    layer.ctx.putImageData(dst, rect.x, rect.y);
    layer.imageData = layer.ctx.getImageData(0, 0, layer.width, layer.height);
    layer.history.commitStroke(layer.imageData);
    state.notifyPixelsChanged();
    state.notifyStructureChanged();
  },

  selectAll: function (state) {
    state.setSelection({ x: 0, y: 0, w: state.project.width, h: state.project.height });
  },

  deselect: function (state) {
    state.clearSelection();
  },

  startPaste: function (state) {
    if (!state.clipboard) return;
    state.pasting = { x: 0, y: 0 };
    state.tool = "paste";
    state.emit("toolChanged", "paste");
    state.notifyPixelsChanged();
  },

  cancelPasteToPencil: function (state) {
    state.pasting = null;
    state.tool = "pencil";
    state.emit("toolChanged", "pencil");
    state.notifyPixelsChanged();
  },

  updatePastePosition: function (state, x, y) {
    if (!state.pasting) return;
    state.pasting.x = x;
    state.pasting.y = y;
    state.notifyPixelsChanged();
  },

  confirmPaste: function (state) {
    if (!state.pasting || !state.clipboard) return;
    var layer = state.project.activeLayer();
    layer.history.beginStroke(layer.imageData);
    var tmp = document.createElement("canvas");
    tmp.width = state.clipboard.w;
    tmp.height = state.clipboard.h;
    tmp.getContext("2d").putImageData(state.clipboard.imageData, 0, 0);
    layer.ctx.drawImage(tmp, state.pasting.x, state.pasting.y);
    layer.imageData = layer.ctx.getImageData(0, 0, layer.width, layer.height);
    layer.history.commitStroke(layer.imageData);
    state.pasting = null;
    state.tool = "pencil";
    state.emit("toolChanged", "pencil");
    state.notifyPixelsChanged();
    state.notifyStructureChanged();
  }
};
