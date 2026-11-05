// Nút chuyển VI ↔ EN bằng i18next tiêu chuẩn (thay thế Google Translate script).
import { useTranslation } from 'react-i18next'
import styles from './TranslateToggle.module.css'

export function TranslateToggle() {
  const { i18n } = useTranslation()
  const english = i18n.language.startsWith('en')

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
    <div className={`${styles.toggle} ${english ? styles.en : ''}`} role="group" aria-label="Ngôn ngữ / Language">
      <span className={styles.thumb} />
      <button
        type="button"
        className={`${styles.option} ${!english ? styles.current : ''}`}
        aria-pressed={!english}
        onClick={() => choose('vi')}
      >
        VI
      </button>
      <button
        type="button"
        className={`${styles.option} ${english ? styles.current : ''}`}
        aria-pressed={english}
        onClick={() => choose('en')}
      >
        EN
      </button>
    </div>
  )
}

