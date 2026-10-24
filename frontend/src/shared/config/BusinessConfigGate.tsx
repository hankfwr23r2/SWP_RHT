import { useEffect, useState, type ReactNode } from 'react'
import { systemConfigApi } from '@shared/api/systemConfig'
import { applyRuntimeBusinessConfig } from './runtimeBusinessRules'

type LoadState = { status: 'loading' } | { status: 'ready' } | { status: 'error'; message: string }

export function BusinessConfigGate({ children }: { children: ReactNode }) {
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    systemConfigApi.listPublicConfigs()
      .then(configs => {
        applyRuntimeBusinessConfig(Object.fromEntries(configs.map(config => [config.configKey, config.configValue])))
        if (active) setState({ status: 'ready' })
      })
      .catch(error => {
        if (active) {
          setState({
            status: 'error',
            message: error instanceof Error ? error.message : 'Không thể tải tham số nghiệp vụ.',
          })
        }
      })
    return () => { active = false }
  }, [attempt])

  if (state.status === 'loading') {
    return <main className="page"><div className="wrap"><p>Đang tải tham số nghiệp vụ…</p></div></main>
  }
  if (state.status === 'error') {
    return (
      <main className="page">
        <div className="wrap">
          <div className="alert alert-danger" role="alert">
            <b>Không thể tải cấu hình hệ thống.</b> {state.message}
            <button
              className="btn btn-outline"
              onClick={() => {
                setState({ status: 'loading' })
                setAttempt(value => value + 1)
              }}
            >
              Thử lại
            </button>
          </div>
        </div>
      </main>
    )
  }
  return children
}
