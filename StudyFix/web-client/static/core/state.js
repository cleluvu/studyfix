// Auto-split from the original monolithic static/app.js. See docs/FRONTEND_ARCHITECTURE.md.

export const state = {
  token: sessionStorage.getItem("studyfix.web.token") || "",
  identity: null,
  peerId: "",
  page: "overview",
  exerciseMode: "practice",
  roomId: "",
  room: null,
  classroom: null,
  classroomDrafts: {},
  classroomAutoCheck: true,
  rooms: [],
  roomPage: 1,
  roomSearch: "",
  roomPageSize: 5,
  manualRoomDelete: false,
  roomsTotal: 0,
  roomsTotalPages: 1,
  customRoomCodes: false,
  newRoomCode: "",
  joinRoomCode: "",
  assignments: [],
  catalog: [],
  students: [],
  progress: [],
  contests: [],
  catalogQuery: "",
  catalogMode: "",
  catalogTopic: "",
  catalogDifficulty: "",
  catalogSort: "bank",
  chosenExercise: null,
  activeDebugAssignment: null,
  starterLoadNotice: null,
  exerciseDetail: null,
  detailKind: "",
  assignmentScope: "ROOM",
  assignmentTarget: "",
  assignmentDeadline: "",
  work: null,
  workDetail: null,
  workCode: "",
  workSubmissions: [],
  workResult: null,
  workBusy: false,
  contestFormOpen: false,
  contestQuery: "",
  contestSelected: [],
  contestTitle: "",
  contestStart: 0,
  contestDuration: 60,
  selectedContest: null,
  contestDetail: null,
  contestWork: null,
  contestCode: "",
  contestSubmissions: [],
  leaderboard: [],
  sideTab: "chat",
  roomTab: "code",
  chatDraft: "",
  chatItems: [],
  roomEvents: [],
  eventCursor: 0,
  studentTaskStatus: "",
  screenCursor: 0,
  screenPeers: new Map(),
  localStream: null,
  remoteStream: null,
  screenActive: false,
  activeScreenPresenterId: "",
  remoteScreenPresenter: "",
  remoteScreenPresenterName: "",
  screenRequestedPresenterPeerId: "",
  screenPendingIce: new Map(),
  screenLocalStarting: false,
  history: [],
  historyOpen: false,
  notice: "",
  busy: false,
  filters: { query: "", mode: "", topic: "" },
  activeSubmissionPoll: null,
  progressParentAssignmentId: "",
  codeDirty: false,
  workCodeDirty: false,
  refreshTimer: null,
  booting: true,
  roomLeaving: false,
  meetingClosed: false,
};

// A reload restores the view only after server membership and identity are checked.
export function rememberView() {
  if(!state.identity)return;
  try {sessionStorage.setItem("studyfix.web.view",JSON.stringify({userId:state.identity.userId,page:state.page,exerciseMode:state.exerciseMode,roomId:state.room?.roomId||""}));}catch(_){}
}
export function restoreView() {
  state.page="overview";
  try {
    const saved=JSON.parse(sessionStorage.getItem("studyfix.web.view")||"null");
    if(saved?.userId===state.identity?.userId&&saved.page==="exercises"&&saved.exerciseMode!=="room") {
      state.exerciseMode="practice";state.page="exercises";return state.page;
    }
    if(saved?.userId===state.identity?.userId&&state.room&&saved.roomId===state.room.roomId
      &&["overview","room","exercises","contests"].includes(saved.page)){state.page=saved.page;state.exerciseMode=saved.exerciseMode||"practice";}
  }catch(_){}
  return state.page;
}
