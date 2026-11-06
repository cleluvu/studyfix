import assert from "node:assert/strict";
globalThis.sessionStorage={getItem:()=>null,setItem(){}};globalThis.document={querySelector:()=>null,getElementById:()=>null,addEventListener(){}};
const {randomPick}=await import("../static/features/contests/contest-assignment.js");
const source=Array.from({length:12},(_,id)=>({id}));const selected=randomPick(source,5);
assert.equal(selected.length,5);assert.equal(new Set(selected.map(p=>p.id)).size,5);assert.equal(source[0].id,0);assert.ok(selected.every(p=>source.includes(p)));
for(const count of [0,-1,13,1.5,NaN])assert.throws(()=>randomPick(source,count));
assert.equal(randomPick(source,source.length).length,12);
console.log("Contest assignment selection PASS: exact unique subset, valid pool, immutable input and invalid counts.");
