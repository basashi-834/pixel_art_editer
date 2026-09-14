// Modal dialogs (New canvas / Resize canvas / Sprite sheet export) and the
// right-click context menu. Built as plain DOM (no <dialog> element) so
// styling and iOS Safari behavior stay fully under our control.
var PSE = window.PSE || (window.PSE = {});

// ---- Generic modal -----------------------------------------------------------

PSE.Modal = {
  open: function (opts) {
    var overlay = document.createElement("div");
    overlay.className = "modal-overlay";

    var modal = document.createElement("div");
    modal.className = "modal";

    var title = document.createElement("h3");
    title.textContent = opts.title;
    modal.appendChild(title);

    var body = document.createElement("div");
    body.className = "modal-body";
    if (typeof opts.body === "string") body.innerHTML = opts.body;
    else body.appendChild(opts.body);
    modal.appendChild(body);

    var actions = document.createElement("div");
    actions.className = "modal-actions";
    modal.appendChild(actions);

    overlay.appendChild(modal);
    document.body.appendChild(overlay);

    function close() {
      document.removeEventListener("keydown", onKey);
      overlay.remove();
    }
    function onKey(e) {
      if (e.key === "Escape") close();
    }
    document.addEventListener("keydown", onKey);
    overlay.addEventListener("click", function (e) {
      if (e.target === overlay) close();
    });

    (opts.buttons || []).forEach(function (btn) {
      var b = document.createElement("button");
      b.type = "button";
      b.textContent = btn.label;
      b.className = btn.primary ? "btn-primary" : "btn-secondary";
      b.addEventListener("click", function () {
        var shouldClose = btn.onClick ? btn.onClick() : true;
        if (shouldClose !== false) close();
      });
      actions.appendChild(b);
    });

    return { el: modal, body: body, close: close };
  }
};

// ---- New canvas ----------------------------------------------------------------

PSE.Dialogs = {};

PSE.Dialogs.newCanvas = function (state) {
  var body = document.createElement("div");
  body.className = "form-grid";
  body.innerHTML =
    '<label>幅 (px) <input type="number" id="dlg-new-w" min="1" max="2048" value="64"></label>' +
    '<label>高さ (px) <input type="number" id="dlg-new-h" min="1" max="2048" value="64"></label>' +
    '<p class="hint">16の倍数(例: 64x64)が格ゲー用スプライトのタイル構成に適しています。</p>';

  PSE.Modal.open({
    title: "新規キャンバス",
    body: body,
    buttons: [
      { label: "キャンセル" },
      {
        label: "作成", primary: true, onClick: function () {
          var w = parseInt(body.querySelector("#dlg-new-w").value, 10);
          var h = parseInt(body.querySelector("#dlg-new-h").value, 10);
          if (!w || !h || w < 1 || h < 1) return false;
          state.loadProject(new PSE.Project(w, h));
        }
      }
    ]
  });
};

// ---- Resize canvas ---------------------------------------------------------------

PSE.Dialogs.resizeCanvas = function (state) {
  var body = document.createElement("div");
  body.className = "form-grid";
  var w = state.project.width, h = state.project.height;
  body.innerHTML =
    '<label>幅 (px) <input type="number" id="dlg-rs-w" min="1" max="2048" value="' + w + '"></label>' +
    '<label>高さ (px) <input type="number" id="dlg-rs-h" min="1" max="2048" value="' + h + '"></label>' +
    '<p>基準位置</p>' +
    '<div class="anchor-grid" id="dlg-rs-anchor"></div>';

  var anchorGrid = body.querySelector("#dlg-rs-anchor");
  var selected = { ax: 0, ay: 0 }; // default: top-left, matches Java version
  for (var row = 0; row < 3; row++) {
    for (var col = 0; col < 3; col++) {
      (function (col, row) {
        var btn = document.createElement("button");
        btn.type = "button";
        btn.className = "anchor-cell" + (col === 0 && row === 0 ? " active" : "");
        btn.addEventListener("click", function () {
          anchorGrid.querySelectorAll(".anchor-cell").forEach(function (c) { c.classList.remove("active"); });
          btn.classList.add("active");
          selected.ax = col / 2;
          selected.ay = row / 2;
        });
        anchorGrid.appendChild(btn);
      })(col, row);
    }
  }

  PSE.Modal.open({
    title: "キャンバスをリサイズ",
    body: body,
    buttons: [
      { label: "キャンセル" },
      {
        label: "リサイズ", primary: true, onClick: function () {
          var nw = parseInt(body.querySelector("#dlg-rs-w").value, 10);
          var nh = parseInt(body.querySelector("#dlg-rs-h").value, 10);
          if (!nw || !nh || nw < 1 || nh < 1) return false;
          state.project.resizeCanvas(nw, nh, selected.ax, selected.ay);
          state.clearSelection();
          state.notifyStructureChanged();
          state.notifyPixelsChanged();
          state.emit("canvasResized");
        }
      }
    ]
  });
};

