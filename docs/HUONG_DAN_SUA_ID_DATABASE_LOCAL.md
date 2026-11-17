# Hướng dẫn sửa bộ đếm ID của database local SWP_RHT

Tài liệu này có thể gửi riêng cho người khác: toàn bộ SQL cần chạy đã có bên dưới, không cần tải thêm `repair_sequences.sql` hoặc cập nhật code ứng dụng.

Áp dụng cho PostgreSQL local trong Docker, container `racehorse-postgres`, database `horse_transport_db`. Backend có thể chạy bằng Docker hoặc IDE. Không dùng các lệnh này để sửa database Aiven/deploy.

## Lỗi nào được xử lý?

Backend báo `duplicate key value violates unique constraint ...` và `Detail: Key (...)=(...) already exists` khi thêm dữ liệu mới. Ví dụ: bảng đã có `documentid` tới 98 nhưng bộ đếm mới ở 29. Dữ liệu nhập sẵn có ID có thể làm bộ đếm tự tăng chạy sau dữ liệu.

Script sẽ đồng bộ các bộ đếm tăng dần, không quay vòng, thuộc cột SERIAL/IDENTITY trong schema `public`, bao gồm giấy tờ, đơn hàng, nhật ký và thông báo. Chỉ bộ đếm đang chậm mới được đẩy lên; không đổi ID cũ, không sửa hoặc xóa bản ghi, không lùi bộ đếm đã cao hơn dữ liệu. Bảng rỗng được giữ nguyên. Có thể chạy lại.

Thông báo giao diện “Lỗi hệ thống, vui lòng thử lại sau” là lỗi chung; cần có log trùng khóa như trên để xác định đúng vấn đề. Nếu lỗi là thiếu giấy tờ, sai quyền hoặc sai dữ liệu đầu vào, cần xử lý theo thông báo tương ứng.

## 1. Mở PowerShell và kiểm tra database đích

Bật Docker Desktop, mở PowerShell tại thư mục dự án chứa `docker-compose.yml`. Chạy các khối lệnh từ bước 1 đến bước 6 **theo thứ tự, trong cùng một cửa sổ PowerShell** để giữ các biến. Nếu khối lệnh báo lỗi thì dừng tại đó, chưa chạy bước tiếp theo.

```powershell
$rhtPgContainer = 'racehorse-postgres'
$rhtPgDatabase = 'horse_transport_db'
$rhtPgUser = 'postgres'
$rhtBackendContainer = 'racehorse-backend'
$rhtRepairSucceeded = $false

docker ps --format '{{.Names}}  {{.Image}}  {{.Status}}'
if ($LASTEXITCODE -ne 0) { throw 'Chua ket noi duoc Docker Desktop.' }

$rhtRunningContainers = @(docker ps --format '{{.Names}}')
if ($LASTEXITCODE -ne 0) { throw 'Khong doc duoc danh sach container.' }
if ($rhtRunningContainers -notcontains $rhtPgContainer) {
    throw 'Container PostgreSQL local chua chay. Bat postgres cua du an roi thu lai.'
}

docker exec $rhtPgContainer psql -X -v ON_ERROR_STOP=1 -U $rhtPgUser -d $rhtPgDatabase -c "SELECT current_database(), current_user, version(); SELECT pg_get_serial_sequence('public.horse_document', 'documentid') AS document_sequence, pg_get_serial_sequence('public.booking', 'bookingid') AS booking_sequence;"
if ($LASTEXITCODE -ne 0) { throw 'Khong doc duoc database/cac bang cua du an.' }
```

Kết quả phải là database `horse_transport_db` trong container local và tên hai sequence không được trống. Với backend chạy bằng IDE, kiểm tra cấu hình datasource của IDE cũng trỏ tới PostgreSQL local này; sửa một database khác sẽ không hết lỗi trên ứng dụng đang dùng.

Nếu container postgres chưa chạy, bật service `postgres` của dự án bằng `docker compose up -d postgres`, rồi chạy lại bước 1. Nếu tên container/database trên máy đã được tùy chỉnh, sửa các biến tương ứng trước khi tiếp tục.

## 2. Dừng các backend đang ghi vào database

**Chạy bằng IDE hoặc terminal:** bấm Stop trong IDE hoặc Ctrl+C ở terminal chạy backend. Dừng cả các instance khác đang dùng cùng database. Giữ PostgreSQL chạy.

**Chạy bằng Docker:** khối dưới chỉ dừng backend nếu container đó đang chạy, đồng thời ghi nhớ để bật lại đúng trạng thái ban đầu.

