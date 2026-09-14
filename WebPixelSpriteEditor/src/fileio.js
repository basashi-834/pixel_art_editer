// FileIO: PNG open/save (composite of the active frame) and a JSON-based
// project format (frames/layers/palettes, one PNG data URL per layer).
//
// Note: this is a different container format from the Java version's
// .pxproj (a real ZIP written with java.util.zip). Implementing our own ZIP
// writer/reader would mean hand-rolling compression and CRC32 with no
// external library; a JSON file with embedded PNG data URLs gets the same
// "everything but pixels stays human-inspectable, no dependencies" property
// using only built-in browser APIs (canvas.toDataURL / Image), so that is
// what this Web version uses. See WebPixelSpriteEditor/README.md.
var PSE = window.PSE || (window.PSE = {});
PSE.FileIO = {};

function downloadBlob(blob, filename) {
  var url = URL.createObjectURL(blob);
  var a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
}

function canvasToBlob(canvas) {
  return new Promise(function (resolve) {
    canvas.toBlob(function (blob) { resolve(blob); }, "image/png");
  });
}

function loadImage(src) {
  return new Promise(function (resolve, reject) {
    var img = new Image();
    img.onload = function () { resolve(img); };
    img.onerror = reject;
    img.src = src;
  });
}

function pickFile(accept) {
  return new Promise(function (resolve) {
    var input = document.createElement("input");
    input.type = "file";
    input.accept = accept;
    input.addEventListener("change", function () {
      resolve(input.files[0] || null);
    });
    input.click();
  });
}

function readAsText(file) {
  return new Promise(function (resolve, reject) {
    var reader = new FileReader();
    reader.onload = function () { resolve(reader.result); };
    reader.onerror = reject;
    reader.readAsText(file);
  });
}

// ---- PNG -----------------------------------------------------------------------

PSE.FileIO.savePNG = function (state) {
  var frame = state.project.activeFrame();
  var canvas = frame.compositeToCanvas();
  canvasToBlob(canvas).then(function (blob) {
    downloadBlob(blob, "sprite.png");
  });
};

PSE.FileIO.openPNG = function (state) {
  pickFile("image/png,image/*").then(function (file) {
    if (!file) return;
    var url = URL.createObjectURL(file);
    loadImage(url).then(function (img) {
      URL.revokeObjectURL(url);
      var project = new PSE.Project(img.naturalWidth, img.naturalHeight);
      var layer = project.activeLayer();
      layer.ctx.drawImage(img, 0, 0);
      layer.imageData = layer.ctx.getImageData(0, 0, layer.width, layer.height);
      state.loadProject(project);
    });
  });
};

// ---- Sprite sheet export ----------------------------------------------------------

PSE.FileIO.exportSpriteSheet = function (state, columns) {
  var project = state.project;
  var w = project.width, h = project.height;
  var frameCount = project.frames.length;
  var cols = Math.max(1, Math.min(columns, frameCount));
  var rows = Math.ceil(frameCount / cols);
  var canvas = document.createElement("canvas");
  canvas.width = w * cols;
  canvas.height = h * rows;
  var ctx = canvas.getContext("2d");
  project.frames.forEach(function (frame, i) {
    var col = i % cols, row = Math.floor(i / cols);
    frame.compositeInto(ctx, col * w, row * h);
  });
  canvasToBlob(canvas).then(function (blob) {
    downloadBlob(blob, "spritesheet.png");
  });
};

// ---- Project (.pxproj.json) -------------------------------------------------------

PSE.FileIO.saveProject = function (state) {
  var project = state.project;
  var data = {
    format: "pixel-sprite-editor-web-project",
    version: 1,
    width: project.width,
    height: project.height,
    activeFrameIndex: project.activeFrameIndex,
    activePaletteIndex: project.activePaletteIndex,
    recentColors: project.recentColors,
    palettes: project.palettes.map(function (p) {
      return { name: p.name, editable: p.editable, colors: p.colors };
    }),
    frames: project.frames.map(function (frame) {
      return {
        activeLayerIndex: frame.activeLayerIndex,
        layers: frame.layers.map(function (layer) {
          return { name: layer.name, visible: layer.visible, png: layer.canvas.toDataURL("image/png") };
        })
      };
    })
  };
  var blob = new Blob([JSON.stringify(data)], { type: "application/json" });
  downloadBlob(blob, "project.pxproj.json");
};

PSE.FileIO.openProject = function (state) {
  pickFile(".json,application/json,.pxproj.json").then(function (file) {
    if (!file) return;
    readAsText(file).then(function (text) {
      var data = JSON.parse(text);
      return PSE.FileIO._projectFromData(data).then(function (project) {
        state.loadProject(project);
      });
    }).catch(function (err) {
      alert("プロジェクトファイルの読み込みに失敗しました: " + err);
    });
  });
};

PSE.FileIO._projectFromData = function (data) {
  var project = new PSE.Project(data.width, data.height);
  project.activeFrameIndex = data.activeFrameIndex || 0;
  project.activePaletteIndex = data.activePaletteIndex || 0;
  project.recentColors = data.recentColors || [];
  project.palettes = (data.palettes || []).map(function (p) {
    return new PSE.ColorPalette(p.name, p.editable, p.colors);
  });
  if (project.palettes.length === 0) project.palettes = [PSE.makeDefaultPalette()];

  var framePromises = data.frames.map(function (frameData) {
    var frame = new PSE.Frame(data.width, data.height);
    frame.activeLayerIndex = frameData.activeLayerIndex || 0;
    var layerPromises = frameData.layers.map(function (layerData) {
      return loadImage(layerData.png).then(function (img) {
        var layer = new PSE.Layer(data.width, data.height, layerData.name);
        layer.visible = layerData.visible;
        layer.ctx.drawImage(img, 0, 0);
        layer.imageData = layer.ctx.getImageData(0, 0, layer.width, layer.height);
        return layer;
      });
    });
    return Promise.all(layerPromises).then(function (layers) {
      frame.layers = layers;
      return frame;
    });
  });

  return Promise.all(framePromises).then(function (frames) {
    project.frames = frames;
    return project;
  });
};
