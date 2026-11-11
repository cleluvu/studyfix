import assert from 'node:assert/strict';
import {eb,blankExam} from '../static/features/exam-bank/state.js';
import {sourcePool,weekQuestions,chooseRandomQuestions,availableSourceCount} from '../static/features/exam-bank/source-selection.js';
eb.exam=blankExam();eb.questions=[{id:'A',code:'A',title:'Mảng số',topic:'Mảng',bankScope:'PRACTICE'},{id:'B',code:'B',title:'Chuỗi',topic:'Chuỗi',bankScope:'PRACTICE'},{id:'C',code:'C',title:'Bài thi riêng',topic:'Mảng',bankScope:'EXAM_PRIVATE'},{id:'D',code:'D',title:'Legacy public',topic:'Java'}];
eb.weeks=[{id:'week1',exerciseIds:['A','B','C']},{id:'week2',exerciseIds:['A','D']}];eb.questionQuery='';
eb.source='all';assert.deepEqual(sourcePool().map(q=>q.id),['A','B','D']);
eb.source='private';assert.deepEqual(sourcePool().map(q=>q.id),['C']);
eb.source='weeks';eb.sourceWeekId='';assert.deepEqual(sourcePool(),[]);
eb.sourceWeekId='week1';assert.deepEqual(sourcePool().map(q=>q.id),['A','B']);assert.deepEqual(weekQuestions(eb.weeks[0]).map(q=>q.id),['A','B']);
eb.questionQuery='mang';assert.deepEqual(sourcePool().map(q=>q.id),['A']);eb.questionQuery='';
eb.exam.items=[{exerciseId:'A',points:20}];assert.equal(availableSourceCount(),1);assert.deepEqual(chooseRandomQuestions(sourcePool(),['A'],1).map(q=>q.id),['B']);
assert.throws(()=>chooseRandomQuestions(sourcePool(),['A'],2),/chỉ còn 1/);
for(const n of ['',0,-1,1.5,'abc'])assert.throws(()=>chooseRandomQuestions(sourcePool(),[],n));
const many=Array.from({length:220},(_,i)=>({id:String(i)}));assert.throws(()=>chooseRandomQuestions(many,Array.from({length:199},(_,i)=>String(i)),2),/200/);
for(let i=0;i<25;i++){const chosen=chooseRandomQuestions([...many,...many],['0','1','2'],20);assert.equal(chosen.length,20);assert.equal(new Set(chosen.map(q=>q.id)).size,20);assert.ok(chosen.every(q=>!['0','1','2'].includes(q.id)));}
console.log('Exam sources PASS: scope isolation, Week pool, search, exact random count, deduplication and limits.');
