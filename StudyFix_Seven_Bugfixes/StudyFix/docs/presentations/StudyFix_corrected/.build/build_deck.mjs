import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {PresentationFile,FileBlob} from '@oai/artifact-tool';
const here=path.dirname(fileURLToPath(import.meta.url));
const task=path.dirname(here);
const SKILL='C:/Users/ADMIN/.codex/plugins/cache/openai-primary-runtime/presentations/26.1030.10813/skills/presentations';
const PY='C:/Users/ADMIN/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe';
const p=await PresentationFile.importPptx(await FileBlob.load('C:/Users/ADMIN/Downloads/StudyFix_Project_Overview_28_slides.pptx'));
const C={ink:'#0C2236',muted:'#52677D',blue:'#3375B9',teal:'#089DA9',green:'#268B70',amber:'#D98225',red:'#BE4A4A',light:'#F6F9FC',pale:'#EAF3FA',edge:'#C9D8E3',white:'#FFFFFF'};
let serial=0;
function text(s,t,x,y,w,h,size=24,color=C.ink,bold=false,align='left'){
 const sh=s.shapes.add({name:`edited-text-${++serial}`,geometry:'textbox',position:{left:x,top:y,width:w,height:h},fill:'none',line:{fill:'none',width:0}});
 sh.text=t;sh.text.style={fontFamily:'Arial',typeface:'Arial',fontSize:size,color,bold,alignment:align,verticalAlignment:'top',wrap:'word',insets:0};return sh;
}
function box(s,t,x,y,w,h,color=C.blue,size=23){
 const sh=s.shapes.add({name:`diagram-node-${++serial}`,geometry:'rect',position:{left:x,top:y,width:w,height:h},fill:C.pale,line:{fill:color,width:1.5}});
 sh.text=t;sh.text.style={typeface:'Arial',fontSize:size,color:C.ink,bold:true,alignment:'center',verticalAlignment:'middle',insets:12};return sh;
}
function conn(s,a,b,from='right',to='left',color=C.teal){return s.shapes.connect(a,b,{kind:'straight',fromSide:from,toSide:to,line:{style:'solid',fill:color,width:2},tail:{type:'arrow',width:'med',length:'med'}});}
function line(s,x,y,w,color=C.edge){s.shapes.add({geometry:'line',position:{left:x,top:y,width:w,height:0},fill:'none',line:{fill:color,width:1}});}
function clear(s){for(const sh of [...s.shapes.items])if(sh.position.top>=150&&sh.position.top<680&&!(sh.position.height<=8&&sh.position.width<160&&sh.position.left<100))sh.delete();}
function title(s,t,sub){
 for(const sh of s.shapes.items){const y=sh.position.top;if(y>55&&y<105&&String(sh.text).trim()){sh.text=t;sh.text.fontSize=36;}if(y>=115&&y<142&&String(sh.text).trim()){sh.text=sub;sh.text.fontSize=19;}}
}
function replace(n,old,next){const s=p.slides.items[n-1];const target=s.shapes.items.find(sh=>String(sh.text)===old);if(!target)throw Error(`Text not found slide ${n}: ${old}`);target.text=next;}
function notes(n,body,files){p.slides.items[n-1].speakerNotes.text=`${body}\n\nNguồn source đối chiếu ngày 04/11/2026:\n${files.map(f=>'StudyFix/'+f).join('\n')}\nCác ví dụ là minh họa nghiệp vụ, không phải dữ liệu đo tải hay dữ liệu người dùng.`;}
function columns(n,titleText,sub,leftTitle,left,rightTitle,right){const s=p.slides.items[n-1];clear(s);title(s,titleText,sub);text(s,leftTitle,72,196,540,42,27,C.teal,true);text(s,rightTitle,676,196,535,42,27,C.blue,true);line(s,72,246,1135);text(s,left,72,270,525,340,25);text(s,right,676,270,530,340,25);return s;}

// Normalize the imported typography and move its accent rule away from subtitles.
for(let i=0;i<p.slides.items.length;i++)for(const sh of p.slides.items[i].shapes.items){
 if(String(sh.text).trim())sh.text.typeface='Arial';
 if(i>0&&i<27&&sh.position.height<=8&&sh.position.top>125&&sh.position.top<165&&sh.position.width<160)sh.position={...sh.position,top:164};
}