// ---- Sprite sheet export ---------------------------------------------------------

PSE.Dialogs.spriteSheetExport = function (state) {
  var body = document.createElement("div");
  body.className = "form-grid";
  var frameCount = state.project.frames.length;
  body.innerHTML =
    '<label>列数 <input type="number" id="dlg-sheet-cols" min="1" max="' + frameCount + '" value="' + frameCount + '"></label>' +
    '<p class="hint">全 ' + frameCount + ' フレームを、指定した列数でグリッド状に並べます。</p>';

  PSE.Modal.open({
    title: "スプライトシートとして書き出し",
    body: body,
    buttons: [
      { label: "キャンセル" },
      {
        label: "書き出し", primary: true, onClick: function () {
          var cols = parseInt(body.querySelector("#dlg-sheet-cols").value, 10);
          if (!cols || cols < 1) return false;
          PSE.FileIO.exportSpriteSheet(state, cols);
        }
      }
    ]
  });
};

// ---- Right-click context menu -----------------------------------------------------

PSE.ContextMenu = {
  init: function (state) {
    this.state = state;
    this.el = null;
    var self = this;
    document.addEventListener("click", function () { self.hide(); });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape") self.hide(); });
  },

  hide: function () {
    if (this.el) { this.el.remove(); this.el = null; }
  },

  showAt: function (clientX, clientY) {
    this.hide();
    var state = this.state;
    var hasSelection = !!state.selection;
    var items = [
      { label: "コピー", disabled: !hasSelection, action: function () { PSE.SelectionOps.copy(state); } },
      { label: "切り取り", disabled: !hasSelection, action: function () { PSE.SelectionOps.cut(state); } },
      { label: "貼り付け", disabled: !state.clipboard, action: function () { PSE.SelectionOps.startPaste(state); } },
      { label: "選択範囲を削除", disabled: !hasSelection, action: function () { PSE.SelectionOps.deleteSelection(state); } },
      { label: "選択範囲を塗りつぶし", disabled: !hasSelection, action: function () { PSE.SelectionOps.fillSelection(state); } },
      { label: "左右反転", action: function () { PSE.SelectionOps.flip(state, true); } },
      { label: "上下反転", action: function () { PSE.SelectionOps.flip(state, false); } },
      { label: "全て選択", action: function () { PSE.SelectionOps.selectAll(state); } },
      { label: "選択解除", disabled: !hasSelection, action: function () { PSE.SelectionOps.deselect(state); } }
    ];

    var menu = document.createElement("div");
    menu.className = "context-menu";
    items.forEach(function (item) {
      var el = document.createElement("button");
      el.type = "button";
      el.textContent = item.label;
      el.disabled = !!item.disabled;
      el.addEventListener("click", function (e) {
        e.stopPropagation();
        item.action();
        PSE.ContextMenu.hide();
      });
      menu.appendChild(el);
    });

    document.body.appendChild(menu);
    var mw = menu.offsetWidth, mh = menu.offsetHeight;
    var x = Math.min(clientX, window.innerWidth - mw - 4);
    var y = Math.min(clientY, window.innerHeight - mh - 4);
    menu.style.left = x + "px";
    menu.style.top = y + "px";
    this.el = menu;
  }
};
