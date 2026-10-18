import { useState, type FormEvent } from 'react'
import { HORSE_BREEDS as BREEDS, HORSE_DOC, HORSE_DOC_TYPES, type HorseDocType } from '@shared/config/booking-rules'
import { fromIsoDay, toIsoDay } from '@shared/lib/booking'
import { formatDate } from '@shared/lib/format'
import { horsesApi, type NewHorse } from '@shared/services/horses'
import { SEX_LABEL, type HorseDoc, type HorseProfile, type Sex } from '@shared/types/booking'
import { FileField } from '@shared/ui/FileField'
import { Modal } from '@shared/ui/Modal'
import s from './Horses.module.css'
import { FormSelect } from '@shared/ui/FormSelect'

const getInitialEventDate = (t: HorseDocType, expiresAt?: number): string => {
  if (!expiresAt) return ''
  const d = new Date(expiresAt)
  if (t === 'vaccine') {
    d.setFullYear(d.getFullYear() - 1)
  } else if (t === 'lab') {
    d.setMonth(d.getMonth() - 6)
  }
  return toIsoDay(d.getTime())
}

const computeExpiryFromEventDate = (t: HorseDocType, dateIso: string): number | undefined => {
  if (!dateIso) return undefined
  const d = new Date(dateIso)
  if (t === 'vaccine') {
    d.setFullYear(d.getFullYear() + 1)
  } else if (t === 'lab') {
    d.setMonth(d.getMonth() + 6)
  }
  return fromIsoDay(d.toISOString().slice(0, 10))
}

type Errors = Partial<Record<'name' | 'microchip' | 'color' | 'birthYear' | HorseDocType | `${HorseDocType}Expiry`, string>>

interface Props {
  owner: string
  horse?: HorseProfile // có = sửa
  onClose: () => void
  onSaved: (h: HorseProfile) => void
}

