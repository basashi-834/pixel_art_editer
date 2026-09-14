// FramesPanel: frame thumbnail strip (add/duplicate/delete/reorder), an 8fps
// play/stop preview, and the onion-skin toggle. Frame add/delete/reorder are
// intentionally not undoable, mirroring the desktop app.
var PSE = window.PSE || (window.PSE = {});

PSE.FRAME_FPS = 8;

PSE.FramesPanel = function (state, root) {
  this.state = state;
  this.root = root;
  this.listEl = root.querySelector("#frames-list");
  this.btnAdd = root.querySelector("#btn-frame-add");
  this.btnDuplicate = root.querySelector("#btn-frame-duplicate");
  this.btnDelete = root.querySelector("#btn-frame-delete");
  this.btnLeft = root.querySelector("#btn-frame-left");
  this.btnRight = root.querySelector("#btn-frame-right");
  this.btnPlay = root.querySelector("#btn-frame-play");
  this.chkOnionSkin = root.querySelector("#chk-onion-skin");
  this.playTimer = null;

  this._bind();
  this.render();

  var self = this;
  ["structureChanged", "projectReplaced", "pixelsChanged"].forEach(function (ev) {
    state.on(ev, function () { self.render(); });
  });
};

PSE.FramesPanel.prototype._bind = function () {
  var state = this.state;
  var self = this;

  this.btnAdd.addEventListener("click", function () {
    self._stopPlaying();
    var p = state.project;
    var frame = new PSE.Frame(p.width, p.height);
    p.frames.push(frame);
    p.activeFrameIndex = p.frames.length - 1;
    state.notifyStructureChanged();
  });

  this.btnDuplicate.addEventListener("click", function () {
    self._stopPlaying();
    var p = state.project;
    var copy = p.activeFrame().clone();
    p.frames.splice(p.activeFrameIndex + 1, 0, copy);
    p.activeFrameIndex = p.activeFrameIndex + 1;
    state.notifyStructureChanged();
  });

  this.btnDelete.addEventListener("click", function () {
    self._stopPlaying();
    var p = state.project;
    if (p.frames.length <= 1) return;
    p.frames.splice(p.activeFrameIndex, 1);
    p.activeFrameIndex = Math.max(0, p.activeFrameIndex - 1);
    state.notifyStructureChanged();
  });

  this.btnLeft.addEventListener("click", function () {
    var p = state.project;
    var i = p.activeFrameIndex;
    if (i <= 0) return;
    var tmp = p.frames[i]; p.frames[i] = p.frames[i - 1]; p.frames[i - 1] = tmp;
    p.activeFrameIndex = i - 1;
    state.notifyStructureChanged();
  });

  this.btnRight.addEventListener("click", function () {
    var p = state.project;
    var i = p.activeFrameIndex;
    if (i >= p.frames.length - 1) return;
    var tmp = p.frames[i]; p.frames[i] = p.frames[i + 1]; p.frames[i + 1] = tmp;
    p.activeFrameIndex = i + 1;
    state.notifyStructureChanged();
  });

  this.btnPlay.addEventListener("click", function () {
    if (state.playing) self._stopPlaying(); else self._startPlaying();
  });

  this.chkOnionSkin.addEventListener("change", function () {
    state.onionSkin = self.chkOnionSkin.checked;
    state.notifyPixelsChanged();
  });
};

PSE.FramesPanel.prototype._startPlaying = function () {
  var state = this.state;
  state.playing = true;
  this.btnPlay.textContent = "■ 停止";
  var self = this;
  this.playTimer = setInterval(function () {
    var p = state.project;
    p.activeFrameIndex = (p.activeFrameIndex + 1) % p.frames.length;
    state.notifyStructureChanged();
  }, 1000 / PSE.FRAME_FPS);
};

PSE.FramesPanel.prototype._stopPlaying = function () {
  if (this.playTimer) { clearInterval(this.playTimer); this.playTimer = null; }
  this.state.playing = false;
  this.btnPlay.textContent = "▶ 再生";
};

PSE.FramesPanel.prototype.render = function () {
  var state = this.state;
  var p = state.project;
  var listEl = this.listEl;
  listEl.innerHTML = "";

  p.frames.forEach(function (frame, i) {
    var cell = document.createElement("button");
    cell.type = "button";
    cell.className = "frame-thumb" + (i === p.activeFrameIndex ? " active" : "");

    var thumb = document.createElement("canvas");
    thumb.width = 40; thumb.height = 40;
    var tctx = thumb.getContext("2d");
    tctx.imageSmoothingEnabled = false;
    var composite = frame.compositeToCanvas();
    tctx.drawImage(composite, 0, 0, 40, 40);

    var label = document.createElement("span");
    label.textContent = String(i + 1);

    cell.appendChild(thumb);
    cell.appendChild(label);
    cell.addEventListener("click", function () {
      p.activeFrameIndex = i;
      state.notifyStructureChanged();
    });
    listEl.appendChild(cell);
  });

  this.btnDelete.disabled = p.frames.length <= 1;
  this.btnLeft.disabled = p.activeFrameIndex <= 0;
  this.btnRight.disabled = p.activeFrameIndex >= p.frames.length - 1;
  this.chkOnionSkin.checked = state.onionSkin;
};
