import { useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { authApi } from '@shared/api/auth'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { useToast } from '@shared/ui/toast'
import { EMPTY_PASSWORD, PASSWORD_MAX, PASSWORD_MIN, passwordErrors, passwordServerError, type PasswordErrors } from './model'
import s from './Profile.module.css'

const FIELDS = [
  { key: 'currentPassword', autoComplete: 'current-password' },
  { key: 'newPassword', autoComplete: 'new-password' },
  { key: 'confirmPassword', autoComplete: 'new-password' },
] as const

// Đổi mật khẩu đăng nhập
export function PasswordForm() {
  const { t } = useTranslation()
  const toast = useToast()
  const [form, setForm] = useState(EMPTY_PASSWORD)
  const [errors, setErrors] = useState<PasswordErrors>({})
  const [busy, setBusy] = useState(false)
  const saving = useRef(false)
  const labelled = (found: PasswordErrors): PasswordErrors =>
    Object.fromEntries(Object.entries(found).map(([key, code]) => [key, t(`profile.errors.${code}`, { min: PASSWORD_MIN })]))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (saving.current) return
    const found = passwordErrors(form)
    setErrors(labelled(found))
    if (Object.keys(found).length) return
    saving.current = true
    setBusy(true)
    try {
      await authApi.changePassword(form)
      setForm(EMPTY_PASSWORD)
      toast(t('profile.passwordSaved'))
    } catch (error) {
      setErrors(labelled(passwordServerError(error instanceof Error ? error.message : '')))
      toast(t('profile.passwordError'), 'error')
    } finally {
      saving.current = false
      setBusy(false)
    }
  }

  return (
    <Card title={t('profile.tabSecurity')}>
      <form className={s.form} onSubmit={submit} noValidate>
        <p className={s.hint}>{t('profile.passwordHint', { min: PASSWORD_MIN })}</p>
        <div className={s.grid}>
          {FIELDS.map(({ key, autoComplete }) => (
            <Field key={key} label={t(`profile.${key}`)} error={errors[key]} required className={key === 'currentPassword' ? s.wide : undefined}>
              {control => <Input {...control} type="password" autoComplete={autoComplete} maxLength={PASSWORD_MAX} value={form[key]} disabled={busy}
                onChange={event => {
                  const { value } = event.target
                  setForm(current => ({ ...current, [key]: value }))
                  setErrors(current => ({ ...current, [key]: undefined }))
                }} />}
            </Field>
          ))}
        </div>
        <div className={s.submit}><Button type="submit" variant="primary" loading={busy}>{t('profile.changePasswordBtn')}</Button></div>
      </form>
    </Card>
  )
}
