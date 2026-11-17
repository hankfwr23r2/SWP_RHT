# Khôi phục dữ liệu và đồng bộ ID PostgreSQL local

Dừng backend đang ghi vào database trước khi chạy các script này; bật lại sau khi hoàn tất. Hai script `restore_local.py` và `restore_local.sh` tự chạy `repair_sequences.sql` sau khi nạp dữ liệu thành công và trả mã lỗi nếu khôi phục hoặc sửa bộ đếm thất bại.

Chỉ đồng bộ bộ đếm trên Docker local, không nạp lại backup (PowerShell tại thư mục gốc repo):

```powershell
docker stop racehorse-backend
Get-Content -Raw -Encoding UTF8 docker/postgres/repair_sequences.sql | docker exec -i racehorse-postgres psql -X -v ON_ERROR_STOP=1 -U postgres -d horse_transport_db
docker start racehorse-backend
```

Script tìm các sequence tăng dần, không quay vòng, thuộc cột SERIAL/IDENTITY của schema `public`. Chỉ sequence có ID kế tiếp không vượt ID lớn nhất trong bảng mới được đẩy lên. Bảng rỗng và bộ đếm đang cao hơn dữ liệu được giữ nguyên; không đổi hoặc xóa bản ghi. Có thể chạy lại. Các thao tác sửa sequence nằm trong một giao dịch; gặp lỗi sẽ hủy các thay đổi của lần chạy đó.

Python restore dùng `pg8000` và định dạng SQL chia câu lệnh bằng `;` ở cuối dòng như script hiện tại. Với dump PostgreSQL có lệnh `COPY`, lệnh psql hoặc khối PL/pgSQL, dùng bản Bash/`psql`.

Kiểm thử:

```powershell
python -B -m unittest discover -s docker/postgres -p test_restore_local.py
cd backend
.\mvnw.cmd -Dtest=PostgresSequenceRepairTest test
```

`PostgresSequenceRepairTest` chạy trên PostgreSQL Testcontainers riêng, kiểm tra ID nhập sẵn, sequence SERIAL/IDENTITY, bộ đếm cao hơn dữ liệu, bảng rỗng, cache, chạy lại và rollback khi một sequence hết miền giá trị.
