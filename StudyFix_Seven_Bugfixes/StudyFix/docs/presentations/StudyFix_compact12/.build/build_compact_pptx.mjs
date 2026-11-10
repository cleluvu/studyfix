import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {Presentation,PresentationFile} from '@oai/artifact-tool';
const here=path.dirname(fileURLToPath(import.meta.url)),task=path.dirname(here);
const SKILL='C:/Users/ADMIN/.codex/plugins/cache/openai-primary-runtime/presentations/26.1030.10813/skills/presentations';
const PY='C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe';
const data=JSON.parse(await fs.readFile(path.join(here,'content.json'),'utf8'));
const p=Presentation.create({slideSize:{width:1280,height:720}});
const C={ink:'#0D263A',muted:'#526B7D',blue:'#2B70AB',teal:'#008D99',green:'#23785C',amber:'#B56816',red:'#A44141',bg:'#F7FAFC',line:'#CEDDE7',white:'#FFFFFF',pale:'#EAF3F8'};
let id=0;
function text(s,t,x,y,w,h,size=24,color=C.ink,bold=false,align='left'){
 const sh=s.shapes.add({name:`text-${++id}`,geometry:'textbox',position:{left:x,top:y,width:w,height:h},fill:'none',line:{fill:'none',width:0}});
 sh.text=t;sh.text.style={typeface:'Arial',fontSize:size,color,bold,alignment:align,verticalAlignment:'top',insets:0,wrap:'word'};return sh;
}
function rule(s,x,y,w,color=C.line){s.shapes.add({geometry:'line',position:{left:x,top:y,width:w,height:0},line:{fill:color,width:1},fill:'none'});}
function node(s,t,x,y,w,h,color=C.blue,size=24){
 const sh=s.shapes.add({name:`node-${++id}`,geometry:'rect',position:{left:x,top:y,width:w,height:h},fill:C.pale,line:{fill:color,width:1.5}});
 sh.text=t;sh.text.style={typeface:'Arial',fontSize:size,color:C.ink,bold:true,alignment:'center',verticalAlignment:'middle',insets:12};return sh;
}
function connect(s,a,b,from='right',to='left',color=C.teal){return s.shapes.connect(a,b,{kind:'straight',fromSide:from,toSide:to,line:{fill:color,width:2},tail:{type:'arrow',width:'med',length:'med'}});}
function base(i){const s=p.slides.add();s.background.fill=C.bg;text(s,`${String(i+1).padStart(2,'0')} / ${data[i].section}`,60,30,1150,24,14,C.teal,true);text(s,data[i].title,60,75,1160,65,38,C.ink,true);text(s,data[i].lead,60,145,1160,53,20,C.muted);rule(s,60,203,1160);text(s,'STUDYFIX | TỔNG QUAN DỰ ÁN',60,683,900,20,12,C.muted);text(s,`${i+1} / 12`,1130,683,90,20,12,C.muted,false,'right');s.speakerNotes.text=data[i].paragraphs.join('\n\n')+'\n\nChuyển ý: '+data[i].transition+'\n\nGhi nhớ: '+data[i].reminder+'\n\nNguồn đối chiếu 04/11/2026:\n'+data[i].sources.map(f=>'StudyFix/'+f).join('\n');return s;}
function rows(s,items,start=234,gap=78){for(let i=0;i<items.length;i++){const [a,b]=items[i],y=start+i*gap;text(s,a,64,y,315,43,24,C.blue,true);text(s,b,402,y,808,gap-10,23);if(i<items.length-1)rule(s,64,y+gap-14,1146);}}

