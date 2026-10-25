import { useEffect, useState, type ReactNode } from 'react'
import { routesApi } from '@shared/api/routes'
import { setNetworkLocations } from './network'

export function LocationCatalogGate({ children }: { children: ReactNode }) {
  const [state, setState] = useState<{ loading: boolean; error?: string }>({ loading: true })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    routesApi.listLocations()
      .then(locations => {
        if (!active) return
        if (locations.length === 0) {
          throw new Error('Danh mục địa điểm chưa có dữ liệu hoạt động.')
        }
        setNetworkLocations(locations)
        setState({ loading: false })
      })
      .catch(error => {
        if (active) {
          setState({
            loading: false,
            error: error instanceof Error ? error.message : 'Không thể tải danh mục địa điểm.',
          })
        }
      })
    return () => { active = false }
  }, [attempt])

  const retry = () => {
    setState({ loading: true })
    setAttempt(value => value + 1)
  }

  if (state.loading) {
    return <div role="status" style={{ padding: 32, textAlign: 'center' }}>Đang tải danh mục địa điểm…</div>
  }
  return (
    <>
      {state.error && (
        <div role="alert" style={{ padding: '8px 16px', textAlign: 'center' }}>
          <span>Không thể tải danh mục địa điểm: {state.error} </span>
          <button type="button" className="btn btn-ghost btn-sm" onClick={retry}>
            Thử tải lại
          </button>
        </div>
      )}
      {children}
    </>
  )
}
