// Service sự cố. Sau này: GET/PATCH /api/incidents
import { createStore } from './store'
import { seedIncidents, type Incident } from './mock/incidents'

const store = createStore<Incident>('incidents', seedIncidents)

export const incidentsApi = {
  list: async (): Promise<Incident[]> => structuredClone(store.all()),
  update: async (id: string, patch: Partial<Incident>) => structuredClone(store.update(id, patch)),
  create: async (data: Omit<Incident, 'id'>) => {
    const next = Math.max(0, ...store.all().map(i => Number(i.id.slice(4)))) + 1
    return structuredClone(store.add({ id: `INC-${String(next).padStart(3, '0')}`, ...data }))
  },
}
export type { Incident }
