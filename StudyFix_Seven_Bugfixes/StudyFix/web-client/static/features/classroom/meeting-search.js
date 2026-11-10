import {state} from "../../core/state.js";
import {esc} from "../../shared/format.js";

export function meetingMatches(value,query){
  const normalize=text=>String(text||"").normalize("NFD").replace(/\p{M}/gu,"").replaceAll("đ","d").replaceAll("Đ","D").toLowerCase();
  const text=normalize(value);
  return normalize(query).trim().split(/\s+/).every(word=>text.includes(word));
}

export function meetingSearch(key,label){
  return `<label class="meeting-panel-search" for="meeting-search-${key}"><span>${esc(label)}</span><input type="search" id="meeting-search-${key}" data-meeting-search="${key}" maxlength="160" placeholder="${esc(label)}" value="${esc(state.meetingSearch?.[key]||"")}" autocomplete="off"></label>`;
}
