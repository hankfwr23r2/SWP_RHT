import { useState, useMemo } from 'react'
import { usePageData } from '@shared/routing/pageData'
import { systemConfigApi, type SystemConfigItem } from '@shared/api/systemConfig'
import { backendHorseApi, type BackendHorseTypeDto } from '@shared/api/horses'
import { applyRuntimeBusinessConfig } from '@shared/config/runtimeBusinessRules'
import { applyRuntimeHorseTypes } from '@shared/config/booking-rules'
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
    key: 'CREW_FEE_PER_DAY',
    name: 'Phí kíp kỹ thuật / ngày',
    group: 'OPERATIONS',
    unit: '₫/ngày',
    desc: 'Công tác phí tiêu chuẩn cho 01 tài xế chuyên tuyến + 01 hộ tống chăm sóc ngựa',
    defaultValue: '1800000',
  },
  {
    key: 'MARGIN_RATE',
    name: 'Biên lợi nhuận gộp (Margin)',
    group: 'OPERATIONS',
    unit: '%',
    desc: 'Tỷ lệ biên lợi nhuận được gộp đều vào toàn bộ đơn giá báo giá',
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
  {
    key: 'INSURANCE_RATE',
    name: 'Tỷ lệ phí bảo hiểm chuyến đi',
    group: 'OPERATIONS',
    unit: '%',
    desc: 'Tỷ lệ tính phí bảo hiểm trên tổng giá trị định giá của từng giống ngựa',
    defaultValue: '0.02',
    isPercent: true,
  },
]

function formatVND(val: number) {
  return val.toLocaleString('vi-VN') + ' ₫'
}

function formatNumber(val: number | string) {
  const n = Number(val)
  return isNaN(n) ? String(val) : n.toLocaleString('vi-VN')
}