```powershell
$rhtBackendWasRunning = $rhtRunningContainers -contains $rhtBackendContainer
if ($rhtBackendWasRunning) {
    docker stop $rhtBackendContainer
    if ($LASTEXITCODE -ne 0) { throw 'Khong dung duoc backend Docker.' }
}
```

Không dùng `docker compose down -v`, không xóa volume, không reset database.

## 3. Sao lưu trước khi sửa

Khối lệnh tạo backup đầy đủ, kiểm tra đọc được danh mục backup, rồi chép ra thư mục `.local-db-backups` trên máy. Thư mục này đã được dự án bỏ qua trong Git; giữ backup trên máy, không gửi kèm tài liệu.

```powershell
$rhtRunStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$rhtBackupDir = Join-Path (Get-Location).Path ('.local-db-backups\id-repair-' + $rhtRunStamp)
New-Item -ItemType Directory -Path $rhtBackupDir -ErrorAction Stop | Out-Null
$rhtLocalBackup = Join-Path $rhtBackupDir 'before-repair.dump'
$rhtContainerBackup = '/tmp/rht-before-id-repair-' + $rhtRunStamp + '.dump'

docker exec $rhtPgContainer pg_dump -U $rhtPgUser -d $rhtPgDatabase -Fc -f $rhtContainerBackup
if ($LASTEXITCODE -ne 0) { throw 'Sao luu that bai; chua duoc chay sua ID.' }

docker exec $rhtPgContainer pg_restore --list $rhtContainerBackup | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Khong doc duoc danh muc backup.' }

docker cp "${rhtPgContainer}:$rhtContainerBackup" $rhtLocalBackup
if ($LASTEXITCODE -ne 0) { throw 'Chua chep duoc backup ra may.' }
if ((Get-Item -LiteralPath $rhtLocalBackup -ErrorAction Stop).Length -eq 0) {
    throw 'File backup rong; chua duoc chay sua ID.'
}

Write-Output "Backup da luu tai: $rhtLocalBackup"

# Ghi nhan so dong, ID lon nhat va checksum noi dung bang giay to de doi chieu sau khi sua.
$rhtSnapshotSql = "SELECT count(*)::text || ':' || coalesce(max(documentid)::text, '') || ':' || md5(coalesce(string_agg(row_to_json(d)::text, '|' ORDER BY documentid), '')) FROM public.horse_document d;"
$rhtBeforeRows = @(docker exec $rhtPgContainer psql -X -At -v ON_ERROR_STOP=1 -U $rhtPgUser -d $rhtPgDatabase -c $rhtSnapshotSql)
if ($LASTEXITCODE -ne 0) { throw 'Khong doc duoc du lieu truoc khi sua.' }
```

## 4. Chạy SQL đồng bộ bộ đếm

Sao chép **nguyên khối** dưới đây. Cặp `@'` và `'@` giữ nguyên dấu `$` của SQL trong PowerShell. SQL được ghi thành file rồi đưa vào container, không cần có sẵn file script trong repo.

