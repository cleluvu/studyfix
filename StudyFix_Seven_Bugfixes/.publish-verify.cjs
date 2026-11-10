const fs = require('fs');
const path = require('path');
const cp = require('child_process');
const root = path.resolve('.publish-studyfix');
const files = cp.execFileSync('git',['-c','safe.directory='+root.replaceAll('\\','/'),'-C',root,'diff','--cached','--name-only','-z'], {encoding:'utf8'}).split('\0').filter(Boolean);
let verified = 0;
for (const file of files) {
  if (/(^|\/)\.env($|\.(?!example$))|\.(db|sqlite3?|p12|pfx|jks|pem|key|class|log)$|\/(build|build-launcher|dist|data|private|\.build)\//i.test(file)) throw new Error('Excluded file staged: '+file);
  if (file.startsWith('StudyFix/')) {
    if (!fs.readFileSync(file).equals(fs.readFileSync(path.join(root,file)))) throw new Error('Content differs: '+file);
    verified++;
  }
}
console.log('Verified '+verified+' updated project files match local source; no excluded files staged.');
