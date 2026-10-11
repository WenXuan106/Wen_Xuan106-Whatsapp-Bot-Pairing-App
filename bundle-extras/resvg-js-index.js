// Android has no prebuilt native resvg, so this module offers the same `Resvg` class
// on top of @resvg/resvg-wasm. main.js loads the WASM once (initWasm) after the bot has started.
// Nothing heavy happens until the first image is drawn, so a problem here can never stop the bot from starting.
const fs = require("fs");
const path = require("path");

const FONT_DIR = path.join(__dirname, "..", "..", "..", "fonts");
let fontBuffers = null;
function fonts() {
  if (!fontBuffers) {
    fontBuffers = ["DejaVuSans.ttf", "DejaVuSans-Bold.ttf"]
      .map((f) => path.join(FONT_DIR, f))
      .filter((f) => fs.existsSync(f))
      .map((f) => new Uint8Array(fs.readFileSync(f)));
  }
  return fontBuffers;
}

class Resvg {
  constructor(svg, options = {}) {
    if (!global.__resvgWasmReady) {
      throw new Error("The image renderer isn't ready (it may be switched off, or failed to load - see the app's logs).");
    }
    const wasm = require("@resvg/resvg-wasm");
    const opts = Object.assign({}, options);
    // The native build reads fonts installed on the phone; the WASM build can't, so it gets the bundled ones.
    opts.font = Object.assign({}, options.font || {}, {
      loadSystemFonts: false,
      fontBuffers: fonts(),
      defaultFontFamily: "DejaVu Sans",
      sansSerifFamily: "DejaVu Sans",
      serifFamily: "DejaVu Sans",
      monospaceFamily: "DejaVu Sans",
    });
    this._inner = new wasm.Resvg(svg, opts);
  }
  render() { return this._inner.render(); }
  toString() { return this._inner.toString(); }
  get width() { return this._inner.width; }
  get height() { return this._inner.height; }
  free() { if (this._inner.free) this._inner.free(); }
}

module.exports = { Resvg };
