#!/bin/bash
# RaceHorse Transport - Bash script restore dữ liệu vào Docker Postgres
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_FILE="${DIR}/aiven_backup_2026.sql"

if [ ! -f "$BACKUP_FILE" ]; then
    echo "❌ Không tìm thấy file backup: $BACKUP_FILE"
    exit 1
fi

echo "🚀 Đang nạp file backup vào Docker container 'racehorse-postgres'..."
docker exec -i racehorse-postgres psql -U postgres -d horse_transport_db < "$BACKUP_FILE"

echo "🎉 Phục hồi dữ liệu từ Aiven Cloud hoàn tất!"
