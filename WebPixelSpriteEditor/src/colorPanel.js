// ColorPanel: RGBA sliders + numeric inputs, checkerboard preview, palette
// dropdown + CRUD (default palette is fixed; user palettes are editable),
// and a most-recently-used color strip.
var PSE = window.PSE || (window.PSE = {});

PSE.ColorPanel = function (state, root) {
  this.state = state;
  this.root = root;
  this.els = {
    sliderR: root.querySelector("#slider-r"),
    sliderG: root.querySelector("#slider-g"),
    sliderB: root.querySelector("#slider-b"),
    sliderA: root.querySelector("#slider-a"),
    numR: root.querySelector("#num-r"),
    numG: root.querySelector("#num-g"),
    numB: root.querySelector("#num-b"),
    numA: root.querySelector("#num-a"),
    preview: root.querySelector("#color-preview"),
    paletteSelect: root.querySelector("#palette-select"),
    paletteColors: root.querySelector("#palette-colors"),
    btnPaletteNew: root.querySelector("#btn-palette-new"),
    btnPaletteDelete: root.querySelector("#btn-palette-delete"),
    btnAddCurrentToPalette: root.querySelector("#btn-add-current-to-palette"),
    recentColors: root.querySelector("#recent-colors")
  };
  this._bind();
  this.renderAll();

  var self = this;
  state.on("colorChanged", function () { self.renderSliders(); self.renderPreview(); });
  state.on("recentColorsChanged", function () { self.renderRecent(); });
};

PSE.ColorPanel.prototype._bind = function () {
  var self = this;
  var state = this.state;

  function fromSliders() {
    return {
      r: parseInt(self.els.sliderR.value, 10),
      g: parseInt(self.els.sliderG.value, 10),
      b: parseInt(self.els.sliderB.value, 10),
      a: parseInt(self.els.sliderA.value, 10)
    };
  }
  function onSliderInput() {
    self._syncNumFromSlider();
    state.setCurrentColor(fromSliders());
  }
  [this.els.sliderR, this.els.sliderG, this.els.sliderB, this.els.sliderA].forEach(function (el) {
    el.addEventListener("input", onSliderInput);
    el.addEventListener("change", function () { state.commitCurrentColorToRecent(); });
  });

  function clamp255(v) {
    v = parseInt(v, 10);
    if (isNaN(v)) v = 0;
    return Math.max(0, Math.min(255, v));
  }
  function onNumChange() {
    var color = {
      r: clamp255(self.els.numR.value),
      g: clamp255(self.els.numG.value),
      b: clamp255(self.els.numB.value),
      a: clamp255(self.els.numA.value)
    };
    state.setCurrentColor(color);
    state.commitCurrentColorToRecent();
  }
  [this.els.numR, this.els.numG, this.els.numB, this.els.numA].forEach(function (el) {
    el.addEventListener("change", onNumChange);
  });

  this.els.btnPaletteNew.addEventListener("click", function () {
    var name = prompt("新しいパレット名を入力してください");
    if (!name) return;
    state.project.palettes.push(new PSE.ColorPalette(name, true, []));
    state.project.activePaletteIndex = state.project.palettes.length - 1;
    self.renderPalettes();
  });

  this.els.btnPaletteDelete.addEventListener("click", function () {
    var pal = state.project.palettes[state.project.activePaletteIndex];
    if (!pal || !pal.editable) return;
    if (!confirm("パレット「" + pal.name + "」を削除しますか?")) return;
    state.project.palettes.splice(state.project.activePaletteIndex, 1);
    state.project.activePaletteIndex = 0;
    self.renderPalettes();
  });

  this.els.btnAddCurrentToPalette.addEventListener("click", function () {
    var pal = state.project.palettes[state.project.activePaletteIndex];
    if (!pal || !pal.editable) return;
    pal.colors.push({ r: state.currentColor.r, g: state.currentColor.g, b: state.currentColor.b, a: state.currentColor.a });
    self.renderPalettes();
  });

  this.els.paletteSelect.addEventListener("change", function () {
    state.project.activePaletteIndex = parseInt(self.els.paletteSelect.value, 10);
    self.renderPalettes();
  });
};