// 02: remove literal Python lists and increase readability.
columns(2,'Bài toán trong lớp học lập trình','Quan sát quá trình sửa lỗi và tổ chức thực hành trong cùng một không gian.','NHU CẦU TRONG GIỜ HỌC',
'Giáo viên cần theo dõi cách học sinh sửa lỗi, ngoài kết quả cuối cùng.\n\nFile bài nộp rời rạc khó thể hiện tiến trình và ngữ cảnh trao đổi.',
'CÁCH STUDYFIX TỔ CHỨC',
'Một Master làm điểm xuất phát chung.\n\nMỗi học sinh làm trên nhánh riêng; giáo viên xem code và kết quả, rồi chọn lời giải để giảng tiếp.');

// 04: editable protocol diagrams, replacing a redundant value slide.
{
const s=p.slides.items[3];clear(s);title(s,'Giao thức TCP và ranh giới thông điệp','Header ghi số byte; một lần gửi TCP không tương đương một lần nhận.');
for(const sh of s.shapes.items)if(sh.position.top<50&&String(sh.text).trim())sh.text='04 / GIAO THỨC';
text(s,'TCP 5000: lệnh văn bản  |  TCP 5002: JSON',72,192,1130,35,25,C.blue,true);
const a=box(s,'4 byte signed int\nBig-endian: N',72,245,290,100);const b=box(s,'N byte payload UTF-8\n0 ≤ N ≤ 65.536',432,245,760,100,C.teal);conn(s,a,b);
text(s,'Relay native 5001: packet nhị phân',72,388,1130,35,25,C.blue,true);
const c=box(s,'4 byte\nL = 1 + N',72,437,290,100);const d=box(s,'1 byte\nLoại',425,437,210,100,C.teal);const e=box(s,'N byte payload',700,437,492,100,C.amber);conn(s,c,d);conn(s,d,e);
text(s,'1 ≤ L ≤ 2 MiB  •  HELLO=1, FRAME=2, STOP=3, ERROR=4, STATUS=5',72,563,1130,36,22);
text(s,'Luồng media của browser dùng WebRTC; không đi qua relay JPEG 5001.',72,620,1130,34,21,C.muted);
notes(4,'Protocol và AssignmentWire ghi độ dài payload UTF-8, giới hạn 65536 byte. ScreenShareWire ghi length=1+payload.length; giới hạn length 2*1024*1024. readFully xử lý frame đến thành nhiều phần. 64 KiB là giới hạn frame, không phải số ký tự Unicode. Không gán frame TCP này cho HTTP body.', ['server/src/studyfix/Protocol.java','server/src/studyfix/assignment/protocol/AssignmentWire.java','server/src/studyfix/screenshare/ScreenShareWire.java']);
}

// 07: three distinct backend routes; native/Swing clients are outside this web deck.
{
const s=p.slides.items[6];clear(s);title(s,'Kiến trúc Web Client và Java Server','Browser gọi HTTP/HTTPS; gateway dùng lại các nghiệp vụ Java hiện có.');
const browser=box(s,'BROWSER\nHTML / CSS / JavaScript',72,205,300,100);const gw=box(s,'WebGatewayServer\nHTTP/HTTPS 8080',448,205,355,100,C.teal);conn(s,browser,gw);
text(s,'Static web, token, API\nvà polling sự kiện',850,211,345,80,22,C.muted);
const a=box(s,'Server / ClientHandler\nRoom / Store',72,405,330,103);const b=box(s,'ClassroomService\nMaster / nhánh / Merge',475,405,330,103,C.green);const c=box(s,'AssignmentService\nPractice / bài tập / thi',878,405,330,103,C.amber);
conn(s,gw,a,'bottom','top');conn(s,gw,b,'bottom','top');conn(s,gw,c,'bottom','top');
text(s,'TCP nội bộ 5000',72,353,330,32,22,C.blue,true,'center').fill=C.light;text(s,'Gọi hàm Java',475,353,330,32,22,C.green,true,'center').fill=C.light;text(s,'Gọi hàm Java (*)',878,353,330,32,22,C.amber,true,'center').fill=C.light;
text(s,'Core SQLite',72,527,330,35,23,C.muted,false,'center');text(s,'Core DB + grader nhánh',475,527,330,35,23,C.muted,false,'center');text(s,'Learning DB + worker',878,527,330,35,23,C.muted,false,'center');
text(s,'(*) Qua AssignmentTcpServer.handleWebRequest(); web không gửi TCP 5002.',72,589,1135,31,20,C.muted);
text(s,'Media WebRTC: browser A ⇄ browser B  •  Gateway trao đổi OFFER / ANSWER / ICE.',72,633,1135,31,21,C.teal,true);
notes(7,'Server Manager khởi động core, assignment và gateway trong cùng JVM. WebSession mở socket 127.0.0.1 tới core control. /api/classroom gọi ClassroomService trực tiếp; /api/assignments gọi assignments.handleWebRequest trực tiếp. Console client còn trong backend dùng 5000; client Swing đã bị xóa. Relay 5001 vẫn được core khởi tạo nhưng không phải luồng media web. Classroom grader và AssignmentService queue riêng.', ['server/src/studyfix/WebGatewayServer.java','server/src/studyfix/ServerAdmin.java','server/src/studyfix/assignment/transport/AssignmentTcpServer.java','server/src/studyfix/Server.java']);
}

