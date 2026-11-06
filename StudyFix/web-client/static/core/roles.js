export const teacherPageLabels={admin:"Tổng quan giảng viên","admin-classes":"Lớp học của tôi","admin-students":"Sinh viên","admin-connections":"Theo dõi lớp học","admin-assignments":"Bài tập & tiến độ"};
export const teacherPages=[...Object.keys(teacherPageLabels),"admin-attendance"];
export const isAdminPage=page=>teacherPages.includes(page);
export const canNavigate=(identity,page)=>!isAdminPage(page)||identity?.role==="TEACHER";
export const homePage=identity=>identity?.role==="TEACHER"?"admin":"overview";
