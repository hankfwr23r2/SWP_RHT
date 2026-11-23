// Ứng dụng hiện trường của tài xế và hộ tống
export const fieldCrew = {
  myTrips: {
    "run": {
      "open": "Mở lượt xe",
      "back": "Lượt xe của tôi",
      "loadError": "Không tải được lượt xe",
      "start": "Xuất phát",
      "complete": "Kết thúc lượt",
      "stops": "Điểm dừng",
      "stopState": {
        "waiting": "Chưa tới",
        "here": "Đang ở đây",
        "left": "Đã rời"
      },
      "arrive": "Tới nơi",
      "depart": "Rời đi",
      "handover": {
        "PICKUP": "Nhận ngựa",
        "DELIVERY": "Giao ngựa",
        "HUB_IN": "Giao vào hub",
        "HUB_OUT": "Nhận từ hub"
      },
      "handoverStatus": {
        "PENDING": "Đang giao nhận",
        "DONE": "Đã xong",
        "TIMEOUT": "Hết giờ chờ",
        "FAILED": "Không thành"
      },
      "chip": "Chip của {{name}}",
      "scan": "Quét",
      "matched": "Đúng chip",
      "mismatched": "Sai chip",
      "notScanned": "Chưa quét",
      "completeHandover": "Hoàn tất giao nhận",
      "done": {
        "started": "Xe đã xuất phát",
        "arrived": "Đã tới điểm dừng",
        "departed": "Đã rời điểm dừng",
        "opened": "Đã mở giao nhận",
        "scanned": "Đã quét chip",
        "handed": "Đã hoàn tất giao nhận",
        "completed": "Đã kết thúc lượt xe"
      }
    },
    "title": "Lượt xe của tôi",
    "description": "Lượt xe được phân công. Xác nhận nhận việc để điều phối biết bạn đã sẵn sàng.",
    "loadError": "Không tải được lượt xe",
    "emptyTitle": "Chưa có lượt xe nào",
    "emptyDescription": "Khi điều phối phân công, lượt xe sẽ hiện ở đây.",
    "acknowledge": "Xác nhận nhận việc",
    "acknowledged": "Đã nhận việc",
    "waiting": "Chờ bạn xác nhận",
    "journey": "{{from}} → {{to}}",
    "vehicle": "Xe {{plate}}",
    "start": "Bắt đầu {{time}}",
    "done": "Đã xác nhận nhận việc"
  },
  fieldCrew: {
    tabJob: 'Việc',
    tabJourney: 'Hành trình',
    tabManifest: 'Lệnh',
    noTrips: 'Bạn chưa có chuyến nào cần xử lý.',
    journeyMilestones: 'Các mốc hành trình',
    driverVehicleCheck: 'Xe đã kiểm tra: dầu, lốp, điều hòa thùng, máy phát điện phụ',
    escortKitTitle: 'Chuẩn bị trang thiết bị hộ tống',
    arrivePickup: 'Xác nhận có mặt tại điểm đón',
    arriveCheckpoint: 'Xác nhận có mặt tại mốc',
    arriveHint: 'Chụp ảnh tại chỗ để hệ thống và điều phối viên ghi nhận',
    takePhoto: 'Ảnh chụp tại chỗ',
    escortScanMicrochip: 'Hộ tống quét microchip',
    scannedCount: 'Đã quét {{scanned}}/{{total}} ngựa.',
    unfitHorseAlert: 'Đã loại bỏ {{count}} cá thể ngựa do ốm/sốt ({{horses}}). Chi phí đã được giảm trừ cho khách.',
    collectOriginalDocs: 'Thu chứng từ gốc',
    collectOriginalDocsHint: 'Kiểm tra và nhận đủ bản gốc từ người gửi. Tick từng giấy khi đã nhận.',
    startTrip: 'Bắt đầu hành trình',
    finishTrip: 'Hoàn tất bàn giao',
    reportSos: 'Báo cáo sự cố khẩn cấp (SOS)',
  },
} as const
