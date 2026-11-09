// Báo cáo chuyến đi & nhật ký. Chuyển từ Manager/manager_trip_reports.html + manager_trip_reports.js.
// Bản cũ chỉ có 1 báo cáo chi tiết viết cứng; nay bấm dòng nào xem báo cáo dòng đó. Xuất PDF dùng hộp thoại in của trình duyệt.
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import { formatDate, formatDateTime, formatVND } from '@shared/lib/format'
import { ImageThumb } from '@shared/ui/ImageThumb'
import { type TripReport } from '@shared/services/reports'
import { Modal } from '@shared/ui/Modal'
import { usePagination } from '@shared/ui/usePagination'
import { SearchBox, partStyles as m } from '../../../shared/parts'
import s from './TripReports.module.css'
import { usePageData } from '@shared/routing/pageData'
import type { tripReportsLoader } from '../../../shared/loaders'

const ON_TIME_MS = 15 * 60_000 // lệch trong 15 phút vẫn tính đúng giờ, cùng ngưỡng với backend
const dash = '—'
const clock = (at: number | null) => (at == null ? dash : formatDateTime(at))
function legStatus(planned: number | null, actual: number | null, t: TFunction) {
  if (planned == null || actual == null) return <span className={s.status}>{t('tripReportsPage.noCheckin', 'Chưa có giờ thực tế')}</span>
  const d = Math.round((actual - planned) / 60_000)
  if (Math.abs(actual - planned) <= ON_TIME_MS) return <span className={`${s.status} ${s.onTime}`}><i className="fa-solid fa-check-circle" /> {t('tripReportsPage.onTime', 'Đúng giờ')}</span>
  return d < 0
    ? <span className={`${s.status} ${s.early}`}><i className="fa-solid fa-bolt" /> {t('tripReportsPage.early', 'Sớm {{min}}p', { min: -d })}</span>
    : <span className={`${s.status} ${s.late}`}><i className="fa-solid fa-clock" /> {t('tripReportsPage.late', 'Trễ {{min}}p', { min: d })}</span>
}

