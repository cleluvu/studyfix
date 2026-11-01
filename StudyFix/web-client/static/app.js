// Entry point. Only responsible for: wiring the page registry, loading the
// event-delegation module (side effects: attaches the click/submit/input/change
// listeners), and starting the app. No business logic lives here — see
// docs/FRONTEND_ARCHITECTURE.md for where everything moved to.

import { registerPage } from "./core/router.js";
import { renderOverview } from "./ui/render.js";
import { renderRoom } from "./features/room/room.js";
import { renderExercises } from "./features/exercises/exercises.js";
import { renderContests } from "./features/contests/contests.js";
import { init } from "./core/lifecycle.js";

// Attaches document/root click, submit, input and change listeners.
import "./core/events.js";

registerPage("overview", renderOverview);
registerPage("room", renderRoom);
registerPage("exercises", renderExercises);
registerPage("contests", renderContests);

init();
