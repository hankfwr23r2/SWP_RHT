// Hiệu ứng và tiện ích dùng chung cho các khối trang chủ (công khai và sau đăng nhập)
import { useEffect, type CSSProperties, type RefObject } from 'react'
import { useLocation } from 'react-router'
import type { Photo } from '@shared/config/photos'

// Thư viện hiệu ứng chỉ tải khi có hiệu ứng cần chạy, sau khi trang đã hiện
const loadGsap = () => Promise.all([import('gsap'), import('gsap/MotionPathPlugin'), import('gsap/ScrollTrigger')])
  .then(([{ gsap }, { MotionPathPlugin }, { ScrollTrigger }]) => {
    gsap.registerPlugin(MotionPathPlugin, ScrollTrigger)
    return { gsap, ScrollTrigger }
  })
type Motion = Awaited<ReturnType<typeof loadGsap>>

// Chạy hiệu ứng GSAP trong phạm vi một khối, chỉ tải thư viện khi khối sắp vào màn hình.
// Đổi deps hoặc rời trang thì hoàn nguyên.
export function useGsapEffect(run: (motion: Motion) => void, scope: RefObject<Element | null>, deps: unknown[], enabled = true) {
  useEffect(() => {
    if (!enabled || !scope.current || typeof IntersectionObserver === 'undefined') return
    let alive = true
    let revert = () => {}
    const observer = new IntersectionObserver(entries => {
      if (!entries.some(entry => entry.isIntersecting)) return
      observer.disconnect()
      void loadGsap().then(motion => {
        if (!alive || !scope.current) return
        const context = motion.gsap.context(() => run(motion), scope.current)
        revert = () => context.revert()
      }).catch(() => {})
    }, { rootMargin: '200px' })
    observer.observe(scope.current)
    return () => { alive = false; observer.disconnect(); revert() }
    // oxlint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
}

// Cuộn tới khối ghi trên địa chỉ (/#quy-trinh). Khối tải lười gọi lại hook này để cuộn khi đã hiện ra
export function useHashScroll() {
  const { hash, key } = useLocation()
  useEffect(() => {
    if (hash) document.getElementById(hash.slice(1))?.scrollIntoView({ behavior: 'smooth' })
  }, [hash, key])
}

export const cx = (...c: (string | false | undefined)[]) => c.filter(Boolean).join(' ')

// Trang khách đã đăng nhập dùng lại các khối này: đổi liên kết sang trang trong app khách
export type LinkMap = (to: string) => string
export const publicLinks: LinkMap = to => to
export const portalLinks: LinkMap = to => {
  if (to === '/login' || to.startsWith('/?tab=price')) return '/booking/route'
  if (to.startsWith('/?tab=order')) return '/orders'
  return to.startsWith('/#') ? to.slice(1) : to
}

// Ảnh nền qua biến CSS: điện thoại dùng bản nhỏ
export const photoVars = (photo: Photo) => ({ '--bg': `url('${photo.src}')`, '--bg-sm': `url('${photo.small}')` }) as CSSProperties