// 10: concrete server composition.
{
const s=p.slides.items[9];clear(s);title(s,'Server lõi và các dịch vụ phối hợp','Server Manager vận hành backend; trang Tổng quan giáo viên là giao diện client.');
const rows=[['Server','Nhận kết nối TCP; quản lý các phòng và phiên đang chạy.'],['ClientHandler + Protocol','Đọc frame/lệnh, xác thực, heartbeat và queue gửi từng kết nối.'],['Room + EditSession','Thành viên, Master/version, token lượt sửa và bản nháp.'],['Store + Repository','Tài khoản, phòng, revision, nhánh và transaction SQLite.'],['Dịch vụ phối hợp','WebGatewayServer, ClassroomService, AssignmentService, MeetingCalls.']];
rows.forEach(([h,t],i)=>{let y=193+i*88;text(s,h,72,y,360,36,24,i===4?C.green:C.blue,true);text(s,t,456,y,740,62,23);if(i<4)line(s,72,y+71,1128);});
notes(10,'ServerAdmin.startServices tạo Server, AssignmentTcpServer và WebGatewayServer. Handler nhận lệnh trong vòng lặp TCP, queue gửi 64 thuộc mỗi handler. Service kiểm tra quyền nghiệp vụ; repository truy cập SQLite. Không đồng nhất tên Server.java với toàn bộ backend.', ['server/src/studyfix/ServerAdmin.java','server/src/studyfix/Server.java','server/src/studyfix/ClientHandler.java','server/src/studyfix/Room.java','server/src/studyfix/Store.java']);
}

// 11: accurate authentication sequence and distinct identifiers.
{
const s=p.slides.items[10];clear(s);title(s,'Đăng nhập và ánh xạ phiên','Mật khẩu được kiểm tra ở backend; browser nhận một bearer token riêng.');
const a=box(s,'1  Browser\nPOST /api/auth/login',72,203,330,92);const b=box(s,'2  WebGateway\nSocket nội bộ TCP 5000',475,203,330,92,C.teal);const c=box(s,'3  Store / Passwords\nPBKDF2 + role từ SQLite',878,203,330,92,C.green);conn(s,a,b);conn(s,b,c);
const d=box(s,'4  Server\nLOGIN_OK + core session',878,377,330,92,C.green);const e=box(s,'5  WebGateway\nWebSession + bearer token',475,377,330,92,C.teal);const f=box(s,'6  Browser\nAuthorization: Bearer …',72,377,330,92);conn(s,c,d,'bottom','top');conn(s,d,e,'left','right');conn(s,e,f,'left','right');
text(s,'sessionId: kết nối lõi   |   userId: tài khoản   |   token: phiên web   |   peerId: thiết bị',72,518,1135,65,23,C.ink,true);
text(s,'Token lưu trong sessionStorage. Backend kiểm tra role, chủ phòng và membership.',72,610,1135,50,22,C.muted);
notes(11,'Chuỗi phản hồi: [LOGIN_OK] session=<sessionId> userId=<userId> role=<TEACHER|STUDENT> | <displayName>. Không đồng nhất sessionId và userId; token web là UUID mapping phiên, không tự gọi JWT. PBKDF2WithHmacSHA256 dùng 600000 vòng, salt; Store.login thực hiện verify ngoài khóa database. Không đưa mật khẩu/token thật vào slide hoặc nhật ký.', ['server/src/studyfix/WebGatewayServer.java','server/src/studyfix/ClientHandler.java','server/src/studyfix/Passwords.java','server/src/studyfix/Store.java','web-client/static/features/auth/auth.js']);
}

