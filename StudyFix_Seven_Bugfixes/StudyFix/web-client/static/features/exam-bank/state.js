export const statusLabels={DRAFT:'Bản nháp',APPROVED:'Đã duyệt',ARCHIVED:'Lưu trữ'};
export const stepNames=['Thông tin','Chọn nguồn bài','Cấu trúc & điểm','Cấu hình chấm','Xem lại & lưu'];
export const defaultGrading=()=>({inherit:true,javaVersion:17,showPublicTests:true,showScore:true,allowTemplate:true,allowResubmit:true,timeLimitMs:3000,memoryLimitMb:256,outputLimitKb:2048,checker:'EXACT',partialScoring:true});
export const blankExam=()=>({code:'EXM_'+Date.now().toString(36).toUpperCase(),title:'',description:'',durationMinutes:90,totalPoints:10,scoringMode:'WEIGHTED_TEN',version:1,revision:0,status:'DRAFT',sourceWeeks:[],items:[],grading:defaultGrading()});
export const eb={owner:'',view:'list',step:1,list:[],stats:{},total:0,page:1,pages:1,query:'',status:'',sourceFilter:'',selected:new Set(),questions:[],weeks:[],source:'weeks',sourceWeekId:'',randomCount:1,weekRandomCounts:{},questionQuery:'',topic:'',difficulty:'',exam:blankExam(),dirty:false,busy:false,error:'',initialized:false,detailTab:'Tổng quan',io:'import',importPreview:null,updateImport:false,preview:null,previewIndex:0};
export const sumPoints=()=>Math.round(eb.exam.items.reduce((s,i)=>s+Number(i.points||0),0)*100)/100;
export const normalize=s=>String(s).toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/đ/g,'d');
export const questionFor=id=>eb.exam.items.find(i=>i.exerciseId===id)?.question||eb.questions.find(q=>q.id===id)||{id,title:id,topic:'—',difficulty:'—'};
