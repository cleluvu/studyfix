import {eb,normalize} from './state.js';

export function practiceQuestions(){return eb.questions.filter(q=>(q.bankScope||'PRACTICE')==='PRACTICE');}
export function weekQuestions(week){const ids=new Set(week?.exerciseIds||[]);return practiceQuestions().filter(q=>ids.has(q.id));}
export function sourcePool(){
 const questions=eb.source==='weeks'?weekQuestions(eb.weeks.find(w=>w.id===eb.sourceWeekId)):eb.questions.filter(q=>(q.bankScope||'PRACTICE')===(eb.source==='private'?'EXAM_PRIVATE':'PRACTICE'));
 const needle=normalize(eb.questionQuery);
 return questions.filter(q=>normalize(q.code+' '+q.title+' '+q.topic).includes(needle));
}
export function availableSourceCount(){const selected=new Set(eb.exam.items.map(i=>i.exerciseId));return sourcePool().filter(q=>!selected.has(q.id)).length;}
function randomIndex(bound){
 const buffer=new Uint32Array(1),limit=2**32-(2**32%bound);let value;
 do{crypto.getRandomValues(buffer);value=buffer[0];}while(value>=limit);
 return value%bound;
}
export function chooseRandomQuestions(questions,selectedIds,count){
 const n=Number(count),selected=new Set(selectedIds);
 if(String(count).trim()===''||!Number.isInteger(n)||n<1)throw Error('Nhập số lượng bài là số nguyên lớn hơn 0.');
 const pool=[...new Map(questions.filter(q=>!selected.has(q.id)).map(q=>[q.id,q])).values()];
 if(n>pool.length)throw Error(`Danh sách hiện tại chỉ còn ${pool.length} bài chưa chọn.`);
 if(selected.size+n>200)throw Error('Một bộ đề tối đa 200 câu.');
 for(let i=0;i<n;i++){const j=i+randomIndex(pool.length-i);[pool[i],pool[j]]=[pool[j],pool[i]];}
 return pool.slice(0,n);
}
