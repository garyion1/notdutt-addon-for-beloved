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

if (fs.existsSync(bedrockDir)) {
  fs.rmSync(bedrockDir, { recursive: true, force: true });
  console.log('Pruned unused Bedrock Edition data from minecraft-data (~330MB saved).');
}
