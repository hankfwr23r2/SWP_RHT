// Gửi lựa chọn VI/EN của khách lên tài khoản (lúc đăng nhập và mỗi lần đổi) để mail hóa đơn gửi đúng ngôn ngữ
import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { customerProfileApi } from '../api/customerProfile'
import { useAuth } from './AuthContext'

export function CustomerLanguageSync() {
  const { i18n } = useTranslation()
  const { session } = useAuth()
  const sent = useRef<string | null>(null)
  const language: 'vi' | 'en' = i18n.language.startsWith('en') ? 'en' : 'vi'
  const who = session?.role === 'customer' ? session.username : null
  useEffect(() => {
    if (!who) { sent.current = null; return }
    const key = `${who}:${language}`
    if (sent.current === key) return
    sent.current = key
    // Lưu không được thì thôi: mail vẫn gửi bằng ngôn ngữ đã lưu trước đó (mặc định tiếng Việt)
    customerProfileApi.setLanguage(language).catch(() => { sent.current = null })
  }, [who, language])
  return null
}
