// Trang không tải được dữ liệu (backend chưa trả lời, mất mạng…): báo lỗi và cho thử lại, thay cho màn lỗi mặc định của router.
import { isRouteErrorResponse, useRevalidator, useRouteError } from 'react-router'
import i18n from '@shared/i18n'
import s from './RouteError.module.css'

export function RouteError() {
  const error = useRouteError()
  const { revalidate, state } = useRevalidator()
  const notFound = isRouteErrorResponse(error) && error.status === 404
  if (notFound) {
    return (
      <div role="alert" className={s.box}>
        <h1 className={s.title}>{i18n.t('common.pageNotFound')}</h1>
        <a href={window.location.pathname.startsWith('/backoffice') ? '/backoffice/' : '/'} className="btn btn-primary">{i18n.t('common.backHome')}</a>
      </div>
    )
  }
  const message = error instanceof Error ? error.message : 'Không tải được dữ liệu của trang.'
  return (
    <div role="alert" className={s.box}>
      <h1 className={s.title}>Chưa tải được trang</h1>
      <p className={`text-muted ${s.message}`}>{message}</p>
      <button type="button" className="btn btn-primary" disabled={state === 'loading'} onClick={() => revalidate()}>
        {state === 'loading' ? 'Đang tải lại…' : 'Thử tải lại'}
      </button>
    </div>
  )
}
