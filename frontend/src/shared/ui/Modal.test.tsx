// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import '@shared/i18n'
import { Modal } from './Modal'

afterEach(cleanup)

function Opener() {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button onClick={() => setOpen(true)}>Mở</button>
      {open && (
        <Modal title="Sửa xe" onClose={() => setOpen(false)} footer={<button>Lưu xe</button>}>
          <input aria-label="Biển số" />
        </Modal>
      )}
    </>
  )
}

describe('Modal', () => {
  it('có tên lấy từ tiêu đề, nút đóng có nhãn', () => {
    render(<Modal title="Sửa xe" onClose={() => {}}>nội dung</Modal>)
    expect(screen.getByRole('dialog', { name: 'Sửa xe' }).getAttribute('aria-modal')).toBe('true')
    expect(screen.getByRole('button', { name: 'Đóng' })).toBeTruthy()
  })

  it('Escape đóng hộp thoại', () => {
    const onClose = vi.fn()
    render(<Modal title="Sửa xe" onClose={onClose}><input aria-label="Biển số" /></Modal>)
    fireEvent.keyDown(screen.getByLabelText('Biển số'), { key: 'Escape' })
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('tiêu điểm rơi ra trang khi nút bị khóa thì được kéo lại hộp thoại trên cùng', () => {
    const view = (busy: boolean) => (
      <Modal title="Ngoài" onClose={() => {}}><Modal title="Trong" onClose={() => {}}><button disabled={busy}>Lưu</button></Modal></Modal>
    )
    const { rerender } = render(view(false))
    ;(document.activeElement as HTMLElement).blur()
    rerender(view(true))
    expect(document.activeElement).toBe(screen.getByRole('dialog', { name: 'Trong' }))
  })

  it('Escape đã được ô chọn xử lý (preventDefault) thì không đóng', () => {
    const onClose = vi.fn()
    render(<Modal title="Sửa xe" onClose={onClose}><input aria-label="Biển số" onKeyDown={e => e.preventDefault()} /></Modal>)
    fireEvent.keyDown(screen.getByLabelText('Biển số'), { key: 'Escape' })
    expect(onClose).not.toHaveBeenCalled()
  })

  it('hộp thoại lồng nhau: Escape chỉ đóng hộp thoại trên cùng', () => {
    const outer = vi.fn()
    const inner = vi.fn()
    render(
      <Modal title="Ngoài" onClose={outer}>
        <Modal title="Trong" onClose={inner}><input aria-label="Ô trong" /></Modal>
      </Modal>,
    )
    fireEvent.keyDown(screen.getByLabelText('Ô trong'), { key: 'Escape' })
    expect(inner).toHaveBeenCalledTimes(1)
    expect(outer).not.toHaveBeenCalled()
  })

  it('bấm nền ngoài hộp thoại thì đóng, bấm bên trong thì không', () => {
    const onClose = vi.fn()
    render(<Modal title="Sửa xe" onClose={onClose}>nội dung</Modal>)
    fireEvent.click(screen.getByText('nội dung'))
    expect(onClose).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('dialog').parentElement!)
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('mở ra thì tiêu điểm vào hộp thoại, ưu tiên ô có data-autofocus', () => {
    render(<Modal title="Sửa xe" onClose={() => {}}><input aria-label="Biển số" data-autofocus /></Modal>)
    expect(document.activeElement).toBe(screen.getByLabelText('Biển số'))
  })

  it('Tab ở ô cuối quay về ô đầu, Shift+Tab ở ô đầu sang ô cuối', () => {
    render(<Opener />)
    fireEvent.click(screen.getByText('Mở'))
    const close = screen.getByRole('button', { name: 'Đóng' })
    const save = screen.getByRole('button', { name: 'Lưu xe' })

    save.focus()
    fireEvent.keyDown(save, { key: 'Tab' })
    expect(document.activeElement).toBe(close)

    fireEvent.keyDown(close, { key: 'Tab', shiftKey: true })
    expect(document.activeElement).toBe(save)
  })

  it('đóng xong trả tiêu điểm về nút đã mở và mở lại cuộn trang', () => {
    render(<Opener />)
    const opener = screen.getByText('Mở')
    opener.focus()
    fireEvent.click(opener)
    expect(document.body.style.overflow).toBe('hidden')

    fireEvent.click(screen.getByRole('button', { name: 'Đóng' }))
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(document.activeElement).toBe(opener)
    expect(document.body.style.overflow).toBe('')
  })

  it('form trong hộp thoại không gửi form của trang chứa nó', () => {
    const outer = vi.fn((event: { preventDefault: () => void }) => event.preventDefault())
    const inner = vi.fn((event: { preventDefault: () => void }) => event.preventDefault())
    render(
      <form onSubmit={outer}>
        <Modal title="Thêm địa chỉ" onClose={() => {}} footer={<button type="submit" form="address">Lưu</button>}>
          <form id="address" onSubmit={inner}><input aria-label="Tên" /></form>
        </Modal>
      </form>,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(inner).toHaveBeenCalledTimes(1)
    expect(outer).not.toHaveBeenCalled()
    // Hộp thoại nằm ngoài form của trang
    expect(screen.getByRole('dialog').closest('form')).toBeNull()
  })
})
