// Coordinator texts shared with crew and manager; dispatch pages are in staffDispatch.ts
export const coordinator = {
  tripWords: {
    type: {
      "PICKUP": "Pickup",
      "LINEHAUL": "Linehaul",
      "DELIVERY": "Delivery",
      "DIRECT": "Direct",
      "TRANSFER": "Transfer"
    },
    status: {
      "PLANNED": "Planned",
      "DISPATCHED": "Dispatched",
      "IN_PROGRESS": "In progress",
      "COMPLETED": "Completed",
      "CANCELLED": "Cancelled"
    },
    role: {
      "DRIVER": "Driver",
      "ESCORT": "Escort"
    },
    choose: "Choose",
    returning: "Returning",
    returnExpectedAt: "Expected back {{time}}",
    returnLate: "Late"
  },
} as const
