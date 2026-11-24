import { useNavigate } from 'react-router'
import { Button } from '@shared/ui/Button'

// Nút quay về danh sách ở đầu trang chi tiết
export function BackButton({ to, label }: { to: string; label: string }) {
  const navigate = useNavigate()
  return <div><Button size="sm" variant="ghost" icon={<i className="fa-solid fa-arrow-left" />} onClick={() => navigate(to)}>{label}</Button></div>
}
