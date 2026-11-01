---
target: trang đăng nhập và đăng ký
total_score: 22
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 3
target_identity: "file:/home/khiet/Repos/SWP_RHT/.claude/worktrees/impeccable-init-e0b75e/frontend/src/apps/customer/features/auth"
timestamp: 2026-10-31T18-18-54Z
slug: frontend-src-apps-customer-features-auth
---
# Critique: trang Đăng nhập và Đăng ký (cổng khách hàng)
Method: dual-agent. Total 22/40 (Acceptable). Scope: /login, /register, AuthShell; /backoffice/login noted.

## Heuristics
1 Status 3 (register success = alert()) · 2 Real world 2 (backend "Tên đăng nhập/email…", Title Case, "chiến mã") · 3 Control 2 (register lacks "Về Trang chủ", forced re-login) · 4 Consistency 2 (login noValidate vs register native English bubbles; toggle only on login) · 5 Error prevention 2 (no required/optional marks, phone placeholder misleading) · 6 Recognition 3 (rules in placeholders) · 7 Efficiency 2 (no new-password) · 8 Aesthetic 3 (6 exits on login) · 9 Error recovery 2 (one error at a time, top of form, not announced) · 10 Help 1 (no contact, no terms link).

## Specificity
Generic split-screen SaaS shell; race.jpg instead of transport images; tagline "An toàn, chuẩn quốc tế và giám sát 24/7…" (AuthShell.tsx:17) makes unbacked claims; gradient (AuthShell.module.css:14) violates DESIGN.md. Detector: CLI 0; browser overlay 8 findings: low-contrast #fff on #ea580c 3.56:1 (buttons), #ea580c on #f8fafc 3.4:1 (links), straggler in h1.

## Priority issues
- [P1] Register uses English native validation, no inline errors — add noValidate (RegisterPage.tsx:63), per-field errors with .invalid/aria-invalid/aria-describedby. /impeccable harden
- [P1] Orange #ea580c fails 4.5:1 for button text and links (system-wide token) — use #c2410c for button bg and link text; update DESIGN.md. /impeccable colorize
- [P1] Register ends with alert() + forced re-login (RegisterPage.tsx:52) — auto-login to /portal or redirect with success banner + prefilled email. /impeccable onboard
- [P2] Errors not announced/attached, login too; raw backend message — role="alert", focus invalid field, "Email hoặc mật khẩu không đúng." /impeccable clarify
- [P2] Register parity: no show-password, no new-password, rules in placeholders, required/optional unmarked, Title Case — reuse toggle, hint lines, sentence case. /impeccable polish

## Persona red flags
Jordan: no reason given for phone/address; English bubbles; dumped to login. Sam: errors unannounced; default focus outline on orange button/toggle; toggle 26x25 (staff 18x16); h1 marketing / h2 form. Riley: two different email validators; name regex rejects "Trại ngựa Số 1". Stable owner: booking intent lost (register drops `from`); farm name rejected; "Dành cho nội bộ" confusing.

## Minor
Password placeholder "••••••••"; wordy switch line with "!"; footer repeats h1; label "Email (dùng làm tên đăng nhập)"; staff page same tagline problem and Title Case.

## Questions
Why a separate login after register? Left panel: emotion or promise? Account = person or stable?
