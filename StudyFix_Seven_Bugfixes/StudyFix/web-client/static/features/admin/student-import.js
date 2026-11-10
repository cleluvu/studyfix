import {state} from "../../core/state.js";
import {api} from "../../core/api.js";
import {modal,clearModal} from "../../ui/modal.js";
import {esc} from "../../shared/format.js";
import {toast} from "../../ui/toast.js";

const headerKey=s=>s.normalize("NFD").replace(/[\u0300-\u036f]/g,"").replace(/[đĐ]/g,"d").toLowerCase().replace(/[\s_-]/g,"");
export function parseStudentCsv(text){
 const source=String(text).replace(/^\uFEFF/,""),first=source.split(/\r?\n/,1)[0];
 const sep=(first.match(/;/g)||[]).length>(first.match(/,/g)||[]).length?";":",";
 if(source.includes("\uFFFD"))throw new Error("File không đúng UTF-8. Hãy lưu lại dưới dạng CSV UTF-8.");
 const rows=[];let row=[],field="",quoted=false,closed=false;
 function endField(){row.push(field);field="";closed=false;}
 function endRow(){endField();if(row.some(v=>v.trim()))rows.push(row);row=[];}
 for(let i=0;i<source.length;i++){
  const ch=source[i];
  if(quoted){if(ch==='"'){if(source[i+1]==='"'){field+='"';i++;}else{quoted=false;closed=true;}}else field+=ch;continue;}
  if(ch==='"'){if(field||closed)throw new Error("Dấu ngoặc kép trong CSV không hợp lệ.");quoted=true;}
  else if(ch===sep)endField();
  else if(ch==="\n"||ch==="\r"){if(ch==="\r"&&source[i+1]==="\n")i++;endRow();}
  else if(closed){if(!/\s/.test(ch))throw new Error("CSV có nội dung sau dấu ngoặc kép.");}
  else field+=ch;
 }
 if(quoted)throw new Error("CSV thiếu dấu ngoặc kép đóng.");if(field||row.length||closed)endRow();
 return parseStudentRows(rows);
}
export function parseStudentRows(input){
 const rows=input.map(row=>[...row]);if(rows.length<2)throw new Error("File cần dòng tiêu đề và ít nhất một sinh viên.");
 const aliases={username:"username",tendangnhap:"username",taikhoan:"username",displayname:"displayName",hovaten:"displayName",hoten:"displayName",password:"password",matkhau:"password",mssv:"studentCode",masv:"studentCode",masinhvien:"studentCode",studentcode:"studentCode",email:"email",lop:"classCode",lophoc:"classCode",malop:"classCode",classcode:"classCode",trangthai:"accountStatus",accountstatus:"accountStatus"};
 const headers=rows.shift().map(h=>aliases[headerKey(String(h))]);
 if(headers.includes(undefined)||new Set(headers).size!==headers.length||!["username","displayName","password"].every(h=>headers.includes(h)))throw new Error("Cần các cột Tên đăng nhập, Họ và tên, Mật khẩu; có thể thêm MSSV, Email, Lớp, Trạng thái.");
 if(rows.length>100)throw new Error("Mỗi file tối đa 100 sinh viên.");const students=[],errors=[],seen=new Set();
 rows.forEach((values,index)=>{const entry=Object.fromEntries(headers.map((key,i)=>[key,String(values[i]??"")]));for(const k of ["username","displayName","studentCode","email","classCode"])if(entry[k]!=null)entry[k]=entry[k].trim();entry.studentCode??=entry.username;
 const status=headerKey(entry.accountStatus||"ACTIVE");entry.accountStatus=({active:"ACTIVE",danghoatdong:"ACTIVE",locked:"LOCKED",tamkhoa:"LOCKED",disabled:"DISABLED",davohieuhoa:"DISABLED"})[status];
 const issues=[];if(values.length>headers.length)issues.push("thừa cột");if(!/^[A-Za-z0-9_.-]{1,64}$/.test(entry.username))issues.push("tên đăng nhập không hợp lệ");if(!/^[A-Za-z0-9_.-]{1,64}$/.test(entry.studentCode))issues.push("MSSV không hợp lệ");if(!entry.displayName||entry.displayName.length>200)issues.push("họ tên bắt buộc, tối đa 200 ký tự");if(entry.password.length<12||entry.password.length>256)issues.push("mật khẩu cần 12–256 ký tự");if(entry.email&&(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(entry.email)||entry.email.length>254))issues.push("email không hợp lệ");if(!entry.accountStatus)issues.push("trạng thái không hợp lệ");if(seen.has(entry.username)||seen.has(entry.studentCode))issues.push("trùng tài khoản hoặc MSSV trong file");seen.add(entry.username);seen.add(entry.studentCode);if(issues.length)errors.push(`Dòng dữ liệu ${index+1}: ${issues.join("; ")}.`);students.push(entry);});return {students,errors};
}