replace(12,'Lưu thành viên\n& trạng thái','Membership RAM\nDữ liệu phòng SQLite');
replace(8,'Mô tả theo source được phân tích; không gán framework không có trong dự án','Web ES Modules, backend Java và môi trường chấm Docker.');
replace(9,'Format • Crypto\napp.css • meeting.css','Format • Crypto\nTiện ích dùng chung');
replace(9,'24 module JavaScript trong báo cáo phân tích; trình bày theo nhóm để dễ theo dõi.','24 module JS; app.css và meeting.css nằm ở gốc web-client/static.');
replace(12,'Phòng trống vẫn tồn tại qua restart; xóa phòng là soft-delete (active = 0).','Phòng trống được giữ; xóa là soft-delete. Kết nối thành viên không sống qua restart.');
replace(13,'Đóng vòng chỉnh sửa','Tạm khóa sửa nhánh');

// 14: native state machine with separate, correctly directed return paths.
{
const s=p.slides.items[13];clear(s);title(s,'State machine của phiên học','RESUME cho sửa tiếp; MERGE đưa lời giải được chọn trở lại Master.');
const w=box(s,'WAITING',72,245,210,85);const l=box(s,'LECTURE',382,245,210,85,C.teal);const b=box(s,'BRANCHING',692,245,210,85,C.green);const m=box(s,'MERGING',1002,245,210,85,C.amber);
conn(s,w,l);conn(s,l,b);conn(s,b,m);text(s,'START',287,213,90,26,18,C.blue,true,'center');text(s,'FORK',597,213,90,26,18,C.teal,true,'center');text(s,'FREEZE',907,213,90,26,18,C.green,true,'center');
// Separate horizontal return routes below the main row, linked via short vertical segments.
const route=(x1,y1,x2,y2,color,arrow=false)=>s.shapes.add({geometry:'connector',kind:'straight',position:{left:Math.min(x1,x2),top:Math.min(y1,y2),width:Math.abs(x2-x1),height:Math.abs(y2-y1),horizontalFlip:x2<x1,verticalFlip:y2<y1},fill:'none',line:{fill:color,width:2},tail:{type:arrow?'triangle':'none',width:'med',length:'med'}});
route(1105,330,1105,386,C.green);route(1105,386,795,386,C.green);route(795,386,795,330,C.green,true);text(s,'RESUME',838,352,190,30,21,C.green,true,'center');
route(1155,330,1155,462,C.teal);route(1155,462,486,462,C.teal);route(486,462,486,330,C.teal,true);text(s,'MERGE',675,429,200,30,21,C.teal,true,'center');
text(s,'END: LECTURE / BRANCHING / MERGING → ENDED',72,508,1138,37,26,C.red,true);
text(s,'NEW_SESSION: ENDED → WAITING',72,562,1138,37,26,C.blue,true);
text(s,'SELECT_EXERCISE và MASTER_SAVE giữ LECTURE. Merge chọn toàn bộ một nhánh.',72,622,1138,40,21,C.muted);
notes(14,'Các transition đọc từ ClassroomService.action. END không hợp lệ ở WAITING/ENDED và còn kiểm tra idle. RESUME từ MERGING về BRANCHING cùng round. MERGE từ MERGING về LECTURE, kiểm tra quyền chủ phòng, phase/round/Master baseVersion/branchVersion. NEW_SESSION từ ENDED về WAITING. Không bắt buộc AC mới được Merge; không Git three-way merge. Các đường END/NEW_SESSION thể hiện bằng nhãn rõ để tránh nối chéo sơ đồ.', ['server/src/studyfix/ClassroomService.java','server/src/studyfix/ClassroomRepository.java']);
}

// 15 timeline copy: accurate debounce, save and check semantics.
replace(15,'Học sinh sửa Branch; autosave debounce ~1,2 giây.','Dừng gõ ~1,2 giây thì thử lưu nhánh, nếu phase còn hợp lệ.');
replace(15,'Lưu snapshot trước khi đưa vào queue chấm.','Lưu nhánh trước; Check chấm snapshot đúng version.');

// 16 add domain limitation in notes, original layout retained.
notes(16,'Master expectedVersion; Branch expectedVersion; teacher action expectedPhase + round. Student save/check được kiểm tra phase và round trong service. Merge kiểm tra Master còn bằng baseVersion lúc FORK và branchVersion khớp nhánh đang xem. repo.change commit dữ liệu liên quan trong core DB trước publish Room. Không có transaction phân tán bao trùm cả 2 DB và WebRTC.', ['server/src/studyfix/ClassroomService.java','server/src/studyfix/ClassroomRepository.java','server/src/studyfix/Store.java']);

