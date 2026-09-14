// LineUtil: Bresenham line interpolation, shared by Pencil/Eraser/Line tools.
var PSE = window.PSE || (window.PSE = {});

PSE.LineUtil = {
  // Returns an array of {x, y} integer points from (x0,y0) to (x1,y1) inclusive.
  bresenham: function (x0, y0, x1, y1) {
    x0 = Math.round(x0); y0 = Math.round(y0);
    x1 = Math.round(x1); y1 = Math.round(y1);
    var points = [];
    var dx = Math.abs(x1 - x0);
    var dy = -Math.abs(y1 - y0);
    var sx = x0 < x1 ? 1 : -1;
    var sy = y0 < y1 ? 1 : -1;
    var err = dx + dy;
    var x = x0, y = y0;
    while (true) {
      points.push({ x: x, y: y });
      if (x === x1 && y === y1) break;
      var e2 = 2 * err;
      if (e2 >= dy) { err += dy; x += sx; }
      if (e2 <= dx) { err += dx; y += sy; }
    }
    return points;
  }
};
