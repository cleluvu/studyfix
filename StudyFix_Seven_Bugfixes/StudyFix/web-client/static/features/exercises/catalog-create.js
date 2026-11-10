import {state} from "../../core/state.js";
const stateToken=()=>state.token;
import {assignment} from "../../core/api.js";
import {modal,clearModal} from "../../ui/modal.js";
import {esc} from "../../shared/format.js";
import {toast} from "../../ui/toast.js";

// Hộp thoại tự quản lý form để polling trang không làm mất nội dung đang nhập.
export function openCatalogCreate(onSaved,existing=null){
  const openedToken=stateToken();
  const field=(name,label,max,required=false,area=false,value="")=>`<label style="display:block;margin-bottom:14px">${label}${required?" *":""}${area?`<textarea class="field" style="min-height:90px" name="${name}" maxlength="${max}" ${required?"required":""}>${esc(value)}</textarea>`:`<input class="field" name="${name}" maxlength="${max}" ${required?"required":""} value="${esc(value)}">`}</label>`;
  modal(`<div class="modal-head"><div><h3>Thêm câu hỏi vào ngân hàng</h3><p>Bài mới dùng chung cho hệ thống. Các trường * bắt buộc.</p></div><button type="button" id="catalog-close" class="btn btn-soft">Đóng</button></div><form id="catalog-create-form"><div class="modal-body">
    ${field("id","Mã câu hỏi (chữ, số, _ hoặc -)",64,true)}${field("title","Tên câu hỏi",200,true)}${field("topic","Category / Chủ đề",100,true)}
    <label>Độ khó<select class="select" name="difficulty"><option>Dễ</option><option>Trung bình</option><option>Khó</option></select></label>
    <label>Loại bài<select class="select" name="mode"><option value="BUILD_FROM_SCRATCH">Lập trình từ đầu</option><option value="DEBUG_COLLAB">Sửa lỗi cộng tác</option></select></label>
    <div class="notice-box" id="catalog-mode-help" style="margin-top:16px"></div>
    ${field("statement",'<span id="catalog-statement-label">Đề bài</span>',12000,true,true)}${field("inputFormat","Định dạng đầu vào",4000,true,true)}${field("outputFormat","Định dạng đầu ra",4000,true,true)}${field("constraints","Ràng buộc",4000,false,true)}
    <fieldset id="catalog-build-fields" style="border:0;padding:0;margin:0"><legend><strong>Khung code cho bài lập trình từ đầu</strong></legend><p>Người học tự viết lời giải từ khung này. Có thể để trống nếu không cần code gợi ý.</p>${field("template","Khung code Main.java",32000,false,true,"public class Main {\n    public static void main(String[] args) {\n        // Viết code tại đây\n    }\n}")}</fieldset>
    <fieldset id="catalog-debug-fields" style="border:0;padding:0;margin:0" hidden disabled><legend><strong>Code lỗi để học sinh sửa</strong></legend><p>Đoạn code này được dùng làm code ban đầu trên Master và các nhánh cá nhân. Nhập chương trình Main.java có lỗi cần sửa.</p>${field("starterCode","Code có lỗi Main.java",32000,false,true)}</fieldset>
    ${field("hints",'<span id="catalog-hints-label">Gợi ý giải bài</span>',4000,false,true)}
    <h4 id="catalog-tests-label">Testcase chấm lời giải</h4><p id="catalog-tests-help"></p><div id="catalog-tests"></div><button type="button" id="catalog-add-test" class="btn btn-soft">+ Thêm testcase</button>
    <p id="catalog-error" role="alert" style="color:#b42318;white-space:pre-wrap"></p><button type="submit" class="btn btn-primary">Lưu câu hỏi</button></div></form>`);
  const form=document.getElementById("catalog-create-form"),list=form.querySelector("#catalog-tests"),error=form.querySelector("#catalog-error");
  const mode=form.querySelector('[name="mode"]');
  if(existing){for(const [name,value] of Object.entries(existing)){const el=form.querySelector(`[name="${name}"]`);if(el&&typeof value==="string")el.value=value;}form.querySelector('[name="id"]').value=existing.displayCode||existing.id;form.querySelector('[name="id"]').readOnly=true;document.querySelector("#active-modal h3").textContent="Sửa đề trong ngân hàng";}
  const publication=document.createElement("label");publication.innerHTML=`Trạng thái xuất bản<select class="select" name="publication"><option value="PUBLISHED">Đã xuất bản</option><option value="DRAFT">Bản nháp</option></select><small>Bản nháp chỉ hiển thị trong quản lý ngân hàng, chưa dùng để giao hoặc tạo kỳ thi.</small>`;mode.parentElement.after(publication);publication.querySelector("select").value=existing?.publication||"PUBLISHED";
  if(existing){const note=document.createElement("p");note.className="notice-box";note.textContent="Lưu thay đổi tạo phiên bản mới. Nội dung và testcase đã giao/thi vẫn được giữ nguyên.";publication.after(note);}
  const updateMode=()=>{
    const debug=mode.value==="DEBUG_COLLAB";
    for(const [id,visible] of [["catalog-build-fields",!debug],["catalog-debug-fields",debug]]){
      const section=form.querySelector(`#${id}`);section.hidden=!visible;section.disabled=!visible;
    }
    form.querySelector('[name="starterCode"]').required=debug;
    form.querySelector("#catalog-mode-help").textContent=debug
      ?"Sửa lỗi cộng tác: cung cấp chương trình có lỗi và yêu cầu sửa. Giáo viên dùng bài này cho luồng Master → nhánh cá nhân → kiểm tra → chọn lời giải để Merge."
      :"Lập trình từ đầu: cung cấp đề bài và dữ liệu vào/ra. Người học tự xây dựng lời giải; bài có thể dùng cho luyện code và kỳ thi cá nhân.";
    form.querySelector("#catalog-statement-label").textContent=debug?"Mô tả bài toán và yêu cầu sửa lỗi":"Đề bài";
    form.querySelector('[name="statement"]').placeholder=debug?"Mô tả kết quả đúng cần đạt và yêu cầu sửa chương trình bên dưới…":"Mô tả bài toán người học cần giải…";
    form.querySelector("#catalog-hints-label").textContent=debug?"Gợi ý tìm và sửa lỗi":"Gợi ý giải bài";
    form.querySelector("#catalog-tests-label").textContent=debug?"Testcase kiểm tra code sau khi sửa":"Testcase chấm lời giải";
    form.querySelector("#catalog-tests-help").textContent=(debug?"Output mong đợi là kết quả đúng sau khi sửa, không phải kết quả sai của code lỗi. ":"")+"Ít nhất một test công khai để chạy thử. Test ẩn chỉ dùng để chấm bài. Input/output có thể để trống.";
    error.textContent="";
  };
  mode.addEventListener("change",updateMode);updateMode();
  document.getElementById("catalog-close").onclick=clearModal;
  const add=()=>{
    if(list.children.length>=50){error.textContent="Tối đa 50 testcase.";return;}
    const row=document.createElement("fieldset");row.className="card-body";
    row.innerHTML=`<legend>Testcase</legend><label>Input<textarea class="field" name="testInput" maxlength="8000"></textarea></label><label>Output mong đợi<textarea class="field" name="testExpected" maxlength="8000"></textarea></label><label>Trọng số<input class="field" type="number" name="testWeight" min="1" max="100" step="1" value="1" required></label><label><input type="checkbox" name="testHidden"> Test ẩn</label> <button type="button" class="btn btn-soft btn-sm">Xóa testcase</button>`;
    row.querySelector("button").onclick=()=>row.remove();list.appendChild(row);
  };
  form.querySelector("#catalog-add-test").onclick=add;add();
  if(existing){list.replaceChildren();for(const test of existing.tests||[]){add();const row=list.lastElementChild;row.querySelector('[name="testInput"]').value=test.input;row.querySelector('[name="testExpected"]').value=test.expectedOutput;row.querySelector('[name="testWeight"]').value=test.weight;row.querySelector('[name="testHidden"]').checked=test.hidden;}}
  form.onsubmit=async event=>{
    event.preventDefault();if(stateToken()!==openedToken){clearModal();return;}error.textContent="";
    const payload=Object.fromEntries(new FormData(form));
    for(const key of Object.keys(payload))if(key.startsWith("test"))delete payload[key];
    payload.id=payload.id.trim();
    if(existing){payload.id=existing.id;payload.expectedRevision=existing.revision;}
    // Fieldset bị ẩn không được gửi; giữ bản nháp của từng loại khi chuyển qua lại.
    payload.template=payload.mode==="BUILD_FROM_SCRATCH"?(payload.template||""):"";
    payload.starterCode=payload.mode==="DEBUG_COLLAB"?(payload.starterCode||""):"";
    payload.tests=[...list.children].map(row=>({input:row.querySelector('[name="testInput"]').value,expectedOutput:row.querySelector('[name="testExpected"]').value,weight:Number(row.querySelector('[name="testWeight"]').value),hidden:row.querySelector('[name="testHidden"]').checked}));
    if(!payload.tests.length||!payload.tests.some(t=>!t.hidden)){error.textContent="Cần ít nhất một testcase công khai.";return;}
    if(payload.mode==="DEBUG_COLLAB"&&!payload.starterCode.trim()){error.textContent="Bài sửa lỗi cần code có lỗi ban đầu.";return;}
    if(new TextEncoder().encode(JSON.stringify(payload)).length>120000){error.textContent="Đề và testcase quá lớn. Hãy giảm nội dung xuống dưới 120 KB.";return;}
    const submit=form.querySelector('[type="submit"]');submit.disabled=true;
    const token=stateToken();let result;try{result=await assignment(existing?"BANK_UPDATE":"CATALOG_CREATE",payload);}catch(e){error.textContent=e.message;submit.disabled=false;return;}
    if(stateToken()!==token||!form.isConnected)return;clearModal();toast(existing?"Đã lưu phiên bản đề mới.":"Đã thêm bài vào ngân hàng.","success");await onSaved(result.exercise?.id||payload.id);
  };
}
