import {renderStudentPortal,studentLabels} from "./features/student/portal.js";

import "./features/problem-builder/controller.js";
// File khởi động: đăng ký các trang, nạp phần xử lý sự kiện
// cho các thao tác click, submit, input, change
// rồi khởi chạy ứng dụng. Logic từng chức năng nằm ở các module riêng.
// Xem vị trí các module trong README.md ở thư mục StudyFix, mục 6.3.

import { registerPage } from "./core/router.js";
import {renderExamBank} from "./features/exam-bank/index.js";
import { renderOverview } from "./ui/render.js";
import { renderRoom } from "./features/room/room.js";
import { renderExercises } from "./features/exercises/exercises.js";
import { renderPractice } from "./features/exercises/practice.js";
import { state } from "./core/state.js";
import { btn } from "./ui/components.js";
import {renderOrganization} from "./features/exam-organization/index.js";
import { renderContests } from "./features/contests/contests.js";
import { init } from "./core/lifecycle.js";
import {renderAdmin} from "./features/admin/admin.js";
import {teacherPages} from "./core/roles.js";

// Đăng ký xử lý click, gửi biểu mẫu và thay đổi ô nhập trên document và phần tử gốc.
import "./core/events.js";


registerPage("overview", () => state.identity?.role==="STUDENT"?renderStudentPortal():renderOverview());
for(const page of Object.keys(studentLabels).filter(p=>p!=="overview"))registerPage(page,renderStudentPortal);
registerPage("room", renderRoom);
registerPage("exercises", () => state.exerciseMode==="room"?`<div class="tool-row">${btn("← Về cuộc họp","navigate","btn-soft btn-sm",'data-page="room"')}${btn("Ngân hàng câu hỏi","navigate","btn-soft btn-sm",'data-page="exercises"')}</div>${renderExercises()}`:renderPractice());
registerPage("contests", renderContests);
registerPage("exam-organization", renderOrganization);
registerPage("exam-bank",renderExamBank);
for(const page of teacherPages)registerPage(page,renderAdmin);

init();
