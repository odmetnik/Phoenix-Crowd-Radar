# Phoenix Crowd Radar 0.4 — visual verification

Source visual truth: user attachment `upload/01-1000172505.png`, 1024 × 1536.
Implementation: Android offline WebView assets, `app/src/main/assets/ui/`.
State to compare: demo, Marko / Ana / Ivan, portrait.

final result: blocked

Browser-rendered implementation screenshot: unavailable. Cloud browser returned “upstream connect error or disconnect/reset before headers, connection termination” at the preview after launch and one restart. Source capture is available; implementation capture, equal viewport comparison and console inspection are blocked. No claim of pixel equivalence or visual approval.

Implemented surfaces pending visual inspection:
- Typography: wide bold cyan heading, uppercase cards, small tracking on branding; system Arial fallback, rather than exact unidentified display font.
- Spacing: circular radar, degree labels, three angular cards, large search button, three lower tabs. Extra operational controls and status are intentional production additions.
- Color: near-black blue, luminous cyan, GPS green, BLE amber.
- Assets: locally bundled MIT Tabler icons; animated radar uses Canvas as requested. No internet assets or generated static radar.
- Copy: Croatian, clear demo badge, editable actual group name, unknown direction never plotted as a point.

Verified without browser: JS syntax and Node UI state tests (navigation, native action routing, measured zero, stale measurements, unknown bearing, untrusted names rendered as text). These checks do not replace screenshot QA or physical Android tests.

Follow-up: compare portrait demo screenshot with the source, inspect console and adjust typography/layout if needed. Test QR and Nearby enrollment and BLE/GPS/UWB between two real phones.
