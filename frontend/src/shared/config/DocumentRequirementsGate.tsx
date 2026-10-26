import { useEffect, useState, type ReactNode } from 'react'
import { documentRequirementsApi } from '@shared/api/documentRequirements'
import { applyDocumentRequirements } from './runtimeDocumentRequirements'

export function DocumentRequirementsGate({ children }: { children: ReactNode }) {
  const [state, setState] = useState<{ loading: boolean; error?: string }>({ loading: true })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    documentRequirementsApi.listPublic()
      .then(requirements => {
        if (!active) return
        applyDocumentRequirements(requirements)
        setState({ loading: false })
      })
      .catch(error => {
        if (active) {
          setState({
            loading: false,
            error: error instanceof Error ? error.message : 'Không thể tải cấu hình giấy tờ.',
          })
        }
      })
    return () => { active = false }
  }, [attempt])

  if (state.loading) {
    return <div role="status" style={{ padding: 32, textAlign: 'center' }}>Đang tải quy định giấy tờ…</div>
  }
  return (
    <>
      {state.error && (
        <div role="alert" style={{ padding: '8px 16px', textAlign: 'center' }}>
          <span>Không thể tải quy định giấy tờ: {state.error} </span>
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => {
            setState({ loading: true })
            setAttempt(value => value + 1)
          }}>
            Thử tải lại
          </button>
        </div>
      )}
      {children}
    </>
  )
}
