# StudyFix Web FE — kiến trúc sau refactor

Tài liệu này mô tả cấu trúc `web-client/static` sau khi tách `app.js` (trước đây
~1970 dòng, một file duy nhất) thành các module ES (`import`/`export`), và cách
mở rộng tiếp.

## Vì sao tách theo cách này

Bản gốc dùng vanilla JS không build step, phục vụ trực tiếp qua Java
`WebGatewayServer`. Yêu cầu bắt buộc: giữ nguyên hành vi, giữ nguyên API
contract, không thêm bundler/dependency, không đổi entry point. Vì vậy việc
tách module dùng **ES Modules native của trình duyệt** (`<script type="module">`
đã có sẵn trong `index.html`), không cần Vite/webpack.

Toàn bộ logic nghiệp vụ trong file gốc được **trích xuất nguyên văn** (từng
hàm/khai báo top-level được cắt theo đúng ranh giới dòng của nó, không gõ lại
tay) rồi phân phối vào các file bên dưới và thêm `import`/`export`. Điều này
giảm tối đa rủi ro gõ sai logic trong lúc tách file.

## Sơ đồ phụ thuộc (không có vòng import)

```
shared/format.js      (không phụ thuộc gì)
shared/crypto.js      (không phụ thuộc gì)
core/state.js         (không phụ thuộc gì)
core/router.js        (không phụ thuộc gì — registry thuần)
ui/modal.js           (không phụ thuộc gì)

core/dom.js            -> core/state.js
ui/components.js       -> shared/format.js
ui/toast.js            -> core/dom.js, shared/format.js
core/api.js            -> core/state.js
core/polling.js        -> core/state.js, core/api.js, ui/toast.js, ui/render.js
ui/render.js           -> core/state.js, core/dom.js, core/router.js,
                           shared/format.js, ui/components.js

features/screen-share/ -> core/state.js, core/api.js, ui/toast.js, ui/render.js,
                           core/dom.js, ui/components.js, core/polling.js
features/auth/         -> core/state.js, core/api.js, core/polling.js,
                           ui/render.js, ui/toast.js, features/screen-share/
features/room/         -> core/*, ui/*, shared/*, features/auth/,
                           features/screen-share/
features/exercises/    -> core/*, ui/*, shared/*, features/room/ (chỉ
                           fetchDebugExercise, cho luồng mở bài sửa lỗi)
features/contests/     -> core/*, ui/*, shared/*, features/exercises/ (chỉ
                           renderSubmissionResult)

core/events.js          -> gần như mọi feature module (điểm dispatch action)
core/lifecycle.js       -> core/state.js, core/polling.js, ui/render.js,
                           features/auth/auth.js
app.js                  -> core/router.js, ui/render.js, 3 feature render,
                           core/lifecycle.js, core/events.js (side-effect)
```

`app.js` và `core/events.js` là hai điểm "lá" duy nhất không bị ai import —
đúng vai trò entry point / wiring.

### Hai điểm phá vòng lặp import có chủ đích

Code gốc có 2 chỗ mà một module "lõi" (core) cần *gọi* một hàm nằm ở module
"tính năng" (feature) — nhưng module tính năng đó lại cần *gọi lại* core để
render/đồng bộ, nên import trực tiếp hai chiều sẽ tạo vòng lặp
(`core/polling.js <-> features/auth/auth.js`,
`core/polling.js <-> features/screen-share/screen-share.js`, và
`core/api.js <-> features/auth/auth.js`). Thay vì import ngược, các chỗ này
dùng **callback đăng ký (dependency injection nhỏ)**, hành vi runtime giữ
nguyên 100% so với bản gốc:

1. **401 Unauthorized** (`core/api.js`): thay vì gọi thẳng `logout()`, phát
   `CustomEvent("studyfix:unauthorized")` trên `document`. `features/auth/auth.js`
   lắng nghe sự kiện này và tự gọi `logout(false)` — y hệt bản gốc, chỉ đổi
   cách "dây điện" được nối.
