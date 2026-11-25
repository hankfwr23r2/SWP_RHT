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
        "message": "Xác nhận có người nhận lại ngựa và giấy tờ gốc tại nơi đón. Đơn chuyển sang Cần chọn lại chuyến. Nếu VNPAY đang xác nhận giao dịch, hãy chờ thêm.",
        "done": "Đã trả ngựa và mở lại lựa chọn lịch"
      },
      "refusal": {
        "reasonCode": "Nguyên nhân",
        "senderContact": "Kết quả gọi người gửi",
        "customerContact": "Kết quả gọi khách",
        "emergencyContact": "Kết quả gọi liên hệ khẩn cấp",
        "photo": "Ảnh tại điểm đón",
        "reasons": {
          "SENDER_ABSENT": "Người gửi vắng",
          "CHIP_MISMATCH": "Sai chip",
          "ORIGINAL_DOCUMENTS_MISSING": "Thiếu giấy gốc",
          "HORSE_UNWELL": "Ngựa ốm",
          "HORSE_WILL_NOT_BOARD": "Ngựa không lên xe",
          "VEHICLE_FAILURE": "Xe hỏng",
          "UNSUITABLE_STALL": "Khoang không phù hợp",
          "LATE_ARRIVAL": "Xe tới trễ",
          "CREW_SHORTAGE": "Thiếu tổ lái",
          "FLOOD": "Bão lũ",
          "ROAD_CLOSURE": "Cấm đường",
        },
        "refuse": "Ghi đón hụt",
        "title": "Ghi đón hụt cho {{name}}?",
        "message": "Ghi lại lý do. Ngựa vẫn trong đơn và khách sẽ chọn cách xử lý.",
        "reason": "Lý do không nhận",
        "done": "Đã ghi đón hụt"
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
        "cause": "Nguyên nhân đề xuất",
        "scope": "Ảnh hưởng",
        "wholeTrip": "Cả xe",
        "incidentTitle": "Tiêu đề",
        "incidentDescription": "Mô tả",
        "send": "Gửi báo cáo",
        "reported": "Đã báo sự cố",
        "startRest": "Bắt đầu cho ngựa nghỉ",
        "startRestTitle": "Bắt đầu tính giờ nghỉ?",
        "startRestMessage": "Hệ thống ghi giờ hiện tại làm mốc tính thời gian nghỉ của ngựa.",
        "restStarted": "Đã bắt đầu cho ngựa nghỉ",
        "restRemaining": "Còn {{minutes}} phút nghỉ",
        "restComplete": "Đã nghỉ đủ thời gian",
        "confirmFit": "Đủ sức đi tiếp",
        "confirmFitTitle": "Xác nhận ngựa đủ sức đi tiếp?",
        "confirmFitMessage": "Sau khi bạn xác nhận, điều phối có thể đánh dấu sự cố đã xử lý.",
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
      "completion": {
        "condition": "Tình trạng xe",
        "normal": "Bình thường",
        "needsMaintenance": "Cần bảo dưỡng",
        "note": "Ghi chú tình trạng xe",
        "hint": "Lượt sẽ được ghi nhận là đã hoàn thành.",
        "maintenanceHint": "Lượt sẽ hoàn thành và xe sẽ chuyển sang bảo dưỡng. Điều phối và quản trị viên sẽ nhận thông báo."
      },
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
} as const
