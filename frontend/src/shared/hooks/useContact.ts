import { useEffect, useState } from 'react'
import { contactApi, type Contact } from '@shared/api/contact'
import { HOTLINE, OFFICE_ADDRESS } from '@shared/config/site'

let cached: Promise<Contact> | undefined

// Hotline và địa chỉ: hiện ngay giá trị dự phòng, thay bằng giá trị quản trị viên đặt khi tải xong; mỗi lần mở web chỉ tải một lần
export function useContact() {
  const [contact, setContact] = useState<Contact>({ hotline: HOTLINE, officeAddress: OFFICE_ADDRESS })
  useEffect(() => {
    let active = true
    cached ??= contactApi.get()
    cached.then(value => { if (active) setContact(value) }, () => { cached = undefined })
    return () => { active = false }
  }, [])
  return contact
}
