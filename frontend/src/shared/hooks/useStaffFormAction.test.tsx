// @vitest-environment jsdom
import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useStaffFormAction } from './useStaffFormAction'

const toast = vi.fn()
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('react-router', () => ({ useRevalidator: () => ({ revalidate: vi.fn() }) }))
vi.mock('@shared/ui/toast', () => ({ useToast: () => toast }))

const failWith = async (error: unknown) => {
  const { result } = renderHook(() => useStaffFormAction())
  await act(async () => { expect(await result.current.run(() => Promise.reject(error))).toBe(false) })
}

describe('useStaffFormAction', () => {
  beforeEach(() => toast.mockClear())

  it('hiện lý do của máy chủ khi bị từ chối', async () => {
    await failWith(Object.assign(new Error('Điểm dừng 2 phải nghỉ ít nhất 30 phút.'), { status: 400 }))
    expect(toast).toHaveBeenCalledWith('Điểm dừng 2 phải nghỉ ít nhất 30 phút.', 'error')
  })

  it('không hiện chi tiết kỹ thuật khi máy chủ hỏng hoặc mất mạng', async () => {
    await failWith(Object.assign(new Error('NullPointerException'), { status: 500 }))
    await failWith(new Error('Network Error'))
    expect(toast.mock.calls).toEqual([['staffNetwork.actionError', 'error'], ['staffNetwork.actionError', 'error']])
  })
})
