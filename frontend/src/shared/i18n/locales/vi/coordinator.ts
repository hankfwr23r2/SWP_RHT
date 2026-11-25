// Trang của điều phối viên. Chữ của các trang điều độ ở staffDispatch.ts; ở đây chỉ giữ chữ tài xế và quản lý cùng dùng
export const coordinator = {
  tripWords: {
    type: {
      "PICKUP": "Đón",
      "LINEHAUL": "Trục",
      "DELIVERY": "Giao",
      "DIRECT": "Chạy thẳng",
      "TRANSFER": "Chuyển xe"
    },
    status: {
      "PLANNED": "Đã lập",
      "DISPATCHED": "Đã điều",
      "IN_PROGRESS": "Đang chạy",
      "COMPLETED": "Hoàn thành",
      "CANCELLED": "Đã hủy"
    },
    role: {
      "DRIVER": "Tài xế",
      "ESCORT": "Hộ tống"
    },
    choose: "Chọn"
  },
} as const
