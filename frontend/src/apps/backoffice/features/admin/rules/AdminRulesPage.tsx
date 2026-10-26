import { useState } from 'react'
import { systemConfigApi, type SystemConfigGroup, type SystemConfigItem } from '@shared/api/systemConfig'
import { applyRuntimeBusinessConfig } from '@shared/config/runtimeBusinessRules'
import { useToast } from '@shared/ui/toast'
import { usePageData } from '@shared/routing/pageData'
import type { systemConfigLoader } from '../../../shared/loaders'
import s from './AdminRules.module.css'

const GROUPS: Array<{ id: SystemConfigGroup; label: string; icon: string }> = [
  { id: 'WELFARE', label: 'An sinh & Vận chuyển', icon: 'fa-horse' },
  { id: 'PAYMENT', label: 'Tài chính & Đặt cọc', icon: 'fa-credit-card' },
  { id: 'OPERATIONS', label: 'Vận hành', icon: 'fa-gears' },
  { id: 'JOURNEY', label: 'Lộ trình & Giờ lái', icon: 'fa-route' },
]

const INPUTS: Record<string, { min: number; max: number; step: number }> = {
  MAX_CONTINUOUS_HOURS: { min: 0.25, max: 12, step: 0.25 },
  MIN_REST_MINUTES: { min: 15, max: 240, step: 1 },
  VITAL_CHECK_INTERVAL_HOURS: { min: 1, max: 24, step: 1 },
  DEPOSIT_RATE: { min: 0.01, max: 0.99, step: 0.01 },
  QUOTE_VALID_HOURS: { min: 1, max: 168, step: 1 },
  DEMURRAGE_PER_HOUR: { min: 0, max: 100000000, step: 1000 },
  MIN_LEAD_DAYS: { min: 0, max: 365, step: 1 },
  MAX_DRIVE_HOURS_PER_DAY: { min: 1, max: 24, step: 1 },
  OVERNIGHT_REST_HOURS: { min: 1, max: 24, step: 1 },
  BORDER_PROCESSING_HOURS: { min: 0, max: 24, step: 1 },
  TRUCK_TOP_SPEED_KMH: { min: 30, max: 120, step: 1 },
}

function formatNumberWithDots(val: string | number) {
  const digits = String(val).replace(/\D/g, '')
  if (!digits) return ''
  return Number(digits).toLocaleString('vi-VN')
}

function parseDotsNumber(val: string) {
  return val.replace(/\./g, '')
}

function formatConfigValue(config: SystemConfigItem) {
  if (config.configKey === 'DEPOSIT_RATE') {
    return `${(Number(config.configValue) * 100).toLocaleString('vi-VN')}%`
  }
  if (config.configKey === 'MAX_CONTINUOUS_HOURS') {
    const totalHours = Number(config.configValue)
    if (isNaN(totalHours)) return `${config.configValue} giờ`
    const hours = Math.floor(totalHours)
    const minutes = Math.round((totalHours - hours) * 60)
    if (hours > 0 && minutes > 0) {
      return `${hours} giờ ${minutes} phút`
    }
    if (hours > 0) {
      return `${hours} giờ`
    }
    return `${minutes} phút`
  }
  if (config.configKey.endsWith('_MINUTES') && config.configGroup === 'BORDER') {
    const totalMinutes = Number(config.configValue)
    const hours = Math.floor(totalMinutes / 60).toString().padStart(2, '0')
    const minutes = (totalMinutes % 60).toString().padStart(2, '0')
    return `${hours}:${minutes}`
  }
  return `${Number(config.configValue).toLocaleString('vi-VN')} ${config.unit}`
}

function toTimeValue(minutes: number) {
  const hours = Math.floor(minutes / 60).toString().padStart(2, '0')
  const remainder = (minutes % 60).toString().padStart(2, '0')
  return `${hours}:${remainder}`
}

