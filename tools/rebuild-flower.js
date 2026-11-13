
const zlib = require('zlib');
const fs = require('fs');
const rows = [
"...PPPPP...",
"..PPPPPPP..",
".PPPYYYPPP.",
".PPYYYYYPP.",
"PPPYYYYYPPP",
"PPPYYYYYPPP",
"PPPYYYYYPPP",
".PPYYYYYPP.",
".PPPYYYPPP.",
"..PPPPPPP..",
"...PPPPP...",
];
const W = 13, H = 11, off = 1;
const cells = Buffer.alloc(W * H * 2);
let beads = 0;
for (let r = 0; r < H; r++) {
  for (let c = 0; c < 11; c++) {
    const ch = rows[r][c];
    if (ch === '.') continue;
    const idx = r * W + (c + off);
    const v = ch === 'P' ? 1 : 2;
    cells[idx * 2] = v & 0xff;
    cells[idx * 2 + 1] = (v >> 8) & 0xff;
    beads++;
  }
}
const b64 = zlib.gzipSync(cells).toString('base64');
const dto = {
  version: 1,
  id: "p1794053129953-3798",
  name: "小花",
  createdAt: 1794053129953,
  updatedAt: 1794053129953,
  paletteId: "brand-mard",
  paletteName: "小花两色",
  paletteColors: [
    { code: "A1", name: "粉", hex: "#F5A3A5", series: "A", type: "normal", hex2: null },
    { code: "A2", name: "黄", hex: "#F5D06A", series: "A", type: "normal", hex2: null },
  ],
  width: W, height: H, cells: b64,
  baseImagePath: null, note: "", beadCount: beads, progress: "",
};
const dest = String.raw`F:\Agent-Program\项目09_(拼豆)\pindou-android\.flower.json`;
fs.writeFileSync(dest, JSON.stringify(dto), 'utf8');
console.log('beads', beads, 'json', fs.statSync(dest).size);
