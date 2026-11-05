# Horse location after handover

Both handover paths now save the destination country, address, source, update time
and passport entry through `HorseDeliveryLocationService`. Trip delivery changes
only that vehicle's horses; an order with several vehicles waits for every
delivery before moving to settlement. A vehicle's return to HQ does not move the
horse back to HQ.

Pickup comparison recognizes complete normalized address components, including
accents and component order. A city-only or partial address needs confirmation;
different farms or house numbers remain different. Changing the selected horses
or pickup invalidates the previous confirmation. Existing cross-country blocks
remain enforced by both frontend and backend.

## Existing data

The code fix does not silently migrate production records. Run
`backend/scripts/audit-horse-delivery-locations.sql` against PostgreSQL 16 or newer
with a read-only database account, for example:

```powershell
psql --file backend/scripts/audit-horse-delivery-locations.sql
```

Supply the connection via the usual libpq environment variables; do not put
passwords in the command or commit a connection file. The script starts a
read-only transaction and rolls it back. It reports the latest recorded
`run.deliveredAt` or `handover.handoverAt`, the saved location, and a proposed
destination and country.

Review each `CANDIDATE_FOR_REPAIR` before applying any change. Owner declarations,
locations updated after delivery, horses in transit and missing destination
evidence require manual review. Do not infer a country merely from a city name
or overwrite a newer owner relocation. Records without handover evidence appear
in the second report if their country is missing or unsupported.

For a customer who needs to book immediately, use Horse Profiles to confirm the
actual country and full stable address, then return to booking. The pickup step
will ask for confirmation if the saved address is only a city or differs from
the requested pickup.

## Regression checks

- Finish delivery in Hanoi with an initially empty country; verify `VN`,
  `SYSTEM_DELIVERY`, the arrival timestamp and a passport entry are persisted.
- Close the order after settlement, then book the same horse for a VN pickup.
- Deliver a cross-border trip; the destination country replaces the origin.
- Deliver one vehicle in a multi-vehicle order; the other horses stay in transit.
- Retry a previously recorded handover after an owner relocation; the relocation
  and journey history remain unchanged.
- Compare reordered/accented addresses, a city-only address, different farms and
  different house numbers. Confirm the pickup again after changing selection.

Deploying the backend and frontend is needed for these changes to affect the
hosted application. The audit script itself changes no production data.

## Validation (2026-11-05)

- Backend `mvnw.cmd verify`: 311 tests, 0 failures/errors, 2 existing live-routing
  tests skipped; the remaining 309 passed. All database checks used local
  PostgreSQL or disposable Testcontainers databases.
- Frontend `npm run lint`, `npx tsc -b`, `npm test`, `npm run build`: passed;
  251 frontend tests passed. Lint still reports existing project warnings.
- Installed dependencies with `npm ci` to match the lockfile after pulling the
  upstream i18next changes; package and lockfile contents were not changed.
- The read-only audit SQL was exercised against local PostgreSQL. The project's
  root `.env` targets a local database; no Aiven audit or data repair was executed.
- Ran `graphify update .` and checked the final diff for whitespace errors.

## Integration with main (2026-11-05)

- Updated local `main` by 16 commits to `f818d27` (PR #271).
- Preserved all previous local work, including untracked files, in stash commit
  `2f0ffc6ffae2dfc858c08521617a8553c9d875d2` before updating.
- Reconciled the two fleet-plan/map conflicts while retaining the new backend
  truck-route calculation and the local explicit station-add interaction.
- A station's rest duration now participates in the preview request key, so
  editing the duration recalculates the schedule. Equivalent route refreshes
  keep the same map instance and viewport.
- Retained and checked the horse-location fix, partial profile-save retry and
  file-size validation, and null insurance-document handling in order details.
- Validation: 260 frontend unit tests passed; 14 distinct browser scenarios
  passed (the map tests were rerun after correcting a test-only zoom selector).
  Backend verify ran 323 tests with no failures/errors and 2 live-routing skips,
  using a disposable database instead of the developer's working database.
- Lint, TypeScript, production builds, final diff checks and graphify update
  completed. Existing lint warnings remain.
