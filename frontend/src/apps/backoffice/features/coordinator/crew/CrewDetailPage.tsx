// Chi tiết nhân sự đi theo chuyến (tài xế / hộ tống). Chuyển từ Fleet And Route/chi-tiet-nhan-su.html + staff-detail.js.
import { Link, useParams } from 'react-router'
import { formatDate } from '@shared/lib/format'
import { TRIP_STATUS_LABEL } from '@shared/services/trips'
import { InfoItem, partStyles as p } from '../../../shared/parts'
import c from '../Coordinator.module.css'
import { useOps } from '../../../shared/useOps'

export default function CrewDetailPage() {
  const { id } = useParams()
  const { ready, trips, crew } = useOps()
  const member = crew.find(x => x.id === id)
  if (!ready) return <div className="page"><div className="wrap"><p className="text-muted">Đang tải…</p></div></div>
  if (!member) return <div className="page"><div className="wrap"><div className="alert alert-warning"><i className="fa-solid fa-user-slash" /><div>Không tìm thấy nhân sự {id}. <Link className="text-orange" to="/coordinator/assignment">Về Phân công</Link></div></div></div></div>
  const rows = trips.flatMap(t => t.legs.filter(l => l.driverId === id || l.escortId === id).map(l => ({ t, l })))

  return (
    <div className="page">
      <div className="wrap">
        <div className="breadcrumb">Điều phối / <Link to="/coordinator/assignment">Phân công</Link> / <span className="text-orange font-semibold">{member.name}</span></div>
        <div className="page-header"><h1>{member.name} ({member.id})</h1><p>Hồ sơ người đi theo chuyến và các chặng đang phụ trách.</p></div>
        <div className="card">
          <div className={p.infoGrid}>
            <InfoItem label="Vai trò">{member.role === 'driver' ? 'Tài xế' : 'Nhân viên hộ tống'}</InfoItem>
            <InfoItem label="Điện thoại">{member.phone}</InfoItem>
            <InfoItem label="Ghi chú">{member.note ?? '—'}</InfoItem>
          </div>
        </div>
        <div className="card">
          <div className="card-header"><h3>Chặng đang phụ trách</h3></div>
          <div className="table-wrap">
            <table className="data-table">
              <thead><tr><th>Chuyến</th><th>Tuyến</th><th>Khởi hành</th><th>Chặng</th><th>Từ → Đến</th><th>Trạng thái chuyến</th></tr></thead>
              <tbody>{rows.length ? rows.map(({ t, l }) => (
                <tr key={t.id + l.no}>
                  <td className={p.idCell}>{t.id}</td>
                  <td>{t.order.routeShort}</td>
                  <td className="text-muted">{formatDate(t.order.departAt)}</td>
                  <td>Chặng {l.no}</td>
                  <td>{l.from} → {l.to}</td>
                  <td>{TRIP_STATUS_LABEL[t.status]}</td>
                </tr>
              )) : <tr><td colSpan={6} className="text-center text-muted" style={{ padding: 24 }}>Chưa phân công chặng nào.</td></tr>}</tbody>
            </table>
          </div>
          <div className={c.actions}><Link className="btn btn-ghost" to="/coordinator/assignment">Về Phân công</Link></div>
        </div>
      </div>
    </div>
  )
}
