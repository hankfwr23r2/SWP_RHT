import { useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { HORSE_DOC, HORSE_DOC_TYPES, type HorseDocType } from '@shared/config/booking-rules'
import { horsesApi, type NewHorse } from '@shared/services/horses'
import { useLoad } from '@shared/services/useLoad'
import { documentUploadError } from '@shared/lib/documentUpload'
import { SEX_LABEL, sexLabel, type HorseDoc, type HorseProfile, type Sex } from '@shared/types/booking'
import { FileField } from '@shared/ui/FileField'
import { Modal } from '@shared/ui/Modal'
import s from './Horses.module.css'
import { FormSelect } from '@shared/ui/FormSelect'
import { isIsoMicrochip, normalizeMicrochip } from '@shared/lib/microchip'

type Errors = Partial<Record<'name' | 'microchip' | 'breed' | 'color' | 'birthYear' | HorseDocType, string>>

interface Props {
  owner: string
  horse?: HorseProfile // có = sửa
  onClose: () => void
  onSaved: (h: HorseProfile) => void
}

export function HorseFormModal({ owner, horse, onClose, onSaved }: Props) {
  const { i18n } = useTranslation()
  const isEn = i18n.language.startsWith('en')
  const [persistedHorse, setPersistedHorse] = useState(horse)
  const editing = !!persistedHorse
  const saving = useRef(false)
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
  const [fileErrors, setFileErrors] = useState<Partial<Record<HorseDocType, string>>>({})
  // Danh sách documentId của các giấy tờ đã có trên server nhưng bị user xóa: sẽ gọi DELETE khi submit
  const [deletedDocIds, setDeletedDocIds] = useState<number[]>([])
  const [errors, setErrors] = useState<Errors>({})
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')

  const selectedDocsCount = HORSE_DOC_TYPES.filter(t => !!docs[t]?.fileName).length
  const savedDocsCount = HORSE_DOC_TYPES.filter(t => !!docs[t]?.documentId && !files[t]).length
  const allDocsUploaded = savedDocsCount === HORSE_DOC_TYPES.length
  const hasFileError = Object.values(fileErrors).some(Boolean)
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
        // Nếu giấy tờ này đã có trên server (có documentId), đánh dấu để gọi DELETE khi submit
        const existingId = d[t]?.documentId
        if (existingId) setDeletedDocIds(prev => prev.includes(existingId) ? prev : [...prev, existingId])
        delete next[t]
      } else {
        next[t] = { fileName: '', uploadedAt: Date.now(), ...d[t], ...patch }
      }
      return next
    })

  const validate = () => {
    const e: Errors = { ...fileErrors }
    for (const t of HORSE_DOC_TYPES) {
      const file = files[t]
      if (file) {
        const message = documentUploadError(file)
        if (message) e[t] = message
      }
    }
    if (!name.trim()) e.name = 'Nhập tên ngựa.'
    if (!microchip.trim()) e.microchip = 'Nhập mã microchip.'
    else if (!editing && !isIsoMicrochip(normalizeMicrochip(microchip))) e.microchip = 'Mã microchip phải gồm đúng 15 chữ số (chuẩn ISO 11784/11785).'
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
    if (saving.current || !validate()) return
    saving.current = true
    setBusy(true)
    setFormError('')
    const type = horseTypes?.find(option => option.typeName === selectedBreed)
    const data: NewHorse = { name: name.trim(), microchip, breed: selectedBreed, horseTypeId: type?.id ?? (selectedBreed === 'Khác' ? undefined : horse?.horseTypeId), sex, color: color.trim(), birthYear: Number(birthYear), marks: marks.trim(), docs }
    let infoSaved = false
    try {
      const saved = persistedHorse ? await horsesApi.update(owner, persistedHorse.id, data) : await horsesApi.create(owner, data)
      infoSaved = true
      setPersistedHorse(saved)
      const serverDocs = { ...saved.docs }
      // Xóa các giấy tờ đã bị user xóa khỏi form (gọi DELETE backend)
      for (const docId of deletedDocIds) {
        await horsesApi.deleteDocument(docId)
        setDeletedDocIds(ids => ids.filter(id => id !== docId))
        for (const t of HORSE_DOC_TYPES) {
          if (serverDocs[t]?.documentId === docId) delete serverDocs[t]
        }
      }
      // Wait for every upload before enabling retry, and remember individual successes.
      const pending = HORSE_DOC_TYPES.flatMap(type => files[type] ? [{ type, file: files[type]! }] : [])
      const results = await Promise.allSettled(pending.map(({ type, file }) => horsesApi.uploadDocument(saved.id, type, file)))
      const uploadErrors: Errors = {}
      results.forEach((result, index) => {
        const { type, file } = pending[index]
        if (result.status === 'rejected') {
          uploadErrors[type] = result.reason instanceof Error ? result.reason.message : 'Không tải được giấy tờ. Vui lòng thử lại.'
          return
        }
        const uploaded = result.value
        const savedDoc: HorseDoc = {
          documentId: uploaded.id, fileName: file.name,
          fileUrl: uploaded.documentUrl ?? uploaded.fileUrl,
          uploadedAt: Date.now(), status: uploaded.status,
        }
        serverDocs[type] = savedDoc
        setDoc(type, savedDoc)
        setFiles(current => { const next = { ...current }; if (next[type] === file) delete next[type]; return next })
      })
      setPersistedHorse({ ...saved, docs: serverDocs })
      if (Object.keys(uploadErrors).length) {
        setErrors(uploadErrors)
        setFormError('Thông tin ngựa đã được lưu. Một số giấy tờ chưa tải lên được; hãy thử lại để gửi những giấy tờ còn thiếu, không cần tạo lại ngựa.')
        setTab('docs')
        return
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
      } else setFormError(infoSaved ? `Thông tin ngựa đã được lưu. ${message} Bạn có thể thử lại, không cần tạo lại ngựa.` : message)
    } finally {
      saving.current = false
      setBusy(false)
    }
  }

  const close = () => {
    if (saving.current) return
    // Refresh the list even when the user closes a partially completed save.
    if (persistedHorse && persistedHorse !== horse) onSaved(persistedHorse)
    else onClose()
  }

  return (
    <Modal
      wide onClose={close}
      title={editing ? `Sửa hồ sơ ${persistedHorse!.name}` : 'Thêm ngựa vào Hồ sơ ngựa'}
      subtitle="Khai báo một lần, các lần đặt sau chỉ cần chọn ngựa."
      footer={<>
        <button type="button" className="btn btn-ghost" onClick={close} disabled={busy}>Đóng</button>
        {tab === 'info' ? (
          <button type="button" className="btn btn-outline" onClick={() => setTab('docs')}>
            Tiếp tục: Giấy tờ &amp; Kiểm dịch ({selectedDocsCount}/3 đã chọn) <i className="fa-solid fa-arrow-right" />
          </button>
        ) : (
          <button type="button" className="btn btn-ghost" onClick={() => setTab('info')}>
            <i className="fa-solid fa-arrow-left" /> Quay lại: Nhận dạng
          </button>
        )}
        <button type="submit" form="horse-form" className="btn btn-primary" disabled={busy || hasFileError || !horseTypes?.length}>{busy ? 'Đang lưu…' : editing ? 'Lưu thay đổi' : 'Lưu vào hồ sơ'}</button>
      </>}
    >
      <div className={s.modalContent}>
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
            {selectedDocsCount}/{HORSE_DOC_TYPES.length} đã chọn · {savedDocsCount}/{HORSE_DOC_TYPES.length} đã lưu
          </span>
        </button>
      </div>

      <form id="horse-form" onSubmit={submit} noValidate className={s.form}>
        {formError && <div className="alert alert-danger" role="alert"><i className="fa-solid fa-circle-exclamation" /><div>{formError}</div></div>}

        {tab === 'info' && (
          <>
            <fieldset className={s.fieldset} disabled={busy}>
              <legend>Nhận dạng</legend>
              <div className={s.grid2}>
                <div className="form-group">
                  <label htmlFor="h-name" className="required">Tên ngựa</label>
                  <input id="h-name" className={`form-control ${errors.name ? 'invalid' : ''}`} value={name} onChange={e => setName(e.target.value)} placeholder="VD: Storm Runner" />
                  {errors.name && <div className="form-error">{errors.name}</div>}
                </div>
                <div className="form-group">
                  <label htmlFor="h-chip" className="required">Mã microchip</label>
                  <input id="h-chip" className={`form-control ${errors.microchip ? 'invalid' : ''}`} value={microchip} onChange={e => { setMicrochip(e.target.value); if (errors.microchip) setErrors(({ microchip: _m, ...rest }) => rest) }} placeholder="VD: 985112000123456" inputMode="numeric" disabled={editing} aria-describedby="h-chip-hint" style={{ fontVariantNumeric: 'tabular-nums' }} />
                  {errors.microchip && <div className="form-error">{errors.microchip}</div>}
                  <div id="h-chip-hint" className="form-hint">{editing ? 'Mã microchip là khóa định danh, không sửa được sau khi lưu.' : '15 chữ số in trên giấy chứng nhận chip (chuẩn ISO). Không sửa được sau khi lưu.'}</div>
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
                  <FormSelect id="h-sex" className="form-control" value={sex} onChange={e => setSex(e.target.value as Sex)}>{(Object.keys(SEX_LABEL) as Sex[]).map(k => <option key={k} value={k}>{sexLabel(k, isEn)}</option>)}</FormSelect>
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
            <fieldset className={s.fieldset} disabled={busy}>
              <legend>Hồ sơ sức khỏe & kiểm dịch <span className={s.legendHint}>Cần đủ 3 giấy để ngựa ở trạng thái “Sẵn sàng đặt”.</span></legend>
              <div className={s.docs}>
                {HORSE_DOC_TYPES.map(t => (
                  <div key={t} className={s.docRow}>
                    <FileField label={HORSE_DOC[t].label} fileName={docs[t]?.fileName || undefined} fileUrl={files[t] ? undefined : docs[t]?.fileUrl}
                      error={fileErrors[t] || errors[t]}
                      onValidationError={message => setFileErrors(current => { const next = { ...current }; if (message) next[t] = message; else delete next[t]; return next })}
                      onChange={(f, file) => {
                        setErrors(current => { const next = { ...current }; delete next[t]; return next })
                        setDoc(t, f ? { fileName: f, uploadedAt: Date.now() } : undefined)
                        setFiles(current => { const next = { ...current }; if (file) next[t] = file; else delete next[t]; return next })
                      }} />
                  </div>
                ))}
              </div>
            </fieldset>

          </>
        )}
      </form>
      </div>
    </Modal>
  )
}
