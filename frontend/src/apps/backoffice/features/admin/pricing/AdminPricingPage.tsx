import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { usePageData } from '@shared/routing/pageData'
import { systemConfigApi, type SystemConfigItem } from '@shared/api/systemConfig'
import { applyRuntimeBusinessConfig } from '@shared/config/runtimeBusinessRules'
import { useToast } from '@shared/ui/toast'
import type { adminPricingLoader } from '../../../shared/loaders'
import s from './AdminPricing.module.css'

interface ParameterDef {
  key: string
  name: string
  group: 'DISTANCE' | 'VEHICLE' | 'OPERATIONS'
  unit: string
  desc: string
  defaultValue: string
  isPercent?: boolean
}

const PARAMETER_DEFS: ParameterDef[] = [
  // Cước cự ly
  {
    key: 'TRIP_OPEN_FEE',
    name: 'Phí mở chuyến cơ bản',
    group: 'DISTANCE',
    unit: '₫/xe',
    desc: 'Phí cố định khởi động chuyến cho mỗi đầu xe vận chuyển tiêu chuẩn',
    defaultValue: '3000000',
  },
  {
    key: 'KM_TIER_1_LIMIT',
    name: 'Định mức cự ly Bậc 1',
    group: 'DISTANCE',
    unit: 'km',
    desc: 'Ngưỡng cự ly tối đa áp dụng đơn giá Bậc 1 (mặc định 0 - 300 km)',
    defaultValue: '300',
  },
  {
    key: 'KM_TIER_1_RATE',
    name: 'Đơn giá km Bậc 1',
    group: 'DISTANCE',
    unit: '₫/km',
    desc: 'Đơn giá cho mỗi km trong phạm vi Bậc 1 (0 - 300 km)',
    defaultValue: '26000',
  },
  {
    key: 'KM_TIER_2_LIMIT',
    name: 'Định mức cự ly Bậc 2',
    group: 'DISTANCE',
    unit: 'km',
    desc: 'Ngưỡng cự ly tối đa áp dụng đơn giá Bậc 2 (301 - 600 km)',
    defaultValue: '600',
  },
  {
    key: 'KM_TIER_2_RATE',
    name: 'Đơn giá km Bậc 2',
    group: 'DISTANCE',
    unit: '₫/km',
    desc: 'Đơn giá cho mỗi km trong phạm vi Bậc 2 (301 - 600 km)',
    defaultValue: '20000',
  },
  {
    key: 'KM_TIER_3_RATE',
    name: 'Đơn giá km Bậc 3 (Đường dài)',
    group: 'DISTANCE',
    unit: '₫/km',
    desc: 'Đơn giá cho mỗi km đường dài vượt trên Bậc 2 (> 600 km)',
    defaultValue: '15000',
  },

  // Hệ số xe
  {
    key: 'CLASS_FACTOR_LIGHT',
    name: 'Hệ số Xe Light (Van / 2 ngăn)',
    group: 'VEHICLE',
    unit: 'hệ số',
    desc: 'Hệ số tính cước cho dòng xe tải nhẹ hoặc van chuyên dụng 2 khoang',
    defaultValue: '1.0',
  },
  {
    key: 'CLASS_FACTOR_MEDIUM',
    name: 'Hệ số Xe Medium (4 - 6 ngăn)',
    group: 'VEHICLE',
    unit: 'hệ số',
    desc: 'Hệ số tính cước cho xe tải trung 4 đến 6 khoang chở ngựa chuyên biệt',
    defaultValue: '1.4',
  },
  {
    key: 'CLASS_FACTOR_HEAVY',
    name: 'Hệ số Xe Heavy (9 ngăn)',
    group: 'VEHICLE',
    unit: 'hệ số',
    desc: 'Hệ số tính cước cho dòng xe tải hạng nặng đoàn xe lớn 9 khoang',
    defaultValue: '1.9',
  },

  // Chi phí & Quy định
  {
    key: 'MARGIN_RATE',
    name: 'Biên lợi nhuận gộp (Margin)',
    group: 'OPERATIONS',
    unit: '%',
    desc: 'Tỷ lệ biên lợi nhuận được gộp',
    defaultValue: '0.05',
    isPercent: true,
  },
  {
    key: 'CLEARANCE_FEE_INTERNATIONAL',
    name: 'Phí thủ tục hải quan quốc tế',
    group: 'OPERATIONS',
    unit: '₫/chuyến',
    desc: 'Phí dịch vụ trọn gói hồ sơ kiểm dịch thú y & thông quan cửa khẩu quốc tế',
    defaultValue: '300000',
  },

]

