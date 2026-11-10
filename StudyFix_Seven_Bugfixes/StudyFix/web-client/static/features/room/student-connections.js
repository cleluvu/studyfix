import {esc} from "../../shared/format.js";

const connectionTime=value=>{
  const date=new Date(Number(value));
  return !value||Number.isNaN(date.getTime())?"—":date.toLocaleString("vi-VN");
};

export function renderStudentConnections(room,identity){
  if(identity?.role!=="TEACHER"||!Array.isArray(room?.studentConnections))return "";
  const rows=room.studentConnections,online=rows.filter(row=>row.online);
  const students=new Set(online.map(row=>row.userId)).size;
  const reasons={LOGOUT:"Đã đăng xuất",HEARTBEAT_TIMEOUT:"Hết hạn heartbeat",CONNECTION_LOST:"Mất kết nối",LEFT_ROOM:"Đã rời lớp",SERVER_RESTART:"Server đã khởi động lại",SERVER_STOP:"Server đã dừng"};
  return `<section class="student-connections" aria-label="Quản lý kết nối sinh viên"><h4>Kết nối sinh viên</h4><p>${students} sinh viên Online · ${online.length} kết nối Online</p><small>Cập nhật theo heartbeat. Mỗi dòng là một phiên đăng nhập; một sinh viên có thể dùng nhiều thiết bị.</small>${rows.map(row=>`<article class="student-connection"><div class="student-connection-title"><strong>${esc(row.displayName)}</strong><span class="${row.online?"connection-online":"connection-offline"}">${row.online?"Online":"Offline"}</span></div><dl><dt>Mã sinh viên</dt><dd>${esc(row.studentCode)}</dd><dt>IP kết nối</dt><dd class="code-mono">${esc(row.ip)}</dd><dt>Đăng nhập</dt><dd>${esc(connectionTime(row.loginTime))}</dd><dt>Hoạt động cuối</dt><dd>${esc(connectionTime(row.lastActivity))}</dd><dt>Kết nối</dt><dd>${esc(String(row.connectionId).slice(0,8))}</dd>${!row.online?`<dt>Trạng thái</dt><dd>${esc(reasons[row.reason]||"Không còn trong lớp")}</dd>`:""}</dl></article>`).join("")||'<div class="empty-slim">Chưa có sinh viên tham gia lớp.</div>'}</section>`;
}