2. **Polling** (`core/polling.js`): expose `setRoomJoinedHandler()` (bản gốc
   gọi thẳng `refreshMe().then(loadCurrentRoom).then(loadPageData)` sau khi
   vào phòng) và `setScreenSignalHandler()` (bản gốc gọi thẳng
   `receiveScreenSignal()` mỗi lần poll). `features/auth/auth.js` và
   `features/screen-share/screen-share.js` tự đăng ký callback của mình ở
   cuối file. Thứ tự gọi, thời điểm gọi (`setTimeout(..., 250)`, mỗi vòng
   poll 1600ms) giữ nguyên.

Tương tự, `ui/render.js` không import `renderRoom`/`renderExercises`/
`renderContests` trực tiếp (các module đó cần gọi `render()` sau khi đổi
state, nên import ngược sẽ tạo vòng). Thay vào đó `core/router.js` cung cấp
`registerPage(name, fn)` / `renderPage(name)`; `app.js` đăng ký 4 trang khi
khởi động, `render()` chỉ gọi `renderPage(state.page)`.

## Vai trò từng thư mục

- **`core/`** — hạ tầng dùng chung, không chứa nghiệp vụ UI cụ thể:
  `state.js` (object state toàn cục, y hệt bản gốc), `api.js` (`api`,
  `assignment`, `command` — giữ nguyên URL/method/body), `dom.js` (tham chiếu
  `#app`, `#toast-region`, và `bindVideoElements`), `router.js` (registry
  trang), `polling.js` (vòng lặp `/api/events`, các hàm `loadXxx`,
  `navigate`), `events.js` (3 listener `click`/`submit`/`input`/`change` —
  xem bên dưới), `lifecycle.js` (`init()`).
- **`ui/`** — thành phần giao diện dùng chung: `toast.js`, `modal.js`,
  `components.js` (`pill`/`btn`/`empty`), `render.js` (`render()`,
  `layout()`, trang đăng nhập, trang tổng quan).
- **`shared/`** — hàm thuần không phụ thuộc DOM/state: `format.js` (định dạng
  ngày giờ, nhãn, lớp CSS theo verdict/độ khó...), `crypto.js`
  (`cryptoRandomId`, `sha256` — dùng cho idempotency key và hash code khi
  kiểm tra chung).
- **`features/`** — từng nhóm nghiệp vụ, mỗi nhóm có cả hàm render lẫn hàm
  gọi API của riêng nó:
  - `auth/auth.js`: `login`, `logout`, `refreshMe`.
  - `room/room.js`: phòng học, code cộng tác, quyền sửa, bản nháp, lịch sử
    phiên bản, kiểm tra code chung, chat, danh sách thành viên, tạo/tham gia
    phòng. (Chat và danh sách thành viên là `renderChat`/`renderMembers`,
    2 hàm nhỏ dùng chung state của phòng nên vẫn nằm trong `room.js` thay vì
    tách file riêng — xem mục "Giới hạn" bên dưới.)
  - `screen-share/screen-share.js`: WebRTC signaling, `getDisplayMedia`.
  - `exercises/exercises.js`: ngân hàng bài, giao bài, làm bài cá nhân, nộp
    bài, báo cáo tiến độ.
  - `contests/contests.js`: tạo kỳ thi, workspace thi, bảng xếp hạng.

## `core/events.js` — dispatcher, không phải nơi chứa nghiệp vụ

File này giữ nguyên 4 listener gốc (`click`, `submit`, `input`, `change`)
**gần như y hệt bản gốc về cấu trúc `switch(action)`**, nhưng khác bản gốc ở
điểm quan trọng: mỗi `case` giờ chỉ gọi một hàm đã được chuyển sang module
nghiệp vụ tương ứng (import ở đầu file), không còn nghiệp vụ nằm trực tiếp
trong `switch`. Những dòng còn giữ logic tại chỗ (vd. `state.roomTab = d.tab;
render();`) là các thao tác state 1 dòng, không đáng tách thêm hàm.

## Những gì **không** đổi (bảo toàn hợp đồng)

