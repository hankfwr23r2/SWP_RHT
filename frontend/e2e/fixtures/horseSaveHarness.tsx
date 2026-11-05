import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { HorseFormModal } from '../../src/apps/customer/features/horses/HorseFormModal'
import type { HorseProfile } from '../../src/shared/types/booking'
import '../../src/shared/styles/base.css'

function Harness() {
  const [saved, setSaved] = useState<HorseProfile>()
  const [closed, setClosed] = useState(false)
  if (saved) return <output data-testid="saved-horse">{JSON.stringify(saved)}</output>
  if (closed) return <p>Đã đóng</p>
  return <HorseFormModal owner="test" onClose={() => setClosed(true)} onSaved={setSaved} />
}

createRoot(document.getElementById('root')!).render(<Harness />)
