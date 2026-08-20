const fs = require('fs');
const path = require('path');

const bedrockDir = path.join(
  __dirname,
  '..',
  'node_modules',
  'minecraft-data',
  'minecraft-data',
  'data',
  'bedrock'
);

// minecraft-data's supportsFeature.js unconditionally requires
// bedrock/common/features.json even when only Java Edition ("pc") is used,
// so `common` (and `latest`, which isn't a per-version dump) must stay.
// The per-version folders (1.21.80, 1.19.1, etc.) are the ~330MB of dead
// weight, since this bot only ever connects to Java Edition servers.
const KEEP = new Set(['common', 'latest']);

if (fs.existsSync(bedrockDir)) {
  let freed = false;
  for (const entry of fs.readdirSync(bedrockDir)) {
    if (KEEP.has(entry)) continue;
    fs.rmSync(path.join(bedrockDir, entry), { recursive: true, force: true });
    freed = true;
  }
  if (freed) {
    console.log('Pruned unused Bedrock Edition version data from minecraft-data (~330MB saved).');
  }
}
