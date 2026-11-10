import fs from 'node:fs/promises';
import {PresentationFile,FileBlob} from '@oai/artifact-tool';
const p=await PresentationFile.importPptx(await FileBlob.load('C:/Users/ADMIN/Downloads/StudyFix_Project_Overview_28_slides.pptx'));
await fs.mkdir(new URL('./source/',import.meta.url),{recursive:true});
console.log('Imported slides:',p.slides.items.length);
await fs.writeFile(new URL('./source-inspection.json',import.meta.url),JSON.stringify(await p.inspect({kind:'slide,layout,textbox',maxChars:5000})));
for(let i=0;i<p.slides.items.length;i++){
 const b=await p.export({slide:p.slides.items[i],format:'png',scale:1});
 await fs.writeFile(new URL(`./source/${String(i+1).padStart(2,'0')}.png`,import.meta.url),new Uint8Array(await b.arrayBuffer()));
}
console.log('Source rendered');
