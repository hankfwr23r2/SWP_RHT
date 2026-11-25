import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from './Button'
import { Modal } from './Modal'

interface ConfirmDialogProps {
  title: ReactNode
  // Nói rõ hậu quả của thao tác (AGENTS.md mục 8.6)
  message: ReactNode
  // Động từ cụ thể, ví dụ "Hủy đơn", không dùng "OK"
  confirmLabel: string
  cancelLabel?: string
  tone?: 'primary' | 'danger'
  confirmDisabled?: boolean
  // Trả về Promise thì nút bị khóa tới khi xong. Thành công thì nơi gọi tự đóng hộp thoại; lỗi thì hộp thoại mở lại để thử tiếp
  onConfirm: () => unknown
  onCancel: () => void
}

// Xác nhận thao tác không hoàn tác được (gửi, hủy, duyệt, thanh toán, xóa). Tiêu điểm đặt sẵn ở nút Hủy.
export function ConfirmDialog({ title, message, confirmLabel, cancelLabel, tone = 'primary', confirmDisabled, onConfirm, onCancel }: ConfirmDialogProps) {
  const { t } = useTranslation()
  // React vẽ lại ngay sau cú bấm, nên nút đã khóa trước cú bấm thứ hai
  const [busy, setBusy] = useState(false)

  const confirm = async () => {
    setBusy(true)
    try {
      await onConfirm()
    } catch {
      // Nơi gọi báo lỗi bằng Toast; ở đây chỉ mở khóa để người dùng thử lại
    } finally {
      setBusy(false)
    }
  }

  const cancel = () => { if (!busy) onCancel() }

  return (
    <Modal
      alert
      title={title}
      onClose={cancel}
      footer={(
        <>
          <Button data-autofocus onClick={cancel} disabled={busy}>{cancelLabel ?? t('ui.cancel')}</Button>
          <Button variant={tone} onClick={confirm} disabled={confirmDisabled} loading={busy}>{confirmLabel}</Button>
        </>
      )}
    >
      {message}
    </Modal>
  )
}
