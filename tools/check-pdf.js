
const fs = require('fs');
const zlib = require('zlib');
const buf = fs.readFileSync(process.argv[2]);
const text = buf.toString('latin1');
console.log('header:', text.slice(0, 8));
console.log('eof:', text.trimEnd().endsWith('%%EOF'));
console.log('mediabox:', /\/MediaBox\s*\[([^\]]+)\]/.exec(text)?.[1]?.trim());
console.log('pageobjs:', (text.match(/\/Type\s*\/Page\b/g) || []).length);
console.log('imageobjs:', (text.match(/\/Subtype\s*\/Image/g) || []).length);
// 解压内容流，确认有绘制指令
const re = /stream\r?\n/g;
let m, ok = 0, total = 0;
while ((m = re.exec(text)) !== null) {
  const start = m.index + m[0].length;
  const end = text.indexOf('endstream', start);
  if (end < 0) continue;
  total++;
  const raw = buf.slice(start, end);
  try {
    const inflated = zlib.inflateSync(raw).toString('latin1');
    if (/cm|Do|re|Tj/.test(inflated)) ok++;
  } catch (e) { /* 图片流可能是 DCT */ }
}
console.log('streams:', total, 'drawable/inflated:', ok);
