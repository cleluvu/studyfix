const fs = require('fs');
const path = require('path');
const source = path.resolve('StudyFix');
const destination = path.resolve('.publish-studyfix/StudyFix');
const blockedDirs = new Set(['build','build-launcher','dist','data','private','.git','node_modules','__pycache__','.cache','.idea','.build']);
const blockedExtensions = new Set(['.db','.sqlite','.sqlite3','.p12','.pfx','.jks','.pem','.key','.log','.class','.zip','.docx','.pptx','.pdf']);
let count = 0;
function walk(folder) {
  for (const entry of fs.readdirSync(folder, {withFileTypes:true})) {
    const full = path.join(folder, entry.name);
    const rel = path.relative(source, full);
    if (entry.isDirectory()) {
      if (!blockedDirs.has(entry.name.toLowerCase())) walk(full);
      continue;
    }
    const name = entry.name.toLowerCase();
    const ext = path.extname(name);
    if ((name.startsWith('.env') && name !== '.env.example') || blockedExtensions.has(ext) || /-(wal|shm)$/.test(name)) continue;
    if (rel.split(path.sep)[0] === 'docs' && !new Set(['.md','.html','.css','.js','.json','.svg']).has(ext)) continue;
    const target = path.join(destination, rel);
    fs.mkdirSync(path.dirname(target), {recursive:true});
    fs.copyFileSync(full, target);
    count++;
  }
}
walk(source);
console.log('Source files copied: ' + count);
