// Check the actual optimized APK/DEX, not debug classes or source code.
// Usage: node scripts/verify-ocr-release.cjs <apk-or-dex>
const fs = require('node:fs');
const zlib = require('node:zlib');
const expected = [
  'com.google.mlkit.vision.text.internal.TextRegistrar',
  'com.google.mlkit.vision.common.internal.VisionCommonRegistrar',
  'com.google.mlkit.common.internal.CommonComponentRegistrar',
];

function dexFiles(data) {
  if (data.subarray(0, 4).toString() === 'dex\n') return [data];
  let end = data.length - 22;
  while (end >= Math.max(0, data.length - 65557) && data.readUInt32LE(end) !== 0x06054b50) end--;
  if (end < Math.max(0, data.length - 65557)) throw Error('Not an APK/ZIP');
  let offset = data.readUInt32LE(end + 16);
  const files = [];
  for (let i = 0; i < data.readUInt16LE(end + 10); i++) {
    if (data.readUInt32LE(offset) !== 0x02014b50) throw Error('Invalid ZIP directory');
    const nameLength = data.readUInt16LE(offset + 28);
    const name = data.subarray(offset + 46, offset + 46 + nameLength).toString();
    if (/^classes(?:\d+)?\.dex$/.test(name)) {
      const local = data.readUInt32LE(offset + 42);
      const start = local + 30 + data.readUInt16LE(local + 26) + data.readUInt16LE(local + 28);
      const compressed = data.subarray(start, start + data.readUInt32LE(offset + 20));
      const method = data.readUInt16LE(offset + 10);
      if (method !== 0 && method !== 8) throw Error('Unsupported ZIP compression');
      files.push(method === 8 ? zlib.inflateRawSync(compressed) : compressed);
    }
    offset += 46 + nameLength + data.readUInt16LE(offset + 30) + data.readUInt16LE(offset + 32);
  }
  if (!files.length) throw Error('No DEX in APK');
  return files;
}

function constructors(dex) {
  let cursor = 0;
  const uleb = () => {
    let value = 0, shift = 0, byte;
    do { byte = dex[cursor++]; value |= (byte & 127) << shift; shift += 7;
      if (shift > 35) throw Error('Invalid DEX integer');
    } while (byte & 128);
    return value >>> 0;
  };
  const strings = Array.from({length:dex.readUInt32LE(56)}, (_, i) => {
    cursor = dex.readUInt32LE(dex.readUInt32LE(60) + i * 4);
    uleb();
    return dex.subarray(cursor, dex.indexOf(0, cursor)).toString();
  });
  const type = i => strings[dex.readUInt32LE(dex.readUInt32LE(68) + i * 4)];
  const result = new Set();
  for (let i = 0; i < dex.readUInt32LE(96); i++) {
    const cls = dex.readUInt32LE(100) + i * 32;
    const name = type(dex.readUInt32LE(cls)).slice(1, -1).replaceAll('/', '.');
    if (!expected.includes(name)) continue;
    cursor = dex.readUInt32LE(cls + 24);
    if (!cursor) continue;
    const statics = uleb(), instances = uleb(), direct = uleb(); uleb();
    for (let f = 0; f < statics + instances; f++) { uleb(); uleb(); }
    let methodIndex = 0;
    for (let m = 0; m < direct; m++) {
      methodIndex += uleb(); const flags = uleb(); const code = uleb();
      const method = dex.readUInt32LE(92) + methodIndex * 8;
      const proto = dex.readUInt32LE(76) + dex.readUInt16LE(method + 2) * 12;
      const params = dex.readUInt32LE(proto + 8);
      if (strings[dex.readUInt32LE(method + 4)] === '<init>' && (flags & 1) && code &&
          (!params || dex.readUInt32LE(params) === 0)) result.add(name);
    }
  }
  return result;
}

try {
  const found = new Set(dexFiles(fs.readFileSync(process.argv[2])).flatMap(dex => [...constructors(dex)]));
  const missing = expected.filter(name => !found.has(name));
  if (missing.length) throw Error('Missing public no-argument OCR registrar constructors:\n' + missing.join('\n'));
  console.log('PASS: all 3 ML Kit registrars retain public no-argument constructors.');
} catch (error) { console.error(error.message); process.exitCode = 1; }
