// Quản trị viên: Quản lý Danh mục Dịch vụ & Gói cước (Chăm sóc trên xe, Dinh dưỡng, Cữ nước, Bảo hiểm...)
import { useState } from 'react'
import {
  adminServicesApi,
  type BackendServiceItem,
  type BackendCategoryItem,
  type CreateServicePayload,
} from '@shared/api/adminServices'
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'
import { formatVND } from '@shared/lib/format'
import { SERVICE_BILLING_UNIT_LABEL, type ServiceBillingUnit } from '@shared/api/serviceCatalog'
import { ListPage, type Column, type TabDef } from '../../../shared/ListPage'
import s from './AdminServices.module.css'
import { usePageData } from '@shared/routing/pageData'
import type { adminServicesLoader } from '../../../shared/loaders'

type Tab = 'all' | 'ACTIVE' | 'INACTIVE'
const INSURANCE_CATEGORY_ID = 4

interface ServiceModalProps {
  service: BackendServiceItem | null // null = tạo mới
  categories: BackendCategoryItem[]
  onClose: () => void
  onDone: () => void
}

function ServiceModal({ service, categories, onClose, onDone }: ServiceModalProps) {
  const toast = useToast()
  const [serviceCode, setServiceCode] = useState(service?.serviceCode ?? '')
  const [serviceName, setServiceName] = useState(service?.serviceName ?? '')
  const [categoryId, setCategoryId] = useState<number>(service?.categoryId ?? (categories[0]?.id || 1))
  const [price, setPrice] = useState(service?.price !== undefined ? String(service.price) : '0')
  const [billingUnit, setBillingUnit] = useState<ServiceBillingUnit>(service?.billingUnit ?? 'PER_HORSE')
  const [description, setDescription] = useState(service?.description ?? '')
  const [status, setStatus] = useState(service?.status ?? 'ACTIVE')
  const [busy, setBusy] = useState(false)

  const save = async () => {
    if (!serviceName.trim()) {
      toast('Tên dịch vụ không được để trống', 'error')
      return
    }
    if (!/^[A-Z][A-Z0-9_]{1,99}$/.test(serviceCode.trim())) {
      toast('Mã dịch vụ viết hoa, bắt đầu bằng chữ và chỉ gồm chữ, số, dấu gạch dưới.', 'error')
      return
    }
    const numPrice = Number(price)
    if (isNaN(numPrice) || numPrice < 0) {
      toast('Đơn giá phải là số hợp lệ không âm', 'error')
      return
    }
    setBusy(true)
    try {
      const payload: CreateServicePayload = {
        serviceCode: serviceCode.trim(),
        serviceName: serviceName.trim(),
        categoryId,
        price: numPrice,
        billingUnit,
        description: description.trim(),
        status,
      }
      if (service) {
        await adminServicesApi.updateService(service.id, payload)
        toast('Cập nhật dịch vụ thành công', 'success')
      } else {
        await adminServicesApi.createService(payload)
        toast('Thêm dịch vụ mới thành công', 'success')
      }
      onDone()
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } }; message?: string }
      toast(err.response?.data?.message || err.message || 'Lỗi lưu dịch vụ', 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      title={service ? `Sửa dịch vụ: ${service.serviceName}` : 'Khai báo dịch vụ mới'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn btn-outline" onClick={onClose} disabled={busy}>
            Hủy
          </button>
          <button type="button" className="btn btn-primary" onClick={save} disabled={busy}>
            {busy ? 'Đang lưu…' : service ? 'Lưu thay đổi' : 'Tạo dịch vụ'}
          </button>
        </>
      }
    >
      <div className={s.form}>
        <div className="form-group">
          <label htmlFor="svc-code" className="required">Mã ổn định</label>
          <input
            id="svc-code"
            className="form-control"
            placeholder="VD: FEED_SPORT"
            value={serviceCode}
            onChange={e => setServiceCode(e.target.value.toUpperCase())}
            disabled={!!service}
            maxLength={100}
            required
          />
          <small className="form-hint">Dùng mã này trong cấu hình và đơn mới; không thể đổi sau khi tạo.</small>
        </div>

        <div className={`form-group ${s.full}`}>
          <label htmlFor="svc-name" className="required">
            Tên dịch vụ / gói cước
          </label>
          <input
            id="svc-name"
            className="form-control"
            placeholder="VD: Gói thức ăn thể thao"
            value={serviceName}
            onChange={e => setServiceName(e.target.value)}
            required
          />
        </div>

        <div className="form-group">
          <label htmlFor="svc-cat" className="required">
            Phân loại danh mục
          </label>
          <select
            id="svc-cat"
            className="form-control"
            value={categoryId}
            onChange={e => setCategoryId(Number(e.target.value))}
          >
            {categories.map(c => (
              <option key={c.id} value={c.id}>
                {c.categoryName}
              </option>
            ))}
          </select>
        </div>

        <div className="form-group">
          <label htmlFor="svc-unit" className="required">Đơn vị tính</label>
          <select
            id="svc-unit"
            className="form-control"
            value={billingUnit}
            onChange={e => setBillingUnit(e.target.value as ServiceBillingUnit)}
          >
            {Object.entries(SERVICE_BILLING_UNIT_LABEL).map(([unit, label]) => (
              <option key={unit} value={unit}>{label}</option>
            ))}
          </select>
        </div>

        <div className="form-group">
          <label htmlFor="svc-price" className="required">
            {categoryId === INSURANCE_CATEGORY_ID ? 'Giá tham chiếu (không dùng tính phí bảo hiểm)' : `Đơn giá (VNĐ / ${SERVICE_BILLING_UNIT_LABEL[billingUnit]})`}
          </label>
          <input
            id="svc-price"
            type="number"
            min={0}
            step={1000}
            className="form-control"
            placeholder="VD: 50000"
            value={price}
            onChange={e => setPrice(e.target.value)}
            required
          />
          {categoryId === INSURANCE_CATEGORY_ID && <small className="form-hint">Phí bảo hiểm đang được tính bằng 2% giá trị bảo hiểm theo giống ngựa.</small>}
        </div>

        <div className={`form-group ${s.full}`}>
          <label htmlFor="svc-description">Mô tả dịch vụ</label>
          <textarea
            id="svc-description"
            className="form-control"
            placeholder="Thành phần, số bữa mỗi ngày (nếu là gói thức ăn), lưu ý áp dụng…"
            value={description}
            onChange={e => setDescription(e.target.value)}
            rows={3}
            maxLength={1000}
          />
        </div>

        <div className={`form-group ${s.full}`}>
          <label htmlFor="svc-status">Trạng thái áp dụng</label>
          <select
            id="svc-status"
            className="form-control"
            value={status}
            onChange={e => setStatus(e.target.value)}
          >
            <option value="ACTIVE">Áp dụng (ACTIVE)</option>
            <option value="INACTIVE">Tạm ngưng (INACTIVE)</option>
          </select>
        </div>
      </div>
    </Modal>
  )
}

