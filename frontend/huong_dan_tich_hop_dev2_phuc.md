# Hướng Dẫn Tích Hợp Nhánh Dev 2 (Phúc) & Giải Quyết Lỗi Git "Unrelated Histories"

> **Dành cho:** Phúc (Dev 2) & AI Coding Agent hỗ trợ bạn Phúc  
> **Dự án:** RaceHorse Transport System (Monorepo)  
> **Mục tiêu:** Tích hợp hơn 3.500 dòng mã nguồn tính năng Chuyên viên (Specialist), Hồ sơ ngựa (Horse Document), Thanh toán (Payment/Settlement) và Cloudinary vào nhánh chuẩn từ `main` mà không làm hỏng cấu hình hạ tầng Cloud (PostgreSQL, Java 21, Render, Vercel).

---

## 1. Bối cảnh & Nguyên nhân lỗi hiện tại

* **Hiện tượng:** Nhánh `origin/feature/phuc/todo-dev-2` không thể tạo Pull Request hoặc Merge vào nhánh `main`. Git báo lỗi:
  ```text
  fatal: refusing to merge unrelated histories
  ```
* **Nguyên nhân:** Commit đầu tiên của nhánh Phúc (`9f9cac1`) là một **Root Commit** (không có commit cha liên kết với `main`). Do đó hai nhánh không có bất kỳ điểm giao nhau nào trong cây lịch sử Git (No Merge-Base).
* **Vấn đề môi trường:** Nhánh `feature/phuc/todo-dev-2` được tách ra lúc dự án còn dùng SQL Server, chưa có cấu hình PostgreSQL Aiven Cloud, chưa nâng cấp Java 21 và chưa có cấu hình CORS cho Vercel + Render. Nếu ép merge cả nhánh, toàn bộ cấu hình hạ tầng Cloud trên `main` sẽ bị ghi đè và làm sập ứng dụng.

---

## 2. Giải pháp kỹ thuật chuẩn mực (Selective Checkout)

Thay vì cố gắng merge một cây Git bị đứt gãy, chúng ta sẽ:
1. Tạo một nhánh mới chuẩn bắt đầu từ `origin/main` mới nhất (`feat/phuc/specialist-and-payment`).
2. Kéo toàn bộ các file tính năng mới (Controller, Service, DTO, Client Frontend) mà Phúc đã viết từ `origin/feature/phuc/todo-dev-2` sang.
3. Bổ sung dependency Cloudinary vào `pom.xml` của `main`.
4. Bổ sung các biến cấu hình Cloudinary vào `application.properties` của `main`.
5. Biên dịch kiểm tra với JDK 21 (`BUILD SUCCESS`).
6. Push nhánh mới lên remote và tạo Pull Request vào `main` mượt mà 100%.

---

## 3. Prompt Hoàn Chỉnh Dành Cho AI Agent Của Phúc

> **Hướng dẫn sử dụng:** Copy toàn bộ nội dung trong khung bên dưới và gửi trực tiếp cho AI Agent (Cursor / Antigravity / Claude Code) của bạn Phúc để agent tự động thực hiện:

