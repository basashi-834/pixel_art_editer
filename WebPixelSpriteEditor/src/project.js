// Data model: Layer / Frame / Project. Pixel data lives at native (unscaled)
// resolution as ImageData, backed by an offscreen <canvas> used for compositing
// and for fast drawImage-based rendering into the on-screen CanvasPanel.
var PSE = window.PSE || (window.PSE = {});

var nextId = 1;
function newId() { return nextId++; }

// ---- Layer ----------------------------------------------------------------

PSE.Layer = function (width, height, name) {
  this.id = newId();
  this.name = name || "レイヤー";
  this.visible = true;
  this.width = width;
  this.height = height;
  this.canvas = document.createElement("canvas");
  this.canvas.width = width;
  this.canvas.height = height;
  this.ctx = this.canvas.getContext("2d", { willReadFrequently: true });
  this.imageData = this.ctx.createImageData(width, height);
  this.history = new PSE.History();
  this._flush();
};

PSE.Layer.prototype._flush = function () {
  this.ctx.putImageData(this.imageData, 0, 0);
};

PSE.Layer.prototype.getPixel = function (x, y) {
  if (x < 0 || y < 0 || x >= this.width || y >= this.height) return null;
  var i = (y * this.width + x) * 4;
  var d = this.imageData.data;
  return { r: d[i], g: d[i + 1], b: d[i + 2], a: d[i + 3] };
};

PSE.Layer.prototype.setPixel = function (x, y, color) {
  if (x < 0 || y < 0 || x >= this.width || y >= this.height) return;
  var i = (y * this.width + x) * 4;
  var d = this.imageData.data;
  d[i] = color.r; d[i + 1] = color.g; d[i + 2] = color.b; d[i + 3] = color.a;
};

PSE.Layer.prototype.commitPixels = function () {
  this._flush();
};

PSE.Layer.prototype.setImageData = function (imageData) {
  this.imageData = imageData;
  this._flush();
};

PSE.Layer.prototype.clone = function () {
  var copy = new PSE.Layer(this.width, this.height, this.name + " コピー");
  copy.setImageData(PSE.History.cloneImageData(this.imageData));
  copy.visible = this.visible;
  return copy;
};

PSE.Layer.prototype.resize = function (newWidth, newHeight, anchorX, anchorY) {
  // anchorX/anchorY in [0, 0.5, 1] describe where the existing content is
  // kept: 0 = left/top edge pinned, 0.5 = centered, 1 = right/bottom pinned.
  var offX = Math.round((newWidth - this.width) * anchorX);
  var offY = Math.round((newHeight - this.height) * anchorY);
  var newCanvas = document.createElement("canvas");
  newCanvas.width = newWidth;
  newCanvas.height = newHeight;
  var nctx = newCanvas.getContext("2d");
  nctx.imageSmoothingEnabled = false;
  nctx.drawImage(this.canvas, offX, offY);
  this.width = newWidth;
  this.height = newHeight;
  this.canvas = newCanvas;
  this.ctx = nctx;
  this.ctx.willReadFrequently = true;
  this.imageData = this.ctx.getImageData(0, 0, newWidth, newHeight);
  this.history = new PSE.History();
};

// ---- Frame ------------------------------------------------------------------

PSE.Frame = function (width, height) {
  this.id = newId();
  this.width = width;
  this.height = height;
  this.layers = [new PSE.Layer(width, height, "レイヤー 1")];
  this.activeLayerIndex = 0;
};

PSE.Frame.prototype.activeLayer = function () {
  return this.layers[this.activeLayerIndex];
};

PSE.Frame.prototype.clone = function () {
  var f = new PSE.Frame(this.width, this.height);
  f.layers = this.layers.map(function (l) { return l.clone(); });
  f.activeLayerIndex = this.activeLayerIndex;
  return f;
};

// Composites all visible layers (bottom to top) into the given canvas context
// at the given offset (used as-is for a single frame, offset for sprite sheets).
PSE.Frame.prototype.compositeInto = function (ctx, offsetX, offsetY) {
  offsetX = offsetX || 0;
  offsetY = offsetY || 0;
  ctx.clearRect(offsetX, offsetY, this.width, this.height);
  for (var i = 0; i < this.layers.length; i++) {
    var layer = this.layers[i];
    if (!layer.visible) continue;
    ctx.drawImage(layer.canvas, offsetX, offsetY);
  }
};

PSE.Frame.prototype.compositeToCanvas = function () {
  var canvas = document.createElement("canvas");
  canvas.width = this.width;
  canvas.height = this.height;
  this.compositeInto(canvas.getContext("2d"));
  return canvas;
};

PSE.Frame.prototype.resize = function (newWidth, newHeight, anchorX, anchorY) {
  this.layers.forEach(function (l) { l.resize(newWidth, newHeight, anchorX, anchorY); });
  this.width = newWidth;
  this.height = newHeight;
};

// ---- Palette ----------------------------------------------------------------

PSE.DEFAULT_PALETTE_COLORS = [
  { r: 0, g: 0, b: 0, a: 0 },
  { r: 0, g: 0, b: 0, a: 255 },
  { r: 255, g: 255, b: 255, a: 255 },
  { r: 136, g: 136, b: 136, a: 255 },
  { r: 224, g: 224, b: 224, a: 255 },
  { r: 172, g: 50, b: 50, a: 255 },
  { r: 217, g: 87, b: 99, a: 255 },
  { r: 226, g: 152, b: 68, a: 255 },
  { r: 243, g: 216, b: 92, a: 255 },
  { r: 106, g: 176, b: 76, a: 255 },
  { r: 52, g: 101, b: 164, a: 255 },
  { r: 106, g: 158, b: 221, a: 255 },
  { r: 130, g: 89, b: 176, a: 255 },
  { r: 200, g: 130, b: 190, a: 255 },
  { r: 121, g: 85, b: 61, a: 255 },
  { r: 219, g: 189, b: 155, a: 255 }
];

PSE.ColorPalette = function (name, editable, colors) {
  this.name = name;
  this.editable = editable !== false;
  this.colors = colors || [];
};

PSE.makeDefaultPalette = function () {
  return new PSE.ColorPalette("デフォルト", false, PSE.DEFAULT_PALETTE_COLORS.map(function (c) {
    return { r: c.r, g: c.g, b: c.b, a: c.a };
  }));
};

// ---- Project ------------------------------------------------------------------

PSE.Project = function (width, height) {
  this.width = width;
  this.height = height;
  this.frames = [new PSE.Frame(width, height)];
  this.activeFrameIndex = 0;
  this.palettes = [PSE.makeDefaultPalette()];
  this.activePaletteIndex = 0;
  this.recentColors = [];
};

PSE.Project.prototype.activeFrame = function () {
  return this.frames[this.activeFrameIndex];
};

PSE.Project.prototype.activeLayer = function () {
  return this.activeFrame().activeLayer();
};

PSE.Project.prototype.resizeCanvas = function (newWidth, newHeight, anchorX, anchorY) {
  this.frames.forEach(function (f) { f.resize(newWidth, newHeight, anchorX, anchorY); });
  this.width = newWidth;
  this.height = newHeight;
};

PSE.Project.prototype.addRecentColor = function (color) {
  var key = color.r + "," + color.g + "," + color.b + "," + color.a;
  this.recentColors = this.recentColors.filter(function (c) {
    return (c.r + "," + c.g + "," + c.b + "," + c.a) !== key;
  });
  this.recentColors.unshift({ r: color.r, g: color.g, b: color.b, a: color.a });
  if (this.recentColors.length > 24) this.recentColors.length = 24;
};