// 01 introduction with scope, project identity and blank group metadata.
{
const s=base(0);text(s,'MASTER  →  NHÁNH RIÊNG  →  KIỂM TRA  →  CHỌN LỜI GIẢI',64,242,1150,55,28,C.teal,true);
text(s,'Giáo viên chuẩn bị code và theo dõi quá trình thực hành.\nHọc sinh làm bài trên nhánh riêng hoặc luyện code độc lập.',64,330,1135,105,30);
text(s,'Phạm vi trình bày: chức năng, kiến trúc, công nghệ, giao thức, kiểm chứng và giới hạn.',64,480,1135,65,24,C.muted);
text(s,'Nhóm ..................................     Lớp ..................................\nGiảng viên ................................................................................',64,579,1135,63,21);
}
// 02 main functions.
{
const s=base(1);rows(s,[['Phòng học','Mã tự đặt; tìm kiếm; 5 phòng/trang; chủ phòng chủ động xóa.'],['Lớp học cộng tác','Master, nhánh cá nhân, autosave, Check, Freeze và Merge.'],['Luyện code','Chọn đề độc lập; chạy thử; nộp code hoặc nạp file; lịch sử version.'],['Giao bài và thi','Phạm vi người nhận, deadline; thi cá nhân và bảng xếp hạng.'],['Trao đổi trong lớp','Chat, thông báo chưa đọc, mic, camera và chia sẻ màn hình.']],232,83);
}
// 03 architecture.
{
const s=base(2);const browser=node(s,'BROWSER\nWeb Client',65,231,265,87);const gw=node(s,'WebGatewayServer\nHTTP/HTTPS 8080',424,231,385,87,C.teal);connect(s,browser,gw);text(s,'Server Manager\nkhởi động và giám sát',872,242,320,70,23,C.muted);
const a=node(s,'Server / ClientHandler\nRoom / Store',65,410,337,95);const b=node(s,'ClassroomService\nMaster / nhánh / Merge',473,410,337,95,C.green);const c=node(s,'AssignmentService\nPractice / giao bài / thi',881,410,337,95,C.amber);
connect(s,gw,a,'bottom','top');connect(s,gw,b,'bottom','top');connect(s,gw,c,'bottom','top');
text(s,'TCP nội bộ 5000',65,359,337,32,22,C.blue,true,'center').fill=C.bg;text(s,'Gọi Java trực tiếp',473,359,337,32,22,C.green,true,'center').fill=C.bg;text(s,'Gọi Java trực tiếp (*)',881,359,337,32,22,C.amber,true,'center').fill=C.bg;
text(s,'Core SQLite',65,521,337,36,23,C.muted,false,'center');text(s,'Core DB + grader nhánh',473,521,337,36,23,C.muted,false,'center');text(s,'Learning DB + worker',881,521,337,36,23,C.muted,false,'center');
text(s,'(*) Qua AssignmentTcpServer.handleWebRequest(); browser không mở TCP 5002.',65,581,1150,36,20,C.muted);
text(s,'Media WebRTC: browser A ⇄ browser B; signaling được gateway chuyển tiếp.',65,627,1150,36,22,C.teal,true);
}
// 04 technology responsibilities.
{
const s=base(3);rows(s,[['HTML CSS JavaScript','ES Modules; core / features / ui / shared; không dùng backend Node.js.'],['Java và Ant','JDK 26 chạy/build backend; Server, ClientHandler, Room, service, repository.'],['SQLite qua JDBC','Core DB: account, phòng, lớp học. Learning DB: bài, submission, thi.'],['Docker Java 21','Image eclipse-temurin:21-jdk cho sandbox, khác JDK host server.'],['API và media','HTTP/HTTPS + JSON + bearer; TCP nội bộ; WebRTC cho media.']],232,83);text(s,'Mật khẩu: PBKDF2WithHmacSHA256 có salt; backend kiểm tra role, owner và membership.',65,645,1150,28,19,C.muted);
}
// 05 HTTP / JSON / login / polling.
{
const s=base(4);const a=node(s,'Browser gửi JSON\nPOST /api/auth/login',65,240,330,92);const b=node(s,'Gateway xác thực\nqua TCP lõi 5000',477,240,330,92,C.teal);const c=node(s,'WebSession + token\nAuthorization: Bearer …',889,240,330,92,C.green);connect(s,a,b);connect(s,b,c);
rows(s,[['HTTP và HTTPS','Request/response tới gateway; HTTPS cần certificate phù hợp.'],['JSON và định danh','JSON là định dạng dữ liệu. userId, sessionId, token, peerId khác nhau.'],['API nghiệp vụ','/api/classroom; /api/assignments; /api/rooms/current/command.'],['Polling và phản hồi','Interval 1.600 ms, có thể bỏ lượt. 202 có thể chỉ là đã chuyển lệnh.']],370,70);
}
// 06 TCP framing with explicit packet lengths.
{
const s=base(5);text(s,'5000: lệnh văn bản   |   5002: JSON',65,226,1150,35,26,C.blue,true);
const a=node(s,'4 byte signed int\nBig-endian: N',65,276,306,87);const b=node(s,'N byte UTF-8  •  0 ≤ N ≤ 65.536',449,276,770,87,C.teal,25);connect(s,a,b);
text(s,'5001: relay native, khác luồng media của web',65,400,1150,35,26,C.blue,true);
const c=node(s,'4 byte\nL = 1 + N',65,449,306,88);const d=node(s,'1 byte loại',448,449,224,88,C.teal);const e=node(s,'N byte payload',749,449,470,88,C.amber);connect(s,c,d);connect(s,d,e);
text(s,'1 ≤ L ≤ 2 MiB. HELLO=1, FRAME=2, STOP=3, ERROR=4, STATUS=5.',65,557,1150,37,22);
text(s,'readFully đọc đủ frame; khóa ghi tránh xen header/payload. Heartbeat lõi timeout 20 giây.',65,612,1150,50,22,C.muted);
}
// 07 separate signaling and media paths.
{
const s=base(6);const a=node(s,'Browser A',65,261,280,92);const g=node(s,'Gateway + MeetingCalls\nSignaling qua HTTP',455,261,370,92,C.teal);const b=node(s,'Browser B',935,261,280,92);connect(s,a,g);connect(s,g,b);
text(s,'OFFER / ANSWER / ICE; kiểm tra peer cùng phòng, roster và giới hạn tín hiệu.',65,379,1150,45,24);
const pa=node(s,'Track mic / camera / màn hình',65,450,460,83,C.green);const pb=node(s,'Stream nhận → audio / video',755,450,460,83,C.green);connect(s,pa,pb,'right','left',C.green);text(s,'WebRTC',538,470,206,35,24,C.green,true,'center').fill=C.bg;
text(s,'getUserMedia / getDisplayMedia → track → RTCPeerConnection → stream phía nhận.',65,558,1150,40,22);
text(s,'Không gói media vào HTTP 8080/TCP 5000; STUN có sẵn, chưa có TURN/SFU riêng.',65,612,1150,45,22,C.muted);
}
// 08 phase transitions and guard rules.
{
const s=base(7);const w=node(s,'WAITING',65,246,211,77);const l=node(s,'LECTURE',379,246,211,77,C.teal);const b=node(s,'BRANCHING',693,246,211,77,C.green);const m=node(s,'MERGING',1007,246,211,77,C.amber);connect(s,w,l);connect(s,l,b);connect(s,b,m);text(s,'START',282,218,90,25,18,C.blue,true,'center');text(s,'FORK',596,218,90,25,18,C.teal,true,'center');text(s,'FREEZE',910,218,90,25,18,C.green,true,'center');
text(s,'RESUME: MERGING → BRANCHING',65,363,1150,39,27,C.green,true);text(s,'MERGE: MERGING → LECTURE, tạo Master mới từ toàn bộ một nhánh.',65,414,1150,60,27,C.teal,true);
text(s,'Chỉ chủ phòng điều khiển; kiểm tra phase, round, Master version và branchVersion.',65,493,1150,58,24);
text(s,'AC không tự đổi Master; Merge không bắt buộc AC và không phải Git three-way merge.',65,565,1150,55,23);
text(s,'END: phiên đang hoạt động → ENDED. NEW_SESSION: ENDED → WAITING.',65,633,1150,34,21,C.muted);
}
// 09 exercise and grading.
{
const s=base(8);const labels=['Nhận code\nKiểm tra quyền','Lưu snapshot\nQUEUED','Worker\nRUNNING','Docker\nCompile / test','Lưu verdict\nWeb polling'];let prev;
labels.forEach((v,i)=>{const n=node(s,v,65+i*234,248,215,102,[C.blue,C.teal,C.amber,C.green,C.blue][i],22);if(prev)connect(s,prev,n);prev=n;});
rows(s,[['Practice','Không cần phòng; RUN public-only, SUBMIT theo bộ test; lịch sử version.'],['Assignment và Contest','Giao bài theo người nhận/deadline; thi cá nhân BUILD_FROM_SCRATCH.'],['Lưu và chạy an toàn','IdempotencyKey; snapshot bất biến; Docker tắt mạng và giới hạn tài nguyên.'],['Verdict và retry','AC / WA / CE… khác DONE. Ghi DB lỗi thì giữ verdict và thử lưu lại.']],384,68);
}
// 10 room, communication and state.
{
const s=base(9);rows(s,[['Mã phòng và roomId','Mã công khai do giáo viên đặt; ID nội bộ dùng liên kết dữ liệu.'],['Rời họp và rời phòng','Rời họp dừng media, giữ membership; rời phòng tách thành viên.'],['Chat và chưa đọc','Lưu chat trước broadcast; marker frontend theo tài khoản/phòng.'],['Lưu trữ và đồng bộ','Core DB / learning DB; version + khóa Room + transaction theo ngữ cảnh.'],['Server Manager','Xem chủ phòng, Master, nhánh, kết quả và nhật ký; dữ liệu đọc ở nền.']],232,83);
}
// 11 evidence, distinct scopes.
{
const s=base(10);text(s,'ĐÃ CÓ LOG NGÀY 04/11/2026',65,235,1150,38,26,C.green,true);
text(s,'Backend integration-test: BUILD SUCCESSFUL.\nĐộ tin cậy server: 19 kiểm tra. Docker thật qua HTTP: 25 kiểm tra AC / WA / CE.',65,300,1150,112,28);
rule(s,65,443,1150);text(s,'PHẠM VI KIỂM CHỨNG',65,472,1150,38,26,C.blue,true);
text(s,'Signaling test không xác nhận media thật.\nMô phỏng DOM/HTTP không thay thế E2E browser.\nCác số checks không phải benchmark hay số người chịu tải đã đo.',65,535,1150,119,25);
}
// 12 limitations and closing.
{
const s=base(11);rows(s,[['Media và mạng','16 thiết bị gọi/phòng; chưa có TURN/SFU riêng; cần thử LAN + HTTPS thật.'],['Phiên và nháp','Restart không giữ phiên lõi; nháp browser khác code đã lưu trong database.'],['Hạ tầng chấm','Image cần có sẵn; retry chưa thể bảo đảm lưu nếu database vẫn lỗi.'],['Việc tiếp theo','Sửa refreshMe, kiểm tra nháp khi đổi user, dọn test còn tham chiếu Swing.']],234,83);
text(s,'StudyFix tổ chức dạy học quanh Master, nhánh riêng và phản hồi kiểm tra.',65,594,1150,42,26,C.teal,true);text(s,'CẢM ƠN THẦY CÔ VÀ CÁC BẠN  •  HỎI ĐÁP',65,645,1150,27,20,C.muted,true);
}
await fs.mkdir(path.join(here,'previews'),{recursive:true});
for(let i=0;i<p.slides.items.length;i++){const png=await p.export({slide:p.slides.items[i],format:'png',scale:1});await fs.writeFile(path.join(here,'previews',`${String(i+1).padStart(2,'0')}.png`),new Uint8Array(await png.arrayBuffer()));}
const candidate=path.join(here,'candidate.pptx');await(await PresentationFile.exportPptx(p)).save(candidate);
const {finalizePresentation}=await import(pathToFileURL(path.join(SKILL,'container_tools/artifact_tool_utils.mjs')).href);
await finalizePresentation({workspaceDir:task,candidatePath:candidate,finalPath:path.join(task,'output','StudyFix_Tong_Quan_12_Slide.pptx'),pythonExecutable:PY,
integrityValidatorPath:path.join(SKILL,'container_tools/inspect_presentation_package_integrity.py'),layoutValidatorPath:path.join(SKILL,'container_tools/inspect_presentation_layout_geometry.py'),layoutArgs:['--expected-slide-size-emu','12192000,6858000','--validate-bullet-geometry','--validate-heading-fit'],explicitTotalSlideCount:12,requiredNativeTableOwnerSlides:[],requiredNativeChartOwnerSlides:[],fontPolicy:{basis:'design',families:['Arial']},verifyArtifactToolImport:true,receiptPath:path.join(here,'validation.json')});
console.log('Finalized 12-slide deck');
