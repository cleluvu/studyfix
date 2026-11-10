// Regression test with DOM/HTTP doubles; does not create a real contest.
import assert from "node:assert/strict";
let checks = 0;
const check = (value, message) => { assert.ok(value, message); checks++; };
const handlers = {};
const root = { innerHTML: "", addEventListener(type, handler) { handlers[type] = handler; } };
globalThis.sessionStorage = { getItem: () => null, setItem() {} };
globalThis.window = { addEventListener() {} };
globalThis.document = {
  title: "StudyFix", activeElement: null,
  querySelector: selector => selector === "#app" ? root : selector === "#toast-region" ? { appendChild() {} } : null,
  querySelectorAll: () => [], getElementById: () => null, addEventListener() {},
  createElement: () => ({ remove() {} }),
};
globalThis.setTimeout = () => 1;
const { state } = await import("../static/core/state.js");
const { renderContestCreator } = await import("../static/features/contests/contests.js");
const { registerPage } = await import("../static/core/router.js");
const { render } = await import("../static/ui/render.js");
await import("../static/core/events.js");
registerPage("contests", renderContestCreator);
Object.assign(state, {
  booting: false, token: "test", identity: { userId: "teacher", role: "TEACHER" },
  room: { roomId: "1" }, page: "contests", contestFormOpen: true,
  contestTitle: "", contestStart: 0, contestDuration: 60, contestSelected: [],
  catalog: [{ id: "A", title: "Example", mode: "BUILD_FROM_SCRATCH" }, { id: "B", title: "Other", mode: "BUILD_FROM_SCRATCH" }],
});
const form = { id: "contest-form" };
function field(name, value) { return { id: "", name, value, form, matches: () => false }; }
handlers.input({ target: field("title", 'Kiểm tra Java & "A"') });
check(state.contestTitle === 'Kiểm tra Java & "A"', "typing persists title before any rerender");
await handlers.change({ target: field("startInMinutes", "15") });
await handlers.change({ target: field("durationMinutes", "90") });
check(state.contestStart === 15 && state.contestDuration === 90, "select changes persist both timing values");
const checkbox = (id, checked) => ({ id: "", name: "", form, checked, dataset: { contestExercise: id }, matches: selector => selector === "[data-contest-exercise]" });
await handlers.change({ target: checkbox("A", true) });
check(state.contestSelected.includes("A"), "real delegated checkbox handler selects problem");
check(root.innerHTML.includes('value="Kiểm tra Java &amp; &quot;A&quot;"'), "checkbox rerender retains and escapes title");
check(root.innerHTML.includes('<option value="15" selected>'), "checkbox rerender retains delayed start");
check(root.innerHTML.includes('<option value="90" selected>'), "checkbox rerender retains duration");
await handlers.change({ target: checkbox("B", true) });
await handlers.change({ target: checkbox("A", false) });
render(); // Simulates an unrelated polling render while editing.
check(state.contestSelected.join() === "B" && root.innerHTML.includes('value="Kiểm tra Java &amp; &quot;A&quot;"'), "select/deselect and polling render do not clear title");
handlers.input({ target: field("startInMinutes", "60") });
handlers.input({ target: field("durationMinutes", "120") });
render();
check(root.innerHTML.includes('<option value="60" selected>') && root.innerHTML.includes('<option value="120" selected>'), "input events also preserve select values");
handlers.input({ target: { ...field("title", "Unrelated"), form: { id: "other-form" } } });
check(state.contestTitle === 'Kiểm tra Java & "A"', "other forms cannot overwrite contest draft");

// Read the values rendered into the form, then exercise the real submit handler.
globalThis.FormData = class {
  constructor() {
    const decode = text => text.replaceAll("&quot;", '"').replaceAll("&amp;", "&");
    this.values = { title: decode(root.innerHTML.match(/name="title"[^>]*value="([^"]*)"/)[1]) };
    for (const name of ["startInMinutes", "durationMinutes"]) {
      const options = root.innerHTML.match(new RegExp(`<select name="${name}"[^>]*>([\\s\\S]*?)</select>`))[1];
      this.values[name] = options.match(/value="([^"]*)" selected/)[1];
    }
  }
  get(name) { return this.values[name]; }
};
let submitted;
globalThis.fetch = async (_url, options) => {
  const body = JSON.parse(options.body);
  if (body.type === "CONTEST_CREATE") submitted = body;
  return { ok: true, status: 200, json: async () => ({ status: "OK", data: { contests: [] } }) };
};
await handlers.submit({ target: form, preventDefault() {} });
check(submitted?.title === 'Kiểm tra Java & "A"', "submit sends title retained after selecting problems");
check(submitted.startInMinutes === 60 && submitted.durationMinutes === 120, "submit sends retained timing");
check(submitted.exerciseIds.join() === "B" && !state.contestFormOpen, "successful submit sends selection and closes creator");
console.log(`Contest form: ${checks} checks passed`);
