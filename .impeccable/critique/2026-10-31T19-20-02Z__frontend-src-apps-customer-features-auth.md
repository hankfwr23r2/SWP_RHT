---
target: trang đăng nhập và đăng ký
total_score: 27
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
target_identity: "file:/home/khiet/Repos/SWP_RHT/.claude/worktrees/impeccable-init-e0b75e/frontend/src/apps/customer/features/auth"
timestamp: 2026-10-31T19-20-02Z
slug: frontend-src-apps-customer-features-auth
---
# Critique (re-run): trang Đăng nhập và Đăng ký
Method: dual-agent. Total 27/40 (Acceptable, up from 22). CLI detector 0; overlay: overused-font (/register, false positive: global Roboto by design), straggler on staff tagline. Contrast: 0 failures (button 5.18, links 4.95). Focus visible on every tab stop; no native bubbles; 1 h1 per page.

## Heuristics
1:3 · 2:3 · 3:3 (forgot-password email locked) · 4:2 (three error patterns; staff label/placeholder/error mismatch) · 5:3 · 6:3 · 7:3 · 8:3 (left heading repeats brand) · 9:3 (register server error not tied to field, focus lost) · 10:1 (no contact line)

## Priority issues
- [P1] Register server errors (e.g. duplicate email) go to top alert, focus drops to body, email not marked invalid; after redirect to /login focus not on password — map email errors to field, autofocus password when registeredEmail.
- [P1] No help/contact line on auth pages — reuse homepage footer contact, no invented details.
- [P2] Staff login: label "Email" vs placeholder/error "email hoặc tên đăng nhập"; two back affordances; no aria-invalid.
- [P2] Left panel heading repeats brand; photo has no horse — consider corridor "Việt Nam · Lào · Campuchia".
- [P3] Forgot password: no aria-invalid/focus on error, no show-password, no change-email, OTP submit button 41px at 390.

## Minor
No account-created welcome after auto-login; wrong credentials always mark password; email/phone lack maxLength; "Đăng nhập vào tài khoản" wordy; staff loading copy differs; dead ternary homeHref in ForgotPasswordPage.