// 19: explain the worker and result persistence rather than hiding it behind compilation.
{
const s=p.slides.items[18];clear(s);title(s,'Từ nộp code đến kết quả','Source snapshot được giữ theo từng submission; chấm không chặn request ban đầu.');
const labels=['Nhận yêu cầu\nKiểm tra quyền','Lưu snapshot\nQUEUED','Worker\nRUNNING','Docker\nCompile / test','Lưu kết quả\nWeb polling'];
let previous;labels.forEach((t,i)=>{let n=box(s,t,72+i*233,245,205,118,[C.blue,C.teal,C.amber,C.green,C.blue][i],22);if(previous)conn(s,previous,n);previous=n;});
text(s,'Chạy thử Practice: chỉ public tests. Nộp chính thức: bộ test của bài.',72,414,1130,60,27,C.ink,true);
text(s,'DONE là đã chấm xong; verdict mới cho biết AC, WA, CE, RE, TLE…',72,498,1130,59,25);
text(s,'Ghi DB lỗi: giữ verdict và thử lưu lại; có log/bộ đếm chờ lưu.',72,592,1130,50,23,C.muted);
notes(19,'AssignmentService.handlePractice/createPractice/gradeTask/persistUntilSaved và PracticeRepository lưu source bất biến. IdempotencyKey chống nộp trùng; version chính thức theo user+exercise, run version 0. Grader nhánh ClassroomService riêng queue AssignmentService. Retry ghi giữ kết quả trên worker có giới hạn, không hứa tồn tại qua process crash. Các verdict ACCEPTED, WRONG_ANSWER, COMPILE_ERROR, RUNTIME_ERROR, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED, OUTPUT_LIMIT_EXCEEDED và lỗi hạ tầng khác nhau.', ['server/src/studyfix/assignment/service/AssignmentService.java','server/src/studyfix/assignment/persistence/PracticeRepository.java','web-client/static/features/exercises/practice.js']);
}

// 20: compile mount vs runtime mount and real limits.
columns(20,'Docker sandbox chạy code như thế nào?','Server dùng JDK 26; image chấm mặc định là eclipse-temurin:21-jdk.','BIÊN DỊCH VÀ CHẠY',
'Ghi snapshot thành Main.java.\n\nContainer compile: javac; thư mục work được ghi.\n\nMỗi test chạy container riêng; thư mục code chỉ đọc, input qua stdin.',
'GIỚI HẠN TRONG SOURCE',
'Không mạng; memory 128 MiB, CPU 0,5, tối đa 32 process.\n\nFilesystem gốc chỉ đọc; có tmpfs. Giới hạn thời gian và output.\n\nThiếu Docker/image: báo lỗi sandbox, không chạy code trực tiếp trên host.');
notes(20,'DockerJavaSandbox.dockerBase: --pull=never --rm --network none --memory=128m --memory-swap=128m --cpus=0.5 --pids-limit=32 --read-only --cap-drop=ALL --security-opt=no-new-privileges --tmpfs /tmp:rw,exec,nosuid,size=16m. Compile bind /work RW, runtime bind /code RO. Runtime JVM -Xmx64m. AssignmentService cấu hình 2 giây/test, deadline tổng 45 giây; giới hạn stdout512KiB stderr16KiB diagnostic12KiB. Không kết luận container an toàn tuyệt đối hoặc AC chứng minh đúng mọi input.', ['server/src/studyfix/assignment/grading/DockerJavaSandbox.java','server/src/studyfix/assignment/service/AssignmentService.java']);

replace(21,'Lưu SQLite trước khi broadcast.\nUnread cursor theo userId / phòng.','Lưu SQLite trước khi broadcast.\nMốc chưa đọc theo tài khoản/phòng trên browser.');
replace(22,'POLLING ~1,6 GIÂY / LƯỢT','POLLING: INTERVAL 1.600 ms');
replace(22,'Polling tạo request định kỳ và có độ trễ; không phải WebSocket push.','Request chậm có thể làm bỏ lượt; không cam kết cập nhật đúng mỗi 1,6 giây.');

