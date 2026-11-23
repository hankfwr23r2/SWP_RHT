// Thư viện giao diện (chỉ ADMIN): mọi component trong shared/ui và 4 trạng thái của một màn hình (AGENTS.md mục 8.5, 8.6).
// Màn hình mới chọn component ở đây. Trang không gọi API; dữ liệu mẫu nằm trong file.
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Badge, type BadgeTone } from '@shared/ui/Badge'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { PageHeader } from '@shared/ui/PageHeader'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { StatusBadge, type StatusTone } from '@shared/ui/StatusBadge'
import { Stepper } from '@shared/ui/Stepper'
import { Table, type TableColumn } from '@shared/ui/Table'
import { Tabs } from '@shared/ui/Tabs'
import { Textarea } from '@shared/ui/Textarea'
import { useToast } from '@shared/ui/toast'
import s from './UiLibrary.module.css'

const SWATCHES = [
  ['--color-bg', s.bg], ['--color-surface', s.surface], ['--color-text', s.text], ['--color-heading', s.heading],
  ['--color-muted', s.muted], ['--color-border', s.border], ['--color-primary', s.primary], ['--color-success', s.success],
  ['--color-warning', s.warning], ['--color-danger', s.danger], ['--color-info', s.info],
] as const
const VARIANTS = ['primary', 'secondary', 'ghost', 'danger'] as const
const SIZES = ['sm', 'md', 'lg'] as const
const TONES: BadgeTone[] = ['neutral', 'primary', 'success', 'warning', 'danger', 'info']
const STATUS_TONES: StatusTone[] = ['neutral', 'success', 'warning', 'danger', 'info']

interface Vehicle { id: number; plate: string; capacity: number; status: 'ready' | 'onTrip' | 'maintenance' }
const VEHICLES: Vehicle[] = [
  { id: 1, plate: '51C-123.45', capacity: 4, status: 'ready' },
  { id: 2, plate: '29H-456.78', capacity: 2, status: 'onTrip' },
  { id: 3, plate: '43A-111.22', capacity: 6, status: 'maintenance' },
]
const STATUS_TONE: Record<Vehicle['status'], StatusTone> = { ready: 'success', onTrip: 'info', maintenance: 'warning' }

const wait = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

