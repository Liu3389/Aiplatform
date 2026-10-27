#!/usr/bin/env bash
set -euo pipefail

# Build the complete RA2WEB browser client. The client is static; the large
# original-game resources stay on the verified public CDN and are not copied
# into the AI platform image or served by this host.
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPOSITORY="${RA2WEB_REPOSITORY:-https://github.com/ra2web/ra2web.github.io.git}"
BRANCH="${RA2WEB_BRANCH:-main}"
PINNED_COMMIT="${RA2WEB_COMMIT:-85d7661f906de774a841844e2f21f68f59951853}"
OUTPUT_DIR="${1:-${RA2WEB_OUTPUT:-/tmp/ra2web-dist}}"
BUILD_ROOT="$(mktemp -d /tmp/ra2web-build.XXXXXX)"
SOURCE_DIR="$BUILD_ROOT/source"

cleanup() {
  rm -rf "$BUILD_ROOT"
}
trap cleanup EXIT

echo "[RA2WEB] cloning $REPOSITORY#$BRANCH"
git clone --depth 1 --branch "$BRANCH" "$REPOSITORY" "$SOURCE_DIR" >/dev/null
actual_commit="$(git -C "$SOURCE_DIR" rev-parse HEAD)"
if [[ "$actual_commit" != "$PINNED_COMMIT" ]]; then
  echo "[RA2WEB] branch moved to $actual_commit; fetching pinned commit $PINNED_COMMIT"
  git -C "$SOURCE_DIR" fetch --depth 1 origin "$PINNED_COMMIT" >/dev/null
  git -C "$SOURCE_DIR" checkout --detach "$PINNED_COMMIT" >/dev/null
fi

# The upstream default resource source does not consistently expose CORS on
# the mix files. This source was verified in a real browser battle and keeps
# the game data off this server.
git -C "$SOURCE_DIR" apply "$PROJECT_ROOT/scripts/ra2web-official-config.patch"

for required_file in index.html config.json version.json js/app.js js/bot1.js res/werhdexp.mix; do
  if [[ ! -f "$SOURCE_DIR/$required_file" ]]; then
    echo "[RA2WEB] missing required file: $required_file" >&2
    exit 1
  fi
done

mkdir -p "$OUTPUT_DIR"
find "$OUTPUT_DIR" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +

# Copy the static deployment package but never publish the temporary Git
# metadata. The upstream package already contains the production-built
# JavaScript/WebAssembly client, so no new Maven/Node dependency is added.
find "$SOURCE_DIR" -mindepth 1 -maxdepth 1 ! -name .git -exec cp -R {} "$OUTPUT_DIR/" \;

echo "[RA2WEB] artifact: $OUTPUT_DIR"
echo "[RA2WEB] source commit: $(git -C "$SOURCE_DIR" rev-parse HEAD)"
echo "[RA2WEB] artifact size: $(du -sh "$OUTPUT_DIR" | awk '{print $1}')"
