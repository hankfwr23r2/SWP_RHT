// Cờ 3 nước (SVG), chuyển nguyên từ home.js
import type { CountryCode } from '../config/network'

export const FLAG_SVG: Record<CountryCode, string> = {
    VN: `<svg viewBox="0 0 30 20" preserveAspectRatio="xMidYMid slice"><rect width="30" height="20" fill="#da251d"/><polygon points="15,4 16.18,7.6 19.96,7.6 16.9,9.8 18.07,13.4 15,11.2 11.93,13.4 13.1,9.8 10.04,7.6 13.82,7.6" fill="#ffff00"/></svg>`,
    LA: `<svg viewBox="0 0 30 20" preserveAspectRatio="xMidYMid slice"><rect width="30" height="20" fill="#ce1126"/><rect y="5" width="30" height="10" fill="#002868"/><circle cx="15" cy="10" r="4" fill="#fff"/></svg>`,
    KH: `<svg viewBox="0 0 30 20" preserveAspectRatio="xMidYMid slice"><rect width="30" height="20" fill="#032ea1"/><rect y="5" width="30" height="10" fill="#e00025"/>
        <g fill="#fff" stroke="#000" stroke-width="0.15"><rect x="8.6" y="13" width="12.8" height="1.1"/><rect x="9.6" y="11" width="10.8" height="2"/>
        <polygon points="15,5.6 16.2,8.2 16.2,11 13.8,11 13.8,8.2"/><polygon points="12.2,7.6 13.1,9.3 13.1,11 11.3,11 11.3,9.3"/><polygon points="17.8,7.6 18.7,9.3 18.7,11 16.9,11 16.9,9.3"/>
        <polygon points="10.2,8.9 10.8,10 10.8,11 9.6,11 9.6,10"/><polygon points="19.8,8.9 20.4,10 20.4,11 19.2,11 19.2,10"/></g></svg>`
}
