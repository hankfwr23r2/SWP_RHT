// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import '@shared/i18n'
import { ConfirmDialog } from './ConfirmDialog'

afterEach(cleanup)

const deferred = () => {
  let resolve!: () => void
  let reject!: (e: unknown) => void
  const promise = new Promise<void>((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

function renderDialog(onConfirm: () => unknown, onCancel = vi.fn()) {
  render(<ConfirmDialog title="Hủy đơn BK-001?" message="Khoang đã giữ sẽ được trả lại." confirmLabel="Hủy đơn" tone="danger" onConfirm={onConfirm} onCancel={onCancel} />)
  return onCancel
}

describe('ConfirmDialog', () => {
  it('đọc cả tiêu đề và hậu quả, tiêu điểm đặt sẵn ở nút Hủy', () => {
    renderDialog(vi.fn())
    const dialog = screen.getByRole('alertdialog', { name: 'Hủy đơn BK-001?' })
    expect(document.getElementById(dialog.getAttribute('aria-describedby')!)?.textContent).toBe('Khoang đã giữ sẽ được trả lại.')
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Hủy' }))
  })

  it('đang xử lý thì bấm lần hai không gọi lại, không đóng được', async () => {
    const job = deferred()
    const onConfirm = vi.fn(() => job.promise)
    const onCancel = renderDialog(onConfirm)
    const confirm = screen.getByRole('button', { name: 'Hủy đơn' })

    fireEvent.click(confirm)
    fireEvent.click(confirm)
    fireEvent.keyDown(confirm, { key: 'Escape' })

    expect(onConfirm).toHaveBeenCalledTimes(1)
    expect(onCancel).not.toHaveBeenCalled()
    expect((confirm as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByRole('button', { name: 'Hủy' }) as HTMLButtonElement).disabled).toBe(true)
    await act(async () => job.resolve())
  })

  it('thao tác lỗi thì mở khóa để thử lại', async () => {
    const job = deferred()
    renderDialog(() => job.promise)
    const confirm = screen.getByRole('button', { name: 'Hủy đơn' })

    fireEvent.click(confirm)
    await act(async () => job.reject(new Error('mạng lỗi')))

    expect((confirm as HTMLButtonElement).disabled).toBe(false)
  })

  it('nút Hủy gọi onCancel', () => {
    const onCancel = renderDialog(vi.fn())
    fireEvent.click(screen.getByRole('button', { name: 'Hủy' }))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })
})