```powershell
$rhtRepairSql = @'
-- Run after restoring data, with application writers stopped.
-- Repairs ascending, non-cycling SERIAL/IDENTITY sequences owned by public columns.
-- Existing rows, empty tables and sequences already ahead of the data are unchanged.
-- ALTER SEQUENCE RESTART is transactional and invalidates previously cached values.
DO $repair_sequences$
DECLARE
    target record;
    maximum_id numeric;
    sequence_value numeric;
    sequence_called boolean;
    next_value numeric;
    restart_value numeric;
BEGIN
    PERFORM set_config('lock_timeout', '10s', true);

    FOR target IN
        SELECT tables.relname AS table_name,
               columns.attname AS column_name,
               format('%I.%I', sequence_schema.nspname, sequences.relname) AS sequence_name,
               settings.seqincrement AS increment_by
        FROM pg_class sequences
        JOIN pg_namespace sequence_schema ON sequence_schema.oid = sequences.relnamespace
        JOIN pg_sequence settings ON settings.seqrelid = sequences.oid
        JOIN pg_depend ownership ON ownership.classid = 'pg_class'::regclass
            AND ownership.objid = sequences.oid
            AND ownership.refclassid = 'pg_class'::regclass
            AND ownership.deptype IN ('a', 'i')
        JOIN pg_class tables ON tables.oid = ownership.refobjid
        JOIN pg_namespace table_schema ON table_schema.oid = tables.relnamespace
        JOIN pg_attribute columns ON columns.attrelid = tables.oid
            AND columns.attnum = ownership.refobjsubid
        WHERE sequences.relkind = 'S'
            AND table_schema.nspname = 'public'
            AND settings.seqincrement > 0
            AND NOT settings.seqcycle
        ORDER BY tables.relname, columns.attname
    LOOP
        -- Block INSERT/UPDATE/DELETE while reading the table and restarting its sequence.
        EXECUTE format('LOCK TABLE public.%I IN SHARE ROW EXCLUSIVE MODE', target.table_name);
        EXECUTE format('SELECT max(%I) FROM public.%I', target.column_name, target.table_name)
            INTO maximum_id;
        IF maximum_id IS NULL THEN
            CONTINUE;
        END IF;

        EXECUTE format('SELECT last_value, is_called FROM %s', target.sequence_name)
            INTO sequence_value, sequence_called;
        next_value := sequence_value + CASE WHEN sequence_called THEN target.increment_by ELSE 0 END;

        IF next_value <= maximum_id THEN
            -- Keep the configured increment and its progression, including increments other than 1.
            restart_value := next_value
                + (floor((maximum_id - next_value) / target.increment_by) + 1) * target.increment_by;
            EXECUTE format('ALTER SEQUENCE %s RESTART WITH %s', target.sequence_name, restart_value);
            RAISE NOTICE 'Repaired %.%: next ID = %', target.table_name, target.column_name, restart_value;
        END IF;
    END LOOP;
END;
$repair_sequences$;
'@

$rhtLocalRepair = Join-Path $rhtBackupDir 'repair_sequences.sql'
[IO.File]::WriteAllText($rhtLocalRepair, $rhtRepairSql, [Text.UTF8Encoding]::new($false))
$rhtContainerRepair = '/tmp/rht-repair-sequences-' + $rhtRunStamp + '.sql'

docker cp $rhtLocalRepair "${rhtPgContainer}:$rhtContainerRepair"
if ($LASTEXITCODE -ne 0) { throw 'Chua chep duoc SQL vao container.' }

docker exec $rhtPgContainer psql -X -v ON_ERROR_STOP=1 -U $rhtPgUser -d $rhtPgDatabase -f $rhtContainerRepair
if ($LASTEXITCODE -ne 0) { throw 'Sua bo dem that bai; giu backend dung va xem loi ben tren.' }
```

Khi có bộ đếm bị lệch, PostgreSQL sẽ in ví dụ `NOTICE: Repaired horse_document.documentid: next ID = 99` và kết thúc bằng `DO`. Con số phụ thuộc dữ liệu trên máy; **không cần cố đặt về 99**. Nếu bộ đếm đã đúng thì có thể chỉ hiện `DO` mà không có `NOTICE`.

Toàn bộ thao tác sửa nằm trong một giao dịch. Nếu có lỗi, các thay đổi bộ đếm của lần chạy này được hủy. Script không tự sửa sequence quay vòng hoặc đếm giảm.

## 5. Đối chiếu dữ liệu và xem ID kế tiếp

