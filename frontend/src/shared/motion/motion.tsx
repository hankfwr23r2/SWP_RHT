// Hiệu ứng chuyển động dùng chung (GSAP). Quy tắc:
// - Trang giới thiệu: hiệu ứng rõ nét. Trang làm việc: nhanh (≤ 0,4 giây) và tinh tế, không làm chậm thao tác.
// - Người dùng bật "giảm chuyển động" trong hệ điều hành thì bỏ hết hiệu ứng.
// - Không tạo hiệu ứng cho số tiền (dễ đọc nhầm giá trị).
// - Luôn dùng fromTo() với điểm đích ghi rõ, KHÔNG dùng from(): React StrictMode chạy hiệu ứng 2 lần,
//   from() lần 2 sẽ lấy trạng thái "đang ẩn" của lần 1 làm đích và phần tử bị kẹt ở trạng thái ẩn.
import { useRef, type ReactNode } from 'react'
import { useLocation } from 'react-router'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { useGSAP } from '@gsap/react'

gsap.registerPlugin(ScrollTrigger, useGSAP)

// Lần đầu vào trang (chưa có cache), font và ảnh tải xong làm bố cục xê dịch:
// tính lại vị trí kích hoạt để hiệu ứng cuộn chạy đúng chỗ.
window.addEventListener('load', () => ScrollTrigger.refresh())
document.fonts?.ready.then(() => ScrollTrigger.refresh())

export const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

// Nội dung trang trượt nhẹ lên khi đổi trang
export function PageTransition({ children }: { children: ReactNode }) {
  const { pathname } = useLocation()
  const ref = useRef<HTMLDivElement>(null)
  useGSAP(() => {
    if (reducedMotion()) return
    gsap.fromTo(ref.current, { opacity: 0, y: 14 }, { opacity: 1, y: 0, duration: 0.35, ease: 'power2.out', clearProps: 'transform' })
  }, { dependencies: [pathname] })
  return <div ref={ref}>{children}</div>
}

// Các phần tử khớp selector hiện lần lượt khi xuất hiện lần đầu (thẻ, dòng bảng…)
export function useStaggerIn(selector: string, deps: unknown[] = []) {
  const scope = useRef<HTMLDivElement>(null)
  useGSAP(() => {
    if (reducedMotion()) return
    const items = scope.current?.querySelectorAll(selector)
    if (!items?.length) return
    gsap.fromTo(items, { opacity: 0, y: 12 }, { opacity: 1, y: 0, duration: 0.35, stagger: 0.04, ease: 'power2.out', clearProps: 'transform,opacity' })
  }, { scope, dependencies: deps })
  return scope
}

// Hiện dần khi cuộn tới (trang giới thiệu). Phần tử có data-delay (ms) sẽ trễ tương ứng.
export function useScrollReveal(selector: string, deps: unknown[] = []) {
  const scope = useRef<HTMLDivElement>(null)
  useGSAP(() => {
    const items = gsap.utils.toArray<HTMLElement>(selector, scope.current)
    if (reducedMotion()) { gsap.set(items, { opacity: 1, y: 0 }); return }
    items.forEach(el => {
      gsap.fromTo(el, { opacity: 0, y: 40 }, {
        opacity: 1, y: 0, duration: 0.8, ease: 'power3.out', delay: Number(el.dataset.delay ?? 0) / 1000,
        scrollTrigger: { trigger: el, start: 'top 88%', once: true },
      })
    })
  }, { scope, dependencies: deps })
  return scope
}

// Số đếm lên khi cuộn tới (dùng cho số liệu giới thiệu, không dùng cho tiền)
export function CountUp({ to, suffix = '' }: { to: number; suffix?: string }) {
  const ref = useRef<HTMLSpanElement>(null)
  useGSAP(() => {
    if (!ref.current || reducedMotion()) return
    const obj = { v: 0 }
    gsap.to(obj, {
      v: to, duration: 1.4, ease: 'power2.out',
      scrollTrigger: { trigger: ref.current, start: 'top bottom', once: true },
      onUpdate: () => { if (ref.current) ref.current.textContent = `${Math.round(obj.v)}${suffix}` },
    })
  }, { dependencies: [to] })
  return <span ref={ref}>{to}{suffix}</span>
}