export default function UiLibraryPage() {
  const { t } = useTranslation()
  const toast = useToast()
  const [vehicleType, setVehicleType] = useState('')
  const [orderTab, setOrderTab] = useState('new')
  const [step, setStep] = useState(1)
  const [modalOpen, setModalOpen] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [screenState, setScreenState] = useState('loading')

  const columns: TableColumn<Vehicle>[] = [
    { key: 'plate', header: t('uiLibrary.colPlate'), render: v => v.plate },
    { key: 'capacity', header: t('uiLibrary.colCapacity'), render: v => v.capacity, align: 'end' },
    { key: 'status', header: t('uiLibrary.colStatus'), render: v => <StatusBadge tone={STATUS_TONE[v.status]} label={t(`uiLibrary.vehicleStatus.${v.status}`)} /> },
  ]
  const vehicleTable = <Table caption={t('uiLibrary.vehicles')} columns={columns} rows={VEHICLES} rowKey={v => v.id} />
  const steps = [t('uiLibrary.step1'), t('uiLibrary.step2'), t('uiLibrary.step3'), t('uiLibrary.step4')]
  const orderTabs = [
    { value: 'new', label: t('uiLibrary.tabNew'), count: 3 },
    { value: 'running', label: t('uiLibrary.tabRunning'), count: 1 },
    { value: 'done', label: t('uiLibrary.tabDone') },
  ]

  return (
    <div className={s.page}>
      <PageHeader title={t('uiLibrary.title')} description={t('uiLibrary.description')} />

      <Card title={t('uiLibrary.colors')}>
        <p className={s.hint}>{t('uiLibrary.colorsHint')}</p>
        <ul className={s.swatches}>
          {SWATCHES.map(([token, className]) => (
            <li key={token} className={s.swatch}><span className={`${s.chip} ${className}`} aria-hidden="true" /><code>{token}</code></li>
          ))}
        </ul>
      </Card>

      <Card title={t('uiLibrary.buttons')}>
        {SIZES.map(size => (
          <div key={size} className={s.row}>
            {VARIANTS.map(variant => <Button key={variant} variant={variant} size={size}>{t(`uiLibrary.variant.${variant}`)}</Button>)}
          </div>
        ))}
        <div className={s.row}>
          <Button variant="primary" icon={<i className="fa-solid fa-plus" />}>{t('uiLibrary.withIcon')}</Button>
          <Button variant="primary" loading>{t('uiLibrary.saving')}</Button>
          <Button disabled>{t('uiLibrary.disabled')}</Button>
          <Button icon={<i className="fa-solid fa-pen" />} aria-label={t('uiLibrary.iconOnly')} />
        </div>
      </Card>

      <Card title={t('uiLibrary.fields')}>
        <div className={s.grid}>
          <Field label={t('uiLibrary.plate')} hint={t('uiLibrary.plateHint')} required>{c => <Input {...c} />}</Field>
          <Field label={t('uiLibrary.plate')} error={t('uiLibrary.plateError')}>{c => <Input {...c} defaultValue="51C-123.45" />}</Field>
          <Field label={t('uiLibrary.vehicleType')} required>
            {c => (
              <Select
                {...c}
                value={vehicleType}
                onChange={setVehicleType}
                placeholder={t('uiLibrary.vehicleTypePlaceholder')}
                options={[
                  { value: 'standard', label: t('uiLibrary.vehicleTypes.standard') },
                  { value: 'large', label: t('uiLibrary.vehicleTypes.large') },
                  { value: 'retired', label: t('uiLibrary.vehicleTypes.retired'), disabled: true },
                ]}
              />
            )}
          </Field>
          <Field label={t('uiLibrary.vehicleCode')} hint={t('uiLibrary.vehicleCodeHint')}>{c => <Input {...c} value="VH-007" disabled readOnly />}</Field>
          <Field label={t('uiLibrary.note')} className={s.wide}>{c => <Textarea {...c} placeholder={t('uiLibrary.notePlaceholder')} />}</Field>
        </div>
      </Card>

      <Card title={t('uiLibrary.badges')}>
        <p className={s.hint}>{t('uiLibrary.badgesHint')}</p>
        <div className={s.row}>{TONES.map(tone => <Badge key={tone} tone={tone}>{t(`uiLibrary.tone.${tone}`)}</Badge>)}</div>
        <div className={s.row}>{STATUS_TONES.map(tone => <StatusBadge key={tone} tone={tone} label={t(`uiLibrary.tone.${tone}`)} />)}</div>
      </Card>

      <Card title={t('uiLibrary.navigation')}>
        <Tabs
          label={t('uiLibrary.tabsLabel')}
          value={orderTab}
          onChange={setOrderTab}
          items={orderTabs}
        >
          <p className={s.hint}>{t('uiLibrary.tabContent', { name: orderTabs.find(tab => tab.value === orderTab)?.label })}</p>
        </Tabs>
        <Stepper label={t('uiLibrary.stepsLabel')} steps={steps} current={step} />
        <div className={`${s.row} ${s.stepActions}`}>
          <Button size="sm" disabled={step === 0} onClick={() => setStep(step - 1)}>{t('uiLibrary.back')}</Button>
          <Button size="sm" variant="primary" disabled={step === steps.length - 1} onClick={() => setStep(step + 1)}>{t('uiLibrary.next')}</Button>
        </div>
      </Card>

      <Card title={t('uiLibrary.table')}>
        <p className={s.hint}>{t('uiLibrary.tableHint')}</p>
        {vehicleTable}
      </Card>

      <Card title={t('uiLibrary.dialogs')}>
        <div className={s.row}>
          <Button onClick={() => setModalOpen(true)}>{t('uiLibrary.openModal')}</Button>
          <Button variant="danger" onClick={() => setConfirmOpen(true)}>{t('uiLibrary.confirmAction')}</Button>
          <Button onClick={() => toast(t('uiLibrary.toastSuccessMessage'), 'success')}>{t('uiLibrary.toastSuccess')}</Button>
          <Button onClick={() => toast(t('uiLibrary.toastInfoMessage'), 'info')}>{t('uiLibrary.toastInfo')}</Button>
          <Button onClick={() => toast(t('uiLibrary.toastErrorMessage'), 'error')}>{t('uiLibrary.toastError')}</Button>
        </div>
      </Card>

      <Card title={t('uiLibrary.states')}>
        <Tabs
          label={t('uiLibrary.statesLabel')}
          value={screenState}
          onChange={setScreenState}
          items={[
            { value: 'loading', label: t('uiLibrary.stateLoading') },
            { value: 'empty', label: t('uiLibrary.stateEmpty') },
            { value: 'error', label: t('uiLibrary.stateError') },
            { value: 'data', label: t('uiLibrary.stateData') },
          ]}
        >
          {screenState === 'loading' && <Skeleton variant="block" lines={3} />}
          {screenState === 'empty' && (
            <EmptyState
              icon={<i className="fa-solid fa-truck" />}
              title={t('uiLibrary.emptyTitle')}
              description={t('uiLibrary.emptyDescription')}
              action={<Button variant="primary" icon={<i className="fa-solid fa-plus" />}>{t('uiLibrary.addVehicle')}</Button>}
            />
          )}
          {screenState === 'error' && <ErrorState message={t('uiLibrary.errorMessage')} onRetry={() => setScreenState('data')} />}
          {screenState === 'data' && vehicleTable}
        </Tabs>
      </Card>

      {modalOpen && (
        <Modal
          title={t('uiLibrary.modalTitle')}
          onClose={() => setModalOpen(false)}
          footer={<Button variant="primary" onClick={() => setModalOpen(false)}>{t('ui.close')}</Button>}
        >
          {t('uiLibrary.modalBody')}
        </Modal>
      )}
      {confirmOpen && (
        <ConfirmDialog
          tone="danger"
          title={t('uiLibrary.confirmTitle')}
          message={t('uiLibrary.confirmMessage')}
          confirmLabel={t('uiLibrary.confirmAction')}
          onCancel={() => setConfirmOpen(false)}
          onConfirm={async () => {
            await wait(1000)
            setConfirmOpen(false)
            toast(t('uiLibrary.confirmDone'), 'success')
          }}
        />
      )}
    </div>
  )
}
