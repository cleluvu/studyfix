# Frontend dependencies served locally

These dependencies are bundled for LAN use; the running application does not fetch them from a CDN.

| Dependency | Version | Source | License |
| --- | --- | --- | --- |
| marked | 17.0.5 | Local installed npm package, `marked/lib/marked.esm.js` | MIT, `marked-17.0.5/LICENSE.md` |
| monaco-editor | 0.52.2 | `https://registry.npmjs.org/monaco-editor/-/monaco-editor-0.52.2.tgz`, `package/min/vs` | MIT, `monaco-0.52.2/LICENSE` |

Monaco source archive SHA-256: `c280cdcf0b0c13d1a2bf01af958d4387ed06d7f6c918401d00c4adcae1bc72b6`.
Marked ESM SHA-256: `0b4487359ce6b85108708e7a532e1242a27f4718241e7b2de109ac6cd307d2dc`.

Markdown parser output is **not** inserted directly. `features/problem-builder/markdown.js` constructs a fresh allowlisted DOM. Uploaded assets are authenticated separately and images use local blob URLs. Monaco runs in a dedicated Builder adapter; it does not replace existing classroom or contest editors.
