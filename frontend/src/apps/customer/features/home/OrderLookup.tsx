// Tra cứu đơn công khai theo mã đơn và 4 số cuối điện thoại người đặt
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { trackingApi, type PublicTracking } from '@shared/api/tracking'
import { cx } from '@shared/components/landing/landingMotion'
import { TrackResult } from './TrackResult'
import s from './HomePage.module.css'

const MAX_TRACK_CODES = 5

export function OrderLookup({ onShowPrice }: { onShowPrice: () => void }) {
  const { t } = useTranslation()
  const [codesText, setCodesText] = useState('')
  const [phone, setPhone] = useState('')
  const [error, setError] = useState<{ field: 'codes' | 'phone'; message: string } | null>(null)
  const [rows, setRows] = useState<PublicTracking[]>([])
  const [loading, setLoading] = useState(false)
  const submit = async (e: FormEvent) => {
    e.preventDefault()
    const codes = [...new Set(codesText.split(',').map(c => c.trim().toUpperCase()).filter(Boolean))]
    if (!codes.length) return setError({ field: 'codes', message: t('home.lookup.errMinCode') })
    if (codes.length > MAX_TRACK_CODES) return setError({ field: 'codes', message: t('home.lookup.errMaxCode', { max: MAX_TRACK_CODES }) })
    if (!/^\d{4}$/.test(phone.trim())) return setError({ field: 'phone', message: t('home.lookup.errPhone') })
    setError(null)
    setLoading(true)
    try {
      setRows(await trackingApi.publicTrack(codes, phone.trim()))
    } catch (err) {
      setError({ field: 'codes', message: err instanceof Error ? err.message : t('home.lookup.notFound') })
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <form className={s.lookupRow} noValidate onSubmit={submit}>
        <div className={cx(s.field, s.fieldGrow)}>
          <i className="fa-solid fa-magnifying-glass" />
          <input
            className={cx(error?.field === 'codes' && s.inputError)}
            aria-invalid={error?.field === 'codes'}
            aria-describedby={error?.field === 'codes' ? 'lookup-error' : undefined}
            value={codesText}
            onChange={e => { setCodesText(e.target.value); setError(null) }}
            aria-label={t('home.lookup.codePlaceholder')}
            placeholder={t('home.lookup.codePlaceholder')}
          />
        </div>
        <div className={cx(s.field, s.fieldPhone)}>
          <i className="fa-solid fa-phone" />
          <input
            className={cx(error?.field === 'phone' && s.inputError)}
            aria-invalid={error?.field === 'phone'}
            aria-describedby={error?.field === 'phone' ? 'lookup-error' : undefined}
            value={phone}
            onChange={e => { setPhone(e.target.value); setError(null) }}
            inputMode="numeric"
            maxLength={4}
            aria-label={t('home.lookup.phonePlaceholder')}
            placeholder={t('home.lookup.phonePlaceholder')}
          />
        </div>
        <button className="btn btn-solid" type="submit" disabled={loading}>
          {loading ? t('home.lookup.searching') : t('home.lookup.searchBtn')}
        </button>
      </form>
      <p className={s.lookupHint}>
        {t('home.lookup.hint')}{' '}
        <button type="button" className={s.hintLink} onClick={onShowPrice}>{t('home.lookup.seePricing')}</button>
      </p>
      {error && <p id="lookup-error" className={s.formError} role="alert"><i className="fa-solid fa-circle-exclamation" aria-hidden="true" /> {error.message}</p>}
      <div aria-live="polite">
      {!error && rows.length > 0 && (
        <div className={s.trackList}>
          {rows.map((row, i) => <TrackResult key={row.code} result={row} delay={i * 80} />)}
        </div>
      )}
      </div>
    </>
  )
}
