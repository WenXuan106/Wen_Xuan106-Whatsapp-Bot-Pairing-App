// Entry point used by the Android app: prepares the environment, starts the bot (index.js),
// then loads the WASM image renderer used by the graph / weather / profile commands.
const fs = require("fs");

process.chdir(__dirname);
process.on("uncaughtException", (err) => console.error("[uncaughtException]", err && err.stack ? err.stack : err));
process.on("unhandledRejection", (err) => console.error("[unhandledRejection]", err && err.stack ? err.stack : err));

// This Node has no ICU (no Intl / time zones / TextDecoder "fatal"): add small stand-ins first.
require("./icu-shim.js");

// Android has no /tmp, and this Node ignores TMPDIR, so os.tmpdir() points at a folder that doesn't exist and every
// image/file upload (Baileys writes temp files there) fails with ENOENT. Use a folder inside the bot's own storage.
const path = require("path");
const os = require("os");
const tmpDir = path.join(__dirname, "tmp");
try { fs.mkdirSync(tmpDir, { recursive: true }); } catch (e) {}
os.tmpdir = () => tmpDir;
process.env.TMPDIR = tmpDir;

console.log("[main] starting bot on port", process.env.PORT || 3000);
require("./index.js");

(async () => {
  // Set BOT_NO_IMAGES=1 in the app's Settings if the bot ever crashes while this loads.
  if (process.env.BOT_NO_IMAGES === "1") {
    console.log("[main] image commands are switched off (BOT_NO_IMAGES=1)");
    return;
  }
  try {
    console.log("[main] loading the image renderer…");
    const resvg = require("@resvg/resvg-wasm");
    await resvg.initWasm(fs.readFileSync(require.resolve("@resvg/resvg-wasm/index_bg.wasm")));
    global.__resvgWasmReady = true;
    console.log("[main] image renderer ready");
  } catch (err) {
    console.error("[main] image renderer failed to load (graph/weather/profile images won't work):", err && err.message);
  }
})();
