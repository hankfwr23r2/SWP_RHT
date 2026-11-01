import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { CaptureField } from '../../src/shared/ui/CaptureField'
import { CaptureScope } from '../../src/shared/ui/CaptureScope'
import '../../src/shared/styles/base.css'

function Harness() {
  const [photo, setPhoto] = useState<string>()
  return <CaptureScope bookingId="BK-1" tripId="T1">
    <CaptureField label="Ảnh điểm đón" purpose="ARRIVE_PICKUP" target="pickup" value={photo} onChange={setPhoto} />
    <button disabled={!photo}>Xác nhận nghiệp vụ</button>
  </CaptureScope>
}
createRoot(document.getElementById('root')!).render(<Harness />)
