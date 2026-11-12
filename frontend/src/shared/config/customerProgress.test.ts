import { expect, it } from 'vitest'
import { customerStepOf, type BookingStatus } from './booking-rules'

it.each<[BookingStatus, number]>([
  ['pending_intake', 0], ['under_review', 1], ['pending_commercial', 2],
  ['awaiting_payment', 2], ['quote_expired', 2], ['waybill_issued', 3],
  ['clearance_in_progress', 3], ['ready_for_pickup', 3], ['en_route_to_pickup', 3],
  ['in_transit', 4], ['incident_reported', 4], ['delivered_pending_settlement', 5],
  ['settlement_issued', 5], ['payment_overdue', 5], ['completed', 6],
])('maps %s onto the six customer steps', (status, step) => {
  expect(customerStepOf(status)).toBe(step)
})
