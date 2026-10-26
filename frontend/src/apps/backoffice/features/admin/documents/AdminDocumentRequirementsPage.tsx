import { useState } from 'react'
import { usePageData } from '@shared/routing/pageData'
import type { DocumentGroup, DocumentRequirement, DocumentRequirementPayload, DocumentType } from '@shared/api/documentRequirements'
import { documentRequirementsApi } from '@shared/api/documentRequirements'
import { applyDocumentRequirements } from '@shared/config/runtimeDocumentRequirements'
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'
import type { documentRequirementsLoader } from '../../../shared/loaders'
import s from './AdminDocumentRequirements.module.css'

const HORSE_TYPES: DocumentType[] = ['passport', 'vaccine', 'lab']
const CLEARANCE_TYPES: DocumentType[] = [
  'health_cert', 'poa', 'customs_declaration', 'import_permit', 'quarantine_cert', 'ata_carnet', 'commercial_invoice',
]
const LABELS: Record<DocumentType, string> = {
  passport: 'Hộ chiếu ngựa',
  vaccine: 'Hồ sơ tiêm chủng',
  lab: 'Kết quả xét nghiệm máu',
  health_cert: 'Giấy chứng nhận kiểm dịch',
  poa: 'Giấy ủy quyền vận chuyển',
  customs_declaration: 'Tờ khai hải quan',
  import_permit: 'Giấy phép nhập khẩu',
  quarantine_cert: 'Chứng thư kiểm dịch biên giới',
  ata_carnet: 'Sổ ATA Carnet',
  commercial_invoice: 'Hóa đơn thương mại',
}

const blankRequirement = (): DocumentRequirementPayload => ({
  documentGroup: 'HORSE',
  documentType: 'passport',
  documentName: LABELS.passport,
  description: '',
  tripType: 'ALL',
  country: 'ALL',
  required: true,
  status: 'ACTIVE',
})

function RequirementEditor({
  initial,
  onClose,
  onSaved,
}: {
  initial: DocumentRequirement | null
  onClose: () => void
  onSaved: (requirement: DocumentRequirement) => void
}) {
  const toast = useToast()
  const [form, setForm] = useState<DocumentRequirementPayload>(() => initial ? {
    documentGroup: initial.documentGroup,
    documentType: initial.documentType,
    documentName: initial.documentName,
    description: initial.description ?? '',
    tripType: initial.tripType,
    country: initial.country,
    required: initial.required,
    status: initial.status,
  } : blankRequirement())
  const [busy, setBusy] = useState(false)

  const update = <K extends keyof DocumentRequirementPayload>(key: K, value: DocumentRequirementPayload[K]) =>
    setForm(current => ({ ...current, [key]: value }))

  const changeGroup = (group: DocumentGroup) => {
    const type = group === 'HORSE' ? 'passport' : 'health_cert'
    setForm(current => ({
      ...current,
      documentGroup: group,
      documentType: type,
      documentName: LABELS[type],
      tripType: 'ALL',
      country: 'ALL',
    }))
  }

  const save = async () => {
    if (!form.documentName.trim()) {
      toast('Tên giấy tờ không được để trống.', 'error')
      return
    }
    setBusy(true)
    try {
      const saved = initial
        ? await documentRequirementsApi.update(initial.id, form)
        : await documentRequirementsApi.create(form)
      onSaved(saved)
      toast(initial ? 'Đã cập nhật cấu hình giấy tờ.' : 'Đã thêm giấy tờ bắt buộc.', 'success')
    } catch (error) {
      toast(error instanceof Error ? error.message : 'Không thể lưu cấu hình giấy tờ.', 'error')
    } finally {
      setBusy(false)
    }
  }

  const types = form.documentGroup === 'HORSE' ? HORSE_TYPES : CLEARANCE_TYPES

  return (
    <Modal
      title={initial ? 'Cập nhật giấy tờ bắt buộc' : 'Thêm giấy tờ bắt buộc'}
      onClose={onClose}
      footer={(
        <>
          <button type="button" className="btn btn-outline" onClick={onClose} disabled={busy}>Hủy</button>
          <button type="button" className="btn btn-primary" onClick={save} disabled={busy}>
            {busy ? 'Đang lưu…' : 'Lưu cấu hình'}
          </button>
        </>
      )}
    >
      <div className={s.form}>
        <label>
          Nhóm giấy tờ
          <select className="form-control" value={form.documentGroup} onChange={event => changeGroup(event.target.value as DocumentGroup)}>
            <option value="HORSE">Hồ sơ ngựa</option>
            <option value="CLEARANCE">Thông quan chuyến đi</option>
          </select>
        </label>
        <label>
          Loại giấy
          <select
            className="form-control"
            value={form.documentType}
            onChange={event => {
              const type = event.target.value as DocumentType
              const isInternationalOnly = ['customs_declaration', 'import_permit', 'quarantine_cert', 'ata_carnet', 'commercial_invoice'].includes(type)
              setForm(current => ({
                ...current,
                documentType: type,
                documentName: LABELS[type],
                tripType: isInternationalOnly ? 'INTERNATIONAL' : current.tripType,
              }))
            }}
          >
            {types.map(type => <option key={type} value={type}>{LABELS[type]}</option>)}
          </select>
        </label>
        <label className={s.full}>
          Tên hiển thị
          <input className="form-control" value={form.documentName} maxLength={255} onChange={event => update('documentName', event.target.value)} />
        </label>
        <label>
          Loại chuyến
          <select
            className="form-control"
            value={form.tripType}
            onChange={event => update('tripType', event.target.value as DocumentRequirementPayload['tripType'])}
          >
            <option value="ALL">Tất cả</option>
            <option value="DOMESTIC">Trong nước</option>
            <option value="INTERNATIONAL">Quốc tế</option>
          </select>
        </label>
        <label>
          Quốc gia áp dụng
          <select
            className="form-control"
            value={form.country}
            onChange={event => update('country', event.target.value as DocumentRequirementPayload['country'])}
          >
            <option value="ALL">Tất cả</option>
            <option value="VN">Việt Nam</option>
            <option value="KH">Campuchia</option>
            <option value="LA">Lào</option>
          </select>
        </label>
        <label className={s.full}>
          Mô tả
          <textarea className="form-control" rows={3} maxLength={1000} value={form.description} onChange={event => update('description', event.target.value)} />
        </label>
        <label className={s.checkbox}>
          <input type="checkbox" checked={form.required} onChange={event => update('required', event.target.checked)} />
          Bắt buộc phải có trước khi đặt chuyến
        </label>
      </div>
    </Modal>
  )
}