function ConfigEditor({
  config,
  onSaved,
}: {
  config: SystemConfigItem
  onSaved: (updated: SystemConfigItem) => void
}) {
  const toast = useToast()
  const isMaxHours = config.configKey === 'MAX_CONTINUOUS_HOURS'
  const isDepositRate = config.configKey === 'DEPOSIT_RATE'
  const isBorderTime = config.configGroup === 'BORDER'
  const isFormattedNumber = !isMaxHours && !isDepositRate && !isBorderTime

  const initialHours = isMaxHours
    ? String(Math.floor(Number(config.configValue) || 0))
    : ''
  const initialMinutes = isMaxHours
    ? String(Math.round(((Number(config.configValue) || 0) - Math.floor(Number(config.configValue) || 0)) * 60))
    : ''

  const [hours, setHours] = useState(initialHours)
  const [minutes, setMinutes] = useState(initialMinutes)
  const [value, setValue] = useState(() => {
    if (isBorderTime) return toTimeValue(Number(config.configValue))
    if (isFormattedNumber) return formatNumberWithDots(config.configValue)
    return config.configValue
  })
  const [busy, setBusy] = useState(false)
  const input = INPUTS[config.configKey]

  const currentTotalHours = (Number(hours) || 0) + (Number(minutes) || 0) / 60
  const savedTotalHours = Number(config.configValue) || 0
  const isMaxHoursDirty = Math.abs(currentTotalHours - savedTotalHours) > 0.0001

  const isDirty = isMaxHours
    ? isMaxHoursDirty
    : isBorderTime
      ? value !== toTimeValue(Number(config.configValue))
      : isFormattedNumber
        ? parseDotsNumber(value) !== config.configValue
        : value !== config.configValue

  const handleFormattedChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const digits = event.target.value.replace(/\D/g, '')
    if (!digits) {
      setValue('')
      return
    }
    const cleanDigits = digits.replace(/^0+(?=\d)/, '')
    setValue(cleanDigits.replace(/\B(?=(\d{3})+(?!\d))/g, '.'))
  }

  const save = async () => {
    if (isMaxHours) {
      const h = Number(hours)
      const m = Number(minutes)
      if (!Number.isFinite(h) || !Number.isFinite(m) || h < 0 || m < 0 || m >= 60) {
        toast('Vui lòng nhập giờ và phút hợp lệ (phút từ 0 đến 59).', 'error')
        return
      }
      const total = h + m / 60
      if (total < 0.25 || total > 12) {
        toast('Thời gian di chuyển liên tục tối đa phải từ 15 phút (0,25 giờ) đến 12 giờ.', 'error')
        return
      }
      const configValue = Number(total.toFixed(4)).toString()
      setBusy(true)
      try {
        const updated = await systemConfigApi.updateConfig(config.configKey, configValue)
        const nextVal = Number(updated.configValue)
        const nextH = Math.floor(nextVal)
        const nextM = Math.round((nextVal - nextH) * 60)
        setHours(String(nextH))
        setMinutes(String(nextM))
        onSaved(updated)
        toast(`Đã cập nhật “${config.configName}”.`, 'success')
      } catch (error) {
        toast(error instanceof Error ? error.message : 'Không thể cập nhật cấu hình.', 'error')
      } finally {
        setBusy(false)
      }
      return
    }

    const timeParts = /^([01]\d|2[0-3]):([0-5]\d)$/.exec(value)
    const configValue = isBorderTime && timeParts
      ? String(Number(timeParts[1]) * 60 + Number(timeParts[2]))
      : isBorderTime
        ? ''
        : isFormattedNumber
          ? parseDotsNumber(value)
          : value

    if (!configValue.trim() || !Number.isFinite(Number(configValue))) {
      toast('Vui lòng nhập giá trị số hợp lệ.', 'error')
      return
    }
    const num = Number(configValue)
    if (input?.min !== undefined && num < input.min) {
      toast(`Giá trị tối thiểu là ${isFormattedNumber ? formatNumberWithDots(input.min) : input.min}.`, 'error')
      return
    }
    if (input?.max !== undefined && num > input.max) {
      toast(`Giá trị tối đa là ${isFormattedNumber ? formatNumberWithDots(input.max) : input.max}.`, 'error')
      return
    }

    setBusy(true)
    try {
      const updated = await systemConfigApi.updateConfig(config.configKey, configValue)
      if (isBorderTime) {
        setValue(toTimeValue(Number(updated.configValue)))
      } else if (isFormattedNumber) {
        setValue(formatNumberWithDots(updated.configValue))
      } else {
        setValue(updated.configValue)
      }
      onSaved(updated)
      toast(`Đã cập nhật “${config.configName}”.`, 'success')
    } catch (error) {
      toast(error instanceof Error ? error.message : 'Không thể cập nhật cấu hình.', 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <article className={s.rule}>
      <div className={s.ruleInfo}>
        <div className={s.ruleTitle}>{config.configName}</div>
        <p>{config.description}</p>
        <small>Giá trị hiện tại: <b>{formatConfigValue(config)}</b></small>
      </div>
      <div className={s.editor}>
        {isMaxHours ? (
          <div className={s.timeField}>
            <input
              className={`form-control ${s.timeInput}`}
              type="number"
              min={0}
              max={12}
              step={1}
              value={hours}
              aria-label="Số giờ di chuyển"
              placeholder="0"
              onChange={event => setHours(event.target.value)}
            />
            <span>giờ</span>
            <input
              className={`form-control ${s.timeInput}`}
              type="number"
              min={0}
              max={59}
              step={1}
              value={minutes}
              aria-label="Số phút di chuyển"
              placeholder="0"
              onChange={event => setMinutes(event.target.value)}
            />
            <span>phút</span>
          </div>
        ) : (
          <label className={s.valueField}>
            <span className={s.srOnly}>{config.configName}</span>
            <input
              className="form-control"
              type={isBorderTime ? 'time' : isFormattedNumber ? 'text' : 'number'}
              inputMode={isFormattedNumber ? 'numeric' : undefined}
              required
              min={isBorderTime || isFormattedNumber ? undefined : input?.min}
              max={isBorderTime || isFormattedNumber ? undefined : input?.max}
              step={isBorderTime ? 60 : isFormattedNumber ? undefined : input?.step ?? 1}
              value={value}
              onChange={isFormattedNumber ? handleFormattedChange : event => setValue(event.target.value)}
            />
            <span>{isBorderTime ? 'giờ địa phương' : config.configKey === 'DEPOSIT_RATE' ? '0–1' : config.unit}</span>
          </label>
        )}
        <button
          className="btn btn-primary btn-sm"
          type="button"
          disabled={busy || !isDirty}
          onClick={save}
        >
          <i className={`fa-solid ${busy ? 'fa-spinner fa-spin' : 'fa-floppy-disk'}`} />
          {busy ? 'Đang lưu' : 'Lưu'}
        </button>
      </div>
    </article>
  )
}

export default function AdminRulesPage() {
  const [group, setGroup] = useState<SystemConfigGroup>('WELFARE')
  const { configs: loadedConfigs = [] } = usePageData<typeof systemConfigLoader>()
  const [configs, setConfigs] = useState<SystemConfigItem[]>(loadedConfigs)

  const saveConfig = (updated: SystemConfigItem) => {
    const next = configs.map(config => config.configKey === updated.configKey ? updated : config)
    setConfigs(next)
    applyRuntimeBusinessConfig(Object.fromEntries(next.map(config => [config.configKey, config.configValue])))
  }

  const visibleConfigs = configs.filter(config => config.configGroup === group)

  return (
    <div className="page">
      <div className={`wrap ${s.wrap}`}>
        <header className={s.header}>
          <div>
            <h1>Quy định & Tham số hệ thống</h1>
          </div>
        </header>

        <nav className={s.groups} role="tablist" aria-label="Nhóm quy định">
          {GROUPS.map(item => (
            <button
              key={item.id}
              type="button"
              role="tab"
              aria-selected={group === item.id}
              className={`${s.groupTab} ${group === item.id ? s.groupTabActive : ''}`}
              onClick={() => setGroup(item.id)}
            >
              <i className={`fa-solid ${item.icon}`} />
              {item.label}
              <span className={s.groupCount}>{configs.filter(config => config.configGroup === item.id).length}</span>
            </button>
          ))}
        </nav>

        <section className={s.panel} role="tabpanel">
          <div className={s.panelHeader}>
            <div>
              <h2>{GROUPS.find(item => item.id === group)?.label}</h2>
              <p>Các giá trị được kiểm tra ở Backend trước khi lưu.</p>
            </div>
          </div>
          {visibleConfigs.length ? (
            <div className={s.rules}>
              {visibleConfigs.map(config => (
                <ConfigEditor key={config.configKey} config={config} onSaved={saveConfig} />
              ))}
            </div>
          ) : (
            <div className={s.empty}>Không có tham số trong nhóm này.</div>
          )}
          <p className={s.note}>
            <i className="fa-solid fa-circle-info" />
            Thay đổi quy tắc chỉ tác động đến các nghiệp vụ mới; báo giá đã phát hành giữ nguyên hạn và số tiền đã ghi nhận.
          </p>
        </section>
      </div>
    </div>
  )
}
