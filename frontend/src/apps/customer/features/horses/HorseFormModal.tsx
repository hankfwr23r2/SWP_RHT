// Biểu mẫu thêm / sửa ngựa trong Hồ sơ ngựa. Dùng ở trang Hồ sơ ngựa và ở bước chọn ngựa khi đặt đơn.
import { useState, type FormEvent } from 'react'
import { HORSE_BREEDS as BREEDS, HORSE_DOC, HORSE_DOC_TYPES, type HorseDocType } from '@shared/config/booking-rules'
import { fromIsoDay, toIsoDay } from '@shared/lib/booking'
import { horsesApi, type NewHorse } from '@shared/services/horses'
import { SEX_LABEL, type HorseDoc, type HorseProfile, type Sex } from '@shared/types/booking'
import { FileField } from '@shared/ui/FileField'
import { Modal } from '@shared/ui/Modal'
import s from './Horses.module.css'
import { FormSelect } from '@shared/ui/FormSelect'

type Errors = Partial<Record<'name' | 'microchip' | 'color' | 'birthYear' | HorseDocType | `${HorseDocType}Expiry`, string>>

interface Props {
  owner: string
  horse?: HorseProfile // có = sửa
  onClose: () => void
  onSaved: (h: HorseProfile) => void
}

export function HorseFormModal({ owner, horse, onClose, onSaved }: Props) {
  const editing = !!horse
  const [name, setName] = useState(horse?.name ?? '')
  const [microchip, setMicrochip] = useState(horse?.microchip ?? '')
  const [breed, setBreed] = useState(horse?.breed ?? BREEDS[0])
  const [sex, setSex] = useState<Sex>(horse?.sex ?? 'mare')
  const [color, setColor] = useState(horse?.color ?? '')
  const [birthYear, setBirthYear] = useState(String(horse?.birthYear ?? ''))
  const [marks, setMarks] = useState(horse?.marks ?? '')
  const [docs, setDocs] = useState<Partial<Record<HorseDocType, HorseDoc>>>(horse?.docs ?? {})
  const [errors, setErrors] = useState<Errors>({})
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')

  const setDoc = (t: HorseDocType, patch: Partial<HorseDoc> | undefined) =>
    setDocs(d => { const next = { ...d }; if (!patch) delete next[t]; else next[t] = { fileName: '', uploadedAt: Date.now(), ...d[t], ...patch }; return next })

  const validate = () => {
    const e: Errors = {}
    if (!name.trim()) e.name = 'Nhập tên ngựa.'
    if (!microchip.trim()) e.microchip = 'Nhập mã microchip.'
    if (!color.trim()) e.color = 'Nhập màu lông.'
    const year = Number(birthYear)
    if (!year || year < 1990 || year > new Date().getFullYear()) e.birthYear = 'Nhập năm sinh hợp lệ.'
    HORSE_DOC_TYPES.forEach(t => {
      if (docs[t] && HORSE_DOC[t].hasExpiry && !docs[t]!.expiresAt) e[`${t}Expiry`] = 'Nhập ngày hết hạn của giấy.'
    })
    setErrors(e)
    return !Object.keys(e).length
  }

  const submit = async (ev: FormEvent) => {
    ev.preventDefault()
    if (!validate()) return
    setBusy(true)
    setFormError('')
    const data: NewHorse = { name: name.trim(), microchip, breed, sex, color: color.trim(), birthYear: Number(birthYear), marks: marks.trim(), docs }
    try {
      onSaved(editing ? await horsesApi.update(owner, horse.id, data) : await horsesApi.create(owner, data))
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Không lưu được.')
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
        <button type="submit" form="horse-form" className="btn btn-primary" disabled={busy}>{busy ? 'Đang lưu…' : editing ? 'Lưu thay đổi' : 'Lưu vào hồ sơ'}</button>
      </>}
    >
      <form id="horse-form" onSubmit={submit} noValidate className={s.form}>
        {formError && <div className="alert alert-danger"><i className="fa-solid fa-circle-exclamation" /><div>{formError}</div></div>}

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
              <input id="h-chip" className={`form-control ${errors.microchip ? 'invalid' : ''}`} value={microchip} onChange={e => setMicrochip(e.target.value)} placeholder="VD: VN-985211" disabled={editing} aria-describedby="h-chip-hint" style={{ fontVariantNumeric: 'tabular-nums' }} />
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

        <fieldset className={s.fieldset}>
          <legend>Giấy tờ dịch tễ <span className={s.legendHint}>Cần đủ 3 giấy còn hạn để ngựa ở trạng thái “Sẵn sàng đặt”.</span></legend>
          <div className={s.docs}>
            {HORSE_DOC_TYPES.map(t => (
              <div key={t} className={s.docRow}>
                <FileField label={HORSE_DOC[t].label} hint={HORSE_DOC[t].hint} fileName={docs[t]?.fileName || undefined} onChange={f => setDoc(t, f ? { fileName: f, uploadedAt: Date.now() } : undefined)} />
                {docs[t] && HORSE_DOC[t].hasExpiry && (
                  <div className="form-group">
                    <label htmlFor={`h-exp-${t}`} className="required">Ngày hết hạn</label>
                    <input id={`h-exp-${t}`} type="date" className={`form-control ${errors[`${t}Expiry`] ? 'invalid' : ''}`} value={docs[t]!.expiresAt ? toIsoDay(docs[t]!.expiresAt!) : ''} onChange={e => setDoc(t, { expiresAt: e.target.value ? fromIsoDay(e.target.value) : undefined })} />
                    {errors[`${t}Expiry`] && <div className="form-error">{errors[`${t}Expiry`]}</div>}
                  </div>
                )}
              </div>
            ))}
          </div>
        </fieldset>
      </form>
    </Modal>
  )
}