// 24: deployment graph, explicit localhost vs remote LAN address.
{
const s=p.slides.items[23];clear(s);title(s,'Chạy server và kết nối từ máy khác','Chỉ máy chạy backend/chấm cần JDK, Ant và Docker cho các chức năng tương ứng.');
const a=box(s,'Máy giáo viên / học sinh\nTrình duyệt web',72,226,390,118);const b=box(s,'Máy chạy server\nWebGateway 8080',697,226,480,118,C.teal);conn(s,a,b);
text(s,'HTTP/HTTPS',481,260,200,40,22,C.teal,true,'center');
text(s,'Trên máy server: http://localhost:8080',72,403,1136,43,27,C.blue,true);
text(s,'Máy khác: http://<IP hiện tại của server>:8080',72,467,1136,43,27,C.blue,true);
text(s,'Cổng backend mặc định: control 5000, relay native 5001, assignment 5002.',72,540,1136,55,23);
text(s,'Chạy server/run-server-manager.ps1; kiểm tra cổng, firewall và HTTPS phù hợp.',72,614,1136,42,22,C.muted);
notes(24,'Không hard-code IP LAN cũ. localhost trên máy học sinh là máy học sinh. Gateway bind mặc định 0.0.0.0; actual port có thể cấu hình. HTTPS phụ thuộc keystore/certificate và browser secure context khi dùng camera/mic/screen. Client web không phải cài Docker. Chỉ bật Docker không đồng nghĩa mọi container phải chạy thường trực; container chấm ngắn hạn và --rm.', ['server/run-server-manager.ps1','server/src/studyfix/WebGatewayServer.java','server/src/studyfix/assignment/AssignmentModuleConfig.java']);
}

// 25: measured, dated, scoped evidence; avoid stale frontend claims.
columns(25,'Bằng chứng kiểm thử đến 04/11/2026','Kết quả dưới đây là kiểm tra chức năng; không phải số đo hiệu năng hay tải đồng thời.','ĐÃ CHẠY VÀ CÓ LOG',
'Backend integration-test:\nBUILD SUCCESSFUL.\n\nĐộ tin cậy server: 19 kiểm tra.\n\nDocker thật qua HTTP: 25 kiểm tra AC / WA / lỗi biên dịch.',
'PHẠM VI CẦN PHÂN BIỆT',
'Kiểm thử signaling không xác nhận media thật giữa hai máy.\n\nFrontend có mô phỏng DOM/HTTP; không tự coi là E2E browser.\n\nCần nghiệm thu camera/mic/share qua LAN và HTTPS thực tế.');
notes(25,'Bằng chứng: server/build/server-reliability-full-regression.log BUILD SUCCESSFUL, gồm Classroom112, MeetingCall34 signaling-only, Chat16, RoomLifecycle72, Practice63, ServerMonitor39, ServerReliability19 và các suite history/recovery/console. server/build/server-reliability-docker.log PracticeDockerIntegrationTest25 checks PASS real HTTP+Docker AC/WA/CE. Không cộng mù số checks của các test vì harness có thể đếm thêm assertion dùng chung. Kiểm kê source lúc kiểm tra: server/src43 Java, server/test21 Java, frontend24 JS. check_seven_bugfixes.mjs còn tham chiếu Swing đã xóa, nên không tuyên bố mọi script frontend đều PASS hiện tại. Giới hạn tài nguyên cần kịch bản test riêng, không coi 25 checks AC/WA/CE là bao phủ tất cả limits.', ['server/build/server-reliability-full-regression.log','server/build/server-reliability-docker.log','server/test/studyfix/ServerReliabilityIntegrationTest.java','server/test/studyfix/PracticeDockerIntegrationTest.java']);

// 26: clear storage and operational limits in a flat list.
{
const s=p.slides.items[25];clear(s);title(s,'Giới hạn và dữ liệu cần phân biệt','Không suy ra mọi loại nháp, phiên hoặc media có cùng cơ chế lưu và khôi phục.');
const items=[['Phiên web','Token ở sessionStorage; restart backend không giữ nguyên phiên đã xác thực.'],['Nháp chưa lưu','Lớp học: sessionStorage. Practice: localStorage + Map RAM theo ngữ cảnh.'],['Code đã lưu','Nhánh ở core DB; submission Practice ở learning DB, gắn snapshot/version.'],['Cuộc gọi','Giới hạn 16 thiết bị gọi/phòng; có STUN, chưa triển khai TURN/SFU riêng.'],['Hạ tầng','HTTP không mã hóa; Docker image phải có sẵn. Lỗi ổ đĩa vẫn có thể chặn ghi.']];
items.forEach(([h,t],i)=>{let y=191+i*88;text(s,h,72,y,265,35,24,C.blue,true);text(s,t,365,y,830,67,23);if(i<4)line(s,72,y+73,1128);});
notes(26,'Practice.scope chưa reset toàn bộ drafts/requests Map khi đổi tài khoản; cần kiểm tra kỹ case cùng bài sau đổi account, không tuyên bố hoàn toàn cô lập chỉ vì localStorage có key userId. Kết quả đang chờ ghi ở RAM worker có thể mất nếu process crash; DB giữ công việc chưa hoàn tất cho recovery theo thiết kế. Native screen relay không phải luồng media web. Phòng trống vẫn tồn tại nhưng membership online không sống qua restart.', ['web-client/static/features/classroom/classroom.js','web-client/static/features/exercises/practice.js','web-client/static/features/auth/auth.js','server/src/studyfix/MeetingCalls.java','server/src/studyfix/assignment/service/AssignmentService.java']);
}

