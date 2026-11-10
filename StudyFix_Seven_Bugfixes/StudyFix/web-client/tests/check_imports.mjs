// Kiểm tra các module trong static/: mỗi lệnh import phải tìm được
// đúng export trong file đích; các file không được import vòng lẫn nhau.
// Chạy tại thư mục web-client bằng lệnh: node tests/check_imports.mjs.
import { readFileSync, readdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const staticDir = path.join(here, "..", "static");

function walk(dir, base = dir, out = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, base, out);
    else if (entry.name.endsWith(".js")) out.push(path.relative(base, full));
  }
  return out;
}

const files = walk(staticDir).sort();
const importRe = /^import\s*\{([^}]*)\}\s*from\s*"([^"]+)";/gm;
const exportRe = /^export\s+(?:async\s+function|function|const)\s+([A-Za-z0-9_$]+)/gm;

const exportsByFile = {};
const importsByFile = {};

for (const f of files) {
  const text = readFileSync(path.join(staticDir, f), "utf8");
  exportsByFile[f] = new Set([...text.matchAll(exportRe)].map((m) => m[1]));
  for(const match of text.matchAll(/export\s*\{([^}]+)\}/g))for(const entry of match[1].split(",")){
    const name=entry.trim().split(/\s+as\s+/).at(-1);if(/^[A-Za-z_$][\w$]*$/.test(name))exportsByFile[f].add(name);
  }
  const deps = [];
  for (const m of text.matchAll(importRe)) {
    // Compare the exported name, not a local alias in `import {name as alias}`.
    const names = m[1].split(",").map((s) => s.trim().split(/\s+as\s+/)[0]).filter(Boolean);
    const spec = m[2];
    const target = path.normalize(path.join(path.dirname(f), spec));
    deps.push({ target, names });
  }
  importsByFile[f] = deps;
}

let problems = [];
for (const [f, deps] of Object.entries(importsByFile)) {
  for (const { target, names } of deps) {
    if (!(target in exportsByFile)) {
      problems.push(`${f}: imports from missing file ${target}`);
      continue;
    }
    for (const n of names) {
      if (!exportsByFile[target].has(n)) {
        problems.push(`${f}: imports '${n}' not exported by ${target}`);
      }
    }
  }
}

console.log(`Checked ${files.length} files.`);
if (problems.length) {
  console.log("MISSING-EXPORT PROBLEMS:");
  problems.forEach((p) => console.log(" -", p));
} else {
  console.log("No missing-export problems.");
}

// Dùng duyệt theo chiều sâu (DFS) để tìm vòng phụ thuộc.
const graph = {};
for (const [f, deps] of Object.entries(importsByFile)) {
  graph[f] = [...new Set(deps.map((d) => d.target))];
}
let hasCycle = false;
const visited = new Set();
function dfs(node, stack) {
  if (stack.includes(node)) {
    console.log("CYCLE:", [...stack.slice(stack.indexOf(node)), node].join(" -> "));
    hasCycle = true;
    return;
  }
  if (visited.has(node)) return;
  stack.push(node);
  for (const dep of graph[node] || []) dfs(dep, stack);
  stack.pop();
  visited.add(node);
}
for (const f of files) dfs(f, []);
console.log(hasCycle ? "CYCLES FOUND." : "No import cycles found.");

if (problems.length || hasCycle) process.exit(1);
