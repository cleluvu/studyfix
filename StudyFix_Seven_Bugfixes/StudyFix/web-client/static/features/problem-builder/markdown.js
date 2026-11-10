import {marked} from "../../vendor/marked-17.0.5/marked.esm.js";
const tags=new Set('P BR HR H1 H2 H3 H4 H5 H6 STRONG B EM I DEL S UL OL LI BLOCKQUOTE PRE CODE TABLE THEAD TBODY TR TH TD A IMG'.split(' '));
const drop=new Set('SCRIPT STYLE IFRAME OBJECT EMBED SVG MATH FORM INPUT BUTTON TEXTAREA SELECT LINK META BASE TEMPLATE NOSCRIPT'.split(' '));
/** Build a fresh allowlisted tree, never insert the parser output directly. */
export function safeMarkdown(source){
 if(typeof DOMParser!=="function")return String(source||"").replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;");
 const parsed=new DOMParser().parseFromString(marked.parse(String(source||""),{gfm:true,breaks:false}),"text/html"),target=document.createElement("div");
 function copy(node,parent){
  if(node.nodeType===3){parent.appendChild(document.createTextNode(node.nodeValue));return;}if(node.nodeType!==1||drop.has(node.tagName))return;
  if(!tags.has(node.tagName)){for(const child of node.childNodes)copy(child,parent);return;}
  const el=document.createElement(node.tagName.toLowerCase());
  if(node.tagName==="A"){try{const raw=node.getAttribute("href")||"",url=new URL(raw,location.href),asset=raw.match(/^\/api\/problem-builder\/assets\/([a-f0-9-]{36})$/i);if(asset){el.dataset.builderDownload=asset[1];el.href="#";}else if(["http:","https:","mailto:"].includes(url.protocol)){el.setAttribute("href",url.href);el.setAttribute("rel","noopener noreferrer");}}catch(_){} }
  if(node.tagName==="IMG"){
   const src=node.getAttribute("src")||"",match=src.match(/^\/api\/problem-builder\/assets\/([a-f0-9-]{36})$/i);if(!match){parent.appendChild(document.createTextNode(node.getAttribute("alt")||"[Ảnh chưa được tải lên]"));return;}el.dataset.builderImage=match[1];el.setAttribute("alt",node.getAttribute("alt")||"Ảnh đề bài");el.setAttribute("loading","eager");
  }
  if(node.tagName==="CODE"){const cls=node.getAttribute("class")||"";if(/^language-[a-z0-9_-]{1,30}$/i.test(cls))el.className=cls;}
  if(["TD","TH"].includes(node.tagName)){const align=node.getAttribute("align");if(["left","right","center"].includes(align))el.style.textAlign=align;}
  if(node.hasAttribute("title"))el.title=node.getAttribute("title");for(const child of node.childNodes)copy(child,el);parent.appendChild(el);
 }
 for(const child of parsed.body.childNodes)copy(child,target);return target.innerHTML;
}