// 27: distinguish completed fixes from work still outstanding.
columns(27,'Trạng thái hoàn thiện và việc tiếp theo','Nêu đúng những gì đã sửa và những giới hạn vẫn còn trong source.','BACKEND ĐÃ CẬP NHẬT',
'Đồng bộ lệnh với thao tác rời phòng; dọn heartbeat khi ngắt socket.\n\nRetry ghi kết quả chấm; thu hẹp khóa PBKDF2.\n\nTransaction tạo version an toàn hơn; Manager tải chi tiết phòng được chọn.',
'ƯU TIÊN TIẾP THEO',
'Frontend: sửa binding refreshMe và kiểm tra nháp khi đổi tài khoản.\n\nDọn kiểm thử còn tham chiếu client Swing đã xóa.\n\nNghiệm thu browser thật, media LAN/HTTPS và tải nhiều người.');
notes(27,'Các sửa backend mô tả tại docs/SERVER_RELIABILITY_FIXES_VI.md đã có log hồi quy và Docker thật. Lỗi frontend còn quan sát được: core/polling.js.refreshEverything gọi refreshMe không có import/binding; không tự coi việc sửa backend là đã sửa lỗi này. practice.scope chưa reset drafts Map keyed exercise khi đổi owner. Đề xuất tương lai là công việc chưa làm; không ghi như tính năng hoàn tất. Không có benchmark load/p95/throughput được báo cáo.', ['docs/SERVER_RELIABILITY_FIXES_VI.md','web-client/static/core/polling.js','web-client/static/features/exercises/practice.js','web-client/tests/check_seven_bugfixes.mjs']);

