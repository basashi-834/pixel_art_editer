// History: per-layer stroke-based Undo/Redo, using full-canvas ImageData snapshots.
// A "stroke" is any single user operation (a pencil drag, a fill click, a paste
// confirm, a flip, ...). beginStroke() must be called before the pixels change,
// commitStroke() after, once per operation.
var PSE = window.PSE || (window.PSE = {});

PSE.History = function (maxEntries) {
  this.maxEntries = maxEntries || 100;
  this.undoStack = [];
  this.redoStack = [];
  this.pending = null;
};

PSE.History.prototype.beginStroke = function (imageData) {
  this.pending = PSE.History.cloneImageData(imageData);
};

PSE.History.prototype.commitStroke = function (imageData) {
  if (!this.pending) return;
  if (PSE.History.imageDataEquals(this.pending, imageData)) {
    this.pending = null;
    return;
  }
  this.undoStack.push(this.pending);
  if (this.undoStack.length > this.maxEntries) this.undoStack.shift();
  this.redoStack = [];
  this.pending = null;
};

PSE.History.prototype.cancelStroke = function () {
  this.pending = null;
};

PSE.History.prototype.canUndo = function () {
  return this.undoStack.length > 0;
};

PSE.History.prototype.canRedo = function () {
  return this.redoStack.length > 0;
};

// current: current ImageData (will be pushed to the other stack).
// Returns the ImageData to restore, or null if nothing to do.
PSE.History.prototype.undo = function (current) {
  if (this.undoStack.length === 0) return null;
  this.redoStack.push(PSE.History.cloneImageData(current));
  return this.undoStack.pop();
};

PSE.History.prototype.redo = function (current) {
  if (this.redoStack.length === 0) return null;
  this.undoStack.push(PSE.History.cloneImageData(current));
  return this.redoStack.pop();
};

PSE.History.cloneImageData = function (imageData) {
  return new ImageData(new Uint8ClampedArray(imageData.data), imageData.width, imageData.height);
};

PSE.History.imageDataEquals = function (a, b) {
  if (a.width !== b.width || a.height !== b.height) return false;
  var da = a.data, db = b.data;
  if (da.length !== db.length) return false;
  for (var i = 0; i < da.length; i++) {
    if (da[i] !== db[i]) return false;
  }
  return true;
};
