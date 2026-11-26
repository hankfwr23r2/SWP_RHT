// Nút chuyển VI ↔ EN bằng i18next tiêu chuẩn (thay thế Google Translate script).
import { useTranslation } from 'react-i18next'
import { Button } from './Button'
import styles from './TranslateToggle.module.css'

export function TranslateToggle() {
  const { i18n, t } = useTranslation()
  const english = i18n.language?.startsWith('en') ?? false

  const choose = (lang: 'vi' | 'en') => {
    if ((lang === 'en') === english) return
    void i18n.changeLanguage(lang)
    try {
      localStorage.setItem('i18nextLng', lang)
    } catch {
      // ignore storage quota error
    }
  }

  return (
    <div className={styles.toggle} role="group" aria-label={t('header.language')}>
      <Button
        size="sm" variant="ghost"
        className={`${styles.option} ${!english ? styles.current : ''}`}
        aria-pressed={!english}
        onClick={() => choose('vi')}
      >
        VI
      </Button>
      <Button
        size="sm" variant="ghost"
        className={`${styles.option} ${english ? styles.current : ''}`}
        aria-pressed={english}
        onClick={() => choose('en')}
      >
        EN
      </Button>
    </div>
  )
}

