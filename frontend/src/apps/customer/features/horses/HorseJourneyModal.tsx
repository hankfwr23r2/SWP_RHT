import { useEffect, useState } from 'react'
import { formatDateTime } from '@shared/lib/format'
import { horsesApi } from '@shared/services/horses'
import type { HorseJourneyLog, HorseProfile } from '@shared/types/booking'
import { Modal } from '@shared/ui/Modal'
import s from './Horses.module.css'

interface Props {
  horse: HorseProfile
  onClose: () => void
}

export function HorseJourneyModal({ horse, onClose }: Props) {
  const [logs, setLogs] = useState<HorseJourneyLog[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string>('')

  useEffect(() => {
    let mounted = true
    horsesApi.getJourneyLogs(horse.id)
      .then(res => {
        if (mounted) {
          setLogs(res as unknown as HorseJourneyLog[])
          setLoading(false)
        }
      })
      .catch(err => {
        if (mounted) {
          setError(err instanceof Error ? err.message : 'Không thể tải nhật ký hành trình.')
          setLoading(false)
        }
      })
    return () => { mounted = false }
  }, [horse.id])

  return (
    <Modal
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <i className="fa-solid fa-passport" style={{ color: 'var(--icon-accent)' }} />
          <span>Sổ Hành Trình Số (FEI Passport) — {horse.name}</span>
        </div>
      }
      subtitle={`Chip: ${horse.microchip} • Giống: ${horse.breed} • Đã tích lũy ${horse.completedTrips} chuyến đi`}
      wide
      onClose={onClose}
    >
      <div style={{ display: 'grid', gap: 16 }}>
        {/* Header thẻ vị trí hiện tại */}
        <div style={{ background: 'linear-gradient(135deg, #1e293b 0%, #0f172a 100%)', color: 'white', padding: '16px 20px', borderRadius: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
          <div>
            <div style={{ fontSize: '0.78rem', textTransform: 'uppercase', letterSpacing: '0.05em', color: '#94a3b8' }}>
              Vị trí chuồng hiện tại
            </div>
            <div style={{ fontSize: '1.15rem', fontWeight: 800, marginTop: 4, display: 'flex', alignItems: 'center', gap: 8 }}>
              <i className="fa-solid fa-location-dot" style={{ color: '#f97316' }} />
              <span>{horse.currentLocationName || 'Chưa xác định chuồng lưu trú'}</span>
            </div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {horse.transitStatus === 'IN_TRANSIT' ? (
              <span className="badge badge-warning" style={{ fontSize: '0.86rem', padding: '6px 12px' }}>
                <i className="fa-solid fa-truck-fast" /> Đang trên đường vận chuyển
              </span>
            ) : (
              <span className="badge badge-success" style={{ fontSize: '0.86rem', padding: '6px 12px' }}>
                <i className="fa-solid fa-warehouse" /> Đang ở chuồng (Sẵn sàng)
              </span>
            )}
          </div>
        </div>

        {loading && (
          <div style={{ textAlign: 'center', padding: '36px 0', color: 'var(--muted)' }}>
            <i className="fa-solid fa-spinner fa-spin" style={{ fontSize: '1.8rem', color: 'var(--icon-accent)', marginBottom: 8 }} />
            <div>Đang truy vấn lịch sử hành trình từ hệ thống...</div>
          </div>
        )}

        {error && (
          <div className="alert alert-error">
            <i className="fa-solid fa-circle-exclamation" /> {error}
          </div>
        )}

        {!loading && !error && logs.length === 0 && (
          <div style={{ textAlign: 'center', padding: '36px 16px', background: '#f8fafc', borderRadius: 12, border: '1px dashed #cbd5e1' }}>
            <i className="fa-solid fa-route" style={{ fontSize: '2rem', color: '#94a3b8', marginBottom: 8 }} />
            <div style={{ fontWeight: 700, color: 'var(--navy)' }}>Chưa có bản ghi hành trình</div>
            <div style={{ fontSize: '0.86rem', color: 'var(--muted)', marginTop: 4 }}>
              Khi ngựa hoàn thành chuyến xe hoặc bạn dời chuồng thủ công, lịch sử sẽ tự động được ghi nhận tại đây.
            </div>
          </div>
        )}

        {!loading && !error && logs.length > 0 && (
          <div style={{ display: 'grid', gap: 12, position: 'relative' }}>
            <div style={{ fontSize: '0.88rem', fontWeight: 700, color: 'var(--navy)', display: 'flex', alignItems: 'center', gap: 6 }}>
              <i className="fa-solid fa-timeline" style={{ color: 'var(--icon-accent)' }} />
              <span>Dòng thời gian các chặng di chuyển ({logs.length})</span>
            </div>

            <div style={{ display: 'grid', gap: 12 }}>
              {logs.map((log) => {
                const isSystem = log.source === 'SYSTEM_DELIVERY'
                return (
                  <div
                    key={log.id}
                    style={{
                      background: 'white',
                      border: '1px solid var(--line)',
                      borderRadius: 10,
                      padding: '14px 16px',
                      display: 'grid',
                      gap: 8,
                      boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        {isSystem ? (
                          <span className="badge badge-success" style={{ fontSize: '0.78rem' }}>
                            <i className="fa-solid fa-truck-arrow-right" /> Chuyến xe HorseTransport
                          </span>
                        ) : (
                          <span className="badge badge-neutral" style={{ fontSize: '0.78rem' }}>
                            <i className="fa-solid fa-user-pen" /> Chủ ngựa dời chuồng ngoài hệ thống
                          </span>
                        )}
                        {log.tripCode && (
                          <span style={{ fontWeight: 700, fontSize: '0.84rem', color: 'var(--navy)' }}>
                            Mã: {log.tripCode}
                          </span>
                        )}
                      </div>
                      <div style={{ fontSize: '0.82rem', color: 'var(--muted)', fontVariantNumeric: 'tabular-nums' }}>
                        <i className="fa-regular fa-clock" style={{ marginRight: 4 }} />
                        {log.createdAt ? formatDateTime(log.createdAt) : '—'}
                      </div>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap', fontSize: '0.92rem' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <i className="fa-solid fa-circle-dot" style={{ color: '#0ea5e9', fontSize: '0.8rem' }} />
                        <span style={{ fontWeight: 600 }}>{log.fromLocation || 'Điểm khởi hành'}</span>
                      </div>
                      <i className="fa-solid fa-arrow-right-long" style={{ color: 'var(--icon-accent)' }} />
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <i className="fa-solid fa-location-dot" style={{ color: '#ef4444', fontSize: '0.85rem' }} />
                        <span style={{ fontWeight: 700, color: 'var(--navy)' }}>{log.toLocation || 'Điểm đến'}</span>
                      </div>
                    </div>

                    {(log.driverName || log.notes) && (
                      <div style={{ fontSize: '0.84rem', color: 'var(--muted)', background: '#f8fafc', padding: '8px 12px', borderRadius: 6, display: 'flex', flexWrap: 'wrap', gap: 12 }}>
                        {log.driverName && (
                          <span>
                            <strong style={{ color: 'var(--navy)' }}>Tài xế/Kíp xe:</strong> {log.driverName}
                          </span>
                        )}
                        {log.notes && (
                          <span>
                            <strong style={{ color: 'var(--navy)' }}>Ghi chú:</strong> {log.notes}
                          </span>
                        )}
                      </div>
                    )}

                    {log.proofImageUrl && (
                      <div style={{ marginTop: 4 }}>
                        <a href={log.proofImageUrl} target="_blank" rel="noreferrer" style={{ fontSize: '0.82rem', color: 'var(--orange)', textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                          <i className="fa-solid fa-image" /> Xem biên bản / chứng từ bàn giao
                        </a>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          </div>
        )}

        <div className={s.tabActions}>
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Đóng
          </button>
        </div>
      </div>
    </Modal>
  )
}