export function HorseFormModal({ owner, horse, onClose, onSaved }: Props) {
  const [now] = useState(() => Date.now())
  const todayIso = toIsoDay(now)
  const editing = !!horse
  const [name, setName] = useState(horse?.name ?? '')
  const [microchip, setMicrochip] = useState(horse?.microchip ?? '')
  const [breed, setBreed] = useState(horse?.breed ?? BREEDS[0])
  const [sex, setSex] = useState<Sex>(horse?.sex ?? 'mare')
  const [color, setColor] = useState(horse?.color ?? '')
  const [birthYear, setBirthYear] = useState(String(horse?.birthYear ?? ''))
  const [marks, setMarks] = useState(horse?.marks ?? '')
  const [tab, setTab] = useState<'info' | 'docs'>('info')
  const [docs, setDocs] = useState<Partial<Record<HorseDocType, HorseDoc>>>(horse?.docs ?? {})
  const [files, setFiles] = useState<Partial<Record<HorseDocType, File>>>({})
  const [eventDates, setEventDates] = useState<Partial<Record<HorseDocType, string>>>(() => ({
    vaccine: getInitialEventDate('vaccine', horse?.docs.vaccine?.expiresAt),
    lab: getInitialEventDate('lab', horse?.docs.lab?.expiresAt),
  }))
  const [errors, setErrors] = useState<Errors>({})
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')

  const uploadedDocsCount = HORSE_DOC_TYPES.filter(t => !!docs[t] && (files[t] || docs[t]?.fileName)).length
  const allDocsUploaded = uploadedDocsCount === HORSE_DOC_TYPES.length
  const hasInfoError = !!(errors.name || errors.microchip || errors.color || errors.birthYear)
  const hasDocsError = HORSE_DOC_TYPES.some(t => !!errors[`${t}Expiry`])

  const setDoc = (t: HorseDocType, patch: Partial<HorseDoc> | undefined) =>
    setDocs(d => {
      const next = { ...d }
      if (!patch) {
        delete next[t]
        setEventDates(prev => ({ ...prev, [t]: '' }))
      } else {
        next[t] = { fileName: '', uploadedAt: Date.now(), ...d[t], ...patch }
      }
      return next
    })

  const handleDateChange = (t: HorseDocType, value: string) => {
    setEventDates(prev => ({ ...prev, [t]: value }))
    const expiresAt = computeExpiryFromEventDate(t, value)
    setDoc(t, { expiresAt })
    if (value) {
      setErrors(prev => ({ ...prev, [`${t}Expiry`]: undefined }))
    }
  }

  const validate = () => {
    const e: Errors = {}
    if (!name.trim()) e.name = 'Nhập tên ngựa.'
    if (!microchip.trim()) e.microchip = 'Nhập mã microchip.'
    if (!color.trim()) e.color = 'Nhập màu lông.'
    const year = Number(birthYear)
    if (!year || year < 1990 || year > new Date().getFullYear()) e.birthYear = 'Nhập năm sinh hợp lệ.'
    HORSE_DOC_TYPES.forEach(t => {
      if (docs[t] && HORSE_DOC[t].hasExpiry && !docs[t]!.expiresAt) {
        e[`${t}Expiry`] = t === 'vaccine' ? 'Nhập ngày tiêm phòng gần nhất.' : t === 'lab' ? 'Nhập ngày xét nghiệm gần nhất.' : 'Nhập ngày hết hạn của giấy.'
      }
    })
    setErrors(e)
    const errKeys = Object.keys(e)
    if (errKeys.length > 0) {
      const infoErr = !!(e.name || e.microchip || e.color || e.birthYear)
      if (infoErr) setTab('info')
      else setTab('docs')
      return false
    }
    return true
  }

  const submit = async (ev: FormEvent) => {
    ev.preventDefault()
    if (!validate()) return
    setBusy(true)
    setFormError('')
    const data: NewHorse = { name: name.trim(), microchip, breed, sex, color: color.trim(), birthYear: Number(birthYear), marks: marks.trim(), docs }
    try {
      const saved = editing ? await horsesApi.update(owner, horse.id, data) : await horsesApi.create(owner, data)
      for (const t of HORSE_DOC_TYPES) {
        const file = files[t]
        const expiry = docs[t]?.expiresAt && HORSE_DOC[t].hasExpiry ? toIsoDay(docs[t]!.expiresAt!) : undefined
        if (file) {
          await horsesApi.uploadDocument(saved.id, t, file, expiry)
          continue
        }

        const previousDoc = horse?.docs[t]
        if (editing && previousDoc?.expiresAt !== docs[t]?.expiresAt && docs[t]?.expiresAt !== undefined) {
          if (!previousDoc?.documentId) throw new Error(`Không tìm thấy mã giấy tờ ${HORSE_DOC[t].label} để cập nhật ngày hết hạn.`)
          await horsesApi.updateDocumentExpiry(previousDoc.documentId, expiry!)
        }
      }
      const refreshed = (await horsesApi.get(owner, saved.id)) ?? saved
      onSaved(refreshed)
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Không lưu được.'
      // Lỗi về mã microchip hiện ngay dưới ô nhập, không đưa lên đầu biểu mẫu
      if (!editing && /microchip/i.test(message)) {
        setErrors({ microchip: 'Mã microchip không hợp lệ, vui lòng nhập lại.' })
        setTab('info')
        document.getElementById('h-chip')?.focus()
      } else setFormError(message)
      setBusy(false)
    }
  }

  return (
    <Modal
      wide onClose={onClose}
      title={editing ? `Sửa hồ sơ ${horse.name}` : 'Thêm ngựa vào Hồ sơ ngựa'}
      subtitle="Khai báo một lần, các lần đặt sau chỉ cần chọn ngựa."
      footer={<>
        <button type="button" className="btn btn-ghost" onClick={onClose}>Hủy</button>
        {tab === 'info' ? (
          <button type="button" className="btn btn-outline" onClick={() => setTab('docs')}>
            Tiếp: Giấy tờ ({uploadedDocsCount}/3) <i className="fa-solid fa-arrow-right" />
          </button>
        ) : (
          <button type="button" className="btn btn-ghost" onClick={() => setTab('info')}>
            <i className="fa-solid fa-arrow-left" /> Nhận dạng
          </button>
        )}
        <button type="submit" form="horse-form" className="btn btn-primary" disabled={busy}>{busy ? 'Đang lưu…' : editing ? 'Lưu thay đổi' : 'Lưu vào hồ sơ'}</button>
      </>}
    >
      <div className={s.tabBar} role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'info'}
          className={`${s.tabBtn} ${tab === 'info' ? s.tabActive : ''}`}
          onClick={() => setTab('info')}
        >
          <i className="fa-solid fa-horse" /> Nhận dạng & Đặc điểm
          {hasInfoError && <span className={s.tabDotError} title="Có trường chưa điền" />}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'docs'}
          className={`${s.tabBtn} ${tab === 'docs' ? s.tabActive : ''}`}
          onClick={() => setTab('docs')}
        >
          <i className="fa-solid fa-file-medical" /> Giấy tờ & Kiểm dịch
          <span className={`${s.tabBadge} ${allDocsUploaded ? s.tabBadgeSuccess : ''}`}>
            {uploadedDocsCount}/{HORSE_DOC_TYPES.length} giấy
          </span>
          {hasDocsError && <span className={s.tabDotError} title="Có giấy hết hạn/thiếu" />}
        </button>
      </div>

      <form id="horse-form" onSubmit={submit} noValidate className={s.form}>
        {formError && <div className="alert alert-danger"><i className="fa-solid fa-circle-exclamation" /><div>{formError}</div></div>}

        {tab === 'info' && (
          <>
            <fieldset className={s.fieldset}>
              <legend>Nhận dạng</legend>
              <div className={s.grid2}>
                <div className="form-group">
                  <label htmlFor="h-name" className="required">Tên ngựa</label>
                  <input id="h-name" className={`form-control ${errors.name ? 'invalid' : ''}`} value={name} onChange={e => setName(e.target.value)} placeholder="VD: Storm Runner" />
                  {errors.name && <div className="form-error">{errors.name}</div>}
                </div>
                <div className="form-group">
                  <label htmlFor="h-chip" className="required">Mã microchip</label>
                  <input id="h-chip" className={`form-control ${errors.microchip ? 'invalid' : ''}`} value={microchip} onChange={e => { setMicrochip(e.target.value); if (errors.microchip) setErrors(({ microchip: _m, ...rest }) => rest) }} placeholder="VD: VN-985211" disabled={editing} aria-describedby="h-chip-hint" style={{ fontVariantNumeric: 'tabular-nums' }} />
                  {errors.microchip && <div className="form-error">{errors.microchip}</div>}
                  <div id="h-chip-hint" className="form-hint">{editing ? 'Mã microchip là khóa định danh, không sửa được sau khi lưu.' : 'Không sửa được sau khi lưu. Kiểm tra kỹ trước khi lưu.'}</div>
                </div>
                <div className="form-group">
                  <label htmlFor="h-breed">Giống</label>
                  <FormSelect id="h-breed" className="form-control" value={breed} onChange={e => setBreed(e.target.value)}>{BREEDS.map(b => <option key={b}>{b}</option>)}</FormSelect>
                </div>
                <div className="form-group">
                  <label htmlFor="h-sex">Giới tính</label>
                  <FormSelect id="h-sex" className="form-control" value={sex} onChange={e => setSex(e.target.value as Sex)}>{(Object.keys(SEX_LABEL) as Sex[]).map(k => <option key={k} value={k}>{SEX_LABEL[k]}</option>)}</FormSelect>
                </div>
                <div className="form-group">
                  <label htmlFor="h-color" className="required">Màu lông</label>
                  <input id="h-color" className={`form-control ${errors.color ? 'invalid' : ''}`} value={color} onChange={e => setColor(e.target.value)} placeholder="VD: Nâu đỏ (Bay)" />
                  {errors.color && <div className="form-error">{errors.color}</div>}
                </div>
                <div className="form-group">
                  <label htmlFor="h-year" className="required">Năm sinh</label>
                  <input id="h-year" className={`form-control ${errors.birthYear ? 'invalid' : ''}`} inputMode="numeric" value={birthYear} onChange={e => setBirthYear(e.target.value.replace(/\D/g, '').slice(0, 4))} placeholder="VD: 2020" />
                  {errors.birthYear && <div className="form-error">{errors.birthYear}</div>}
                </div>
              </div>
              <div className="form-group">
                <label htmlFor="h-marks">Đặc điểm nhận dạng</label>
                <input id="h-marks" className="form-control" value={marks} onChange={e => setMarks(e.target.value)} placeholder="VD: Sao trắng trán, tất trắng chân sau" />
              </div>
            </fieldset>
            <div className={s.tabActions}>
              <button type="button" className="btn btn-outline btn-sm" onClick={() => setTab('docs')}>
                Tiếp tục: Giấy tờ & Kiểm dịch ({uploadedDocsCount}/3) <i className="fa-solid fa-arrow-right" />
              </button>
            </div>
          </>
        )}

        {tab === 'docs' && (
          <>
            <fieldset className={s.fieldset}>
              <legend>Giấy tờ dịch tễ <span className={s.legendHint}>Cần đủ 3 giấy còn hạn để ngựa ở trạng thái “Sẵn sàng đặt”.</span></legend>
              <div className={s.docs}>
                {HORSE_DOC_TYPES.map(t => (
                  <div key={t} className={s.docRow}>
                    <FileField label={HORSE_DOC[t].label} hint={HORSE_DOC[t].hint} fileName={docs[t]?.fileName || undefined} fileUrl={docs[t]?.fileUrl} onChange={(f, file) => { setDoc(t, f ? { fileName: f, uploadedAt: Date.now() } : undefined); setFiles(current => { const next = { ...current }; if (file) next[t] = file; else delete next[t]; return next }) }} />
                    {docs[t] && HORSE_DOC[t].hasExpiry && (
                      <div className="form-group">
                        <label htmlFor={`h-exp-${t}`} className="required">
                          {t === 'vaccine' ? 'Ngày tiêm phòng gần nhất' : t === 'lab' ? 'Ngày xét nghiệm gần nhất' : 'Ngày cấp'}
                        </label>
                        <input
                          id={`h-exp-${t}`}
                          type="date"
                          max={todayIso}
                          className={`form-control ${errors[`${t}Expiry`] ? 'invalid' : ''}`}
                          value={eventDates[t] ?? ''}
                          onChange={e => handleDateChange(t, e.target.value)}
                        />
                        {docs[t]!.expiresAt && (
                          <div style={{ marginTop: 6, fontSize: '0.82rem', lineHeight: 1.3 }}>
                            {docs[t]!.expiresAt! < now ? (
                              <span style={{ color: 'var(--color-danger, #dc2626)', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                                <i className="fa-solid fa-triangle-exclamation" aria-hidden="true" />
                                Hết hiệu lực ngày {formatDate(docs[t]!.expiresAt!)} (cần tiêm nhắc lại)
                              </span>
                            ) : (
                              <span style={{ color: '#16a34a', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                                <i className="fa-solid fa-circle-check" aria-hidden="true" />
                                Hiệu lực đến: <b>{formatDate(docs[t]!.expiresAt!)}</b>
                                <span className={s.subNote}> ({t === 'vaccine' ? '12 tháng' : '6 tháng'})</span>
                              </span>
                            )}
                          </div>
                        )}
                        {errors[`${t}Expiry`] && <div className="form-error">{errors[`${t}Expiry`]}</div>}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </fieldset>
            <div className={s.tabActions}>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setTab('info')}>
                <i className="fa-solid fa-arrow-left" /> Quay lại: Nhận dạng
              </button>
            </div>
          </>
        )}
      </form>
    </Modal>
  )
}