function ReportDetail({ r, onExport }: { r: TripReport; onExport: () => void }) {
  const { t } = useTranslation()
  return (
    <div id="report-detail" className={`card ${s.detail}`}>
      <div className="card-header"><h2>{t('tripReportsPage.detailTitle', 'Báo cáo Chi tiết: {{id}}', { id: r.id })}</h2><button className="btn btn-ghost btn-sm" onClick={onExport}><i className="fa-solid fa-download" /> {t('tripReportsPage.exportPdf', 'Xuất PDF')}</button></div>
      <div className={m.infoGrid}>
        <div className={m.infoItem}><span className={m.infoLabel}>{t('tripReportsPage.client', 'Khách hàng')}</span><span className={m.infoValue}>{r.customer}</span></div>
        <div className={m.infoItem}><span className={m.infoLabel}>{t('tripReportsPage.transportType', 'Loại hình Vận chuyển')}</span><span className={m.infoValue}>{r.type}</span></div>
        <div className={m.infoItem}><span className={m.infoLabel}>{t('tripReportsPage.horsesDetail', 'Chi tiết Ngựa')}</span><span className={m.infoValue}>{r.horses}</span></div>
        <div className={m.infoItem}><span className={m.infoLabel}>{t('tripReportsPage.escortStaff', 'Nhân viên Đi kèm')}</span><span className={m.infoValue}>{r.escort ?? dash}</span></div>
        <div className={m.infoItem}><span className={m.infoLabel}>Giao ngựa</span><span className={m.infoValue}>{clock(r.completedAt)}{r.plannedAt != null && <span className="sub-text"> (dự kiến {formatDateTime(r.plannedAt)})</span>}</span></div>
        <div className={m.infoItem}><span className={m.infoLabel}>Sự cố</span><span className={m.infoValue}>{r.incidentNote}{r.incidentCost > 0 && ` · ${formatVND(r.incidentCost)}`}</span></div>
      </div>

      <h3 className={s.h3}><i className="fa-solid fa-map-location-dot" /> {t('tripReportsPage.journeyTitle', 'Hành trình Vận chuyển (Đa chặng)')}</h3>
      <div className="sub-text" style={{ marginBottom: 12 }}>{r.route} · Thời gian chạy thực tế: <b>{r.totalTime ?? dash}</b>{r.otd && <> · {legStatus(r.plannedAt, r.completedAt, t)}</>}</div>
      {!r.legs.length && <p className="sub-text">Chuyến chưa có mốc lộ trình nào.</p>}
      <div className={s.timeline}>
        {r.legs.map(l => (
          <div key={l.title} className={s.leg}>
            <div className={s.legHead}>
              <span className={s.legTitle}><span className={`${s.legBadge} ${l.kind === 'gate' ? s.gate : ''}`}><i className={`fa-solid ${l.kind === 'gate' ? 'fa-flag' : 'fa-truck'}`} /></span>{l.title}</span>
              <span className={s.legMeta}>{l.meta}</span>
            </div>
            <div className="sub-text">{l.route}</div>
            <div className={s.times}>
              <span>Dự kiến: <b className="text-muted">{clock(l.plannedStart)} - {clock(l.plannedEnd)}</b></span>
              <span>Thực tế: <b>{clock(l.actualStart)} - {clock(l.actualEnd)}</b></span>
              {legStatus(l.plannedEnd, l.actualEnd, t)}
            </div>
          </div>
        ))}
      </div>

      <h3 className={s.h3}><i className="fa-solid fa-heart-pulse" /> {t('tripReportsPage.healthLogTitle', 'Nhật ký Sinh tồn')}</h3>
      {!r.health.length && <p className="sub-text">Hộ tống chưa gửi nhật ký an sinh nào.</p>}
      <div className={s.log}>{r.health.map(([at, e, ok]) => <div key={`${at}-${e}`} className={s.logItem}><span className="text-muted">{formatDateTime(at)}</span><span>{e}</span><span className={ok ? 'text-green font-semibold' : 'text-red font-semibold'}>{ok ? t('tripReportsPage.normal', 'Bình thường') : t('tripReportsPage.abnormal', 'Bất thường')}</span></div>)}</div>

      <h3 className={s.h3}><i className="fa-solid fa-clipboard-list" /> {t('tripReportsPage.eventsTitle', 'Sự kiện Vận chuyển')}</h3>
      {!r.events.length && <p className="sub-text">Chưa có sự kiện nào được ghi.</p>}
      <div className={s.log}>{r.events.map(([at, e, ok]) => <div key={`${at}-${e}`} className={s.logItem}><span className="text-muted">{formatDateTime(at)}</span><span>{e}</span><span className={ok ? 'text-green font-semibold' : 'text-orange font-semibold'}>{ok ? t('tripReportsPage.good', 'Tốt') : t('tripReportsPage.noted', 'Ghi nhận')}</span></div>)}</div>

      <h3 className={s.h3}><i className="fa-solid fa-road" /> Phí cầu đường (nhà xe chịu)</h3>
      {r.tolls?.length
        ? <div className={s.log}>
            {r.tolls.map(x => <div key={x.id} className={s.logItem}><span className="text-muted">{formatDateTime(x.at)}</span><span style={{ display: 'flex', alignItems: 'center', gap: 8 }}><ImageThumb name={x.photo} size={36} />{x.station || 'Trạm thu phí'} · {x.by}</span><span className="font-semibold">{formatVND(x.amount)}</span></div>)}
            <div className={s.logItem}><span /><b>Tổng phí cầu đường</b><b>{formatVND(r.tollCost ?? 0)}</b></div>
          </div>
        : <p className="sub-text">Tài xế chưa ghi hóa đơn phí cầu đường nào.</p>}

      <h3 className={s.h3}><i className="fa-solid fa-star" /> {t('tripReportsPage.feedbackTitle', 'Đánh giá & Phản hồi từ Khách hàng')}</h3>
      {r.rating == null
        ? <p className="sub-text">Khách chưa chấm điểm chuyến này.</p>
        : <>
            <div className={s.stars} aria-label={`${r.rating} trên 5 sao`}>{Array.from({ length: 5 }, (_, i) => <i key={i} className={`fa-solid fa-star ${i < (r.rating ?? 0) ? s.on : ''}`} />)}</div>
            {r.feedback && <blockquote className={s.feedback}>"{r.feedback}"</blockquote>}
          </>}
    </div>
  )
}

