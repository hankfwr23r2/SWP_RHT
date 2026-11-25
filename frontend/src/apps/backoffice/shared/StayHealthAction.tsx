import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { StayHealthDialog } from './StayHealthDialog'

interface Props {
  bookingHorseId: number
  horseStayId: number
  horseName?: string
  reported?: boolean
  onSaved: () => void
}

export function StayHealthAction({ onSaved, ...target }: Props) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  return <>
    <Button size={target.reported ? 'sm' : 'lg'} onClick={() => setOpen(true)}>{t('stayHealth.record')}</Button>
    {open && <StayHealthDialog {...target} onClose={() => setOpen(false)} onSaved={() => {
      setOpen(false)
      onSaved()
    }} />}
  </>
}
