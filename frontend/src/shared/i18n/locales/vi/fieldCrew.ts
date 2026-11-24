// Ứng dụng hiện trường của tài xế và hộ tống
export const fieldCrew = {
  myTrips: {
    "run": {
      "stayIn": {
        "title": "Gửi chuồng qua đêm",
        "hint": "Các con đang trên xe được gửi cho chuồng đối tác qua đêm. Sáng hôm sau bấm Nhận lại từ chuồng rồi mới rời trạm.",
        "partner": "Chuồng gửi",
        "choose": "Chọn chuồng"
      },
      "papers": {
        "record": "Ảnh biên bản đã ký",
        "upload": "Tải biên bản",
        "recordSaved": "Đã lưu biên bản",
        "recordAttached": "Đã có biên bản",
        "needRecord": "Chụp và tải biên bản đã ký trước khi hoàn tất.",
        "originals": "Giấy tờ gốc",
        "receive": "Đã nhận bản gốc",
        "return": "Đã trả bản gốc",
        "RECEIVED": "Đã nhận bản gốc",
        "RETURNED": "Đã trả bản gốc"
      },
      "stall": "Khoang số {{n}}",
      "agentParty": "Đại lý: {{name}}",
      "unpaidReturn": {
        "action": "Trả ngựa do quá hạn thanh toán",
        "title": "Trả lại ngựa đã nhận?",
        "message": "Khách chưa trả số dư khi đã quá thời gian chờ. Mọi con đã nhận ở điểm này được trả lại (cho người gửi, hoặc cho đại lý ở cửa khẩu) và đơn bị hủy. Nếu khách còn đang thanh toán qua VNPAY, hệ thống sẽ từ chối và bạn chờ thêm.",
        "done": "Đã trả ngựa và hủy đơn"
      },
      "refusal": {
        "refuse": "Không nhận",
        "title": "Không nhận {{name}}?",
        "message": "Con này bị loại khỏi đơn, chỗ trên chuyến được nhả và báo giá được tính lại cho các con còn lại. Quản lý được báo. Các con khác vẫn nhận bình thường.",
        "reason": "Lý do không nhận",
        "done": "Đã ghi không nhận ngựa"
      },
      "delivery": {
        "startWaiting": "Bắt đầu chờ người nhận",
        "waitingSince": "Đang chờ người nhận từ {{time}}",
        "noRecipient": "Không gặp người nhận",
        "noRecipientTitle": "Ghi nhận không gặp người nhận?",
        "noRecipientMessage": "Ngựa chưa giao được, chặng giao bị đánh dấu thất bại và điều phối được báo để giao lại. Chỉ làm được sau khi hết giờ chờ miễn phí.",
        "waitStarted": "Đã bắt đầu chờ người nhận",
        "failed": "Đã ghi nhận không gặp người nhận"
      },
      "incident": {
        "report": "Báo sự cố",
        "reportTitle": "Báo sự cố",
        "cause": "Nguyên nhân",
        "scope": "Ảnh hưởng",
        "wholeTrip": "Cả xe",
        "incidentTitle": "Tiêu đề",
        "incidentDescription": "Mô tả",
        "send": "Gửi báo cáo",
        "reported": "Đã báo sự cố",
        "confirmFit": "Đủ sức đi tiếp",
        "fitDone": "Đã xác nhận đủ sức đi tiếp",
        "addCost": "Ghi chi phí",
        "costDescription": "Nội dung",
        "amount": "Số tiền (VND)",
        "consulted": "Đã hỏi ý kiến khách",
        "costSaved": "Đã ghi chi phí"
      },
      "earlyReason": "Lý do rời sớm (nếu chưa nghỉ đủ)",
      "health": {
        "title": "Ghi sức khỏe tại điểm này",
        "status": "Sức khỏe",
        "water": "Uống nước",
        "feed": "Ăn",
        "temperature": "Nhiệt độ (°C)",
        "save": "Lưu",
        "logged": "Đã ghi",
        "notLogged": "Chưa ghi",
        "saved": "Đã ghi sức khỏe",
        "statusValue": {
          "NORMAL": "Bình thường",
          "WATCH": "Theo dõi",
          "SICK": "Ốm",
          "CRITICAL": "Nguy kịch"
        },
        "intakeValue": {
          "NORMAL": "Bình thường",
          "REDUCED": "Ít",
          "REFUSED": "Bỏ"
        }
      },
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
        "RETURN": "Trả ngựa cho người gửi",
        "AGENT": "Bàn giao với đại lý",
        "SHIFT": "Nhận bàn giao ca",
        "STAY_IN": "Gửi chuồng qua đêm",
        "STAY_OUT": "Nhận lại từ chuồng",
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
