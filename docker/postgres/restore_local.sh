#!/bin/bash
# RaceHorse Transport - Bash script restore dữ liệu vào Docker Postgres
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_FILE="${DIR}/aiven_backup_2026.sql"
REPAIR_FILE="${DIR}/repair_sequences.sql"

if [ ! -f "$BACKUP_FILE" ]; then
    echo "❌ Không tìm thấy file backup: $BACKUP_FILE"
    exit 1
fi

if [ ! -f "$REPAIR_FILE" ]; then
    echo "❌ Không tìm thấy script đồng bộ bộ đếm: $REPAIR_FILE"
    exit 1
fi

# Dừng backend trước khi khôi phục dữ liệu và đồng bộ bộ đếm.
echo "🚀 Đang nạp file backup vào Docker container 'racehorse-postgres'..."
docker exec -i racehorse-postgres psql -X -v ON_ERROR_STOP=1 -U postgres -d horse_transport_db < "$BACKUP_FILE"

echo "🔢 Đang đồng bộ bộ đếm ID sau khi khôi phục..."
docker exec -i racehorse-postgres psql -X -v ON_ERROR_STOP=1 -U postgres -d horse_transport_db < "$REPAIR_FILE"

echo "🎉 Phục hồi dữ liệu từ Aiven Cloud hoàn tất!"
