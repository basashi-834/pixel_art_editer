// LayersPanel: list of layers in the active frame (top of list = topmost /
// frontmost layer, matching the Java version), with visibility toggle,
// add/duplicate/delete/reorder (buttons and drag & drop), a small preview
// thumbnail per row, and an opacity slider for the active layer. Layer
// add/delete/reorder are intentionally NOT undoable, mirroring the desktop
// app's documented limitation.
//
// Reordering uses Pointer Events rather than the native HTML5 drag-and-drop
// API (draggable/dragstart/dragover/drop): that API turned out unreliable
// here -- dragover kept firing but drop would silently never happen -- and
// it doesn't work at all for touch on iOS Safari, which this app targets.
// Pointer Events are the same mechanism CanvasPanel already uses for
// drawing, so this gets working mouse *and* touch reordering for free.
var PSE = window.PSE || (window.PSE = {});

PSE.LayersPanel = function (state, root) {
  this.state = state;
  this.root = root;
  this.listEl = root.querySelector("#layers-list");
  this.btnAdd = root.querySelector("#btn-layer-add");
  this.btnDuplicate = root.querySelector("#btn-layer-duplicate");
  this.btnDelete = root.querySelector("#btn-layer-delete");
  this.btnUp = root.querySelector("#btn-layer-up");
  this.btnDown = root.querySelector("#btn-layer-down");
  this.sliderOpacity = root.querySelector("#slider-layer-opacity");
  this.numOpacity = root.querySelector("#num-layer-opacity");

  // Drag-to-reorder state, tracked across the whole gesture (set in
  // pointerdown, read/updated in pointermove, consumed in pointerup).
  this._dragPointerId = null;
  this._dragFromIndex = null;
  this._dragStartX = 0;
  this._dragStartY = 0;
  this._dragMoved = false;
  this._dragOverRow = null;

  this._bind();
  this.render();

  var self = this;
  ["structureChanged", "projectReplaced", "pixelsChanged"].forEach(function (ev) {
    state.on(ev, function () { self.render(); });
  });
};

PSE.LayersPanel.prototype._bind = function () {
  var state = this.state;
  var self = this;

  this.btnAdd.addEventListener("click", function () {
    var frame = state.project.activeFrame();
    var layer = new PSE.Layer(frame.width, frame.height, "レイヤー " + (frame.layers.length + 1));
    frame.layers.push(layer);
    frame.activeLayerIndex = frame.layers.length - 1;
    state.notifyStructureChanged();
  });

  this.btnDuplicate.addEventListener("click", function () {
    var frame = state.project.activeFrame();
    var src = frame.activeLayer();
    var copy = src.clone();
    frame.layers.splice(frame.activeLayerIndex + 1, 0, copy);
    frame.activeLayerIndex = frame.activeLayerIndex + 1;
    state.notifyStructureChanged();
  });

  this.btnDelete.addEventListener("click", function () {
    var frame = state.project.activeFrame();
    if (frame.layers.length <= 1) return;
    frame.layers.splice(frame.activeLayerIndex, 1);
    frame.activeLayerIndex = Math.max(0, frame.activeLayerIndex - 1);
    state.notifyStructureChanged();
  });

  this.btnUp.addEventListener("click", function () {
    var frame = state.project.activeFrame();
    var i = frame.activeLayerIndex;
    if (i >= frame.layers.length - 1) return;
    var tmp = frame.layers[i]; frame.layers[i] = frame.layers[i + 1]; frame.layers[i + 1] = tmp;
    frame.activeLayerIndex = i + 1;
    state.notifyStructureChanged();
  });

  this.btnDown.addEventListener("click", function () {
    var frame = state.project.activeFrame();
    var i = frame.activeLayerIndex;
    if (i <= 0) return;
    var tmp = frame.layers[i]; frame.layers[i] = frame.layers[i - 1]; frame.layers[i - 1] = tmp;
    frame.activeLayerIndex = i - 1;
    state.notifyStructureChanged();
  });

  function applyOpacity(percent) {
    var frame = state.project.activeFrame();
    var layer = frame.activeLayer();
    layer.opacity = Math.max(0, Math.min(100, percent)) / 100;
    state.notifyPixelsChanged();
  }
  this.sliderOpacity.addEventListener("input", function () {
    self.numOpacity.value = self.sliderOpacity.value;
    applyOpacity(Number(self.sliderOpacity.value));
  });
  this.numOpacity.addEventListener("input", function () {
    self.sliderOpacity.value = self.numOpacity.value;
    applyOpacity(Number(self.numOpacity.value));
  });
};

// Reorders frame.layers by moving the layer at `from` to index `to`,
// keeping the active-layer selection following the moved layer.
PSE.LayersPanel.prototype._moveLayer = function (from, to) {
  var frame = this.state.project.activeFrame();
  if (from === to || from == null || to == null) return;
  var layers = frame.layers;
  if (from < 0 || from >= layers.length || to < 0 || to >= layers.length) return;
  var moved = layers.splice(from, 1)[0];
  layers.splice(to, 0, moved);
  frame.activeLayerIndex = layers.indexOf(moved);
  this.state.notifyStructureChanged();
};

