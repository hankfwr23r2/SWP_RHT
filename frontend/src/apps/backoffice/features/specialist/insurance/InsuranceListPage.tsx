import { useState } from 'react'
import { Link } from 'react-router'
import { usePageData } from '@shared/routing/pageData'
import { useAutoRefresh } from '@shared/services/useLoad'
import { formatDate } from '@shared/lib/format'
import type { Booking } from '@shared/types/booking'
import { insuranceStage, type InsuranceStage } from '@shared/types/insurance'
import { ListPage, idCell, type Column } from '../../../shared/ListPage'
import type { insuranceListLoader } from '../../../shared/loaders'
const labels: Record<InsuranceStage, string> = { quote: 'Cần báo giá / kiểm tra', waiting: 'Chờ duyệt / đặt cọc', policy: 'Cần hợp đồng', done: 'Đã xử lý / đã đóng' }
export default function InsuranceListPage() {
  const { all, reload } = usePageData<typeof insuranceListLoader>()
  useAutoRefresh(reload)
  const [tab, setTab] = useState<InsuranceStage>('quote')
  const rows = all ?? []
  const groups = (stage: InsuranceStage) => rows.filter(b => insuranceStage(b.status, b.horses) === stage)
  const columns: Column<Booking>[] = [
    { head: 'Mã đơn', cell: b => idCell(b) }, { head: 'Khách hàng', cell: b => b.customer },
    { head: 'Khởi hành', cell: b => formatDate(b.departAt) },
    { head: 'Bảo hiểm', cell: b => `${b.horses.filter(h => h.insurance.option === 'assistance').length} hỗ trợ · ${b.horses.filter(h => h.insurance.option === 'personal').length} cá nhân · ${b.horses.filter(h => h.insurance.option === 'none').length} không bảo hiểm${b.horses.some(h => !h.insurance.option) ? ' · cần xác nhận đơn cũ' : ''}` },
    { head: 'Tiến độ', cell: b => labels[insuranceStage(b.status, b.horses)] },
    { head: 'Thao tác', cell: b => <Link className="btn btn-primary btn-sm" to={`/specialist/insurance/${b.id}`}>Mở bảo hiểm</Link>, right: true },
  ]
  return <ListPage title="Bảo hiểm" subtitle="Báo giá, kiểm tra bảo hiểm cá nhân và thu xếp hợp đồng cho các đơn được phân công." tabs={(Object.keys(labels) as InsuranceStage[]).map(k => [k, labels[k], groups(k).length])} tab={tab} onTab={setTab} hot={['quote', 'policy']} rows={groups(tab)} rowKey={b => b.id} columns={columns} haystack={b => [b.id, b.customer, ...b.horses.map(h => h.name)]} dateOf={b => b.departAt} loaded={!!all} emptyText="Không có đơn bảo hiểm ở mục này." />
}
