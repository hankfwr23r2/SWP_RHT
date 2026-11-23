// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useNow } from './useNow'

afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

describe('useNow', () => {
  it('cập nhật giờ theo chu kỳ', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-23T08:00:00Z'))
    const { result } = renderHook(() => useNow(30_000))
    expect(result.current).toBe(Date.parse('2026-11-23T08:00:00Z'))

    act(() => vi.advanceTimersByTime(30_000))
    expect(result.current).toBe(Date.parse('2026-11-23T08:00:30Z'))
  })
})
