import type { StaffRef } from '../types/booking'

/** Match assigned staff by staff code or display name from the active session. */
export const isAssignedTo = (staff: StaffRef | undefined, identity: string | undefined): boolean =>
  !!staff && !!identity && (staff.id === identity || staff.name === identity)
