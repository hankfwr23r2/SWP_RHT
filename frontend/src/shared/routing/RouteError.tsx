// Trang không tải được dữ liệu (backend chưa trả lời, mất mạng…): báo lỗi và cho thử lại, thay cho màn lỗi mặc định của router.
import { useRevalidator, useRouteError } from 'react-router'

export function RouteError() {
  const error = useRouteError()
  const { revalidate, state } = useRevalidator()
  const message = error instanceof Error ? error.message : 'Không tải được dữ liệu của trang.'
  return (
    <div role="alert" style={{ maxWidth: 520, margin: '64px auto', padding: '0 16px', textAlign: 'center' }}>
      <h1 style={{ fontSize: '1.3rem', marginBottom: 8 }}>Chưa tải được trang</h1>
      <p className="text-muted" style={{ marginBottom: 16 }}>{message}</p>
      <button type="button" className="btn btn-primary" disabled={state === 'loading'} onClick={() => revalidate()}>
        {state === 'loading' ? 'Đang tải lại…' : 'Thử tải lại'}
      </button>
    </div>
  )
}