- Toàn bộ URL, method, action name, request/response field của `core/api.js`
  (`/api/me`, `/api/auth/login`, `/api/auth/logout`, `/api/assignments`,
  `/api/rooms/current/command`, `/api/rooms/current`, `/api/rooms/current/history`,
  `/api/events`, `/api/screen/signal`, `/api/screen/signals`, `/api/rooms`).
- Toàn bộ `data-action`, `data-*`, id phần tử DOM dùng trong template string.
- Thứ tự gọi API, thời điểm `render()`, thời gian `setTimeout`/polling
  interval (1600 ms), số lần retry khi poll kết quả chấm (40 lần / 35 lần).
- `entry point` (`static/index.html` nạp `/app.js` dạng `type="module"`,
  `/app.css`) — không đổi đường dẫn tĩnh nào mà server Java đang phục vụ.
- `static/app.css` giữ nguyên một file (xem mục Giới hạn).

## Giới hạn / việc chưa làm, và vì sao

1. **CSS chưa được tách** thành `styles/tokens.css`, `layout.css`,
   `components.css` như cấu trúc gợi ý trong yêu cầu. File `app.css` gốc
   (~400 dòng, 1 selector/dòng, không lồng nhau) không có vấn đề về khả năng
   đọc giống mức độ của `app.js`; việc tách sẽ phải chia lại theo từng
   selector thủ công với rủi ro bỏ sót mà lợi ích bảo trì thấp hơn nhiều so
   với JS. Nếu cần, có thể tách theo từng feature (`features/room.css`,
   `features/contests.css`...) ở một lần refactor sau, với cùng phương pháp
   trích xuất nguyên văn theo selector.
2. **`chat/`, `members/` không tách thành file riêng** dù cấu trúc gợi ý có
   đề cập — `renderChat`/`renderMembers` trong bản gốc chỉ 4–5 dòng mỗi hàm,
   dùng chung `state.room`/`state.chatItems` của `room.js` và không có logic
   gọi API riêng (gửi chat đi qua `command("CHAT", ...)` trong
   `core/events.js`, submit handler). Tách thành file riêng sẽ chỉ tạo thêm
   2 file 10 dòng và một vòng import ngược không cần thiết.
3. **`dashboard/` không có file riêng** — `renderOverview` ở lại `ui/render.js`
   vì đây là trang mặc định (`else renderOverview()` trong bản gốc), không
   có logic tải dữ liệu riêng ngoài những gì `core/polling.js` đã tải.
4. **Không thể kiểm thử tích hợp với server Java thật** vì chỉ có thư mục
   `web-client` — xem `tests/README.md`.

## Thêm một feature mới

1. Tạo `features/<ten>/<ten>.js`, export các hàm `render...`/hàm xử lý.
2. Nếu cần gọi API mới, dùng `assignment()`/`command()`/`api()` có sẵn trong
   `core/api.js` — **không** tự chế endpoint mới nếu server chưa có.
3. Nếu feature có trang riêng: gọi `registerPage("ten-trang", renderTen)`
   trong `app.js`, thêm nút điều hướng trong `nav` của `layout()`
   (`ui/render.js`).
4. Thêm `case` tương ứng vào `core/events.js` nếu cần `data-action` mới,
   import hàm xử lý từ file feature — không viết nghiệp vụ trực tiếp trong
   `events.js`.
5. Không import module feature khác trừ khi thực sự cần tái dùng một hàm
   render nhỏ (như `exercises.js` tái dùng `fetchDebugExercise` từ
   `room.js`, hoặc `contests.js` tái dùng `renderSubmissionResult` từ
   `exercises.js`) — kiểm tra sơ đồ phụ thuộc ở trên để không tạo vòng lặp.

## Chạy / build

Không đổi gì so với README gốc: server Java phục vụ `web-client/static`
trực tiếp, mở `http://localhost:8080` sau khi chạy
`server/run-server-manager.ps1`. Không cần `npm install`, không cần build
step — trình duyệt tải `index.html` → `/app.js` (`type="module"`) → trình
duyệt tự resolve toàn bộ cây `import` tương đối còn lại.
