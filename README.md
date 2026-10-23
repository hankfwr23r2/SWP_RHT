# RaceHorse Transport - Monorepo

Dự án Hệ thống Vận chuyển Ngựa đua (RaceHorse Transport System).

## Cấu trúc thư mục (Monorepo)

- `backend/`: Spring Boot API (Java 26, Spring Security, JWT, PostgreSQL, JPA/Hibernate).
- `frontend/`: React 19 + TypeScript + Vite (Customer SPA & Backoffice SPA).

---

## Hướng dẫn chạy dự án

### Cách 1: Chạy toàn bộ hệ thống bằng Docker (Khuyên dùng)
Chỉ cần cài đặt Docker Desktop và chạy đúng 1 lệnh tại thư mục gốc:
```bash
docker compose up -d --build
```
- Tự động bật PostgreSQL 16 Alpine và tạo database `horse_transport_db`.
- Tự động bật Backend tại: `http://localhost:8080/api/v1`
- Tự động bật Frontend tại: `http://localhost:5173`
- Để dừng hệ thống: `docker compose down`

Để tạo tài khoản quản trị đầu tiên, cấu hình `APP_BOOTSTRAP_ADMIN_USERNAME`,
`APP_BOOTSTRAP_ADMIN_EMAIL` và `APP_BOOTSTRAP_ADMIN_PASSWORD` trong môi trường
chạy Docker Compose trước khi khởi động. Ví dụ trong PowerShell:

```powershell
$env:APP_BOOTSTRAP_ADMIN_USERNAME = "admin"
$env:APP_BOOTSTRAP_ADMIN_EMAIL = "admin@example.com"
$env:APP_BOOTSTRAP_ADMIN_PASSWORD = "<set-a-unique-strong-password>"
docker compose up -d
```

Nếu không cấu hình đủ ba biến, backend sẽ không tạo tài khoản mẫu mặc định.
Tài khoản và dữ liệu đã có trong database không bị xóa.

---

### Docker trong nhóm: dùng chung cách dựng, không dùng chung dữ liệu

`docker-compose.yml` giúp mọi máy có cùng Postgres 16, cùng tên DB, mật khẩu và cấu trúc bảng.
Container chạy trên máy ai thì dữ liệu nằm trên máy người đó: đơn bạn tạo, người khác không thấy và ngược lại.
Database dùng chung duy nhất là Aiven của bản deploy, không dùng để code.

| Mục đích | Lệnh (chạy ở thư mục gốc) |
|---|---|
| Code hằng ngày: chỉ bật DB + pgAdmin, backend/frontend chạy trên máy (Cách 2) | `docker compose up -d postgres pgadmin` |
| Chạy cả hệ thống giống bản deploy (Cách 1) | `docker compose up -d --build` |
| Xem container đang chạy / log backend | `docker compose ps` · `docker compose logs -f backend` |
| Tắt (giữ dữ liệu) | `docker compose stop` |
| Xoá sạch dữ liệu local để làm lại từ đầu | `docker compose down -v` |

Chia sẻ một bộ dữ liệu mẫu (ví dụ trước buổi demo) mà không dùng chung DB:
```bash
# Người có dữ liệu: xuất ra file (file .sql đã bị gitignore, gửi qua Drive/Zalo, không đưa lên GitHub)
docker exec racehorse-postgres pg_dump -U postgres -d horse_transport_db > du-lieu-mau.sql
# Người nhận: làm sạch DB rồi nạp file, sau đó mới bật backend
docker compose down -v && docker compose up -d --wait postgres
docker exec -i racehorse-postgres psql -U postgres -d horse_transport_db < du-lieu-mau.sql
```

Thử backend trong điều kiện giống Render gói free (0,1 CPU, 512 MB) để kiểm tra độ chậm
(`swp_rht_default` là mạng Docker đặt theo tên thư mục dự án; xem tên đúng trên máy bằng `docker network ls`):
```bash
cd backend && ./mvnw -q package -DskipTests && cd ..
docker run --rm --network swp_rht_default --cpus 0.1 --memory 512m -p 8090:8080 \
  -v "$PWD/backend/target/RaceHorse_Transport-0.0.1-SNAPSHOT.jar:/app/app.jar:ro" \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://racehorse-postgres:5432/horse_transport_db \
  eclipse-temurin:21-jre java -XX:TieredStopAtLevel=1 -jar /app/app.jar
```

---

### Cách 2: Chạy thủ công trên máy (Local Dev)

Mỗi người dùng một database Postgres riêng trên máy mình. Backend mặc định kết nối Postgres local,
nên chạy trên máy không bao giờ đụng vào database của bản deploy (Aiven). Không dùng mật khẩu Aiven khi code.

#### 0. Database Postgres + pgAdmin (Docker)
```bash
docker compose up -d postgres pgadmin
```
- Postgres: `localhost:5432`, database `horse_transport_db`, user `postgres`, mật khẩu `RaceHorse@2026`.
- Xem bảng và dữ liệu: mở `http://localhost:5050` → **Servers → RaceHorse local** → nhập mật khẩu ở trên
  (tick *Save Password*) → **Databases → horse_transport_db → Schemas → public → Tables**,
  chuột phải một bảng → *View/Edit Data*.
- Làm lại database từ đầu (xoá toàn bộ dữ liệu local): `docker compose down -v` rồi chạy lại lệnh trên.

#### 1. Backend (`backend/`)
- Lần đầu nên bật seeder để có sẵn danh mục, xe, quy định giấy tờ và tài khoản mẫu
  (chỉ nạp vào bảng đang trống, chạy lại không bị nhân đôi):
  ```bash
  cd backend
  APP_SEED_REFERENCE_DATA=true ./mvnw spring-boot:run
  ```
  PowerShell: `$env:APP_SEED_REFERENCE_DATA="true"; ./mvnw spring-boot:run`.
  IntelliJ: thêm `APP_SEED_REFERENCE_DATA=true` vào *Environment variables* của Run Configuration.
- Backend chạy mặc định tại: `http://localhost:8080` (Context-path: `/api/v1`).
- Tài khoản mẫu: `admin01`, `manager01`, `coordinator01`, `specialist01`, `driver01`–`driver03`,
  `escort01`–`escort03` (đăng nhập bằng email `<tên>@gmail.com`); mật khẩu xem tài liệu nhóm (tab Data).
- Thanh toán VNPay về đúng trang local: đặt thêm `VNPAY_RETURN_URL=http://localhost:5173/payment/vnpay-return`.
- Cần xem câu SQL backend chạy: đặt thêm `SPRING_JPA_SHOW_SQL=true`.

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
- Bản đồ Vietmap cần key: tạo `frontend/.env` (đã được gitignore) theo mẫu `frontend/.env.example`
  và điền `VITE_VIETMAP_API_KEY`. Thiếu key thì bản đồ để trống, các chức năng khác vẫn chạy.