function template(){
 const text="\uFEFFMSSV,Họ và tên,Tên đăng nhập,Mật khẩu,Email,Lớp,Trạng thái\r\nSV001,Nguyễn Văn A,sv001,ThayMatKhau01!,sv001@studyfix.local,,ACTIVE\r\nSV002,Trần Thị B,sv002,ThayMatKhau02!,,,ACTIVE\r\n";
 const url=URL.createObjectURL(new Blob([text],{type:"text/csv;charset=utf-8"}));const link=document.createElement("a");link.href=url;link.download="mau_sinh_vien.csv";link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
export function openStudentImport(onSuccess,classes=[]){
 if(state.identity?.role!=="TEACHER")throw new Error("Chỉ giáo viên được nhập sinh viên.");
 const token=state.token,user=state.identity.userId;let students=[];
 modal(`<div class="modal-head"><h3>Nhập danh sách sinh viên</h3><button class="btn btn-soft" id="student-import-close">Đóng</button></div><div class="modal-body"><p>Nhập CSV UTF-8 hoặc Excel .xlsx, tối đa 100 sinh viên. Các cột: MSSV, Họ và tên, Tên đăng nhập, Mật khẩu; Email, Lớp và Trạng thái là tùy chọn. CSV tối đa 128 KiB, Excel tối đa 1 MiB.</p><button class="btn btn-soft" id="student-import-template">Tải file mẫu CSV</button><label class="form-row" style="display:block;margin:16px 0">Chọn file CSV/Excel<input id="student-import-file" class="field" type="file" accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"/></label><label class="form-row">Lớp mặc định (dòng chưa có lớp)<select id="student-import-class" class="field"><option value="">Chưa phân lớp</option>${classes.filter(c=>c.active&&!c.closed).map(c=>`<option value="${esc(c.classId)}">${esc(c.roomCode)} · ${esc(c.name)}</option>`).join("")}</select></label><p id="student-import-status" role="status" style="white-space:pre-line"></p><div id="student-import-preview" class="table-wrap"></div><p>Mật khẩu chỉ dùng để tạo tài khoản, không hiển thị trong bảng xem trước. Không ghi đè tài khoản cũ. Hãy bảo quản file có mật khẩu.</p><button id="student-import-submit" class="btn btn-primary" disabled>Tạo danh sách sinh viên</button></div>`);
 const dialog=document.getElementById("active-modal"),fileInput=document.getElementById("student-import-file"),submit=document.getElementById("student-import-submit"),status=document.getElementById("student-import-status"),preview=document.getElementById("student-import-preview");
 document.getElementById("student-import-close").onclick=()=>{students=[];clearModal();};document.getElementById("student-import-template").onclick=template;
 const current=()=>dialog.isConnected&&token===state.token&&user===state.identity?.userId&&state.identity?.role==="TEACHER";
 let read=0;
 fileInput.onchange=async()=>{
  const request=++read;students=[];submit.disabled=true;preview.innerHTML="";status.textContent="";const file=fileInput.files?.[0];if(!file)return;
  try{
   let result;
   if(/\.xlsx$/i.test(file.name)){if(file.size>1048576)throw new Error("File Excel tối đa 1 MiB.");const response=await fetch('/api/admin/students/import-preview',{method:'POST',headers:{Authorization:`Bearer ${token}`},body:file});const payload=await response.json();if(!response.ok)throw new Error(payload.message||"Không đọc được Excel.");result=parseStudentRows(payload.rows);}
   else if(/\.csv$/i.test(file.name)){if(file.size>131072)throw new Error("CSV tối đa 128 KiB.");result=parseStudentCsv(await file.text());}else throw new Error("Chọn file CSV UTF-8 hoặc Excel .xlsx.");if(!current()||request!==read)return;
   preview.innerHTML=`<table class="data-table"><thead><tr><th>STT</th><th>MSSV</th><th>Tài khoản</th><th>Họ và tên</th><th>Lớp</th><th>Mật khẩu</th></tr></thead><tbody>${result.students.map((s,i)=>`<tr><td>${i+1}</td><td>${esc(s.studentCode)}</td><td>${esc(s.username)}</td><td>${esc(s.displayName)}</td><td>${esc(s.classCode||"Theo lớp mặc định")}</td><td>••••••••</td></tr>`).join("")}</tbody></table>`;
   if(result.errors.length){status.textContent=result.errors.join("\n");return;}
   if(new TextEncoder().encode(JSON.stringify({action:"STUDENT_IMPORT",students:result.students})).length>131072)throw new Error("Danh sách quá lớn, hãy chia thành nhiều file nhỏ hơn.");
   students=result.students;status.textContent=`Đã đọc ${students.length} sinh viên hợp lệ. Bấm Tạo để lưu; server sẽ kiểm tra tài khoản đã tồn tại.`;submit.disabled=false;
  }catch(e){if(current()&&request===read)status.textContent=e.message;}
 };
 submit.onclick=async()=>{
  if(submit.disabled||!current()||!students.length)return;submit.disabled=true;fileInput.disabled=true;status.textContent="Đang tạo tài khoản…";
  try{const response=await api("/api/admin",{method:"POST",body:{action:"STUDENT_IMPORT",students,classId:document.getElementById("student-import-class").value}});students=[];if(!current())return;clearModal();toast(`Đã tạo ${response.data.createdCount} sinh viên.`,"success");await onSuccess();}
  catch(e){if(current()){status.textContent=e.message;submit.disabled=false;fileInput.disabled=false;}}
 };
}
