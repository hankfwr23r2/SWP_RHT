// Nút chuyển VI ↔ EN bằng Google Translate. Chuyển từ global_translate.js.
import { useEffect, useState } from 'react'
import styles from './TranslateToggle.module.css'

declare global {
  interface Window {
    googleTranslateElementInit?: () => void
    google?: { translate: { TranslateElement: new (opts: object, id: string) => unknown } }
  }
}

function setCookie(name: string, value: string, days: number) {
  const expires = new Date(Date.now() + days * 86_400_000).toUTCString()
  document.cookie = `${name}=${value}; expires=${expires}; path=/`
}
const getCookie = (name: string) => document.cookie.split('; ').find(c => c.startsWith(name + '='))?.split('=')[1] ?? ''

export function TranslateToggle() {
  const [english, setEnglish] = useState(() => /\/(vi|auto)\/en/.test(getCookie('googtrans')))

  useEffect(() => {
    if (document.getElementById('google-translate-script')) return
    window.googleTranslateElementInit = () => {
      new window.google!.translate.TranslateElement({ pageLanguage: 'vi', includedLanguages: 'en,vi', autoDisplay: false }, 'google_translate_element')
    }
    const script = document.createElement('script')
    script.id = 'google-translate-script'
    script.src = '//translate.google.com/translate_a/element.js?cb=googleTranslateElementInit'
    document.head.appendChild(script)
  }, [])

  const choose = (lang: 'vi' | 'en') => {
    if ((lang === 'en') === english) return
    if (lang === 'vi') {
      setCookie('googtrans', '', -1)
      location.reload() // tải lại để trả về tiếng Việt gốc
      return
    }
    setCookie('googtrans', '/vi/en', 1)
    const combo = document.querySelector<HTMLSelectElement>('select.goog-te-combo')
    if (combo) {
      combo.value = 'en'
      combo.dispatchEvent(new Event('change'))
    }
    setEnglish(true)
  }

  return (
    <>
      <div id="google_translate_element" hidden />
      <div className={`${styles.toggle} ${english ? styles.en : ''} notranslate`} role="group" aria-label="Ngôn ngữ">
        <span className={styles.thumb} />
        <button className={`${styles.option} ${!english ? styles.current : ''}`} aria-pressed={!english} onClick={() => choose('vi')}>VI</button>
        <button className={`${styles.option} ${english ? styles.current : ''}`} aria-pressed={english} onClick={() => choose('en')}>EN</button>
      </div>
    </>
  )
}
