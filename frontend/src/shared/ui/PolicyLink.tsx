// Liên kết điều khoản / chính sách mở cùng tab để giữ phiên đăng nhập trong sessionStorage.
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { POLICY_DOCS, type PolicyDocId } from '@shared/config/business-rules'

export function PolicyLink({ doc, children, className }: { doc: PolicyDocId; children?: ReactNode; className?: string }) {
  return <Link to={`/terms?doc=${doc}`} className={className ?? 'text-orange font-semibold'}>{children ?? POLICY_DOCS[doc].title}</Link>
}