// Which row (by layer index, via the row's data-layer-index attribute) a
// client-space point lands on, or null if none.
PSE.LayersPanel.prototype._rowAtPoint = function (clientX, clientY) {
  var rows = this.listEl.children;
  for (var i = 0; i < rows.length; i++) {
    var r = rows[i].getBoundingClientRect();
    if (clientX >= r.left && clientX <= r.right && clientY >= r.top && clientY <= r.bottom) {
      return rows[i];
    }
  }
  return null;
};

PSE.LayersPanel.prototype._setDragOverRow = function (row) {
  if (row === this._dragOverRow) return;
  if (this._dragOverRow) this._dragOverRow.classList.remove("drag-over");
  this._dragOverRow = row;
  if (row) row.classList.add("drag-over");
};

PSE.LayersPanel.prototype.render = function () {
  var state = this.state;
  var frame = state.project.activeFrame();
  var listEl = this.listEl;
  var self = this;
  listEl.innerHTML = "";

  // Render topmost-first (matches on-canvas stacking order in the README).
  for (var i = frame.layers.length - 1; i >= 0; i--) {
    (function (i) {
      var layer = frame.layers[i];
      var row = document.createElement("div");
      row.className = "layer-row" + (i === frame.activeLayerIndex ? " active" : "");
      row.dataset.layerIndex = String(i);
      row.style.touchAction = "none";

      var checkbox = document.createElement("input");
      checkbox.type = "checkbox";
      checkbox.checked = layer.visible;
      checkbox.addEventListener("click", function (e) { e.stopPropagation(); });
      checkbox.addEventListener("change", function () {
        layer.visible = checkbox.checked;
        state.notifyPixelsChanged();
      });

      var thumb = document.createElement("canvas");
      thumb.width = 32; thumb.height = 32;
      thumb.className = "layer-thumb";
      var tctx = thumb.getContext("2d");
      tctx.imageSmoothingEnabled = false;
      tctx.globalAlpha = layer.opacity == null ? 1 : layer.opacity;
      tctx.drawImage(layer.canvas, 0, 0, 32, 32);

      var label = document.createElement("span");
      label.className = "layer-name";
      label.textContent = layer.name;

      row.appendChild(checkbox);
      row.appendChild(thumb);
      row.appendChild(label);

      row.addEventListener("pointerdown", function (e) {
        if (e.target === checkbox || (e.button !== undefined && e.button !== 0)) return;
        self._dragPointerId = e.pointerId;
        self._dragFromIndex = i;
        self._dragStartX = e.clientX;
        self._dragStartY = e.clientY;
        self._dragMoved = false;
        row.setPointerCapture(e.pointerId);
      });
      row.addEventListener("pointermove", function (e) {
        if (self._dragPointerId !== e.pointerId || self._dragFromIndex == null) return;
        var dx = e.clientX - self._dragStartX, dy = e.clientY - self._dragStartY;
        if (!self._dragMoved) {
          if (Math.hypot(dx, dy) < 6) return; // small clicks shouldn't start a drag
          self._dragMoved = true;
          row.classList.add("dragging");
        }
        self._setDragOverRow(self._rowAtPoint(e.clientX, e.clientY));
      });
      row.addEventListener("pointerup", function (e) {
        if (self._dragPointerId !== e.pointerId) return;
        row.releasePointerCapture(e.pointerId);
        var from = self._dragFromIndex;
        var moved = self._dragMoved;
        var overRow = self._dragOverRow;
        self._dragPointerId = null;
        self._dragFromIndex = null;
        self._dragMoved = false;
        row.classList.remove("dragging");
        self._setDragOverRow(null);

        if (moved) {
          if (overRow) self._moveLayer(from, Number(overRow.dataset.layerIndex));
        } else {
          frame.activeLayerIndex = from;
          state.notifyStructureChanged();
        }
      });
      row.addEventListener("pointercancel", function (e) {
        if (self._dragPointerId !== e.pointerId) return;
        self._dragPointerId = null;
        self._dragFromIndex = null;
        self._dragMoved = false;
        row.classList.remove("dragging");
        self._setDragOverRow(null);
      });

      listEl.appendChild(row);
    })(i);
  }

  var activeLayer = frame.activeLayer();
  var opacityPercent = Math.round((activeLayer.opacity == null ? 1 : activeLayer.opacity) * 100);
  this.sliderOpacity.value = opacityPercent;
  this.numOpacity.value = opacityPercent;

  this.btnDelete.disabled = frame.layers.length <= 1;
  this.btnUp.disabled = frame.activeLayerIndex >= frame.layers.length - 1;
  this.btnDown.disabled = frame.activeLayerIndex <= 0;
};
