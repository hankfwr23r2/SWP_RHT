export interface Vehicle {
  id: string
  name: string
  type: string
  capacity: number // số ngăn
  plate: string
  status?: string // AVAILABLE, MAINTENANCE, BUSY
}

export interface CrewMember {
  id: string
  name: string
  role: 'driver' | 'escort'
  phone: string
  note?: string
  idNumber?: string
  license?: string
}
