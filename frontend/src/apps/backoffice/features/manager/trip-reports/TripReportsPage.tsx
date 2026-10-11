// Báo cáo chuyến đi & nhật ký. Chuyển từ Manager/manager_trip_reports.html + manager_trip_reports.js.
// Bản cũ chỉ có 1 báo cáo chi tiết viết cứng; nay bấm dòng nào xem báo cáo dòng đó. Xuất PDF dùng hộp thoại in của trình duyệt.
import { useState } from 'react'
import { formatDate } from '@shared/lib/format'
import { reportsApi, type TripReport } from '@shared/services/reports'
import { useLoad } from '@shared/services/useLoad'
import { Modal } from '@shared/ui/Modal'
import { usePagination } from '@shared/ui/usePagination'
import { SearchBox, partStyles as m } from '../../../shared/parts'
import s from './TripReports.module.css'

const toMin = (hhmm: string) => { const [h, mi] = hhmm.split(':').map(Number); return h * 60 + mi }
function legStatus(planned: string, actual: string) {
  let d = toMin(actual) - toMin(planned)
  if (d < -12 * 60) d += 24 * 60 // qua nửa đêm
  if (d === 0) return <span className={`${s.status} ${s.onTime}`}><i className="fa-solid fa-check-circle" /> Đúng giờ</span>
  return d < 0
    ? <span className={`${s.status} ${s.early}`}><i className="fa-solid fa-bolt" /> Sớm {-d}p</span>
    : <span className={`${s.status} ${s.late}`}><i className="fa-solid fa-clock" /> Trễ {d}p</span>
}

function ReportDetail({ r, onExport }: { r: TripReport; onExport: () => void }) {
  return (
    <div id="report-detail" className={`card ${s.detail}`}>
      <div className="card-header"><h2>Báo cáo Chi tiết: {r.id}</h2><button className="btn btn-ghost btn-sm" onClick={onExport}><i className="fa-solid fa-download" /> Xuất PDF</button></div>
      <div className={m.infoGrid}>
        <div className={m.infoItem}><span className={m.infoLabel}>Khách hàng</span><span className={m.infoValue}>{r.customer}</span></div>
        <div className={m.infoItem}><span className={m.infoLabel}>Loại hình Vận chuyển</span><span className={m.infoValue}>{r.type}</span></div>
        <div className={m.infoItem}><span className={m.infoLabel}>Chi tiết Ngựa</span><span className={m.infoValue}>{r.horses}</span></div>
        <div className={m.infoItem}><span className={m.infoLabel}>Nhân viên Đi kèm</span><span className={m.infoValue}>{r.escort} (NV chăm sóc)</span></div>
      </div>

      <h3 className={s.h3}><i className="fa-solid fa-map-location-dot" /> Hành trình Vận chuyển (Đa chặng)</h3>
      <div className="sub-text" style={{ marginBottom: 12 }}>{r.route} · Tổng thời gian ước tính: <b>{r.totalTime}</b></div>
      <div className={s.timeline}>
        {r.legs.map(l => (
          <div key={l.title} className={s.leg}>
            <div className={s.legHead}>
              <span className={s.legTitle}><span className={`${s.legBadge} ${l.kind === 'gate' ? s.gate : ''}`}><i className={`fa-solid ${l.kind === 'gate' ? 'fa-flag' : 'fa-truck'}`} /></span>{l.title}</span>
              <span className={s.legMeta}>{l.meta}</span>
            </div>
            <div className="sub-text">{l.route}</div>
            <div className={s.times}>
              <span>Dự kiến: <b className="text-muted">{l.planned.join(' - ')}</b></span>
              <span>Thực tế: <b>{l.actual.join(' - ')}</b></span>
              {legStatus(l.planned[1], l.actual[1])}
            </div>
          </div>
        ))}
      </div>

      <h3 className={s.h3}><i className="fa-solid fa-heart-pulse" /> Nhật ký Sinh tồn</h3>
      <div className={s.log}>{r.health.map(([t, e, ok]) => <div key={t} className={s.logItem}><span className="text-muted">{t}</span><span>{e}</span><span className={ok ? 'text-green font-semibold' : 'text-red font-semibold'}>{ok ? 'Bình thường' : 'Bất thường'}</span></div>)}</div>

      <h3 className={s.h3}><i className="fa-solid fa-clipboard-list" /> Sự kiện Vận chuyển</h3>
      <div className={s.log}>{r.events.map(([t, e, ok]) => <div key={t} className={s.logItem}><span className="text-muted">{t}</span><span>{e}</span><span className={ok ? 'text-green font-semibold' : 'text-orange font-semibold'}>{ok ? 'Tốt' : 'Ghi nhận'}</span></div>)}</div>

      <h3 className={s.h3}><i className="fa-solid fa-star" /> Đánh giá &amp; Phản hồi từ Khách hàng</h3>
      <div className={s.stars} aria-label={`${r.rating} trên 5 sao`}>{Array.from({ length: 5 }, (_, i) => <i key={i} className={`fa-solid fa-star ${i < r.rating ? s.on : ''}`} />)}</div>
      <blockquote className={s.feedback}>"{r.feedback}"</blockquote>
    </div>
  )
}

export default function TripReportsPage() {
  const { data: reports = [] } = useLoad(reportsApi.list)
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
              <span className={s.cardFoot}><span className="text-muted"><i className="fa-solid fa-flag-checkered" aria-hidden="true" /> {formatDate(r.completedAt)}</span><span className={s.cardStars} aria-label={`${r.rating} trên 5 sao`}><i className="fa-solid fa-star" aria-hidden="true" /> {r.rating}/5</span></span>
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
