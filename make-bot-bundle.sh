#!/usr/bin/env bash
# Recreates assets/bot.zip: your bot, prepared to run on the Node 18 that is built into the app.
#   usage: ./make-bot-bundle.sh /path/to/Wen_Xuan106-Whatsapp-bot-main
# Needs: node + npm (any recent), python3, zip, and  apt install fonts-dejavu-core
# What it changes (the app's Node.js 18 has no ICU and can't load ES modules with require()):
#   - @resvg/resvg-js (native) is replaced by @resvg/resvg-wasm plus a small adapter, with DejaVu fonts
#   - Baileys is an ES module, so it is bundled to one CommonJS file with esbuild
#   - icu-shim.js + moment-timezone stand in for Intl / time zones / TextDecoder "fatal"
#   - the web server only listens on 127.0.0.1 (this phone), and unused or native packages are removed
set -euo pipefail
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
SRC=$(cd "${1:?path to the bot folder}" && pwd)
WORK=$(mktemp -d)
OUT=$WORK/bot
mkdir -p "$OUT"
( cd "$SRC" && tar --exclude=node_modules --exclude=.git --exclude=Dockerfile --exclude=fly.toml --exclude=render.yaml \
    --exclude=nixpacks.toml --exclude=auth_info_baileys --exclude=data --exclude=.env -cf - . ) | tar -xf - -C "$OUT"
cd "$OUT"

python3 - <<'PY'
import json
p = json.load(open('package.json'))
p['dependencies'].pop('@resvg/resvg-js', None)
p['dependencies']['@resvg/resvg-wasm'] = '2.6.2'
p['dependencies']['moment-timezone'] = '0.6.5'
p.pop('engines', None)
json.dump(p, open('package.json', 'w'), indent=2)
PY
rm -f package-lock.json
npm install --omit=dev --ignore-scripts --no-audit --no-fund --silent
rm -rf node_modules/sharp node_modules/@img node_modules/@types
rm -f node_modules/geo-tz/data/timezones.geojson.* node_modules/geo-tz/data/timezones-now.geojson.* \
      node_modules/geo-tz/dist/find-all.js node_modules/geo-tz/dist/find-now.js
rm -rf node_modules/moment/locale node_modules/moment/min node_modules/moment/src node_modules/moment/dist \
       node_modules/moment/ts3.1-typings node_modules/moment-timezone/builds

# Baileys (ES module) -> one CommonJS file
TOOLS=$WORK/tools; mkdir -p "$TOOLS"
( cd "$TOOLS" && npm init -y >/dev/null && npm i esbuild --silent )
B=node_modules/@whiskeysockets/baileys
"$TOOLS/node_modules/.bin/esbuild" $B/lib/index.js --bundle --platform=node --format=cjs --target=node18 \
  --outfile="$WORK/baileys.cjs" --external:pino --external:axios --external:ws --external:bufferutil \
  --external:utf-8-validate --external:sharp --external:jimp --external:link-preview-js --external:audio-decode \
  --external:@hapi/boom --log-level=warning
rm -rf $B/lib $B/WAProto $B/engine-requirements.js && mkdir $B/lib && mv "$WORK/baileys.cjs" $B/lib/index.cjs
python3 - <<'PY'
import json
f = 'node_modules/@whiskeysockets/baileys/package.json'
p = json.load(open(f))
p['main'] = 'lib/index.cjs'
for k in ('type', 'exports', 'scripts', 'engines'): p.pop(k, None)
json.dump(p, open(f, 'w'), indent=1)
PY

# only this phone may open the pairing page
python3 - <<'PY'
s = open('index.js').read()
assert 'app.listen(config.PORT, () => {' in s
s = s.replace('app.listen(config.PORT, () => {', 'app.listen(config.PORT, "127.0.0.1", () => {', 1)
open('index.js', 'w').write(s)
PY

# resvg adapter, fonts, ICU shim, entry point
mkdir -p fonts node_modules/@resvg/resvg-js
cp /usr/share/fonts/truetype/dejavu/DejaVuSans.ttf /usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf fonts/
cp "$SCRIPT_DIR/bundle-extras/resvg-js-index.js" node_modules/@resvg/resvg-js/index.js
cp "$SCRIPT_DIR/bundle-extras/resvg-js-package.json" node_modules/@resvg/resvg-js/package.json
cp "$SCRIPT_DIR/bundle-extras/icu-shim.js" icu-shim.js
cp "$SCRIPT_DIR/bundle-extras/main.js" main.js

find node_modules \( -name '*.map' -o -name '*.d.ts' \) -type f -delete
find node_modules -type f -iname '*.md' ! -iname 'LICENSE*' -delete
find node_modules -type d \( -name test -o -name tests -o -name __tests__ -o -name docs -o -name example -o -name examples \) -prune -exec rm -rf {} + 2>/dev/null || true
rm -f package-lock.json
mkdir -p "$SCRIPT_DIR/assets" && rm -f "$SCRIPT_DIR/assets/bot.zip"
zip -qr -X "$SCRIPT_DIR/assets/bot.zip" .
echo "assets/bot.zip written ($(du -h "$SCRIPT_DIR/assets/bot.zip" | cut -f1))"
echo "$OUT" > "$SCRIPT_DIR/.last-bundle-dir"