// Add evidence and talking points for every retained slide.
const extra={
1:['Giới thiệu dự án, để trống tên nhóm/lớp/giảng viên cho người dùng điền.',['docs/PHAN_TICH_CHI_TIET_DU_AN_WEB_SERVER_VI.md']],
2:['Mô tả nhu cầu và hướng thiết kế, không khẳng định đã đo hiệu quả dạy học.',['docs/PHAN_TICH_CHI_TIET_DU_AN_WEB_SERVER_VI.md']],
3:['Master/nhánh/check là workflow chính. Check không bắt buộc trước Merge; giáo viên chủ động chọn lời giải.',['server/src/studyfix/ClassroomService.java']],
5:['Chỉ chủ phòng điều khiển phiên/Merge; giáo viên và học sinh đều có Practice độc lập.',['server/src/studyfix/ClassroomService.java','server/src/studyfix/assignment/service/AssignmentService.java']],
6:['Phân biệt lớp học, assignment của phòng và practice độc lập; không tạo assignment giả để luyện tập.',['web-client/static/app.js','server/src/studyfix/assignment/service/AssignmentService.java']],
8:['Backend Java và frontend ES modules; không có backend Node chỉ vì frontend dùng JavaScript. JDK host khác JDK image chấm.',['server/build.xml','web-client/static/app.js','server/src/studyfix/assignment/AssignmentModuleConfig.java']],
9:['Kiểm kê 24 JS module trong static tại ngày đối chiếu; HTML/CSS là file riêng. core quản lý state/api/polling, features xử lý nghiệp vụ.',['web-client/static/app.js','web-client/static/core/events.js']],
12:['Mã phòng công khai khác ID nội bộ. Phòng trống được giữ; xóa active=0. Membership online trong RAM, không tự khôi phục kết nối sau restart.',['server/src/studyfix/Server.java','server/src/studyfix/RoomCode.java','server/src/studyfix/Store.java']],
13:['FORK sao chép Master cho học sinh online theo userId. Late join trong BRANCHING được cấp nhánh qua view. FREEZE có thể RESUME; MERGE thay toàn bộ Master bằng một nhánh.',['server/src/studyfix/ClassroomService.java']],
15:['Autosave debounce1200ms khi ngừng gõ; còn phụ thuộc mạng và phase. runCheck lưu nhánh trước rồi BRANCH_CHECK chấm code đã lưu đúng version.',['web-client/static/features/classroom/classroom.js','server/src/studyfix/ClassroomService.java']],
17:['Assignment có ROOM/INDIVIDUAL và snapshot recipients phục vụ tiến độ. DEBUG_COLLAB yêu cầu scope ROOM. Đây là luồng khác Practice không cần phòng.',['server/src/studyfix/assignment/service/AssignmentService.java','server/src/studyfix/assignment/persistence/AssignmentRepository.java']],
18:['Contest hiện chỉ INDIVIDUAL và BUILD_FROM_SCRATCH, TEAM_COLLAB bị trả CONTEST_TYPE_NOT_READY. Practice dùng cho cả hai role không cần membership phòng.',['server/src/studyfix/assignment/service/AssignmentService.java','web-client/static/features/contests/contests.js']],
21:['Chat được lưu trước broadcast. Mốc unread frontend không phải server read receipt toàn thiết bị. Gateway signaling HTTP; WebRTC media P2P. Autoplay và audio track phụ thuộc browser/OS/quyền.',['server/src/studyfix/Room.java','server/src/studyfix/MeetingCalls.java','web-client/static/features/classroom/call.js','web-client/static/features/screen-share/screen-share.js','web-client/static/features/classroom/chat-notifications.js']],
22:['setInterval1600ms, pollingBusy bỏ lượt khi đang chờ, không phải WebSocket và không cam kết độ trễ cố định. Guard version/generation ngăn response cũ ghi đè một số trạng thái.',['web-client/static/core/polling.js','web-client/static/core/api.js']],
23:['Core DB gồm account/room/revision/classroom data. Learning DB gồm exercise/assignment/submission/contest/practice. Transaction nguyên tử theo từng DB/ngữ cảnh, không distributed transaction.',['server/src/studyfix/Store.java','server/src/studyfix/ClassroomRepository.java','server/src/studyfix/assignment/persistence/AssignmentRepository.java','server/src/studyfix/assignment/persistence/PracticeRepository.java']],
28:['Tóm tắt mục tiêu và thành phần dự án, không trình bày như kết luận thực nghiệm hiệu quả học tập.',['docs/PHAN_TICH_CHI_TIET_DU_AN_WEB_SERVER_VI.md']]
};
for(const [n,[body,files]] of Object.entries(extra))notes(Number(n),body,files);

await fs.mkdir(path.join(here,'final-previews'),{recursive:true});
for(let i=0;i<p.slides.items.length;i++){
 const png=await p.export({slide:p.slides.items[i],format:'png',scale:1});
 await fs.writeFile(path.join(here,'final-previews',`${String(i+1).padStart(2,'0')}.png`),new Uint8Array(await png.arrayBuffer()));
}
const candidate=path.join(here,'corrected-candidate.pptx');
await (await PresentationFile.exportPptx(p)).save(candidate);
await fs.writeFile(path.join(here,'final-inspection.json'),JSON.stringify(await p.inspect({kind:'slide,textbox,shape',maxChars:400000})));
console.log('Draft and 28 previews exported:',candidate);
const {finalizePresentation}=await import(pathToFileURL(path.join(SKILL,'container_tools/artifact_tool_utils.mjs')).href);
const final=path.join(task,'output','StudyFix_Project_Overview_28_slides_Chuan_Source.pptx');
await finalizePresentation({workspaceDir:task,candidatePath:candidate,finalPath:final,pythonExecutable:PY,
 integrityValidatorPath:path.join(SKILL,'container_tools/inspect_presentation_package_integrity.py'),
 layoutValidatorPath:path.join(SKILL,'container_tools/inspect_presentation_layout_geometry.py'),
 layoutArgs:['--expected-slide-size-emu','12191695,6858000','--validate-bullet-geometry','--validate-heading-fit'],
 explicitTotalSlideCount:28, requiredNativeTableOwnerSlides:[],requiredNativeChartOwnerSlides:[],
 fontPolicy:{basis:'design',families:['Arial']},verifyArtifactToolImport:true,
 receiptPath:path.join(here,'validation-final.json')});
console.log('FINAL:',final);
