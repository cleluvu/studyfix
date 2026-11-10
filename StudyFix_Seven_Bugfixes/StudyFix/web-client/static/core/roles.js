export const teacherPageLabels={admin:"Tổng quan giảng viên","admin-classes":"Lớp học","admin-students":"Sinh viên","admin-connections":"Theo dõi lớp học","admin-assignments":"Bài tập & tiến độ","admin-statistics":"Thống kê","admin-settings":"Cài đặt","admin-notifications":"Thông báo"};
export const teacherPages=[...Object.keys(teacherPageLabels),"admin-attendance"];
export const isAdminPage=page=>teacherPages.includes(page);
export const canNavigate=(identity,page)=>page==="problem-builder"?false:page==="exam-bank"?identity?.role==="TEACHER":!isAdminPage(page)||identity?.role==="TEACHER";
export const homePage=identity=>identity?.role==="TEACHER"?"admin":"overview";
