// @vitest-environment jsdom
import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ToastProvider } from '../ui/toast'
import { useAutoRefresh, useLoad } from './useLoad'

afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

const wrapper = ({ children }: { children: ReactNode }) => <ToastProvider>{children}</ToastProvider>
const errorToast = () => document.querySelector('[aria-live="assertive"]')?.textContent

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

describe('useLoad', () => {
  it('lần đầu đang tải, xong thì có dữ liệu', async () => {
    const job = deferred<string[]>()
    const { result } = renderHook(() => useLoad(() => job.promise), { wrapper })
    expect(result.current.loaded).toBe(false)

    await act(async () => job.resolve(['BK-001']))
    expect(result.current).toMatchObject({ loaded: true, data: ['BK-001'], error: undefined })
  })

  it('reload() tải ngầm: giữ dữ liệu cũ trên màn hình tới khi có dữ liệu mới', async () => {
    const jobs = [deferred<string>(), deferred<string>()]
    let call = 0
    const { result } = renderHook(() => useLoad(() => jobs[call++].promise), { wrapper })
    await act(async () => jobs[0].resolve('cũ'))

    act(() => { result.current.reload() })
    expect(result.current).toMatchObject({ loaded: true, data: 'cũ' })

    await act(async () => jobs[1].resolve('mới'))
    expect(result.current.data).toBe('mới')
  })

  it('đổi deps: kết quả về muộn của lần gọi cũ bị bỏ', async () => {
    const jobs: Record<string, ReturnType<typeof deferred<string>>> = { a: deferred(), b: deferred() }
    const { result, rerender } = renderHook(({ id }) => useLoad(() => jobs[id].promise, [id]), { wrapper, initialProps: { id: 'a' } })

    rerender({ id: 'b' })
    await act(async () => jobs.b.resolve('đơn b'))
    await act(async () => jobs.a.resolve('đơn a'))

    expect(result.current.data).toBe('đơn b')
  })

  it('lỗi: trả error để trang hiện ErrorState và mặc định vẫn báo Toast', async () => {
    const { result } = renderHook(() => useLoad(() => Promise.reject(new Error('Mất kết nối'))), { wrapper })
    await waitFor(() => expect(result.current.loaded).toBe(true))

    expect(result.current.error?.message).toBe('Mất kết nối')
    expect(result.current.data).toBeUndefined()
    expect(errorToast()).toBe('Mất kết nối')
  })

  it('toastOnError: false thì chỉ trả error, không báo Toast', async () => {
    const { result } = renderHook(() => useLoad(() => Promise.reject(new Error('Mất kết nối')), [], { toastOnError: false }), { wrapper })
    await waitFor(() => expect(result.current.loaded).toBe(true))

    expect(result.current.error?.message).toBe('Mất kết nối')
    expect(errorToast()).toBe('')
  })

  it('thử lại thành công thì xóa lỗi', async () => {
    let fail = true
    const { result } = renderHook(() => useLoad(() => (fail ? Promise.reject(new Error('Mất kết nối')) : Promise.resolve('ok')), [], { toastOnError: false }), { wrapper })
    await waitFor(() => expect(result.current.error).toBeDefined())

    fail = false
    await act(async () => { await result.current.reload() })
    expect(result.current).toMatchObject({ data: 'ok', error: undefined })
  })
})

describe('useAutoRefresh', () => {
  const setHidden = (hidden: boolean) => Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden })

  it('tải lại theo chu kỳ và khi quay lại tab, không tải khi tab đang ẩn', () => {
    vi.useFakeTimers()
    const reload = vi.fn()
    setHidden(false)
    renderHook(() => useAutoRefresh(reload, 1000))

    vi.advanceTimersByTime(1000)
    expect(reload).toHaveBeenCalledTimes(1)

    setHidden(true)
    vi.advanceTimersByTime(1000)
    expect(reload).toHaveBeenCalledTimes(1)

    setHidden(false)
    document.dispatchEvent(new Event('visibilitychange'))
    window.dispatchEvent(new Event('focus'))
    expect(reload).toHaveBeenCalledTimes(3)
  })

  it('rời trang thì ngừng tải lại', () => {
    vi.useFakeTimers()
    const reload = vi.fn()
    setHidden(false)
    const { unmount } = renderHook(() => useAutoRefresh(reload, 1000))
    unmount()

    vi.advanceTimersByTime(5000)
    window.dispatchEvent(new Event('focus'))
    expect(reload).not.toHaveBeenCalled()
  })
})
