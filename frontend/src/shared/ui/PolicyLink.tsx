// Liên kết mở trang điều khoản / chính sách ở tab mới, để khách không mất dở dang việc đang làm.
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { POLICY_DOCS, type PolicyDocId } from '@shared/config/business-rules'

export function PolicyLink({ doc, children, className }: { doc: PolicyDocId; children?: ReactNode; className?: string }) {
  return <Link to={`/terms?doc=${doc}`} target="_blank" rel="noopener" className={className ?? 'text-orange font-semibold'}>{children ?? POLICY_DOCS[doc].title}</Link>
}
