import {b} from "./state.js";
import {state} from "../../core/state.js";

// Authoring now lives in its own bank tab instead of a modal over the list.
export function renderBuilderDialog(){
 document.getElementById("builder-dialog")?.remove();
 document.body.classList.remove("builder-dialog-open");
 document.getElementById("app")?.removeAttribute("inert");
 if(state.page!=="exercises"||state.bankView!=="create"||state.identity?.role!=="TEACHER")b.open=false;
}
