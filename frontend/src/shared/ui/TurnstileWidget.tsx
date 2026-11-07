import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

interface TurnstileApi {
  render: (container: HTMLElement, options: {
    sitekey: string
    action: string
    callback: (token: string) => void
    'expired-callback': () => void
    'error-callback': () => void
  }) => string
  remove: (widgetId: string) => void
}

declare global {
  interface Window {
    turnstile?: TurnstileApi
  }
}

let turnstileScript: Promise<TurnstileApi> | undefined

function loadTurnstile(): Promise<TurnstileApi> {
  if (window.turnstile) return Promise.resolve(window.turnstile)
  if (!turnstileScript) {
    const loading = new Promise<TurnstileApi>((resolve, reject) => {
      const script = document.createElement('script')
      script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
      script.async = true
      script.defer = true
      script.onload = () => {
        const api = window.turnstile
        if (api) resolve(api)
        else reject(new Error('Turnstile did not initialize'))
      }
      script.onerror = () => reject(new Error('Turnstile script failed to load'))
      document.head.appendChild(script)
    })
    turnstileScript = loading.catch(error => {
      turnstileScript = undefined
      throw error
    })
  }
  return turnstileScript
}

interface Props {
  action: string
  onTokenChange: (token: string | null) => void
}

export function TurnstileWidget({ action, onTokenChange }: Props) {
  const { t } = useTranslation()
  const container = useRef<HTMLDivElement>(null)
  const onTokenChangeRef = useRef(onTokenChange)
  const [error, setError] = useState('')
  const siteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY

  useEffect(() => {
    onTokenChangeRef.current = onTokenChange
  }, [onTokenChange])

  useEffect(() => {
    if (!siteKey || !container.current) return
    let active = true
    let widgetId: string | undefined

    void loadTurnstile().then(api => {
      if (!active || !container.current) return
      widgetId = api.render(container.current, {
        sitekey: siteKey,
        action,
        callback: token => {
          setError('')
          onTokenChangeRef.current(token)
        },
        'expired-callback': () => onTokenChangeRef.current(null),
        'error-callback': () => {
          onTokenChangeRef.current(null)
          setError(t('captcha.failed'))
        },
      })
    }).catch(() => {
      if (active) setError(t('captcha.failed'))
    })

    return () => {
      active = false
      if (widgetId && window.turnstile) window.turnstile.remove(widgetId)
      onTokenChangeRef.current(null)
    }
  }, [action, siteKey, t])

  if (!siteKey) return <p className="form-error" role="alert">{t('captcha.unavailable')}</p>

  return (
    <div className="form-group">
      <div ref={container} />
      {error && <p className="form-error" role="alert">{error}</p>}
    </div>
  )
}