```powershell
$rhtAfterRows = @(docker exec $rhtPgContainer psql -X -At -v ON_ERROR_STOP=1 -U $rhtPgUser -d $rhtPgDatabase -c $rhtSnapshotSql)
if ($LASTEXITCODE -ne 0) { throw 'Khong doc duoc du lieu sau khi sua.' }
if (($rhtBeforeRows -join "`n") -ne ($rhtAfterRows -join "`n")) {
    throw 'Du lieu bang giay to thay doi; giu backend dung de kiem tra, chua phuc hoi backup tu dong.'
}
Write-Output 'So luong va noi dung giay to cu khong thay doi.'

$rhtCheckSql = @'
DO $check_sequences$
DECLARE
    table_name text;
    column_name text;
    sequence_name text;
    sequence_value numeric;
    sequence_called boolean;
    increment_by bigint;
    maximum_id numeric;
    next_id numeric;
BEGIN
    FOR table_name, column_name IN
        VALUES ('horse_document', 'documentid'), ('booking', 'bookingid'),
               ('log', 'logid'), ('notification', 'notificationid')
    LOOP
        sequence_name := pg_get_serial_sequence(format('public.%I', table_name), column_name);
        IF sequence_name IS NULL THEN
            RAISE EXCEPTION 'Missing sequence for %.%', table_name, column_name;
        END IF;
        EXECUTE format('SELECT last_value, is_called FROM %s', sequence_name)
            INTO sequence_value, sequence_called;
        SELECT seqincrement INTO increment_by FROM pg_sequence WHERE seqrelid = sequence_name::regclass;
        EXECUTE format('SELECT max(%I) FROM public.%I', column_name, table_name) INTO maximum_id;
        next_id := sequence_value + CASE WHEN sequence_called THEN increment_by ELSE 0 END;
        IF maximum_id IS NOT NULL AND next_id <= maximum_id THEN
            RAISE EXCEPTION 'Sequence still behind: %, max ID = %, next ID = %', table_name, maximum_id, next_id;
        END IF;
        RAISE NOTICE '%: max ID = %, next ID = %', table_name, maximum_id, next_id;
    END LOOP;
END;
$check_sequences$;
'@

$rhtLocalCheck = Join-Path $rhtBackupDir 'check_sequences.sql'
[IO.File]::WriteAllText($rhtLocalCheck, $rhtCheckSql, [Text.UTF8Encoding]::new($false))
$rhtContainerCheck = '/tmp/rht-check-sequences-' + $rhtRunStamp + '.sql'
docker cp $rhtLocalCheck "${rhtPgContainer}:$rhtContainerCheck"
if ($LASTEXITCODE -ne 0) { throw 'Chua chep duoc SQL kiem tra vao container.' }
docker exec $rhtPgContainer psql -X -v ON_ERROR_STOP=1 -U $rhtPgUser -d $rhtPgDatabase -f $rhtContainerCheck
if ($LASTEXITCODE -ne 0) { throw 'Kiem tra bo dem chua dat; xem loi ben tren.' }
$rhtRepairSucceeded = $true
```

Kết quả mong đợi: ID kế tiếp lớn hơn ID lớn nhất của từng bảng có dữ liệu; bảng rỗng sẽ có `max ID = <NULL>`. Bước này chỉ đọc trạng thái sequence, không gọi `nextval()` nên không lấy mất ID kế tiếp.

## 6. Bật lại backend và thử thao tác

```powershell
if (-not $rhtRepairSucceeded) { throw 'Chua hoan tat kiem tra; chua bat lai backend tu buoc nay.' }
if ($rhtBackendWasRunning) {
    docker start $rhtBackendContainer
    if ($LASTEXITCODE -ne 0) { throw 'Khong bat lai duoc backend Docker.' }
} else {
    Write-Output 'Neu dung IDE/terminal: bat lai backend bang cach thuong dung.'
}
```

Chờ backend khởi động xong, sau đó:

1. Với giấy tờ tải lỗi: mở đúng hồ sơ ngựa đã lưu, chọn lại tệp nếu cần rồi bấm **Lưu thay đổi**. Kiểm tra số giấy tờ đã lưu tăng lên; không tạo lại ngựa chỉ để thử upload.
2. Với gửi đơn: thử gửi một lần khi hồ sơ ngựa đã đủ giấy tờ và các điều kiện đặt chuyến hợp lệ. Kiểm tra danh sách đơn trước khi thử gửi lại để biết lần trước đã tạo được đơn hay chưa.

## Nếu vẫn lỗi

- `lock timeout`: còn tiến trình ghi vào database. Dừng các backend/phiên thao tác đang dùng database, rồi chạy lại từ bước 4 và kiểm tra bước 5.
- `permission denied`: kiểm tra đúng user PostgreSQL local và quyền sở hữu sequence; các lệnh trên dùng user `postgres` của Docker dự án.
- Không thấy bảng/sequence: kiểm tra tên database, schema và phiên bản database đang dùng; không tự tạo bảng/sequence rỗng để bỏ qua lỗi.
- Sequence vượt miền giá trị hoặc quay vòng/đếm giảm: dừng và gửi log để xử lý riêng.
- Giao diện vẫn báo lỗi 500: lấy log của đúng backend, tìm dòng tên constraint và `Detail: Key ...`. Sửa bộ đếm chỉ xử lý lỗi trùng ID, không thay thế kiểm tra nghiệp vụ.

Backend Docker: lấy log bằng `docker logs --since 10m racehorse-backend`. Backend chạy IDE: lấy log ở cửa sổ Run/terminal. Chỉ gửi đoạn lỗi liên quan, không gửi mật khẩu, token hoặc file backup.

Các biến mất khi đóng PowerShell. Nếu mở cửa sổ mới để làm lại, chạy lại từ bước 1. Giữ file `before-repair.dump`; tài liệu này không tự phục hồi hoặc ghi đè database từ backup khi có lỗi.