export default function TripReportsPage() {
  const { reports = [] } = usePageData<typeof tripReportsLoader>()
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<string>()
  const [exporting, setExporting] = useState(false)
  const [fileName, setFileName] = useState('')
  const list = reports.filter(r => !query || [r.id, r.customer, r.route].join(' ').toLowerCase().includes(query.toLowerCase()))
  const { rows, bar } = usePagination(list)
  const current = reports.find(r => r.id === selected)

  const exportPdf = () => {
    const prev = document.title
    document.title = fileName.trim() || current?.id || 'bao-cao' // tên file gợi ý khi lưu PDF
    setExporting(false)
    setTimeout(() => { window.print(); document.title = prev }, 50)
  }

  return (
    <div className="page">
      <div className={`wrap ${s.wrap}`}>
        <div className="breadcrumb">Hệ thống Vận hành / <span className="text-orange font-semibold">Báo cáo Chuyến đi</span></div>
        <div className="page-header"><h1>Chuyến đi</h1><p>Nhật ký, sức khỏe ngựa và đánh giá của khách.</p></div>
        <div className={s.searchWrap}><SearchBox value={query} onChange={setQuery} placeholder="Tìm mã đơn, khách hàng, tuyến..." /></div>
        <div className={s.grid}>
          {rows.map(r => (
            <button key={r.id} className={s.reportCard} onClick={() => setSelected(r.id)}>
              <b className={s.cardId}>{r.id}</b>
              <span className={s.cardRoute}>{r.route}</span>
              <span className="text-muted">{r.customer}</span>
              <span className={s.cardFoot}><span className="text-muted"><i className="fa-solid fa-flag-checkered" aria-hidden="true" /> {r.completedAt != null ? formatDate(r.completedAt) : dash}</span>{r.rating != null ? <span className={s.cardStars} aria-label={`${r.rating} trên 5 sao`}><i className="fa-solid fa-star" aria-hidden="true" /> {r.rating}/5</span> : <span className="text-muted">Chưa chấm</span>}</span>
            </button>
          ))}
        </div>
        {!rows.length && <div className="card" style={{ marginTop: 12 }}><p>Không có báo cáo nào khớp.</p></div>}
        {bar}
      </div>
      {current && selected && (
        <Modal wide title={`Báo cáo chuyến ${current.id}`} onClose={() => setSelected(undefined)}>
          <ReportDetail r={current} onExport={() => { setFileName(current.id); setExporting(true) }} />
        </Modal>
      )}
      {exporting && (
        <Modal title="Xuất báo cáo PDF" onClose={() => setExporting(false)}
          footer={<><button className="btn btn-ghost" onClick={() => setExporting(false)}>Hủy</button><button className="btn btn-primary" disabled={!fileName.trim()} onClick={exportPdf}><i className="fa-solid fa-file-pdf" /> Xuất PDF</button></>}>
          <div className="form-group"><label className="required">Tên file</label><input className={`form-control ${fileName.trim() ? '' : 'invalid'}`} value={fileName} onChange={e => setFileName(e.target.value)} /></div>
          <p className="form-hint">Trình duyệt mở hộp thoại in: chọn "Lưu dưới dạng PDF".</p>
        </Modal>
      )}
    </div>
  )
}
