import fs from 'node:fs/promises';
import {PresentationFile,FileBlob} from '@oai/artifact-tool';
const p=await PresentationFile.importPptx(await FileBlob.load('C:/Users/ADMIN/Downloads/StudyFix_Project_Overview_28_slides.pptx'));
const s=p.slides.items[13], sh=s.shapes.items[0];
for(const [label,o] of [['shapes',s.shapes],['shape',sh],['text',sh.text]]) console.log(label,Object.getOwnPropertyNames(Object.getPrototypeOf(o)));
console.log('text',String(sh.text),'position',sh.position);
console.log((await p.inspect({kind:'textbox,shape',target:{id:s.id},maxChars:3000})).ndjson);
console.log(p.help('*',{search:'connector add remove shape',maxChars:2500}));
await fs.writeFile('model-brief.json',JSON.stringify(p.toProto()).slice(0,1500));
