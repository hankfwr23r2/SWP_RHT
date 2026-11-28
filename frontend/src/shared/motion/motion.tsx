// Hiệu ứng chuyển động dùng chung, chạy bằng Web Animations của trình duyệt để không thêm thư viện vào gói tải đầu. Quy tắc:
// - Trang giới thiệu: hiệu ứng rõ nét. Trang làm việc: nhanh (≤ 0,4 giây) và tinh tế, không làm chậm thao tác.
// - Người dùng bật "giảm chuyển động" trong hệ điều hành thì bỏ hết hiệu ứng.
// - Không tạo hiệu ứng cho số tiền (dễ đọc nhầm giá trị).
// - Nội dung trong màn hình lúc mở trang hiện ngay, không chờ hiệu ứng.
import { useEffect, useRef, type ReactNode } from 'react'
import { useLocation } from 'react-router'

export const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

const EASE_OUT = 'cubic-bezier(0.22, 1, 0.36, 1)'

function riseIn(el: Element, distance: number, duration: number, delay = 0) {
  if (typeof el.animate !== 'function') return
  el.animate(
    [{ opacity: 0, transform: `translateY(${distance}px)` }, { opacity: 1, transform: 'none' }],
    { duration, delay, easing: EASE_OUT, fill: 'backwards' },
  )
}

// Nội dung trang trượt nhẹ lên khi đổi trang
export function PageTransition({ children }: { children: ReactNode }) {
  const { pathname } = useLocation()
  const ref = useRef<HTMLDivElement>(null)
  const shown = useRef(pathname)
  useEffect(() => {
    if (shown.current === pathname) return
    shown.current = pathname
    if (ref.current && !reducedMotion()) riseIn(ref.current, 14, 350)
  }, [pathname])
  return <div ref={ref}>{children}</div>
}

// Các phần tử khớp selector hiện lần lượt khi xuất hiện lần đầu (thẻ, dòng bảng…). deps: lúc chạy lại, do nơi gọi quyết định
export function useStaggerIn(selector: string, deps: unknown[] = []) {
  const scope = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (reducedMotion()) return
    scope.current?.querySelectorAll(selector).forEach((el, i) => riseIn(el, 12, 350, Math.min(i, 12) * 40))
  }, deps)
  return scope
}

// Hiện dần khi cuộn tới (trang giới thiệu). Phần tử có data-delay (ms) sẽ trễ tương ứng.
export function useScrollReveal(selector: string, deps: unknown[] = []) {
  const scope = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (reducedMotion() || typeof IntersectionObserver === 'undefined') return
    const below = [...scope.current?.querySelectorAll<HTMLElement>(selector) ?? []]
      .filter(el => el.getBoundingClientRect().top > window.innerHeight * 0.88)
    const observer = new IntersectionObserver(entries => {
      for (const { isIntersecting, target } of entries) {
        if (!isIntersecting) continue
        observer.unobserve(target)
        const el = target as HTMLElement
        el.style.opacity = ''
        riseIn(el, 40, 800, Number(el.dataset.delay ?? 0))
      }
    }, { rootMargin: '0px 0px -12% 0px' })
    below.forEach(el => { el.style.opacity = '0'; observer.observe(el) })
    return () => { observer.disconnect(); below.forEach(el => { el.style.opacity = '' }) }
  }, deps)
  return scope
}
