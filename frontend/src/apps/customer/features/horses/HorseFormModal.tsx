import { useState, type FormEvent } from 'react'
import { HORSE_DOC, HORSE_DOC_TYPES, type HorseDocType } from '@shared/config/booking-rules'
import { horsesApi, type NewHorse } from '@shared/services/horses'
import { useLoad } from '@shared/services/useLoad'
import { SEX_LABEL, type HorseDoc, type HorseProfile, type Sex } from '@shared/types/booking'
import { FileField } from '@shared/ui/FileField'
import { Modal } from '@shared/ui/Modal'
import s from './Horses.module.css'
import { FormSelect } from '@shared/ui/FormSelect'

type Errors = Partial<Record<'name' | 'microchip' | 'breed' | 'color' | 'birthYear' | HorseDocType, string>>

interface Props {
  owner: string
  horse?: HorseProfile // có = sửa
  onClose: () => void
  onSaved: (h: HorseProfile) => void
}

export function HorseFormModal({ owner, horse, onClose, onSaved }: Props) {
  const editing = !!horse
  const { data: horseTypes, loaded: horseTypesLoaded, reload: reloadHorseTypes } = useLoad(() => horsesApi.listTypes(), [])
  const [name, setName] = useState(horse?.name ?? '')
  const [microchip, setMicrochip] = useState(horse?.microchip ?? '')
  const [breed, setBreed] = useState(horse?.breed)
  const [sex, setSex] = useState<Sex>(horse?.sex ?? 'mare')
  const [color, setColor] = useState(horse?.color ?? '')
  const [birthYear, setBirthYear] = useState(String(horse?.birthYear ?? ''))
  const [marks, setMarks] = useState(horse?.marks ?? '')
  const [tab, setTab] = useState<'info' | 'docs'>('info')
  const [docs, setDocs] = useState<Partial<Record<HorseDocType, HorseDoc>>>(horse?.docs ?? {})
  const [files, setFiles] = useState<Partial<Record<HorseDocType, File>>>({})
  const [errors, setErrors] = useState<Errors>({})
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')

  const uploadedDocsCount = HORSE_DOC_TYPES.filter(t => !!docs[t] && (files[t] || docs[t]?.fileName)).length
  const allDocsUploaded = uploadedDocsCount === HORSE_DOC_TYPES.length
  const hasInfoError = !!(errors.name || errors.microchip || errors.breed || errors.color || errors.birthYear)

  const savedType = horse?.horseTypeId
    ? horseTypes?.find(type => type.id === horse.horseTypeId)
    : undefined
  const selectedBreed = breed ?? savedType?.typeName ?? horseTypes?.[0]?.typeName ?? ''

  const legacyBreed = !!selectedBreed && selectedBreed !== 'Khác' && !horseTypes?.some(type => type.typeName === selectedBreed)
  const setDoc = (t: HorseDocType, patch: Partial<HorseDoc> | undefined) =>
    setDocs(d => {
      const next = { ...d }
      if (!patch) {
        delete next[t]
      } else {
        next[t] = { fileName: '', uploadedAt: Date.now(), ...d[t], ...patch }
      }
      return next
    })

  const validate = () => {
    const e: Errors = {}
    if (!name.trim()) e.name = 'Nhập tên ngựa.'
    if (!microchip.trim()) e.microchip = 'Nhập mã microchip.'
    if (!selectedBreed) e.breed = 'Chọn giống ngựa.'
    if (!color.trim()) e.color = 'Nhập màu lông.'
    const year = Number(birthYear)
    if (!year || year < 1990 || year > new Date().getFullYear()) e.birthYear = 'Nhập năm sinh hợp lệ.'
    setErrors(e)
    const errKeys = Object.keys(e)
    if (errKeys.length > 0) {
      const infoErr = !!(e.name || e.microchip || e.breed || e.color || e.birthYear)
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
    const type = horseTypes?.find(option => option.typeName === selectedBreed)
    const data: NewHorse = { name: name.trim(), microchip, breed: selectedBreed, horseTypeId: type?.id ?? (selectedBreed === 'Khác' ? undefined : horse?.horseTypeId), sex, color: color.trim(), birthYear: Number(birthYear), marks: marks.trim(), docs }
    try {
      const saved = editing ? await horsesApi.update(owner, horse.id, data) : await horsesApi.create(owner, data)
      for (const t of HORSE_DOC_TYPES) {
        const file = files[t]
        if (file) {
          await horsesApi.uploadDocument(saved.id, t, file)
        }
      }
      const refreshed = (await horsesApi.get(owner, saved.id)) ?? saved
      onSaved(refreshed)
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Không lưu được.'
      // Lỗi về mã microchip hiện ngay dưới ô nhập, không đưa lên đầu biểu mẫu
      if (!editing && /microchip/i.test(message)) {
        // Phân biệt lỗi trùng mã vs lỗi khác để thông báo rõ ràng cho khách hàng
        const chipError = /đã được sử dụng|đã tồn tại|already|duplicate/i.test(message)
          ? `Mã microchip "${microchip.trim()}" đã có trong hệ thống. Vui lòng kiểm tra lại mã trên chip gắn ở ngựa.`
          : message
        setErrors({ microchip: chipError })
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
            Tiếp tục: Giấy tờ &amp; Kiểm dịch ({uploadedDocsCount}/3) <i className="fa-solid fa-arrow-right" />
          </button>
        ) : (
          <button type="button" className="btn btn-ghost" onClick={() => setTab('info')}>
            <i className="fa-solid fa-arrow-left" /> Quay lại: Nhận dạng
          </button>
        )}
        <button type="submit" form="horse-form" className="btn btn-primary" disabled={busy || !horseTypes?.length}>{busy ? 'Đang lưu…' : editing ? 'Lưu thay đổi' : 'Lưu vào hồ sơ'}</button>
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
                  <label htmlFor="h-breed" className="required">Giống</label>
                  <FormSelect id="h-breed" className={`form-control ${errors.breed ? 'invalid' : ''}`} value={selectedBreed} onChange={e => { setBreed(e.target.value); if (errors.breed) setErrors(({ breed: _breed, ...rest }) => rest) }} disabled={!horseTypes?.length}>
                    {!selectedBreed && <option value="" disabled>{horseTypesLoaded ? 'Chọn giống ngựa' : 'Đang tải danh sách giống…'}</option>}
                    {horseTypes?.map(type => <option key={type.id} value={type.typeName}>{type.typeName}</option>)}
                    {legacyBreed && <option value={selectedBreed}>{selectedBreed}</option>}
                    <option value="Khác">Khác</option>
                  </FormSelect>
                  {errors.breed && <div className="form-error">{errors.breed}</div>}
                  {horseTypesLoaded && !horseTypes?.length && (
                    <div className="form-error">
                      Không tải được danh sách giống ngựa. <button type="button" className="btn btn-ghost btn-sm" onClick={reloadHorseTypes}>Thử lại</button>
                    </div>
                  )}
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

          </>
        )}

        {tab === 'docs' && (
          <>
            <fieldset className={s.fieldset}>
              <legend>Hồ sơ sức khỏe & kiểm dịch <span className={s.legendHint}>Cần đủ 3 giấy để ngựa ở trạng thái “Sẵn sàng đặt”.</span></legend>
              <div className={s.docs}>
                {HORSE_DOC_TYPES.map(t => (
                  <div key={t} className={s.docRow}>
                    <FileField label={HORSE_DOC[t].label} fileName={docs[t]?.fileName || undefined} fileUrl={docs[t]?.fileUrl} onChange={(f, file) => { setDoc(t, f ? { fileName: f, uploadedAt: Date.now() } : undefined); setFiles(current => { const next = { ...current }; if (file) next[t] = file; else delete next[t]; return next }) }} />
                  </div>
                ))}
              </div>
            </fieldset>

          </>
        )}
      </form>
    </Modal>
  )
}