```markdown
Chào bạn, tôi là Phúc (Dev 2). Hiện tại nhánh `feature/phuc/todo-dev-2` của tôi đang bị lỗi "unrelated histories" với nhánh `main` (do commit gốc không có commit cha liên kết với `main`), khiến GitHub và Git từ chối tạo Pull Request / Merge vào `main`.

Đồng thời, nhánh `main` của dự án vừa được nhóm nâng cấp lên Java 21, chuyển sang PostgreSQL (Aiven Cloud) và cấu hình CORS để deploy lên Vercel + Render.

Hãy giúp tôi tạo một nhánh mới chuẩn từ `origin/main` và tích hợp toàn bộ tính năng Dev 2 (Chuyên viên, Hồ sơ ngựa, Thanh toán, Cloudinary) sang nhánh mới theo đúng các bước sau:

---

### BƯỚC 1: Lấy bản `main` mới nhất và tạo nhánh mới chuẩn
1. Fetch toàn bộ nhánh từ remote:
   ```bash
   git fetch origin
   ```
2. Chuyển sang nhánh `main` và pull bản mới nhất:
   ```bash
   git checkout main
   git pull origin main
   ```
3. Tạo nhánh mới bắt đầu từ `main`:
   ```bash
   git checkout -b feat/phuc/specialist-and-payment
   ```

---

### BƯỚC 2: Kéo toàn bộ các file tính năng mới từ `feature/phuc/todo-dev-2` sang
Dùng lệnh `git checkout origin/feature/phuc/todo-dev-2 -- <path>` để lấy các file tính năng mà tôi đã viết:

1. **Các Controller mới:**
   ```bash
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/controller/SpecialistBookingController.java
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/controller/HorseDocumentController.java
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/controller/PaymentController.java
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/controller/SettlementController.java
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/controller/FileUploadController.java
   ```

2. **Các Service và Implementation:**
   ```bash
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/service/SpecialistService.java
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/service/impl/SpecialistServiceImpl.java
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/service/HorseDocumentService.java
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/service/impl/HorseDocumentServiceImpl.java
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/service/PaymentService.java
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/service/impl/PaymentServiceImpl.java
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/service/SettlementService.java
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/service/impl/SettlementServiceImpl.java
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/service/StorageService.java
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/service/impl/CloudinaryStorageServiceImpl.java
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/config/CloudinaryConfig.java
   ```

3. **Toàn bộ DTO mới:**
   ```bash
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/dto/specialist/
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/dto/document/
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/dto/payment/
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/dto/settlement/
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/dto/file/
   ```

4. **Repository và Frontend API client:**
   ```bash
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/repository/HorseDocumentRepository.java
   git checkout origin/feature/phuc/todo-dev-2 -- backend/src/main/java/com/example/racehorse_transport/repository/TripDocumentRepository.java
   git checkout origin/feature/phuc/todo-dev-2 -- frontend/src/shared/api/specialist.ts
   git checkout origin/feature/phuc/todo-dev-2 -- frontend/src/shared/api/payments.ts
   git checkout origin/feature/phuc/todo-dev-2 -- frontend/src/shared/api/files.ts
   ```

---

### BƯỚC 3: Cấu hình thư viện và môi trường (CỰC KỲ QUAN TRỌNG)
1. **Kiểm tra `backend/pom.xml`:**
   * Giữ nguyên cấu hình Java 21 và PostgreSQL của `main`.
   * Chỉ bổ sung thêm dependency Cloudinary nếu trong `pom.xml` của `main` chưa có:
     ```xml
     <dependency>
         <groupId>com.cloudinary</groupId>
         <artifactId>cloudinary-http44</artifactId>
         <version>1.36.0</version>
     </dependency>
     ```
2. **Kiểm tra `backend/src/main/resources/application.properties`:**
   * Giữ nguyên toàn bộ kết nối PostgreSQL (Aiven) và CORS của `main`.
   * Chỉ thêm các dòng config Cloudinary vào cuối file:
     ```properties
     # Cloudinary Configuration
     cloudinary.cloud-name=${CLOUDINARY_CLOUD_NAME:your_cloud_name}
     cloudinary.api-key=${CLOUDINARY_API_KEY:your_api_key}
     cloudinary.api-secret=${CLOUDINARY_API_SECRET:your_api_secret}
     ```

---

### BƯỚC 4: Kiểm tra biên dịch & Đẩy nhánh mới lên GitHub
1. Chạy lệnh biên dịch kiểm tra trên Backend:
   ```bash
   cd backend
   ./mvnw clean compile
   ```
   * Đảm bảo kết quả là **BUILD SUCCESS**, không có bất kỳ lỗi biên dịch nào.
2. Commit toàn bộ thay đổi với thông điệp chuẩn:
   ```bash
   git add .
   git commit -m "feat(specialist,payment): integrate specialist review, horse docs, payment and cloudinary APIs"
   ```
3. Đẩy nhánh mới lên remote GitHub:
   ```bash
   git push -u origin feat/phuc/specialist-and-payment
   ```
4. Cung cấp đường link tạo Pull Request vào `main` cho tôi.
```

---

## 4. Danh sách các file kiểm tra đối soát sau khi tích hợp

Sau khi nhánh `feat/phuc/specialist-and-payment` được tạo và mở PR vào `main`, các file sau đây phải xuất hiện đầy đủ trong nhánh:

| STT | Loại tệp | Đường dẫn tệp | Chức năng nghiệp vụ |
| :--- | :--- | :--- | :--- |
| 1 | Controller | `backend/.../controller/SpecialistBookingController.java` | API duyệt/yêu cầu bổ sung hồ sơ ngựa, làm thủ tục kiểm dịch & hải quan |
| 2 | Controller | `backend/.../controller/HorseDocumentController.java` | API upload, xem và xóa hồ sơ ngựa |
| 3 | Controller | `backend/.../controller/PaymentController.java` | API xử lý thanh toán đặt cọc & số dư |
| 4 | Controller | `backend/.../controller/SettlementController.java` | API kê khai chi phí phát sinh & quyết toán |
| 5 | Controller | `backend/.../controller/FileUploadController.java` | API upload ảnh lên Cloudinary |
| 6 | Service | `backend/.../service/impl/SpecialistServiceImpl.java` | Logic duyệt hồ sơ và thủ tục kiểm dịch |
| 7 | Service | `backend/.../service/impl/HorseDocumentServiceImpl.java` | Logic quản lý hồ sơ ngựa (thay thế cho TODO cũ) |
| 8 | Service | `backend/.../service/impl/PaymentServiceImpl.java` | Logic thanh toán cọc và hoàn tiền |
| 9 | Service | `backend/.../service/impl/SettlementServiceImpl.java` | Logic đối soát và phát hành quyết toán |
| 10 | Service | `backend/.../service/impl/CloudinaryStorageServiceImpl.java`| Logic lưu trữ file trên Cloud |
| 11 | Frontend Client | `frontend/src/shared/api/specialist.ts` | Axios client gọi API Chuyên viên |
| 12 | Frontend Client | `frontend/src/shared/api/payments.ts` | Axios client gọi API Thanh toán |
| 13 | Frontend Client | `frontend/src/shared/api/files.ts` | Axios client gọi API Upload file |
