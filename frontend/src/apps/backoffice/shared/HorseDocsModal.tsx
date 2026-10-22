import { useEffect, useState } from 'react'
import { HORSE_DOC, HORSE_DOC_TYPES } from '@shared/config/booking-rules'
import { horsesApi } from '@shared/services/horses'
import type { BookingHorse, HorseProfile } from '@shared/types/booking'
import { ImageThumb } from '@shared/ui/ImageThumb'
import { Modal } from '@shared/ui/Modal'

interface Props {
  horses: BookingHorse[]
  onClose: () => void
}

export function HorseDocsModal({ horses, onClose }: Props) {
  const [profiles, setProfiles] = useState<Record<string, HorseProfile>>({})
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let mounted = true
    Promise.all(
      horses.map(async h => {
        try {
          const profile = await horsesApi.byId(h.horseId)
          return [h.horseId, profile] as const
        } catch {
          return [h.horseId, undefined] as const
        }
      })
    ).then(entries => {
      if (!mounted) return
      const map: Record<string, HorseProfile> = {}
      for (const [id, p] of entries) {
        if (p) map[id] = p
      }
      setProfiles(map)
      setLoading(false)
    })
    return () => {
      mounted = false
    }
  }, [horses])

  return (
    <Modal
      wide
      title="Hồ sơ giấy tờ điện tử đối soát"
      subtitle={`Đối chiếu hộ chiếu, microchip và giấy kiểm dịch của ${horses.length} ngựa trên chuyến xe`}
      onClose={onClose}
      footer={<button type="button" className="btn btn-primary" onClick={onClose}>Đã kiểm tra xong</button>}
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: '32px 0', color: 'var(--muted)' }}>
          <i className="fa-solid fa-spinner fa-spin" style={{ marginRight: 8 }} /> Đang tải hồ sơ giấy tờ ngựa…
        </div>
      ) : (
        <div style={{ display: 'grid', gap: 16 }}>
          {horses.map(h => {
            const p = profiles[h.horseId]
            return (
              <div
                key={h.horseId}
                style={{
                  border: '1px solid var(--line, #e2e8f0)',
                  borderRadius: 'var(--radius, 8px)',
                  padding: 14,
                  background: 'var(--bg-card, #ffffff)',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                  <div>
                    <span style={{ fontWeight: 700, fontSize: '1rem', color: 'var(--navy, #0f172a)' }}>{h.name}</span>
                    <span style={{ marginLeft: 8, fontSize: '0.85rem', color: 'var(--muted, #64748b)' }}>
                      Chip: <b>{h.microchip}</b>
                    </span>
                  </div>
                  <span className="badge badge-success" style={{ fontSize: '0.78rem' }}>
                    <i className="fa-solid fa-circle-check" /> Đã kiểm duyệt
                  </span>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
                  {HORSE_DOC_TYPES.map(t => {
                    const doc = p?.docs?.[t]
                    const fileName = doc?.fileUrl || doc?.fileName
                    return (
                      <div
                        key={t}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 10,
                          padding: 10,
                          borderRadius: 'var(--radius-sm, 6px)',
                          background: 'var(--bg-page, #f8fafc)',
                          border: '1px solid var(--line-light, #f1f5f9)',
                        }}
                      >
                        {fileName ? (
                          <ImageThumb
                            name={fileName}
                            size={52}
                            title={`${HORSE_DOC[t].label} · ${h.name} (${h.microchip})`}
                          />
                        ) : (
                          <div
                            style={{
                              width: 52,
                              height: 52,
                              borderRadius: 'var(--radius-sm, 6px)',
                              background: '#f1f5f9',
                              display: 'grid',
                              placeItems: 'center',
                              color: '#94a3b8',
                            }}
                          >
                            <i className="fa-regular fa-file" />
                          </div>
                        )}
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ fontSize: '0.86rem', fontWeight: 600, color: 'var(--navy, #0f172a)' }}>
                            {HORSE_DOC[t].short}
                          </div>
                          <div style={{ fontSize: '0.76rem', color: 'var(--muted, #64748b)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {doc?.fileName || (doc ? 'Đã nộp điện tử' : 'Chưa có bản điện tử')}
                          </div>
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </Modal>
  )
}
