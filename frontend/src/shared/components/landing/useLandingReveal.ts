import { useScrollReveal } from '@shared/motion/motion'
import s from './Landing.module.css'

// Hiệu ứng hiện dần khi cuộn cho các khối trang chủ
export const reveal = s.reveal
export const useLandingReveal = () => useScrollReveal(`.${s.reveal}`)
