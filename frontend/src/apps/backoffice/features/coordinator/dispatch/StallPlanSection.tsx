// Sơ đồ khoang của lượt xe (màn 3.9a): hệ thống đã tự xếp khi gán ngựa; điều phối đổi chỗ, xếp lại tự động rồi lưu.
// Vị trí 1 là đầu xe, số lớn nhất là cửa sau. Khoang rộng chiếm vị trí đã chọn và vị trí kế sau (vách di động)
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type StallPlan } from '@shared/api/dispatch'
import { useLoad } from '@shared/hooks/useLoad'
import { Button } from '@shared/ui/Button'
import { ErrorState } from '@shared/ui/ErrorState'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { Table, type TableColumn } from '@shared/ui/Table'
import { useToast } from '@shared/ui/toast'
import s from './Dispatch.module.css'

type Row = NonNullable<StallPlan['horses']>[number]

// refreshKey đổi khi gán hoặc gỡ ngựa thì tải lại sơ đồ
export function StallPlanSection({ tripId, refreshKey, readOnly }: { tripId: number; refreshKey: string; readOnly?: boolean }) {
  const { t } = useTranslation()
  const toast = useToast()
  const [draft, setDraft] = useState<Record<number, number>>({})
  const [warnings, setWarnings] = useState<string[]>()
  const [busy, setBusy] = useState(false)
  const { data: plan, loaded, error, reload } = useLoad(async () => {
    setDraft({})
    setWarnings(undefined)
    return dispatchApi.stallPlan(tripId)
  }, [tripId, refreshKey], { toastOnError: false })

  if (!loaded) return <Skeleton variant="block" lines={2} />
  if (error || !plan) return <ErrorState message={error?.message} onRetry={reload} />
  const rows = plan.horses ?? []
  if (!rows.length) return null
  const positionOf = (row: Row) => draft[row.horseLegId ?? 0] ?? row.stallPosition
  const changed = rows.some(row => draft[row.horseLegId ?? 0] !== undefined && draft[row.horseLegId ?? 0] !== row.stallPosition)
  const options = Array.from({ length: plan.capacity ?? 0 }, (_, i) => ({ value: String(i + 1), label: String(i + 1) }))

  const suggest = async () => {
    setBusy(true)
    try {
      const next = await dispatchApi.suggestStallPlan(tripId)
      setDraft(Object.fromEntries((next.horses ?? []).map(row => [row.horseLegId ?? 0, row.stallPosition ?? 0])))
      setWarnings(next.warnings ?? [])
    } catch (e) {
      toast(e instanceof Error ? e.message : String(e), 'error')
    } finally {
      setBusy(false)
    }
  }
  const save = async () => {
    setBusy(true)
    try {
      await dispatchApi.applyStallPlan(tripId, rows.map(row => ({
        horseLegId: row.horseLegId ?? 0, currentPosition: row.stallPosition, stallPosition: positionOf(row) ?? 0,
      })))
      toast(t('dispatch.stall.saved'), 'success')
      void reload()
    } catch (e) {
      // 409: người khác vừa đổi sơ đồ; tải lại bản mới
      toast(e instanceof Error ? e.message : String(e), 'error')
      void reload()
    } finally {
      setBusy(false)
    }
  }

  const columns: TableColumn<Row>[] = [
    {
      key: 'position',
      header: t('dispatch.stall.colPosition'),
      render: row => (!readOnly && plan.editable && !row.locked
        ? <Select aria-label={t('dispatch.stall.positionOf', { name: row.horseName })} value={String(positionOf(row) ?? '')} options={options}
            placeholder="–" onChange={value => setDraft(current => ({ ...current, [row.horseLegId ?? 0]: Number(value) }))} />
        : positionOf(row) ?? '–'),
    },
    { key: 'horse', header: t('dispatch.stall.colHorse'), render: row => `${row.horseName} · ${row.bookingCode}` },
    { key: 'seats', header: t('dispatch.stall.colSeats'), render: row => t(row.seats === 2 ? 'dispatch.stall.wide' : 'dispatch.stall.standard') },
    { key: 'route', header: t('dispatch.stall.colRoute'), render: row => `${row.boardStopName} → ${row.alightStopName}` },
  ]
  const shown = warnings ?? plan.warnings ?? []
  return (
    <section className={s.section} aria-labelledby="trip-stalls">
      <div className={s.sectionHead}>
        <h3 id="trip-stalls" className={s.sectionTitle}>{t('dispatch.stall.title')}</h3>
        <span className={s.muted}>{t(plan.editable ? 'dispatch.stall.hint' : 'dispatch.stall.locked', { total: plan.capacity })}</span>
      </div>
      <Table caption={t('dispatch.stall.title')} columns={columns} rows={rows} rowKey={row => row.horseLegId ?? 0} />
      {shown.length > 0 && (
        <ul className={s.list} aria-label={t('dispatch.stall.warnings')}>
          {shown.map(text => <li key={text} className={s.muted}>{text}</li>)}
        </ul>
      )}
      {!readOnly && plan.editable && (
        <div className={s.toolbar}>
          <Button loading={busy} onClick={() => void suggest()}>{t('dispatch.stall.suggest')}</Button>
          <Button variant="primary" disabled={!changed} loading={busy} onClick={() => void save()}>{t('dispatch.stall.save')}</Button>
        </div>
      )}
    </section>
  )
}
