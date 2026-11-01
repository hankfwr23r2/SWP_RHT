import { useState, type ReactNode } from 'react'
import { CaptureScopeContext } from '../lib/captureScope'
import type { CaptureContext } from '../types/capture'
export function CaptureScope({ bookingId, tripId, children }: CaptureContext & { children: ReactNode }) {
  const [busy, setBusy] = useState(false)
  return <CaptureScopeContext.Provider value={{ bookingId, tripId, setBusy }}><fieldset disabled={busy} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>{children}</fieldset></CaptureScopeContext.Provider>
}
