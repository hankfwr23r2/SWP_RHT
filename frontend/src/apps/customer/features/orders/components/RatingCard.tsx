import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ratingsApi, type Rating } from '@shared/api/ratings'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { Field } from '@shared/ui/Field'
import { Textarea } from '@shared/ui/Textarea'
import { useOrderAction } from '../useOrderAction'
import s from '../Orders.module.css'

const SCORES = [1, 2, 3, 4, 5]

export function RatingCard({ bookingId }: { bookingId: number }) {
  const { t } = useTranslation()
  const { run, busy, error } = useOrderAction()
  const [score, setScore] = useState<number>()
  const [comment, setComment] = useState('')
  const [saved, setSaved] = useState<Rating>()

  if (saved) return <Card title={t('myOrders.rating.title')}><p>{t('myOrders.rating.thanks', { score: saved.score })}</p></Card>
  const send = () => run(async () => {
    setSaved(await ratingsApi.rate(bookingId, { score: score!, comment: comment.trim() || undefined }))
  }, t('myOrders.rating.done'))

  return (
    <Card title={t('myOrders.rating.title')}>
      <div className={s.stack}>
        <fieldset className={s.scores}>
          <legend className={s.hint}>{t('myOrders.rating.ask')}</legend>
          {SCORES.map(value => (
            <label key={value} className={s.option}>
              <input type="radio" name="score" checked={score === value} onChange={() => setScore(value)} />
              <strong>{value} <i className="fa-solid fa-star" aria-hidden="true" /></strong>
            </label>
          ))}
        </fieldset>
        <Field label={t('myOrders.rating.comment')}>
          {control => <Textarea {...control} rows={3} maxLength={2000} value={comment} onChange={event => setComment(event.target.value)} />}
        </Field>
        {error && <p className={s.error} role="alert">{error}</p>}
        <div><Button variant="primary" loading={busy} disabled={!score} onClick={send}>{t('myOrders.rating.send')}</Button></div>
      </div>
    </Card>
  )
}
