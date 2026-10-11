# RaceHorse Transport - Monorepo

Dự án Hệ thống Vận chuyển Ngựa đua (RaceHorse Transport System).

## Cấu trúc thư mục (Monorepo)

- `backend/`: Spring Boot 3 API (Java 21, Spring Security, JWT, PostgreSQL, JPA/Hibernate).
- `frontend/`: React 19 + TypeScript + Vite (Customer SPA & Backoffice SPA).

---

## Hướng dẫn chạy dự án

### Cách 1: Chạy toàn bộ hệ thống bằng Docker (Khuyên dùng)
Chỉ cần cài đặt Docker Desktop và chạy đúng 1 lệnh tại thư mục gốc:
```bash
docker compose up -d
```
- Tự động bật PostgreSQL 16 Alpine, tạo database `horse_transport_db` và nạp sẵn tài khoản Admin/Nhân sự.
- Tự động bật Backend tại: `http://localhost:8080/api/v1`
- Tự động bật Frontend tại: `http://localhost:5173`
- Để dừng hệ thống: `docker compose down`

---

### Cách 2: Chạy thủ công trên máy (Local Dev)

#### 1. Backend (`backend/`)
- Mở thư mục `backend/` trong IntelliJ IDEA hoặc chạy lệnh:
  ```bash
  cd backend
  ./mvnw spring-boot:run
  ```
- Backend chạy mặc định tại: `http://localhost:8080` (Context-path: `/api/v1`).

#### 2. Frontend (`frontend/`)
- Di chuyển vào thư mục `frontend/`, cài đặt thư viện và chạy:
  ```bash
  cd frontend
  npm install
  npm run dev
  ```
- Customer App: `http://localhost:5173/`
- Backoffice / Admin App: `http://localhost:5173/backoffice`
- Vite đã cấu hình proxy tự động chuyển tiếp `/api/v1` sang `http://localhost:8080/api/v1`.
