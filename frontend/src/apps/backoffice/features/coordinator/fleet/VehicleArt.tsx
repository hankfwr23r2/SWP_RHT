import s from './VehicleArt.module.css'

// Hình xe chuyên dụng chở ngựa (vẽ riêng, đơn giản): thùng xe có số ô cửa bằng số ngăn, đầu xe, bánh xe. Màu theo trạng thái.
export function VehicleArt({ stalls, color }: { stalls: number; color: string }) {
  const n = Math.min(Math.max(stalls, 1), 9)
  const gap = 118 / n
  return (
    <svg className={s.art} viewBox="0 0 230 110" role="img" aria-label={`Xe ${stalls} ngăn`} style={{ width: '100%', height: 'auto' }}>
      <ellipse cx="115" cy="98" rx="104" ry="5" fill="#0f172a" opacity="0.07" />
      <g className={s.body}><rect x="8" y="16" width="146" height="64" rx="9" fill="white" stroke={color} strokeWidth="3" />
      <rect x="8" y="16" width="146" height="14" rx="9" fill={color} opacity="0.18" />
      {Array.from({ length: n }, (_, i) => <rect key={i} x={16 + i * gap + (gap - Math.min(gap - 6, 22)) / 2} y="38" width={Math.min(gap - 6, 22)} height="26" rx="5" fill={color} opacity="0.22" stroke={color} strokeWidth="1.5" />)}
      <path d="M154 34h34c7 0 11 3 15 9l9 13c2 3 3 5 3 9v15h-61z" fill={color} />
      <path d="M162 42h24c3 0 5 1 7 4l6 9h-37z" fill="white" opacity="0.9" />
      </g>
      <rect x="8" y="80" width="208" height="7" rx="3" fill="#cbd5e1" />
      {[42, 96, 184].map(x => <g key={x} className={s.wheel}><circle cx={x} cy="86" r="13" fill="#1e293b" /><circle cx={x} cy="86" r="5.5" fill="#e2e8f0" /><path d={`M${x} 80.5v11M${x - 5.5} 86h11`} stroke="#94a3b8" strokeWidth="1.6" strokeLinecap="round" /></g>)}
    </svg>
  )
}
