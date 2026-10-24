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
  { id: 'BORDER', label: 'Cửa khẩu & Thủ tục', icon: 'fa-passport' },
  { id: 'OPERATIONS', label: 'Vận hành', icon: 'fa-gears' },
]

const INPUTS: Record<string, { min: number; max: number; step: number }> = {
  MAX_CONTINUOUS_HOURS: { min: 1, max: 12, step: 1 },
  MIN_REST_MINUTES: { min: 15, max: 240, step: 1 },
  VITAL_CHECK_INTERVAL_HOURS: { min: 1, max: 24, step: 1 },
  DEPOSIT_RATE: { min: 0.01, max: 0.99, step: 0.01 },
  QUOTE_VALID_HOURS: { min: 1, max: 168, step: 1 },
  DEMURRAGE_PER_HOUR: { min: 0, max: 100000000, step: 1000 },
  MIN_LEAD_DAYS: { min: 0, max: 365, step: 1 },
  BORDER_OPEN_MINUTES: { min: 0, max: 1439, step: 1 },
  BORDER_CLOSE_MINUTES: { min: 1, max: 1440, step: 1 },
}

function formatConfigValue(config: SystemConfigItem) {
  if (config.configKey === 'DEPOSIT_RATE') {
    return `${(Number(config.configValue) * 100).toLocaleString('vi-VN')}%`
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
  const isBorderTime = config.configGroup === 'BORDER'
  const [value, setValue] = useState(
    isBorderTime ? toTimeValue(Number(config.configValue)) : config.configValue,
  )
  const [busy, setBusy] = useState(false)
  const input = INPUTS[config.configKey]
  const isDirty = isBorderTime
    ? value !== toTimeValue(Number(config.configValue))
    : value !== config.configValue

  const save = async () => {
    const timeParts = /^([01]\d|2[0-3]):([0-5]\d)$/.exec(value)
    const configValue = isBorderTime && timeParts
      ? String(Number(timeParts[1]) * 60 + Number(timeParts[2]))
      : isBorderTime ? '' : value
    if (!configValue.trim() || !Number.isFinite(Number(configValue))) {
      toast('Vui lòng nhập giá trị số hợp lệ.', 'error')
      return
    }
    setBusy(true)
    try {
      const updated = await systemConfigApi.updateConfig(config.configKey, configValue)
      setValue(isBorderTime ? toTimeValue(Number(updated.configValue)) : updated.configValue)
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
        <label className={s.valueField}>
          <span className={s.srOnly}>{config.configName}</span>
          <input
            className="form-control"
            type={isBorderTime ? 'time' : 'number'}
            required
            min={isBorderTime ? undefined : input?.min}
            max={isBorderTime ? undefined : input?.max}
            step={isBorderTime ? 60 : input?.step ?? 1}
            value={value}
            onChange={event => setValue(event.target.value)}
          />
          <span>{isBorderTime ? 'giờ địa phương' : config.configKey === 'DEPOSIT_RATE' ? '0–1' : config.unit}</span>
        </label>
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
            <p>Điều chỉnh quy tắc nghiệp vụ đang được áp dụng cho báo giá, vận chuyển và vận hành.</p>
          </div>
          <span className="badge badge-info"><i className="fa-solid fa-rotate" /> Áp dụng ngay</span>
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