export default function AdminServicesPage() {
  const toast = useToast()
  const [tab, setTab] = useState<Tab>('all')
  const [categoryFilter, setCategoryFilter] = useState<string>('all')
  const [modal, setModal] = useState<{ service: BackendServiceItem | null } | null>(null)

  const { services = [], categories = [], reload } = usePageData<typeof adminServicesLoader>()

  const rows = services
    .filter(s => (tab === 'all' ? true : s.status === tab))
    .filter(s => (categoryFilter === 'all' ? true : String(s.categoryId) === categoryFilter))

  const tabs: TabDef<Tab>[] = [
    ['all', 'Tất cả dịch vụ', services.length],
    ['ACTIVE', 'Đang áp dụng', services.filter(s => s.status === 'ACTIVE').length],
    ['INACTIVE', 'Tạm ngưng', services.filter(s => s.status === 'INACTIVE').length],
  ]

  const columns: Column<BackendServiceItem>[] = [
    {
      head: 'Mã & Tên dịch vụ',
      cell: item => (
        <div className={s.name}>
          <b>
            <span>{item.serviceCode}</span>
            {item.serviceName}
          </b>
          {item.description && <small>{item.description}</small>}
        </div>
      ),
    },
    {
      head: 'Phân loại',
      cell: item => (
        <span className={s.categoryTag}>
          <i className="fa-solid fa-tag" />
          {item.categoryName || `Nhóm #${item.categoryId}`}
        </span>
      ),
    },
    {
      head: 'Đơn giá',
      cell: item => (
        <span className={s.price}>
          {item.categoryId === INSURANCE_CATEGORY_ID
            ? 'Phí tính theo giá trị bảo hiểm của giống'
            : item.price > 0
              ? `${formatVND(item.price)} / ${SERVICE_BILLING_UNIT_LABEL[item.billingUnit]}`
              : 'Miễn phí / Đã gồm'}
        </span>
      ),
      nowrap: true,
    },
    {
      head: 'Trạng thái',
      cell: item => (
        <span className={`badge ${item.status === 'ACTIVE' ? 'badge-success' : 'badge-muted'}`}>
          {item.status === 'ACTIVE' ? 'Đang áp dụng' : 'Tạm ngưng'}
        </span>
      ),
      nowrap: true,
    },
    {
      head: 'Thao tác',
      cell: item => (
        <div className={s.actions}>
          {item.categoryId === INSURANCE_CATEGORY_ID ? (
            <span className="badge badge-muted">Mua / Từ chối (cố định)</span>
          ) : (
            <>
              <button
                type="button"
                className="btn btn-outline btn-sm"
                onClick={() => setModal({ service: item })}
                title="Chỉnh sửa dịch vụ"
              >
                <i className="fa-solid fa-pen-to-square" /> Sửa
              </button>
              {item.status === 'ACTIVE' ? (
                <button
                  type="button"
                  className={`btn btn-ghost btn-sm ${s.pause}`}
                  onClick={async () => {
                    if (window.confirm(`Bạn có chắc muốn tạm ngưng áp dụng dịch vụ "${item.serviceName}"?`)) {
                      try {
                        await adminServicesApi.toggleStatus(item.id)
                        toast(`Đã tạm ngưng dịch vụ ${item.serviceName}`, 'info')
                        await reload()
                      } catch (e: unknown) {
                        const err = e as { message?: string }
                        toast(err.message || 'Lỗi chuyển trạng thái', 'error')
                      }
                    }
                  }}
                  title="Tạm ngưng cung cấp"
                >
                  <i className="fa-solid fa-pause" /> Tạm dừng
                </button>
              ) : (
                <button
                  type="button"
                  className={`btn btn-ghost btn-sm ${s.resume}`}
                  onClick={async () => {
                    try {
                      await adminServicesApi.toggleStatus(item.id)
                      toast(`Đã kích hoạt lại dịch vụ ${item.serviceName}`, 'success')
                      await reload()
                    } catch (e: unknown) {
                      const err = e as { message?: string }
                      toast(err.message || 'Lỗi kích hoạt lại', 'error')
                    }
                  }}
                  title="Kích hoạt lại"
                >
                  <i className="fa-solid fa-play" /> Mở lại
                </button>
              )}
            </>
          )}
        </div>
      ),
      right: true,
    },
  ]

  return (
    <>
      <ListPage<BackendServiceItem, Tab>
        title="Quản lý Dịch vụ & Biểu phí"
        tabs={tabs}
        tab={tab}
        onTab={setTab}
        searchPlaceholder="Tìm theo tên dịch vụ hoặc phân loại…"
        haystack={(item: BackendServiceItem) => [item.serviceCode, item.serviceName, item.categoryName ?? '', String(item.id)]}
        rows={rows}
        columns={columns}
        rowKey={(item: BackendServiceItem) => String(item.id)}
        loaded
        emptyText="Chưa có dịch vụ nào phù hợp."
        countLabel="dịch vụ"
        filters={
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: '0.85rem', color: 'var(--muted)', whiteSpace: 'nowrap' }}>
              Nhóm phân loại:
            </span>
            <select
              className="form-control"
              style={{ minWidth: 180, height: 36, fontSize: '0.85rem' }}
              value={categoryFilter}
              onChange={e => setCategoryFilter(e.target.value)}
            >
              <option value="all">Tất cả phân loại</option>
              {categories.map(c => (
                <option key={c.id} value={String(c.id)}>
                  {c.categoryName}
                </option>
              ))}
            </select>
          </div>
        }
        actions={
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setModal({ service: null })}
          >
            <i className="fa-solid fa-plus" /> Thêm dịch vụ
          </button>
        }
      />

      {modal && (
        <ServiceModal
          service={modal.service}
          categories={categories.filter(category => category.id !== INSURANCE_CATEGORY_ID)}
          onClose={() => setModal(null)}
          onDone={async () => {
            setModal(null)
            await reload()
          }}
        />
      )}
    </>
  )
}
