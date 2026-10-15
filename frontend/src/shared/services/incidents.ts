// Service sự cố kết nối Backend Spring Boot (/incidents)
import { backendIncidentApi } from '../api/incidents'
import { createStore } from './store'
import { seedIncidents, type Incident } from './mock/incidents'

const store = createStore<Incident>('incidents', seedIncidents)

export const incidentsApi = {
  list: async (): Promise<Incident[]> => {
    if (typeof window !== 'undefined') {
      try {
        const beList = await backendIncidentApi.list()
        if (beList && beList.length > 0) {
          return beList.map(item => ({
            id: item.formattedId || `INC-${String(item.id).padStart(3, '0')}`,
            tripId: `TR-${String(item.bookingId).padStart(4, '0')}`,
            orderId: item.bookingCode || `EQ-2026-${String(item.bookingId).padStart(4, '0')}`,
            leg: '1/1',
            time: item.createdAt ? new Date(item.createdAt).getTime() : Date.now(),
            severity: 'emergency' as const,
            type: item.incidentType || 'Y tế ngựa',
            desc: item.description || item.incidentName || '',
            status: (item.status === 'APPROVED' ? 'approved' : item.status === 'PROPOSED' ? 'proposed' : item.status === 'REJECTED' ? 'rejected' : 'open') as Incident['status'],
            proposal: item.directive || '',
            report: item.description,
            cost: item.estimatedCost || 0,
            bearer: (item.costBearer?.toLowerCase() === 'customer' ? 'customer' : 'company') as 'company' | 'customer',
            directive: item.directive,
          }))
        }
      } catch {
        // Fallback store khi offline
      }
    }
    return structuredClone(store.all())
  },

  update: async (id: string, patch: Partial<Incident>) => structuredClone(store.update(id, patch)),

  create: async (data: Omit<Incident, 'id'>) => {
    if (typeof window !== 'undefined') {
      try {
        const numBookingId = Number(data.orderId.replace(/\D/g, '')) || 1
        const res = await backendIncidentApi.report({
          bookingId: numBookingId,
          reporter: 'Điều phối viên',
          incidentName: (data.desc || '').slice(0, 50) || 'Sự cố hành trình',
          incidentType: data.type,
          description: data.desc || '',
          photoUrl: '',
        })
        const created: Incident = {
          id: res.formattedId || `INC-${String(res.id).padStart(3, '0')}`,
          ...data,
        }
        store.add(created)
        return structuredClone(created)
      } catch {
        // Fallback store
      }
    }
    const next = Math.max(0, ...store.all().map(i => Number(i.id.slice(4)))) + 1
    return structuredClone(store.add({ id: `INC-${String(next).padStart(3, '0')}`, ...data }))
  },
}

export type { Incident }

