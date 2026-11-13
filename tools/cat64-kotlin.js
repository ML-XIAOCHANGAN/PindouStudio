
const fs=require('fs');
const dir = String.raw`F:\Agent-Program\项目09_(拼豆)\pindou-android\.batchout`;
const titles=['捂嘴','大哭','捧心','上班','无语','蝴蝶结','躺平','搓手','吐舌'];
const notes=[
 '捂着嘴憋笑，眼角挂着蓝眼泪',
 '抱头大哭，最经典的那一张',
 '捧着一颗红心，表白用',
 '黄帽子加领带，打工人的正装',
 '半睁着眼，什么都不想说',
 '蓝色蝴蝶结，今天也要可爱',
 '躺平摊成一张饼',
 '搓搓小手，等发工资',
 '吐舌头，欠揍但可爱',
];
let out = 'package com.pindou.studio.core\n\n';
out += '/**\n';
out += ' * 「月薪喵」系列模板。\n';
out += ' *\n';
out += ' * 由 App 内「以图生图」直接产出：尺寸 64、主色模式、无抖动、\n';
out += ' * 合并相近色 0.14、饱和度 +20、移除背景，未做任何二次修图。\n';
out += ' */\n';
out += 'object CatTemplates {\n\n';
out += '    val all: List<PatternTemplate> = listOf(\n';

for (let i=1;i<=9;i++){
  const f = dir + '\\' + String(i).padStart(2,'0') + '.txt';
  const lines = fs.readFileSync(f,'utf8').split(/\r?\n/);
  let size=''; const colors={}; const rows=[];
  for(const ln of lines){
    if(ln.startsWith('size ')) size = ln.slice(5);
    else if(ln.startsWith('color ')) { const p=ln.split(' '); colors[p[1]]=p[3]; }
    else if(ln.trim() && !ln.startsWith('###') && !ln.startsWith('_')) rows.push(ln);
  }
  out += '        PatternTemplate(\n';
  out += '            id = "mao-' + String(i).padStart(2,'0') + '",\n';
  out += '            name = "月薪喵 · ' + titles[i-1] + '",\n';
  out += '            note = "' + notes[i-1] + '",\n';
  out += '            rows = listOf(\n';
  rows.forEach(r => { out += '                "' + r + '",\n'; });
  out += '            ),\n';
  out += '            colors = mapOf(\n';
  Object.keys(colors).sort().forEach(k => { out += "                '" + k + "' to \"" + colors[k] + "\",\n"; });
  out += '            ),\n';
  out += '            category = "月薪喵",\n';
  out += '        ),\n';
}
out += '    )\n}\n';
fs.writeFileSync(String.raw`F:\Agent-Program\项目09_(拼豆)\pindou-android\.cattemplates.kt`, out);
console.log('generated', out.length, 'chars');
