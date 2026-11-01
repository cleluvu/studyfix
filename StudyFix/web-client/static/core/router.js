// Auto-split from the original monolithic static/app.js. See docs/FRONTEND_ARCHITECTURE.md.
// Tiny page-renderer registry.
//
// ui/render.js needs to call the current page's render function (renderRoom,
// renderExercises, renderContests, renderOverview, ...), but those feature
// modules all need to call render() (defined in ui/render.js) after they
// mutate state. A direct import in either direction would create a cycle, so
// instead each feature module calls registerPage() once (see app.js) and
// ui/render.js looks the function up here at render time.

const pages = {};

export function registerPage(name, renderFn) {
  pages[name] = renderFn;
}

export function renderPage(name) {
  const renderFn = pages[name] || pages.overview;
  return renderFn ? renderFn() : "";
}