export default function AdminPricingPage() {
  const initialData = usePageData<typeof adminPricingLoader>()
  const toast = useToast()

  const [activeTab, setActiveTab] = useState<'parameters' | 'breeds' | 'simulator'>('parameters')
  const [configsList, setConfigsList] = useState<SystemConfigItem[]>(initialData.configs ?? [])
  const [horseTypesList, setHorseTypesList] = useState<BackendHorseTypeDto[]>(initialData.horseTypes ?? [])

  // Draft configurations dictionary
  const [draftValues, setDraftValues] = useState<Record<string, string>>(() => {
    const map: Record<string, string> = {}
    for (const def of PARAMETER_DEFS) {
      const found = (initialData.configs ?? []).find(c => c.configKey === def.key)
      map[def.key] = found ? found.configValue : def.defaultValue
    }
    return map
  })

  // Has unsaved changes
  const hasChanges = useMemo(() => {
    for (const def of PARAMETER_DEFS) {
      const found = configsList.find(c => c.configKey === def.key)
      const current = found ? found.configValue : def.defaultValue
      if (draftValues[def.key] !== current) return true
    }
    return false
  }, [draftValues, configsList])

  const [isSaving, setIsSaving] = useState(false)

  // Breeds search & modal CRUD
  const [breedSearch, setBreedSearch] = useState('')
  const [breedModalMode, setBreedModalMode] = useState<'create' | 'edit' | null>(null)
  const [selectedBreedForEdit, setSelectedBreedForEdit] = useState<BackendHorseTypeDto | null>(null)
  const [breedFormName, setBreedFormName] = useState('')
  const [breedFormDesc, setBreedFormDesc] = useState('')
  const [editInsuredValStr, setEditInsuredValStr] = useState('')
  const [isUpdatingBreed, setIsUpdatingBreed] = useState(false)

  // Simulator inputs
  const [simKm, setSimKm] = useState(350)
  const [simVehicleClass, setSimVehicleClass] = useState<'light' | 'medium' | 'heavy'>('medium')
  const [simDays, setSimDays] = useState(2)
  const [simIsIntl, setSimIsIntl] = useState(false)
  const [simBreedId, setSimBreedId] = useState<number>(horseTypesList[0]?.id ?? 1)
  const [simStallType, setSimStallType] = useState<'standard' | 'single'>('standard')
  const [simFeed, setSimFeed] = useState<'basic' | 'advanced' | 'sport'>('basic')
  const [simHasInsurance, setSimHasInsurance] = useState(true)

  // Save all system configs
  const handleSaveAllConfigs = async () => {
    setIsSaving(true)
    try {
      const updatedList: SystemConfigItem[] = [...configsList]
      for (const def of PARAMETER_DEFS) {
        const val = draftValues[def.key] ?? def.defaultValue
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
      map[def.key] = found ? found.configValue : def.defaultValue
    }
    setDraftValues(map)
  }

  // Handle open create breed modal
  const handleOpenCreateBreed = () => {
    setSelectedBreedForEdit(null)
    setBreedFormName('')
    setBreedFormDesc('')
    setEditInsuredValStr('400000000')
    setBreedModalMode('create')
  }

  // Handle open edit breed modal
  const handleOpenEditBreed = (breed: BackendHorseTypeDto) => {
    setSelectedBreedForEdit(breed)
    setBreedFormName(breed.typeName)
    setBreedFormDesc(breed.description || '')
    setEditInsuredValStr(String(breed.insuredValue ?? 400000000))
    setBreedModalMode('edit')
  }

  // Handle save create / update breed
  const handleSaveBreedModal = async () => {
    if (!breedFormName.trim()) {
      toast('Vui lòng nhập tên giống ngựa.', 'error')
      return
    }
    const numericVal = Number(editInsuredValStr.replace(/\D/g, ''))
    if (!Number.isFinite(numericVal) || numericVal < 0) {
      toast('Giá trị bảo hiểm không hợp lệ.', 'error')
      return
    }

    setIsUpdatingBreed(true)
    try {
      if (breedModalMode === 'create') {
        const created = await backendHorseApi.createType({
          typeName: breedFormName.trim(),
          description: breedFormDesc.trim(),
          insuredValue: numericVal,
        })
        const nextList = [...horseTypesList, created]
        setHorseTypesList(nextList)
        applyRuntimeHorseTypes(nextList)
        toast(`Đã thêm thành công giống ngựa ${created.typeName}!`, 'success')
      } else if (breedModalMode === 'edit' && selectedBreedForEdit) {
        const updated = await backendHorseApi.updateType(selectedBreedForEdit.id, {
          typeName: breedFormName.trim(),
          description: breedFormDesc.trim(),
          insuredValue: numericVal,
        })
        const nextList = horseTypesList.map(h => (h.id === updated.id ? updated : h))
        setHorseTypesList(nextList)
        applyRuntimeHorseTypes(nextList)
        toast(`Đã cập nhật giống ngựa ${updated.typeName}!`, 'success')
      }
      setBreedModalMode(null)
      setSelectedBreedForEdit(null)
    } catch (e: any) {
      toast(e?.message || 'Có lỗi xảy ra khi lưu giống ngựa.', 'error')
    } finally {
      setIsUpdatingBreed(false)
    }
  }

  // Handle delete breed
  const handleDeleteBreed = async (breed: BackendHorseTypeDto) => {
    const ok = window.confirm(
      `Bạn có chắc chắn muốn xóa giống ngựa "${breed.typeName}"?\n\nLưu ý: Hệ thống sẽ từ chối xóa nếu giống ngựa này đã có cá thể ngựa nào liên kết trong hồ sơ hoặc đơn vận chuyển.`
    )
    if (!ok) return

    try {
      await backendHorseApi.deleteType(breed.id)
      const nextList = horseTypesList.filter(h => h.id !== breed.id)
      setHorseTypesList(nextList)
      applyRuntimeHorseTypes(nextList)
      toast(`Đã xóa giống ngựa "${breed.typeName}" thành công!`, 'success')
    } catch (e: any) {
      toast(e?.message || 'Không thể xóa giống ngựa vì đang có cá thể ngựa liên kết.', 'error')
    }
  }

  // Filtered breeds
  const filteredBreeds = useMemo(() => {
    if (!breedSearch.trim()) return horseTypesList
    const q = breedSearch.toLowerCase()
    return horseTypesList.filter(
      b => b.typeName.toLowerCase().includes(q) || (b.description && b.description.toLowerCase().includes(q))
    )
  }, [horseTypesList, breedSearch])

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

    const crewDaily = Number(draftValues['CREW_FEE_PER_DAY'] ?? 1800000)
    const margin = Number(draftValues['MARGIN_RATE'] ?? 0.05)
    const clearanceIntl = Number(draftValues['CLEARANCE_FEE_INTERNATIONAL'] ?? 300000)
    const insRate = Number(draftValues['INSURANCE_RATE'] ?? 0.02)

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
    const crewCostLine = Math.round((simDays * crewDaily * m) / 1000) * 1000
    const fuelBotLine = Math.round((simKm * 9000 * 1.05 * m) / 1000) * 1000

    const stallCost = simStallType === 'single' ? 1500000 : 0
    const feedCost = simFeed === 'sport' ? 500000 : simFeed === 'advanced' ? 300000 : 0
    const clearanceCost = simIsIntl ? clearanceIntl : 0

    const selectedBreedObj = horseTypesList.find(h => h.id === simBreedId)
    const breedVal = selectedBreedObj?.insuredValue ?? 400000000
    const insuranceCost = simHasInsurance ? Math.round((breedVal * insRate * m) / 1000) * 1000 : 0

    const total = truckCostLine + crewCostLine + fuelBotLine + stallCost + feedCost + clearanceCost + insuranceCost

    return {
      lines: [
        {
          label: 'Cước vận chuyển nguyên chuyến',
          detail: `Xe hạng ${simVehicleClass.toUpperCase()} · ${simKm} km · Phí mở chuyến + 3 bậc km`,
          amount: truckCostLine,
        },
        {
          label: 'Nhân sự kỹ thuật (01 Tài xế + 01 Hộ tống)',
          detail: `${simDays} ngày × ${formatVND(crewDaily)}/ngày (đã gồm Margin ${margin * 100}%)`,
          amount: crewCostLine,
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
                label: `Bảo hiểm chuyến đi (${selectedBreedObj?.typeName ?? 'Ngựa'})`,
                detail: `Định mức ${formatVND(breedVal)} × ${insRate * 100}% tỷ lệ phí`,
                amount: insuranceCost,
              },
            ]
          : []),
      ],
      total,
      breedVal,
    }
  }, [draftValues, simKm, simVehicleClass, simDays, simIsIntl, simBreedId, simStallType, simFeed, simHasInsurance, horseTypesList])

  return (
    <div className={s.wrap}>
      {/* Header */}
      <div className={s.header}>
        <div>
          <h1>
            <i className="fa-solid fa-calculator" /> Biểu Giá & Công Thức Tính Cước
          </h1>
          <p>Quản trị công thức tính cước vận chuyển, các hệ số cấu thành báo giá và giá trị bảo hiểm theo 20 giống ngựa.</p>
        </div>
        <div className={s.headerActions}>
          {hasChanges && (
            <button className="btn btn-secondary" onClick={handleResetDraft} disabled={isSaving}>
              <i className="fa-solid fa-rotate-left" /> Khôi phục
            </button>
          )}
          <button className="btn btn-primary" onClick={handleSaveAllConfigs} disabled={isSaving}>
            <i className="fa-solid fa-floppy-disk" /> {isSaving ? 'Đang lưu...' : 'Lưu tất cả thay đổi'}
          </button>
        </div>
      </div>

      {/* Visual Formula Pipeline Banner */}
      <div className={s.formulaBanner}>
        <div className={s.formulaHeader}>
          <h3>
            <i className="fa-solid fa-network-wired" /> Công Thức Tính Tổng Báo Giá Vận Chuyển
          </h3>
          <span className={s.formulaBadge}>Single Source of Truth</span>
        </div>
        <div className={s.formulaFlow}>
          <div className={s.formulaNode}>
            <span className={s.formulaNodeLabel}>1. Cước Quãng Đường</span>
            <span className={s.formulaNodeDesc}>[Mở chuyến + Bậc km] × Hệ số xe</span>
          </div>
          <span className={s.formulaOp}>+</span>
          <div className={s.formulaNode}>
            <span className={s.formulaNodeLabel}>2. Nhân Sự Chuyên Môn</span>
            <span className={s.formulaNodeDesc}>Số ngày × Phí kíp xe / ngày</span>
          </div>
          <span className={s.formulaOp}>+</span>
          <div className={s.formulaNode}>
            <span className={s.formulaNodeLabel}>3. Nhiên Liệu & BOT</span>
            <span className={s.formulaNodeDesc}>km × 9.000 ₫ × 1.05 (Dự phòng)</span>
          </div>
          <span className={s.formulaOp}>+</span>
          <div className={s.formulaNode}>
            <span className={s.formulaNodeLabel}>4. Dịch Vụ Đi Kèm</span>
            <span className={s.formulaNodeDesc}>Khoang đơn + Dinh dưỡng</span>
          </div>
          <span className={s.formulaOp}>+</span>
          <div className={s.formulaNode}>
            <span className={s.formulaNodeLabel}>5. Bảo Hiểm & Hải Quan</span>
            <span className={s.formulaNodeDesc}>Giá trị giống × Tỷ lệ + Hồ sơ</span>
          </div>
          <span className={s.formulaEqual}>=</span>
          <div className={s.formulaTotalNode}>
            <span className={s.formulaNodeLabel}>Tổng Báo Giá Đơn Hàng</span>
            <span className={s.formulaNodeDesc}>Cộng dồn × (1 + Margin)</span>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className={s.tabs}>
        <button
          className={`${s.tabBtn} ${activeTab === 'parameters' ? s.tabBtnActive : ''}`}
          onClick={() => setActiveTab('parameters')}
        >
          <i className="fa-solid fa-sliders" /> Tham Số Biểu Giá
          <span className={s.tabBadge}>{PARAMETER_DEFS.length}</span>
        </button>
        <button
          className={`${s.tabBtn} ${activeTab === 'breeds' ? s.tabBtnActive : ''}`}
          onClick={() => setActiveTab('breeds')}
        >
          <i className="fa-solid fa-horse-head" /> 20 Giống Ngựa & Bảo Hiểm
          <span className={s.tabBadge}>{horseTypesList.length}</span>
        </button>
        <button
          className={`${s.tabBtn} ${activeTab === 'simulator' ? s.tabBtnActive : ''}`}
          onClick={() => setActiveTab('simulator')}
        >
          <i className="fa-solid fa-wand-magic-sparkles" /> Bộ Thử Nghiệm Tính Cước (Simulator)
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
                        step="0.1"
                        min="0.5"
                        max="5.0"
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
                          step="0.01"
                          min="0"
                          max="1"
                          value={draftValues[def.key] ?? def.defaultValue}
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

      {/* TAB 2: BREEDS */}
      {activeTab === 'breeds' && (
        <div className={s.tableWrap}>
          <div className={s.tableToolbar}>
            <div className={s.searchBox}>
              <i className="fa-solid fa-magnifying-glass" />
              <input
                type="text"
                placeholder="Tìm giống ngựa theo tên..."
                value={breedSearch}
                onChange={e => setBreedSearch(e.target.value)}
              />
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
              <div className={s.tableStats}>
                Hiển thị <b>{filteredBreeds.length}</b> / {horseTypesList.length} giống ngựa
              </div>
              <button className={s.createBreedBtn} onClick={handleOpenCreateBreed}>
                <i className="fa-solid fa-plus" /> Thêm giống ngựa mới
              </button>
            </div>
          </div>

          <table className={s.customTable}>
            <thead>
              <tr>
                <th style={{ width: '60px' }}>ID</th>
                <th>Giống Ngựa</th>
                <th>Mô Tả Tiêu Chuẩn</th>
                <th style={{ width: '220px', textAlign: 'right' }}>Giá Trị Định Mức (VND)</th>
                <th style={{ width: '180px', textAlign: 'right' }}>Phí Bảo Hiểm Ước Tính (2%)</th>
                <th style={{ width: '160px', textAlign: 'center' }}>Thao Tác</th>
              </tr>
            </thead>
            <tbody>
              {filteredBreeds.map(b => {
                const val = b.insuredValue ?? 400000000
                const currentInsRate = Number(draftValues['INSURANCE_RATE'] ?? 0.02)
                const prem = Math.round(val * currentInsRate)
                return (
                  <tr key={b.id}>
                    <td>
                      <span className={s.configKey}>#{b.id}</span>
                    </td>
                    <td>
                      <div className={s.breedBadge}>{b.typeName}</div>
                    </td>
                    <td>
                      <div className={s.breedDesc}>{b.description || 'Chưa có mô tả chi tiết'}</div>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <span className={s.insuredValueCell}>{formatVND(val)}</span>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <span className={s.insurancePremiumCell}>{formatVND(prem)}</span>
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      <div className={s.rowActions}>
                        <button className={s.rowActionBtn} onClick={() => handleOpenEditBreed(b)} title="Chỉnh sửa thông tin giống ngựa">
                          <i className="fa-solid fa-pen-to-square" /> Sửa
                        </button>
                        <button className={s.rowActionBtnDelete} onClick={() => handleDeleteBreed(b)} title="Xóa giống ngựa khỏi danh mục">
                          <i className="fa-solid fa-trash-can" /> Xóa
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })}
              {filteredBreeds.length === 0 && (
                <tr>
                  <td colSpan={6} style={{ textAlign: 'center', padding: '30px', color: 'var(--muted)' }}>
                    Không tìm thấy giống ngựa nào phù hợp với từ khóa "{breedSearch}".
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* TAB 3: SIMULATOR */}
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
                    setSimDays(km > 1000 ? 3 : km > 500 ? 2 : 1)
                  }
                }}
              >
                <option value="0">-- Chọn tuyến đường mẫu hoặc nhập tay bên dưới --</option>
                <option value="120">Sài Gòn - Long Thành (120 km · 1 ngày)</option>
                <option value="450">Sài Gòn - Nha Trang (450 km · 2 ngày)</option>
                <option value="960">Sài Gòn - Đà Nẵng (960 km · 2 ngày)</option>
                <option value="1750">Sài Gòn - Hà Nội (1.750 km · 3 ngày)</option>
                <option value="260">Sài Gòn - Cửa khẩu Mộc Bài - Phnom Penh (260 km)</option>
                <option value="1150">Sài Gòn - Cửa khẩu Lao Bảo - Vientiane (1.150 km)</option>
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
              <label>Thời gian hành trình dự kiến (ngày)</label>
              <input
                type="number"
                min="1"
                max="30"
                value={simDays}
                onChange={e => setSimDays(Math.max(1, Number(e.target.value)))}
              />
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
              <label>Giống ngựa áp tải</label>
              <select value={simBreedId} onChange={e => setSimBreedId(Number(e.target.value))}>
                {horseTypesList.map(h => (
                  <option key={h.id} value={h.id}>
                    {h.typeName} ({formatVND(h.insuredValue ?? 400000000)})
                  </option>
                ))}
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

            <div className={s.simField}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  style={{ width: 'auto' }}
                  checked={simHasInsurance}
                  onChange={e => setSimHasInsurance(e.target.checked)}
                />
                Mua bảo hiểm chuyến đi theo định mức giống
              </label>
            </div>
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

      {/* Modal Create / Edit Breed */}
      {breedModalMode !== null && (
        <div className={s.modalBackdrop} onClick={() => setBreedModalMode(null)}>
          <div className={s.modalCard} onClick={e => e.stopPropagation()}>
            <div className={s.modalHeader}>
              <h3>
                <i className="fa-solid fa-horse-head" />{' '}
                {breedModalMode === 'create' ? 'Thêm Giống Ngựa Mới' : 'Chỉnh Sửa Giống Ngựa & Bảo Hiểm'}
              </h3>
              <button className={s.closeBtn} onClick={() => setBreedModalMode(null)}>
                <i className="fa-solid fa-xmark" />
              </button>
            </div>
            <div className={s.modalBody}>
              <div className={s.modalField}>
                <label>Tên giống ngựa <span style={{ color: '#ef4444' }}>*</span></label>
                <input
                  type="text"
                  placeholder="Ví dụ: Quarter Horse, Andalusian..."
                  value={breedFormName}
                  onChange={e => setBreedFormName(e.target.value)}
                  autoFocus
                />
              </div>
              <div className={s.modalField}>
                <label>Mô tả tiêu chuẩn giống</label>
                <input
                  type="text"
                  placeholder="Ví dụ: Giống ngựa chạy cự ly ngắn siêu tốc, thể hình cơ bắp..."
                  value={breedFormDesc}
                  onChange={e => setBreedFormDesc(e.target.value)}
                />
              </div>
              <div className={s.modalField}>
                <label>Giá trị định mức bảo hiểm quy chuẩn (VNĐ) <span style={{ color: '#ef4444' }}>*</span></label>
                <input
                  type="text"
                  value={formatNumber(editInsuredValStr)}
                  onChange={e => {
                    const raw = e.target.value.replace(/\D/g, '')
                    setEditInsuredValStr(raw)
                  }}
                />
                <div className={s.quickPresets}>
                  <button className={s.presetBtn} onClick={() => setEditInsuredValStr('400000000')}>
                    400 Tr (Tiêu chuẩn)
                  </button>
                  <button className={s.presetBtn} onClick={() => setEditInsuredValStr('600000000')}>
                    600 Tr (Trung bình)
                  </button>
                  <button className={s.presetBtn} onClick={() => setEditInsuredValStr('800000000')}>
                    800 Tr (Cao cấp)
                  </button>
                  <button className={s.presetBtn} onClick={() => setEditInsuredValStr('1000000000')}>
                    1 Tỷ (Thoroughbred)
                  </button>
                </div>
              </div>
              <p style={{ margin: '10px 0 0', fontSize: '0.82rem', color: 'var(--muted)' }}>
                Lưu ý: Mức giá trị này sẽ dùng làm cơ sở tính phí bảo hiểm chuyến đi ({Number(draftValues['INSURANCE_RATE'] ?? 0.02) * 100}%) cho mọi cá thể ngựa thuộc giống này khi khách chọn mua bảo hiểm.
              </p>
            </div>
            <div className={s.modalFooter}>
              <button
                className="btn btn-secondary"
                onClick={() => setBreedModalMode(null)}
                disabled={isUpdatingBreed}
              >
                Hủy
              </button>
              <button
                className="btn btn-primary"
                onClick={handleSaveBreedModal}
                disabled={isUpdatingBreed}
              >
                {isUpdatingBreed ? 'Đang lưu...' : breedModalMode === 'create' ? 'Tạo giống ngựa' : 'Lưu thay đổi'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
