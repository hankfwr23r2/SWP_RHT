import { useEffect, useState } from 'react'
import { transportRulesApi, type TransportRules } from '@shared/api/transportRules'

let cached: Promise<TransportRules> | undefined

// Con số quy định vận chuyển do quản trị viên đặt; mỗi lần mở web chỉ tải một lần. Chưa tải được thì undefined, nơi dùng ẩn dòng số
export function useTransportRules() {
  const [rules, setRules] = useState<TransportRules>()
  useEffect(() => {
    let active = true
    cached ??= transportRulesApi.get()
    cached.then(value => { if (active) setRules(value) }, () => { cached = undefined })
    return () => { active = false }
  }, [])
  return rules
}
