// Registration keeps the classroom renderer independent of polling and API modules.
let hooks={};
export function setExamUi(value){hooks=value;}
export function classroomExamId(){return hooks.classroomExamId?.()||"";}
export function renderStudentExamStatus(){return hooks.renderStudentExamStatus?.()||"";}
export function renderExamMonitor(contest){return hooks.renderExamMonitor?.(contest)||"";}