export default function AdminDocumentRequirementsPage() {
  const { requirements: loaded = [] } = usePageData<typeof documentRequirementsLoader>()
  const [requirements, setRequirements] = useState(loaded)
  const [editing, setEditing] = useState<DocumentRequirement | null>(null)
  const [creating, setCreating] = useState(false)
  const [busyId, setBusyId] = useState<number | null>(null)
  const toast = useToast()

  const saved = (requirement: DocumentRequirement) => {
    const next = [
      ...requirements.filter(item => item.id !== requirement.id),
      requirement,
    ].sort((a, b) => a.documentGroup.localeCompare(b.documentGroup) || a.documentName.localeCompare(b.documentName))
    setRequirements(next)
    applyDocumentRequirements(next)
    setEditing(null)
    setCreating(false)
  }

  const toggleStatus = async (requirement: DocumentRequirement) => {
    setBusyId(requirement.id)
    try {
      const updated = await documentRequirementsApi.changeStatus(
        requirement.id, requirement.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
      )
      saved(updated)
      toast(updated.status === 'ACTIVE' ? 'Đã bật áp dụng giấy tờ.' : 'Đã ngừng áp dụng giấy tờ.', 'success')
    } catch (error) {
      toast(error instanceof Error ? error.message : 'Không thể cập nhật trạng thái.', 'error')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="page">
      <div className={`wrap ${s.wrap}`}>
        <header className={s.header}>
          <div>
            <h1>Giấy tờ bắt buộc</h1>
            <p>Cấu hình hồ sơ ngựa và giấy tờ thông quan theo loại chuyến, quốc gia. Cấu hình chỉ áp dụng cho đơn mới.</p>
          </div>
          <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
            <i className="fa-solid fa-plus" /> Thêm giấy tờ
          </button>
        </header>
        <div className={s.tableWrap}>
          <table className={s.table}>
            <thead>
              <tr>
                <th>Giấy tờ</th>
                <th>Nhóm</th>
                <th>Phạm vi</th>
                <th>Bắt buộc</th>
                <th>Trạng thái</th>
                <th>Thay đổi gần nhất</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {requirements.map(item => (
                <tr key={item.id}>
                  <td><b>{item.documentName}</b><small>{item.documentType}{item.description ? ` · ${item.description}` : ''}</small></td>
                  <td>{item.documentGroup === 'HORSE' ? 'Hồ sơ ngựa' : 'Thông quan'}</td>
                  <td>{item.tripType === 'ALL' ? 'Tất cả chuyến' : item.tripType === 'DOMESTIC' ? 'Trong nước' : 'Quốc tế'} · {item.country}</td>
                  <td>{item.required ? 'Có' : 'Không'}</td>
                  <td><span className={`badge ${item.status === 'ACTIVE' ? 'badge-success' : 'badge-muted'}`}>{item.status === 'ACTIVE' ? 'Đang áp dụng' : 'Ngừng áp dụng'}</span></td>
                  <td>{item.updatedBy ?? '—'}{item.updatedAt ? <small>{new Date(item.updatedAt).toLocaleString('vi-VN')}</small> : null}</td>
                  <td className={s.actions}>
                    <button type="button" className="btn btn-outline btn-sm" onClick={() => setEditing(item)}>Sửa</button>
                    <button type="button" className="btn btn-ghost btn-sm" disabled={busyId === item.id} onClick={() => toggleStatus(item)}>
                      {busyId === item.id ? 'Đang lưu…' : item.status === 'ACTIVE' ? 'Ngừng dùng' : 'Kích hoạt'}
                    </button>
                  </td>
                </tr>
              ))}
              {!requirements.length && <tr><td colSpan={7} className={s.empty}>Chưa có cấu hình giấy tờ.</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
      {(creating || editing) && (
        <RequirementEditor initial={editing} onClose={() => { setCreating(false); setEditing(null) }} onSaved={saved} />
      )}
    </div>
  )
}
