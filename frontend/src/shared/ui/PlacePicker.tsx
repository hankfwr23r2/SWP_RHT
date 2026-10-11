// Chọn điểm đón / giao trên bản đồ: tìm theo tên, bấm điểm trên bản đồ hoặc trong danh sách.
// Chỉ các kho và CLB có trong hệ thống (nhà xe đã có lộ trình tới đó), không nhập địa chỉ tự do.
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useMemo, useRef, useState } from 'react'
import type { BookingLocation } from '../config/network'
import { Modal } from './Modal'
import './PlacePicker.css'

interface Props { id: string; label: string; options: BookingLocation[]; value: string; onChange: (id: string) => void; invalid?: boolean }
const norm = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').toLowerCase()
const icon = (type: BookingLocation['type'], on: boolean) => L.divIcon({ className: 'tt-icon', html: `<span class="pp-pin ${on ? 'pp-on' : ''}"><i class="fa-solid ${type === 'club' ? 'fa-horse-head' : 'fa-warehouse'}"></i></span>`, iconSize: on ? [38, 38] : [30, 30], iconAnchor: on ? [19, 38] : [15, 30] })

function PickModal({ label, options, value, onPick, onClose }: { label: string; options: BookingLocation[]; value: string; onPick: (id: string) => void; onClose: () => void }) {
  const [q, setQ] = useState('')
  const [chosen, setChosen] = useState(value) // điểm đang chọn, chờ khách bấm xác nhận
  const chosenPlace = options.find(o => o.id === chosen)
  const box = useRef<HTMLDivElement>(null)
  const map = useRef<L.Map | null>(null)
  const layer = useRef<L.LayerGroup | null>(null)
  const shown = useMemo(() => options.filter(o => !q.trim() || norm(o.name).includes(norm(q.trim()))), [options, q])

  useEffect(() => {
    if (!box.current) return
    const m = L.map(box.current, { zoomControl: false })
    L.control.zoom({ position: 'topright' }).addTo(m)
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 18, attribution: '© OpenStreetMap' }).addTo(m)
    layer.current = L.layerGroup().addTo(m)
    map.current = m
    m.setView([options[0]?.lat ?? 10.8, options[0]?.lng ?? 106.7], 9)
    const timers = [60, 350].map(ms => setTimeout(() => m.invalidateSize(), ms))
    return () => { timers.forEach(clearTimeout); m.remove(); map.current = null; layer.current = null }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    const g = layer.current, m = map.current
    if (!g || !m) return
    g.clearLayers()
    shown.forEach(o => {
      L.marker([o.lat, o.lng], { icon: icon(o.type, o.id === chosen), title: o.name, zIndexOffset: o.id === chosen ? 500 : 0 })
        .bindTooltip(o.name.split(' — ')[0], { direction: 'top', offset: [0, -28] }).on('click', () => setChosen(o.id)).addTo(g)
    })
    if (shown.length) m.flyToBounds(L.latLngBounds(shown.map(o => [o.lat, o.lng] as [number, number])), { padding: [60, 60], maxZoom: 11, duration: 0.5 })
  }, [shown, chosen]) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <Modal wide title={label} subtitle="Tìm theo tên, bấm vào điểm trên bản đồ hoặc trong danh sách, rồi bấm Xác nhận."  onClose={onClose}
      footer={<>
        <span style={{ flex: 1, minWidth: 0, color: 'var(--muted)', fontSize: '0.88rem' }}>{chosenPlace ? <>Đang chọn: <b style={{ color: 'var(--navy)' }}>{chosenPlace.name}</b></> : 'Chưa chọn điểm nào.'}</span>
        <button className="btn btn-ghost" onClick={onClose}>Hủy</button>
        <button className="btn btn-primary" disabled={!chosen} onClick={() => onPick(chosen)}><i className="fa-solid fa-check" /> Xác nhận điểm này</button>
      </>}>
      <div className="pp-body">
        <div className="pp-side">
          <div className="pp-search"><i className="fa-solid fa-magnifying-glass" aria-hidden="true" /><input autoFocus aria-label="Tìm điểm" placeholder="Tìm kho, CLB, tỉnh…" value={q} onChange={e => setQ(e.target.value)} /></div>
          <div className="pp-list" role="listbox" aria-label="Danh sách điểm">
            {shown.map(o => (
              <button key={o.id} type="button" role="option" aria-selected={o.id === chosen} className={`pp-item ${o.id === chosen ? 'on' : ''}`} onClick={() => setChosen(o.id)}>
                <b>{o.name.split(' — ')[0]}</b><small>{o.name.includes(' — ') ? o.name.split(' — ')[1] : o.type === 'club' ? 'Câu lạc bộ' : 'Kho / trang trại'}</small>
              </button>
            ))}
            {!shown.length && <div className="pp-empty">Không có điểm nào khớp “{q}”.</div>}
          </div>
        </div>
        <div ref={box} className="pp-map" role="application" aria-label="Bản đồ chọn điểm" />
      </div>
    </Modal>
  )
}

export function PlacePicker({ id, label, options, value, onChange, invalid }: Props) {
  const [open, setOpen] = useState(false)
  const cur = options.find(o => o.id === value)
  return (
    <>
      <button id={id} type="button" className={`pp-btn ${invalid ? 'invalid' : ''}`} onClick={() => setOpen(true)} aria-haspopup="dialog">
        <i className="fa-solid fa-location-dot" aria-hidden="true" />
        {cur ? <span>{cur.name}</span> : <span className="pp-ph">Chọn trên bản đồ</span>}
        <small>{cur ? 'Đổi' : 'Chọn'}</small>
      </button>
      {open && <PickModal label={label} options={options} value={value} onPick={v => { onChange(v); setOpen(false) }} onClose={() => setOpen(false)} />}
    </>
  )
}
