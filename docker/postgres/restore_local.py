#!/usr/bin/env python3
"""
RaceHorse Transport - Local Database Restore Script
Khôi phục dữ liệu từ file backup Aiven vào PostgreSQL local.
Dừng backend trước khi khôi phục dữ liệu và đồng bộ bộ đếm ID.

Cách dùng:
    python3 docker/postgres/restore_local.py
Hoặc tùy biến tham số:
    python3 docker/postgres/restore_local.py --host localhost --port 5432 --user postgres --password RaceHorse@2026 --dbname horse_transport_db
"""
import argparse
import sys
import os
from pathlib import Path

try:
    import pg8000.native
except ImportError:
    print("Vui lòng cài đặt pg8000: pip install pg8000")
    sys.exit(1)

def main():
    parser = argparse.ArgumentParser(description="Restore Aiven Cloud backup into local PostgreSQL")
    parser.add_argument("--host", default=os.getenv("PGHOST", "localhost"))
    parser.add_argument("--port", type=int, default=int(os.getenv("PGPORT", "5432")))
    parser.add_argument("--user", default=os.getenv("PGUSER", "postgres"))
    parser.add_argument("--password", default=os.getenv("PGPASSWORD", "RaceHorse@2026"))
    parser.add_argument("--dbname", default=os.getenv("PGDATABASE", "horse_transport_db"))
    
    script_dir = Path(__file__).resolve().parent
    default_backup = script_dir / "aiven_backup_2026.sql"
    repair_file = script_dir / "repair_sequences.sql"
    parser.add_argument("--file", default=str(default_backup))

    args = parser.parse_args()

    if not Path(args.file).exists():
        print(f"❌ Không tìm thấy file backup: {args.file}")
        sys.exit(1)

    if not repair_file.is_file():
        print(f"❌ Không tìm thấy script đồng bộ bộ đếm: {repair_file}")
        sys.exit(1)

    print(f"🔌 Đang kết nối tới PostgreSQL local: {args.host}:{args.port}/{args.dbname} (user: {args.user})...")
    try:
        conn = pg8000.native.Connection(
            user=args.user,
            password=args.password,
            host=args.host,
            port=args.port,
            database=args.dbname
        )
        print("✅ Kết nối thành công!")
    except Exception as e:
        print(f"❌ Kết nối thất bại: {e}")
        print("\n💡 Gợi ý: Hãy đảm bảo Docker Desktop và container PostgreSQL đang chạy:")
        print("   docker compose up -d postgres")
        sys.exit(1)

    try:
        print(f"📄 Đang đọc file backup: {args.file}...")
        sql_content = Path(args.file).read_text(encoding="utf-8")
        repair_sql = repair_file.read_text(encoding="utf-8")

        print("🚀 Đang nạp dữ liệu vào cơ sở dữ liệu...")
        statements = [stmt.strip() for stmt in sql_content.split(";\n") if stmt.strip()]
        total = len(statements)

        for idx, stmt in enumerate(statements, 1):
            if not stmt.endswith(";"):
                stmt += ";"
            conn.run(stmt)
            if idx % 10 == 0 or idx == total:
                print(f"   Tiến độ: {idx}/{total} câu lệnh hoàn tất.")

        print("🔢 Đang đồng bộ bộ đếm ID sau khi khôi phục...")
        # Execute the entire DO block; splitting it at semicolons would break PL/pgSQL.
        conn.run(repair_sql)
    except Exception as e:
        print(f"❌ Khôi phục chưa hoàn tất: {e}")
        sys.exit(1)
    finally:
        conn.close()

    print(f"\n🎉 Đã nạp thành công {total}/{total} câu lệnh và đồng bộ bộ đếm ID!")
    print("✅ Cơ sở dữ liệu PostgreSQL local đã đồng bộ hoàn toàn với dữ liệu thực từ Aiven Cloud.")

if __name__ == "__main__":
    main()