PSE.ColorPanel.prototype._syncNumFromSlider = function () {
  this.els.numR.value = this.els.sliderR.value;
  this.els.numG.value = this.els.sliderG.value;
  this.els.numB.value = this.els.sliderB.value;
  this.els.numA.value = this.els.sliderA.value;
};

PSE.ColorPanel.prototype.renderAll = function () {
  this.renderSliders();
  this.renderPreview();
  this.renderPalettes();
  this.renderRecent();
};

PSE.ColorPanel.prototype.renderSliders = function () {
  var c = this.state.currentColor;
  this.els.sliderR.value = c.r; this.els.numR.value = c.r;
  this.els.sliderG.value = c.g; this.els.numG.value = c.g;
  this.els.sliderB.value = c.b; this.els.numB.value = c.b;
  this.els.sliderA.value = c.a; this.els.numA.value = c.a;
};

PSE.ColorPanel.prototype.renderPreview = function () {
  var c = this.state.currentColor;
  var el = this.els.preview;
  el.style.setProperty("--swatch-color", "rgba(" + c.r + "," + c.g + "," + c.b + "," + (c.a / 255) + ")");
};

PSE.ColorPanel.prototype.renderPalettes = function () {
  var state = this.state;
  var select = this.els.paletteSelect;
  select.innerHTML = "";
  state.project.palettes.forEach(function (p, i) {
    var opt = document.createElement("option");
    opt.value = i;
    opt.textContent = p.name;
    select.appendChild(opt);
  });
  select.value = state.project.activePaletteIndex;

  var pal = state.project.palettes[state.project.activePaletteIndex];
  this.els.btnPaletteDelete.disabled = !pal.editable;
  this.els.btnAddCurrentToPalette.disabled = !pal.editable;

  var container = this.els.paletteColors;
  container.innerHTML = "";
  var self = this;
  pal.colors.forEach(function (c, idx) {
    var onDelete = pal.editable ? function () {
      pal.colors.splice(idx, 1);
      self.renderPalettes();
    } : null;
    var sw = self._makeSwatch(c, function () {
      state.setCurrentColor(c);
      state.commitCurrentColorToRecent();
    }, onDelete);
    container.appendChild(sw);
  });
};

PSE.ColorPanel.prototype.renderRecent = function () {
  var state = this.state;
  var container = this.els.recentColors;
  container.innerHTML = "";
  var self = this;
  state.project.recentColors.forEach(function (c, idx) {
    var sw = self._makeSwatch(c, function () { state.setCurrentColor(c); }, function () {
      state.project.recentColors.splice(idx, 1);
      self.renderRecent();
    });
    container.appendChild(sw);
  });
};

// onDelete (optional): invoked on right-click (desktop) or long-press (touch),
// so removing a palette/recent color works without a mouse on iPhone/Android.
PSE.ColorPanel.prototype._makeSwatch = function (color, onClick, onDelete) {
  var sw = document.createElement("button");
  sw.type = "button";
  sw.className = "swatch";
  sw.style.setProperty("--swatch-color", "rgba(" + color.r + "," + color.g + "," + color.b + "," + (color.a / 255) + ")");
  sw.title = "rgba(" + color.r + "," + color.g + "," + color.b + "," + color.a + ")";
  var suppressClick = false;
  sw.addEventListener("click", function (e) {
    if (suppressClick) { suppressClick = false; return; }
    onClick(e);
  });
  if (onDelete) {
    sw.addEventListener("contextmenu", function (e) { e.preventDefault(); onDelete(); });
    var pressTimer = null;
    sw.addEventListener("pointerdown", function (e) {
      if (e.pointerType !== "touch") return;
      pressTimer = setTimeout(function () {
        pressTimer = null;
        suppressClick = true;
        onDelete();
      }, 550);
    });
    var cancelPress = function () { if (pressTimer) { clearTimeout(pressTimer); pressTimer = null; } };
    sw.addEventListener("pointerup", cancelPress);
    sw.addEventListener("pointerleave", cancelPress);
    sw.addEventListener("pointercancel", cancelPress);
  }
  return sw;
};
