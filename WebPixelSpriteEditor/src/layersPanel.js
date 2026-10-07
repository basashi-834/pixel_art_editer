// LayersPanel: list of layers in the active frame (top of list = topmost /
// frontmost layer, matching the Java version), with visibility toggle,
// add/duplicate/delete/reorder (buttons and drag & drop), a small preview
// thumbnail per row, and an opacity slider for the active layer. Layer
// add/delete/reorder are intentionally NOT undoable, mirroring the desktop
// app's documented limitation.
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
  this._dragIndex = null;
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
      row.draggable = true;

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
      row.addEventListener("click", function () {
        frame.activeLayerIndex = i;
        state.notifyStructureChanged();
      });

      row.addEventListener("dragstart", function (e) {
        self._dragIndex = i;
        row.classList.add("dragging");
        e.dataTransfer.effectAllowed = "move";
        e.dataTransfer.setData("text/plain", String(i));
      });
      row.addEventListener("dragend", function () {
        row.classList.remove("dragging");
        self._dragIndex = null;
      });
      row.addEventListener("dragover", function (e) {
        if (self._dragIndex == null) return;
        e.preventDefault();
        row.classList.add("drag-over");
      });
      row.addEventListener("dragleave", function () {
        row.classList.remove("drag-over");
      });
      row.addEventListener("drop", function (e) {
        e.preventDefault();
        row.classList.remove("drag-over");
        self._moveLayer(self._dragIndex, i);
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
