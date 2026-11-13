
const fs=require('fs'), zlib=require('zlib');
const crcTable=(()=>{const t=new Int32Array(256);for(let n=0;n<256;n++){let c=n;for(let k=0;k<8;k++)c=c&1?0xedb88320^(c>>>1):c>>>1;t[n]=c;}return t;})();
const crc32=b=>{let c=0xffffffff;for(let i=0;i<b.length;i++)c=crcTable[(c^b[i])&0xff]^(c>>>8);return (c^0xffffffff)>>>0;};
const chunk=(type,data)=>{const len=Buffer.alloc(4);len.writeUInt32BE(data.length);const body=Buffer.concat([Buffer.from(type,'ascii'),data]);const crc=Buffer.alloc(4);crc.writeUInt32BE(crc32(body));return Buffer.concat([len,body,crc]);};
const encodePng=(w,h,rgba)=>{const stride=w*4;const raw=Buffer.alloc((stride+1)*h);for(let y=0;y<h;y++){raw[y*(stride+1)]=0;rgba.copy(raw,y*(stride+1)+1,y*stride,y*stride+stride);}const ihdr=Buffer.alloc(13);ihdr.writeUInt32BE(w,0);ihdr.writeUInt32BE(h,4);ihdr[8]=8;ihdr[9]=6;return Buffer.concat([Buffer.from([0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a]),chunk('IHDR',ihdr),chunk('IDAT',zlib.deflateSync(raw,{level:9})),chunk('IEND',Buffer.alloc(0))]);};

const dir = String.raw`F:\Agent-Program\项目09_(拼豆)\pindou-android\.batchout`;
const files = fs.readdirSync(dir).filter(f=>f.endsWith('.txt')).sort();
const parsed = files.map(f => {
  const lines = fs.readFileSync(dir + '\\' + f, 'utf8').split(/\r?\n/);
  let name='', size='', rows=[];
  const colors = {};
  for (const ln of lines) {
    if (ln.startsWith('### ')) name = ln.slice(4);
    else if (ln.startsWith('size ')) size = ln.slice(5);
    else if (ln.startsWith('color ')) { const p = ln.split(' '); colors[p[1]] = p[3]; }
    else if (ln.trim() && !ln.startsWith('_')) rows.push(ln);
  }
  const beads = rows.join('').split('').filter(c=>c!=='.').length;
  return { f, name, size, rows, colors, beads, colorsUsed: Object.keys(colors).length };
});

// 预览拼图
const S=9, PAD=6, cols=3;
const maxW = Math.max(...parsed.map(p=>p.rows[0].length));
const maxH = Math.max(...parsed.map(p=>p.rows.length));
const W = cols*(maxW*S+PAD)+PAD, H = Math.ceil(parsed.length/cols)*(maxH*S+PAD)+PAD;
const buf = Buffer.alloc(W*H*4);
for(let i=0;i<W*H;i++){buf[i*4]=246;buf[i*4+1]=244;buf[i*4+2]=238;buf[i*4+3]=255;}
parsed.forEach((p,n)=>{
  const ox = PAD + (n%cols)*(maxW*S+PAD);
  const oy = PAD + Math.floor(n/cols)*(maxH*S+PAD);
  p.rows.forEach((row,r)=>{ [...row].forEach((ch,c)=>{
    if(ch==='.') return;
    const hex = p.colors[ch]; if(!hex) return;
    const R=parseInt(hex.slice(1,3),16),G=parseInt(hex.slice(3,5),16),B=parseInt(hex.slice(5,7),16);
    for(let y=0;y<S-1;y++)for(let x=0;x<S-1;x++){
      const o=((oy+r*S+y)*W+(ox+c*S+x))*4; buf[o]=R;buf[o+1]=G;buf[o+2]=B;
    }
  });});
});
fs.writeFileSync(String.raw`F:\Agent-Program\项目09_(拼豆)\pindou-android\screenshots\cat64-all.png`, encodePng(W,H,buf));
console.log(parsed.map(p=>p.f+' '+p.size+' beads='+p.beads+' colors='+p.colorsUsed).join('\n'));
console.log('preview', W+'x'+H);
