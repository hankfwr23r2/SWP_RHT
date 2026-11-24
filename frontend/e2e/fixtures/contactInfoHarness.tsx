import { createRoot } from 'react-dom/client'
import { applyRuntimeBusinessConfig } from '../../src/shared/config/runtimeBusinessRules'
import { QuoteSheet } from '../../src/shared/ui/QuoteSheet'
import '../../src/shared/i18n'
import '../../src/shared/styles/base.css'

applyRuntimeBusinessConfig({
  DEPOSIT_RATE: '0.30',
  QUOTE_VALID_HOURS: '24',
  MAX_CONTINUOUS_HOURS: '4',
  MIN_REST_MINUTES: '30',
  VITAL_CHECK_INTERVAL_HOURS: '2',
  MIN_LEAD_DAYS: '20',
  HOTLINE: '1900 7777',
  OFFICE_ADDRESS: '25 Nguyễn Huệ, Quận 1, TP.HCM',
})

createRoot(document.getElementById('root')!).render(
  <QuoteSheet lines={[{ label: 'Cước vận chuyển nguyên chuyến', detail: '', amount: 5_000_000 }]} subtotal={5_000_000}
    total={5_000_000} deposit={1_500_000} balance={3_500_000} expiresAt={Date.now() + 86_400_000} />,
)
