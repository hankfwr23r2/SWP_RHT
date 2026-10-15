export interface Vehicle {
  id: string
  name: string
  type: string
  capacity: number // số ngăn
  plate: string
  vin?: string // số khung
  inspectionNo?: string // số giấy đăng kiểm
  transitPermit?: string // giấy phép liên vận CLV
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
