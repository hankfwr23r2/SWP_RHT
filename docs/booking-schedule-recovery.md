# Overdue departure dates before payment

Booking dates are Vietnam calendar days. An order requested for 30 November 2026
becomes overdue at **00:00 on 1 December 2026 in Asia/Ho_Chi_Minh**, not at the start
of 30 November. The customer currently selects a day, not a departure appointment.

Every 30 seconds, `BookingScheduleScheduler` checks pre-payment orders in intake,
review, commercial approval, awaiting payment and expired-quote states. It locks
each order before changing it to `schedule_change_required`. It releases vehicle
and crew reservations, expires pending quotations, records the history, creates
customer and manager notifications, and sends a customer email after commit if
email delivery is configured. A repeated scan does not duplicate notifications.

Intake, medical approval, route planning, crew assignment, quote issuance and new
deposit checkout also reject overdue schedules before the scan runs. New quotes
cannot remain payable beyond the end of the requested departure day.

## Customer choices

- Choose a new calendar date with the configured `MIN_LEAD_DAYS` preparation time
  (at least tomorrow). The server verifies ownership and the date. The same order
  returns to `pending_intake`; parties and horses are preserved, while medical and
  insurance review, route, crew assignment and quote must be checked again.
- Cancel the unpaid request after entering a reason and explicitly confirming.
- Ask a manager to contact them. The request is recorded once, while the customer
  can still choose a date or cancel. Managers can find it under Intake →
  “Cần xác nhận lại lịch”, including the sender's phone number.

The customer endpoints are `POST /customer/bookings/{id}/schedule-change`,
`POST .../schedule-change/cancel` and `POST .../schedule-change/support` under the
existing `/api/v1` context. A date request is `{"departureDate":"2026-12-21"}`;
a cancellation request is `{"reason":"..."}`. Customer identity comes from the
authenticated principal, not the request body.

## Payment safety and rollout

Orders with any payment other than `FAILED` are excluded from automatic schedule
changes and cannot use the unpaid reschedule/cancel actions. Pending, completed
or review-required payments continue through the existing reconciliation and
management process. Operational trips are not automatically changed or cancelled.
This feature does not impose cancellation penalties or perform refunds.

No database migration is needed: the existing status column and details JSON hold
the new state. Rebuild/restart the backend and frontend for the running app to pick
up the implementation. The scan interval can be overridden with
`app.booking.schedule-scan-ms`. Tests use disposable databases and fixed clocks;
changing the computer's date or the customer's real booking is unnecessary.

## Validation (2026-11-09)

- Backend `mvnw.cmd verify`: 436 tests, zero failures/errors, two existing skips.
  The default datasource was a disposable local PostgreSQL container; integration
  tests also use their own Testcontainers databases.
- Frontend lint, `npx tsc -b`, 285 unit tests and production build passed.
  Lint has existing project warnings; the new schedule components add none.
- Four Chromium scenarios passed: submitting a date, confirming cancellation,
  mobile support requests, and retaining input after a reconciliation refusal.
- Rebuilt and restarted the local backend/frontend containers. Both returned HTTP
  200, and all three schedule endpoints were present in the running API.
- A read-only query confirmed BK-3 remained `pending_intake` with departure date
  30 November 2026 and a schedule cutoff of 1 December 2026 in Vietnam.