function toDraftValue(def: ParameterDef, value: string) {
  return def.isPercent ? String(Number((Number(value) * 100).toPrecision(15))) : value
}

function formatVND(val: number) {
  return val.toLocaleString('vi-VN') + ' ₫'
}

function formatNumber(val: number | string) {
  const n = Number(val)
  return isNaN(n) ? String(val) : n.toLocaleString('vi-VN')
}

export default function AdminPricingPage() {
  const { t } = useTranslation()
  const initialData = usePageData<typeof adminPricingLoader>()
  const toast = useToast()

  const [activeTab, setActiveTab] = useState<'parameters' | 'simulator'>('parameters')
  const [configsList, setConfigsList] = useState<SystemConfigItem[]>(initialData.configs ?? [])

  // Draft configurations dictionary
  const [draftValues, setDraftValues] = useState<Record<string, string>>(() => {
    const map: Record<string, string> = {}
    for (const def of PARAMETER_DEFS) {
      const found = (initialData.configs ?? []).find(c => c.configKey === def.key)
      map[def.key] = toDraftValue(def, found ? found.configValue : def.defaultValue)
    }
    return map
  })

  // Has unsaved changes
  const hasChanges = useMemo(() => {
    for (const def of PARAMETER_DEFS) {
      const found = configsList.find(c => c.configKey === def.key)
      const current = found ? found.configValue : def.defaultValue
      if (draftValues[def.key] !== toDraftValue(def, current)) return true
    }
    return false
  }, [draftValues, configsList])

  const [isSaving, setIsSaving] = useState(false)

  // Simulator inputs
  const [simKm, setSimKm] = useState(350)
  const [simVehicleClass, setSimVehicleClass] = useState<'light' | 'medium' | 'heavy'>('medium')
  const [simIsIntl, setSimIsIntl] = useState(false)
  const [simStallType, setSimStallType] = useState<'standard' | 'single'>('standard')
  const [simFeed, setSimFeed] = useState<'basic' | 'advanced' | 'sport'>('basic')
  const [simInsurancePremium, setSimInsurancePremium] = useState(0)

  // Save all system configs
  const handleSaveAllConfigs = async () => {
    for (const def of PARAMETER_DEFS) {
      const raw = draftValues[def.key] ?? toDraftValue(def, def.defaultValue)
      const value = Number(raw)
      if (!raw.trim() || !Number.isFinite(value) || value < 0
        || (def.isPercent && value > 100)
        || (def.group === 'VEHICLE' && (value < 0.1 || value > 10))
        || (!def.isPercent && def.group !== 'VEHICLE' && !Number.isInteger(value))) {
        toast(`Giá trị ${def.name} không hợp lệ.`, 'error')
        return
      }
    }
    setIsSaving(true)
    try {
      const updatedList: SystemConfigItem[] = [...configsList]
      for (const def of PARAMETER_DEFS) {
        const draft = draftValues[def.key] ?? toDraftValue(def, def.defaultValue)
        const val = def.isPercent ? String(Number(draft) / 100) : draft
        const res = await systemConfigApi.updateConfig(def.key, val)
        const idx = updatedList.findIndex(c => c.configKey === def.key)
        if (idx >= 0) updatedList[idx] = res
        else updatedList.push(res)
      }
      setConfigsList(updatedList)

      // Apply runtime
      const configMap = Object.fromEntries(updatedList.map(c => [c.configKey, c.configValue]))
      applyRuntimeBusinessConfig(configMap)
      try {
        localStorage.setItem('cached_public_system_configs', JSON.stringify(configMap))
      } catch {
        /* empty */
      }

      toast('Đã lưu thành công biểu giá và công thức mới vào hệ thống!', 'success')
    } catch (e: any) {
      toast(e?.message || 'Có lỗi xảy ra khi lưu tham số biểu giá.', 'error')
    } finally {
      setIsSaving(false)
    }
  }

  const handleResetDraft = () => {
    const map: Record<string, string> = {}
    for (const def of PARAMETER_DEFS) {
      const found = configsList.find(c => c.configKey === def.key)
      map[def.key] = toDraftValue(def, found ? found.configValue : def.defaultValue)
    }
    setDraftValues(map)
  }

  // Simulator Calculation Logic using Draft Values
  const simulatorQuote = useMemo(() => {
    const tripOpen = Number(draftValues['TRIP_OPEN_FEE'] ?? 3000000)
    const t1Limit = Number(draftValues['KM_TIER_1_LIMIT'] ?? 300)
    const t1Rate = Number(draftValues['KM_TIER_1_RATE'] ?? 26000)
    const t2Limit = Number(draftValues['KM_TIER_2_LIMIT'] ?? 600)
    const t2Rate = Number(draftValues['KM_TIER_2_RATE'] ?? 20000)
    const t3Rate = Number(draftValues['KM_TIER_3_RATE'] ?? 15000)

    const classFactor =
      simVehicleClass === 'heavy'
        ? Number(draftValues['CLASS_FACTOR_HEAVY'] ?? 1.9)
        : simVehicleClass === 'medium'
          ? Number(draftValues['CLASS_FACTOR_MEDIUM'] ?? 1.4)
          : Number(draftValues['CLASS_FACTOR_LIGHT'] ?? 1.0)

    const margin = Number(draftValues['MARGIN_RATE'] ?? 5) / 100
    const clearanceIntl = Number(draftValues['CLEARANCE_FEE_INTERNATIONAL'] ?? 300000)

    const m = 1 + margin

    // 1. Truck base tiered cost
    let left = simKm
    let tieredTruckCost = tripOpen
    const tiers: [number, number][] = [
      [t1Limit, t1Rate],
      [t2Limit, t2Rate],
      [Infinity, t3Rate],
    ]
    let from = 0
    for (const [to, rate] of tiers) {
      const part = Math.min(left, to - from)
      if (part <= 0) break
      tieredTruckCost += part * rate
      left -= part
      from = to
    }

    const truckCostLine = Math.round((tieredTruckCost * classFactor * m) / 1000) * 1000
    const fuelBotLine = Math.round((simKm * 9000 * 1.05) / 1000) * 1000

    const stallCost = simStallType === 'single' ? 1500000 : 0
    const feedCost = simFeed === 'sport' ? 500000 : simFeed === 'advanced' ? 300000 : 0
    const clearanceCost = simIsIntl ? clearanceIntl : 0

    const insuranceCost = Math.max(0, simInsurancePremium)

    const total = truckCostLine + fuelBotLine + stallCost + feedCost + clearanceCost + insuranceCost

    return {
      lines: [
        {
          label: 'Cước vận chuyển nguyên chuyến',
          detail: `Xe hạng ${simVehicleClass.toUpperCase()} · ${simKm} km · Phí mở chuyến + 3 bậc km`,
          amount: truckCostLine,
        },
        {
          label: 'Nhiên liệu & BOT ước tính',
          detail: `${simKm} km × 9.000 ₫/km (+5% dự phòng rủi ro lộ trình)`,
          amount: fuelBotLine,
        },
        ...(stallCost > 0
          ? [
              {
                label: 'Khoang đơn mở rộng',
                detail: '01 khoang đơn mở rộng cách ly',
                amount: stallCost,
              },
            ]
          : []),
        ...(feedCost > 0
          ? [
              {
                label: `Gói dinh dưỡng (${simFeed === 'sport' ? 'Thể thao' : 'Nâng cao'})`,
                detail: 'Khẩu phần cỏ Timothy & yến mạch',
                amount: feedCost,
              },
            ]
          : []),
        {
          label: 'Thủ tục kiểm dịch & Hải quan',
          detail: simIsIntl ? 'Trọn gói xuất nhập cảnh quốc tế' : 'Giấy kiểm dịch thú y nội địa (Miễn phí)',
          amount: clearanceCost,
        },
        ...(insuranceCost > 0
          ? [
              {
                label: 'Bảo hiểm chuyến đi',
                detail: 'Phí thu hộ do bên thứ ba báo (nhập để mô phỏng)',
                amount: insuranceCost,
              },
            ]
          : []),
      ],
      total,
    }
  }, [draftValues, simKm, simVehicleClass, simIsIntl, simStallType, simFeed, simInsurancePremium])

  return (
    <div className={s.wrap}>
      {/* Header */}
      <div className={s.header}>
        <div>
          <h1>
            {t('adminPricing.title', 'Cấu hình Biểu phí & Báo giá')}
          </h1>
          <p>{t('adminPricing.subtitle', 'Điều chỉnh công thức cước cự ly, phụ phí loại xe và mô phỏng giá.')}</p>
        </div>
        <div className={s.headerActions}>
          {hasChanges && (
            <button className="btn btn-secondary" onClick={handleResetDraft} disabled={isSaving}>
              <i className="fa-solid fa-rotate-left" /> Khôi phục
            </button>
          )}
          <button className="btn btn-primary" onClick={handleSaveAllConfigs} disabled={isSaving}>
            <i className="fa-solid fa-floppy-disk" /> {isSaving ? 'Đang lưu...' : t('adminPricing.saveChanges', 'Lưu cấu hình')}
          </button>
        </div>
      </div>

      {/* Visual Formula Pipeline Banner */}
      <div className={s.formulaBanner}>
        <div className={s.formulaHeader}>
          <h3>
            <i className="fa-solid fa-network-wired" /> Công Thức Tính Tổng Báo Giá Vận Chuyển
          </h3>
        </div>
        <div className={s.formulaFlow}>
          <div className={s.formulaNode}>
            <span className={s.formulaNodeLabel}>1. Cước Quãng Đường</span>
            <span className={s.formulaNodeDesc}>[Mở chuyến + Bậc km] × Hệ số xe x (1+Margin)</span>
          </div>
          <span className={s.formulaOp}>+</span>
          <div className={s.formulaNode}>
            <span className={s.formulaNodeLabel}>2. Nhiên Liệu & BOT</span>
            <span className={s.formulaNodeDesc}>km × 9.000 ₫ × 1.05 (Dự phòng)</span>
          </div>
          <span className={s.formulaOp}>+</span>
          <div className={s.formulaNode}>
            <span className={s.formulaNodeLabel}>3. Dịch Vụ Đi Kèm</span>
            <span className={s.formulaNodeDesc}>Khoang đơn + Dinh dưỡng</span>
          </div>
          <span className={s.formulaOp}>+</span>
          <div className={s.formulaNode}>
            <span className={s.formulaNodeLabel}>4. Bảo Hiểm & Hải Quan</span>
            <span className={s.formulaNodeDesc}>Tiền bảo hiểm và phí thủ tục kiểm dịch & hải quan (nếu có)</span>
          </div>
          <span className={s.formulaEqual}>=</span>
          <div className={s.formulaTotalNode}>
            <span className={s.formulaNodeLabel}>Tổng Tiền</span>
            <span className={s.formulaNodeDesc}>Tiền cộng dồn</span>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className={s.tabs}>
        <button
          className={`${s.tabBtn} ${activeTab === 'parameters' ? s.tabBtnActive : ''}`}
          onClick={() => setActiveTab('parameters')}
        >
          <i className="fa-solid fa-sliders" /> {t('adminPricing.tabParams', 'Thông số & Biểu phí')}
          <span className={s.tabBadge}>{PARAMETER_DEFS.length}</span>
        </button>
        <button
          className={`${s.tabBtn} ${activeTab === 'simulator' ? s.tabBtnActive : ''}`}
          onClick={() => setActiveTab('simulator')}
        >
          <i className="fa-solid fa-wand-magic-sparkles" /> {t('adminPricing.tabSimulator', 'Mô phỏng báo giá')}
        </button>
      </div>

      {/* TAB 1: PARAMETERS */}
      {activeTab === 'parameters' && (
        <>
          <div className={s.grid}>
            {/* Panel 1: Cước cự ly */}
            <div className={s.panel}>
              <div className={s.panelHeader}>
                <h2>
                  <i className="fa-solid fa-route" /> Cước Cự Ly Xe Tiêu Chuẩn (2 Ngăn)
                </h2>
              </div>
              <div className={s.panelBody}>
                {PARAMETER_DEFS.filter(p => p.group === 'DISTANCE').map(def => (
                  <div key={def.key} className={s.configRow}>
                    <div className={s.configMeta}>
                      <span className={s.configKey}>{def.key}</span>
                      <div className={s.configTitle}>{def.name}</div>
                      <div className={s.configDesc}>{def.desc}</div>
                    </div>
                    <div className={s.configInputWrap}>
                      <input
                        type="text"
                        value={formatNumber(draftValues[def.key] ?? def.defaultValue)}
                        onChange={e => {
                          const raw = e.target.value.replace(/\D/g, '')
                          setDraftValues(prev => ({ ...prev, [def.key]: raw }))
                        }}
                      />
                      <span className={s.configUnit}>{def.unit}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Panel 2: Hệ số hạng xe */}
            <div className={s.panel}>
              <div className={s.panelHeader}>
                <h2>
                  <i className="fa-solid fa-truck" /> Hệ Số Nhân Theo Hạng Xe
                </h2>
              </div>
              <div className={s.panelBody}>
                {PARAMETER_DEFS.filter(p => p.group === 'VEHICLE').map(def => (
                  <div key={def.key} className={s.configRow}>
                    <div className={s.configMeta}>
                      <span className={s.configKey}>{def.key}</span>
                      <div className={s.configTitle}>{def.name}</div>
                      <div className={s.configDesc}>{def.desc}</div>
                    </div>
                    <div className={s.configInputWrap}>
                      <input
                        type="number"
                        step="any"
                        min="0.1"
                        max="10"
                        value={draftValues[def.key] ?? def.defaultValue}
                        onChange={e => {
                          setDraftValues(prev => ({ ...prev, [def.key]: e.target.value }))
                        }}
                      />
                      <span className={s.configUnit}>{def.unit}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Panel 3: Chi phí chuyến & Margin */}
            <div className={s.panel}>
              <div className={s.panelHeader}>
                <h2>
                  <i className="fa-solid fa-coins" /> Chi Phí Chuyến & Biên Lợi Nhuận
                </h2>
              </div>
              <div className={s.panelBody}>
                {PARAMETER_DEFS.filter(p => p.group === 'OPERATIONS').map(def => (
                  <div key={def.key} className={s.configRow}>
                    <div className={s.configMeta}>
                      <span className={s.configKey}>{def.key}</span>
                      <div className={s.configTitle}>{def.name}</div>
                      <div className={s.configDesc}>{def.desc}</div>
                    </div>
                    <div className={s.configInputWrap}>
                      {def.isPercent ? (
                        <input
                          type="number"
                          step="any"
                          min="0"
                          max="100"
                          value={draftValues[def.key] ?? toDraftValue(def, def.defaultValue)}
                          onChange={e => {
                            setDraftValues(prev => ({ ...prev, [def.key]: e.target.value }))
                          }}
                        />
                      ) : (
                        <input
                          type="text"
                          value={formatNumber(draftValues[def.key] ?? def.defaultValue)}
                          onChange={e => {
                            const raw = e.target.value.replace(/\D/g, '')
                            setDraftValues(prev => ({ ...prev, [def.key]: raw }))
                          }}
                        />
                      )}
                      <span className={s.configUnit}>{def.unit}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {hasChanges && (
            <div className={s.saveBar}>
              <div className={s.saveBarText}>
                <i className="fa-solid fa-circle-info" />
                <span>Bạn đang có thay đổi chưa lưu trên các tham số biểu giá cước.</span>
              </div>
              <div className={s.saveBarActions}>
                <button className="btn btn-secondary" onClick={handleResetDraft} disabled={isSaving}>
                  Hủy thay đổi
                </button>
                <button className="btn btn-primary" onClick={handleSaveAllConfigs} disabled={isSaving}>
                  {isSaving ? 'Đang lưu...' : 'Lưu biểu giá'}
                </button>
              </div>
            </div>
          )}
        </>
      )}

      {/* TAB 2: SIMULATOR */}
      {activeTab === 'simulator' && (
        <div className={s.simulatorGrid}>
          {/* Controls */}
          <div className={s.simFormCard}>
            <div className={s.simFormTitle}>
              <i className="fa-solid fa-sliders" /> Thiết Lập Chuyến Đi Thử Nghiệm
            </div>

            <div className={s.simField}>
              <label>Tuyến đường mẫu nhanh</label>
              <select
                onChange={e => {
                  const km = Number(e.target.value)
                  if (km > 0) {
                    setSimKm(km)
                  }
                }}
              >
                <option value="0">Chọn tuyến mẫu hoặc nhập cự ly bên dưới</option>
                <option value="120">Sài Gòn → Long Thành (120 km, 1 ngày)</option>
                <option value="450">Sài Gòn → Nha Trang (450 km, 2 ngày)</option>
                <option value="960">Sài Gòn → Đà Nẵng (960 km, 2 ngày)</option>
                <option value="1750">Sài Gòn → Hà Nội (1.750 km, 3 ngày)</option>
                <option value="260">Sài Gòn → Phnom Penh qua Mộc Bài (260 km)</option>
                <option value="1150">Sài Gòn → Vientiane qua Lao Bảo (1.150 km)</option>
              </select>
            </div>

            <div className={s.simField}>
              <label>Quãng đường vận chuyển (km)</label>
              <input
                type="number"
                min="10"
                max="5000"
                value={simKm}
                onChange={e => setSimKm(Math.max(1, Number(e.target.value)))}
              />
            </div>

            <div className={s.simField}>
              <label>Hạng xe chuyên dụng</label>
              <select
                value={simVehicleClass}
                onChange={e => setSimVehicleClass(e.target.value as 'light' | 'medium' | 'heavy')}
              >
                <option value="light">Light (Xe van / 2 ngăn - Hệ số ×{draftValues['CLASS_FACTOR_LIGHT'] ?? '1.0'})</option>
                <option value="medium">Medium (Xe tải trung / 4-6 ngăn - Hệ số ×{draftValues['CLASS_FACTOR_MEDIUM'] ?? '1.4'})</option>
                <option value="heavy">Heavy (Xe tải lớn / 9 ngăn - Hệ số ×{draftValues['CLASS_FACTOR_HEAVY'] ?? '1.9'})</option>
              </select>
            </div>

            <div className={s.simField}>
              <label>Loại tuyến vận chuyển</label>
              <select
                value={simIsIntl ? 'intl' : 'domestic'}
                onChange={e => setSimIsIntl(e.target.value === 'intl')}
              >
                <option value="domestic">Nội địa Việt Nam (Miễn phí thủ tục)</option>
                <option value="intl">Quốc tế (Lào / Campuchia - Gồm phí hải quan)</option>
              </select>
            </div>

            <div className={s.simField}>
              <label>Khoang vận chuyển</label>
              <select
                value={simStallType}
                onChange={e => setSimStallType(e.target.value as 'standard' | 'single')}
              >
                <option value="standard">Khoang tiêu chuẩn (Đã gồm trong cước)</option>
                <option value="single">Khoang đơn mở rộng (+1.500.000 ₫)</option>
              </select>
            </div>

            <div className={s.simField}>
              <label>Gói thức ăn & dinh dưỡng</label>
              <select
                value={simFeed}
                onChange={e => setSimFeed(e.target.value as 'basic' | 'advanced' | 'sport')}
              >
                <option value="basic">Gói cơ bản (Cỏ thường - 0 ₫)</option>
                <option value="advanced">Gói nâng cao (Timothy + Yến mạch - 300.000 ₫)</option>
                <option value="sport">Gói thể thao (Timothy + Khoáng chất - 500.000 ₫)</option>
              </select>
            </div>

            <div className={s.simField}><label>Phí bảo hiểm thu hộ (VNĐ, nhập theo báo giá bên thứ ba)</label><input className="form-control" type="number" min="0" value={simInsurancePremium} onChange={e => setSimInsurancePremium(Math.max(0, Number(e.target.value)))} /></div>
          </div>

          {/* Results Preview */}
          <div className={s.simResultCard}>
            <div className={s.simResultHeader}>
              <div>
                <h3>
                  <i className="fa-solid fa-receipt" /> Kết Quả Tính Cước Tức Thì
                </h3>
                <p>Mô phỏng chính xác theo đúng công thức nghiệp vụ với các tham số đang soạn thảo.</p>
              </div>
              <div className={s.totalDisplay}>
                <div className={s.totalLabel}>Tổng Giá Trị Báo Giá</div>
                <div className={s.totalValue}>{formatVND(simulatorQuote.total)}</div>
              </div>
            </div>

            <table className={s.simLinesTable}>
              <thead>
                <tr>
                  <th>Khoản mục chi phí</th>
                  <th>Diễn giải chi tiết nghiệp vụ</th>
                  <th style={{ textAlign: 'right' }}>Thành tiền (VND)</th>
                </tr>
              </thead>
              <tbody>
                {simulatorQuote.lines.map((l, i) => (
                  <tr key={i}>
                    <td>
                      <b style={{ color: 'var(--navy)' }}>{l.label}</b>
                    </td>
                    <td style={{ color: 'var(--muted)', fontSize: '0.85rem' }}>{l.detail}</td>
                    <td className={s.lineAmount}>{formatVND(l.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

    </div>
  )
}
