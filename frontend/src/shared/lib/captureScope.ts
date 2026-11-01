import { createContext, useContext } from 'react'
import type { CaptureContext } from '../types/capture'
export const CaptureScopeContext = createContext<(CaptureContext & { setBusy: (busy: boolean) => void }) | null>(null)
export function useCaptureScope() { return useContext(CaptureScopeContext) }
