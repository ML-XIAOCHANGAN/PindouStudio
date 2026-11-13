
const zlib = require('zlib');
const fs = require('fs');

const crcTable = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c;
  }
  return t;
})();
const crc32 = (buf) => {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = crcTable[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
};
const chunk = (type, data) => {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
};
const encodePng = (w, h, rgba) => {
  const stride = w * 4;
  const raw = Buffer.alloc((stride + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (stride + 1)] = 0;
    rgba.copy(raw, y * (stride + 1) + 1, y * stride, y * stride + stride);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
};

const glyphs = [
  { c: [0x1E,0x6B,0x3A], rows: ["XXXXX..","XXXXXX.","XX...XX","XX...XX","XXXXXX.","XXXXX..","XX.....","XX.....","XX....."] },
  { c: [0xB5,0xDC,0x6B], rows: ["XXXXX..","XXXXXX.","XX...XX","XX...XX","XX...XX","XX...XX","XX...XX","XXXXXX.","XXXXX.."] },
  { c: [0xF2,0xA0,0x3C], rows: [".XXXXX.","XXXXXXX","XX.....","XX.....","XX..XXX","XX...XX","XX...XX","XXXXXXX",".XXXXX."] },
  { c: [0x63,0xB8,0xE8], rows: ["XXXXXXX","XXXXXXX","XX.....","XX.....","XXXXX..","XXXXX..","XX.....","XX.....","XX....."] },
];

const SIZE = 432, SS = 4;            // 4x 超采样
const W = SIZE * SS;
const cols = 7 * 4 + 3, rows = 9;
const blockW = W * 0.56;             // 自适应图标的圆形安全区约 61%，宽字标最多到 58%
const cell = blockW / cols;
const radius = cell * 0.485;   // 豆子几乎相切，连成厚实的笔画
const ox = (W - blockW) / 2;
const oy = (W - rows * cell) / 2;

const buf = Buffer.alloc(W * W * 4);   // 透明底
const put = (x, y, r, g, b, a) => {
  if (x < 0 || y < 0 || x >= W || y >= W || a <= 0) return;
  const i = (y * W + x) * 4;
  const sr = buf[i], sg = buf[i+1], sb = buf[i+2], sa = buf[i+3] / 255;
  const na = a + sa * (1 - a);
  if (na <= 0) return;
  buf[i]   = Math.round((r * a + sr * sa * (1 - a)) / na);
  buf[i+1] = Math.round((g * a + sg * sa * (1 - a)) / na);
  buf[i+2] = Math.round((b * a + sb * sa * (1 - a)) / na);
  buf[i+3] = Math.round(na * 255);
};

glyphs.forEach((gl, li) => {
  const oxL = ox + li * 8 * cell;
  gl.rows.forEach((row, r) => {
    [...row].forEach((ch, c) => {
      if (ch !== 'X') return;
      const cx = oxL + c * cell + cell / 2;
      const cy = oy + r * cell + cell / 2;
      for (let y = Math.floor(cy - radius - 1); y <= cy + radius + 1; y++) {
        for (let x = Math.floor(cx - radius - 1); x <= cx + radius + 1; x++) {
          const d = Math.hypot(x - cx, y - cy);
          const a = Math.min(1, Math.max(0, radius - d + 0.5));
          if (a > 0) put(x, y, gl.c[0], gl.c[1], gl.c[2], a);
        }
      }
      // 高光
      const hx = cx - radius * 0.34, hy = cy - radius * 0.36, hr = radius * 0.30;
      for (let y = Math.floor(hy - hr - 1); y <= hy + hr + 1; y++) {
        for (let x = Math.floor(hx - hr - 1); x <= hx + hr + 1; x++) {
          const d = Math.hypot(x - hx, y - hy);
          const a = Math.min(1, Math.max(0, hr - d + 0.5)) * 0.30;
          if (a > 0) put(x, y, 255, 255, 255, a);
        }
      }
    });
  });
});

// 降采样
const out = Buffer.alloc(SIZE * SIZE * 4);
for (let y = 0; y < SIZE; y++) {
  for (let x = 0; x < SIZE; x++) {
    let r = 0, g = 0, b = 0, a = 0, n = 0;
    for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) {
      const i = ((y * SS + sy) * W + (x * SS + sx)) * 4;
      const al = buf[i + 3] / 255;
      r += buf[i] * al; g += buf[i+1] * al; b += buf[i+2] * al; a += al; n++;
    }
    const o = (y * SIZE + x) * 4;
    const av = a / n;
    if (av > 0.0001) {
      out[o]   = Math.round(r / a);
      out[o+1] = Math.round(g / a);
      out[o+2] = Math.round(b / a);
      out[o+3] = Math.round(av * 255);
    }
  }
}

const dest = String.raw`F:\Agent-Program\项目09_(拼豆)\pindou-android\app\src\main\res\drawable-nodpi\ic_launcher_foreground.png`;
fs.mkdirSync(require('path').dirname(dest), { recursive: true });
fs.writeFileSync(dest, encodePng(SIZE, SIZE, out));
console.log('written', dest, fs.statSync(dest).size);
