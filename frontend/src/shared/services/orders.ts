// Service đơn hàng. Tên hàm theo REST để sau này thay bằng API Spring Boot:
//   list() → GET /api/orders · get(id) → GET /api/orders/{id} · update(id, patch) → PATCH /api/orders/{id}
import { acceptanceDeadline, paymentDeadline } from '../lib/deadlines'
import { formatDateTime } from '../lib/format'
import type { Order } from '../types/order'
import { seedOrders } from './mock/orders'
import { seedOtherOrders } from './mock/orders-others'
import { createStore } from './store'

// Bộ đơn chung: khách mẫu Trang trại Long Thành + các khách khác
const store = createStore<Order>('orders', () => [...seedOrders(), ...seedOtherOrders()])

// Quy tắc hệ thống chạy mỗi lần đọc đơn (docs/PRD.md mục 3):
// - quá hạn thanh toán → đơn tự hủy, nhả chỗ xe giữ tạm
// - giao xong quá 24 giờ chưa phản hồi → tự động nghiệm thu
// Khi có backend, việc này do job phía server làm.
function applySystemRules() {
  const now = Date.now()
  store.all().forEach(o => {
    if (o.status === 'awaiting_payment' && o.approvedAt && now > paymentDeadline(o.approvedAt, o.departAt)) {
      store.update(o.id, { status: 'cancelled', reason: `Quá hạn thanh toán (hạn ${formatDateTime(paymentDeadline(o.approvedAt, o.departAt))}).` })
    }
    if (o.status === 'delivered' && o.deliveredAt && now > acceptanceDeadline(o.deliveredAt)) {
      store.update(o.id, { status: 'completed', acceptedAt: acceptanceDeadline(o.deliveredAt), acceptedBy: 'auto' })
    }
  })
}

// Bỏ trường nội bộ trước khi đưa sang app khách (khi có backend, API phía khách tự làm việc này)
const INTERNAL_KEYS = ['stage', 'inspector', 'coordinator', 'intakeAt', 'hold', 'warning', 'waitingCustomer', 'pending', 'rejectType', 'report', 'rechecked', 'review', 'task', 'papersReport', 'verification', 'recheckRequest', 'infeasible'] as const
export type CustomerOrderView = Omit<Order, (typeof INTERNAL_KEYS)[number]>
const toCustomerView = (o: Order): CustomerOrderView => {
  const view = structuredClone(o) as Partial<Order>
  INTERNAL_KEYS.forEach(k => delete view[k])
  return view as CustomerOrderView
}

// Dành cho app khách: chỉ đơn của khách đang đăng nhập, không kèm trường nội bộ
export const customerOrdersApi = {
  list: async (customer: string): Promise<CustomerOrderView[]> => { applySystemRules(); return store.all().filter(o => o.customer === customer).map(toCustomerView) },
  get: async (customer: string, id: string): Promise<CustomerOrderView | undefined> => {
    applySystemRules()
    const o = store.get(id)
    return o && o.customer === customer ? toCustomerView(o) : undefined
  },
  update: async (customer: string, id: string, patch: Partial<CustomerOrderView>): Promise<CustomerOrderView> => {
    if (store.get(id)?.customer !== customer) throw new Error('Không có quyền với đơn này')
    return toCustomerView(store.update(id, patch))
  },
}

// Dành cho app nội bộ
export const ordersApi = {
  list: async (): Promise<Order[]> => { applySystemRules(); return structuredClone(store.all()) },
  get: async (id: string): Promise<Order | undefined> => { applySystemRules(); return structuredClone(store.get(id)) },
  update: async (id: string, patch: Partial<Order>): Promise<Order> => structuredClone(store.update(id, patch)),
}
