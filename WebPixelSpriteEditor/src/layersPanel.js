// LayersPanel: list of layers in the active frame (top of list = topmost /
// frontmost layer, matching the Java version), with visibility toggle,
// add/duplicate/delete/reorder. Layer add/delete/reorder are intentionally
// NOT undoable, mirroring the desktop app's documented limitation.
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
};

PSE.LayersPanel.prototype.render = function () {
  var state = this.state;
  var frame = state.project.activeFrame();
  var listEl = this.listEl;
  listEl.innerHTML = "";

  // Render topmost-first (matches on-canvas stacking order in the README).
  for (var i = frame.layers.length - 1; i >= 0; i--) {
    (function (i) {
      var layer = frame.layers[i];
      var row = document.createElement("div");
      row.className = "layer-row" + (i === frame.activeLayerIndex ? " active" : "");

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
      listEl.appendChild(row);
    })(i);
  }

  this.btnDelete.disabled = frame.layers.length <= 1;
  this.btnUp.disabled = frame.activeLayerIndex >= frame.layers.length - 1;
  this.btnDown.disabled = frame.activeLayerIndex <= 0;
};
