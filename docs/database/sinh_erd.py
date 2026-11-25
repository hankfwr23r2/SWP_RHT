# Sinh ERD.md từ cơ sở dữ liệu thật (không vẽ tay). Nhóm bảng đọc từ DB_SCHEMA_59_BANG.md.
# Cách chạy, từ thư mục gốc repo: python docs/database/sinh_erd.py <tên_container_postgres> <tên_csdl>
import pathlib
import re
import subprocess
import sys

container, database = sys.argv[1], sys.argv[2]
here = pathlib.Path(__file__).parent


def query(sql):
    out = subprocess.run(['docker', 'exec', container, 'psql', '-U', 'postgres', '-d', database, '-At', '-F', '|', '-c', sql],
                         check=True, capture_output=True, text=True, encoding='utf-8').stdout
    return [line.split('|') for line in out.splitlines() if line]


columns, keys = {}, {}
for table, name, kind in query("""
        SELECT c.table_name, c.column_name, c.udt_name FROM information_schema.columns c
        JOIN information_schema.tables t USING (table_schema, table_name)
        WHERE c.table_schema = 'public' AND t.table_type = 'BASE TABLE' AND c.table_name <> 'flyway_schema_history'
        ORDER BY c.table_name, c.ordinal_position"""):
    columns.setdefault(table, []).append((name, kind.strip('_')))
links = []
for table, kind, cols, target, nullable, unique in query("""
        SELECT r.relname, c.contype, string_agg(a.attname, ',' ORDER BY k.ord), coalesce(f.relname, ''),
               bool_or(NOT a.attnotnull),
               EXISTS (SELECT 1 FROM pg_constraint u WHERE u.conrelid = c.conrelid AND u.contype IN ('p', 'u') AND u.conkey = c.conkey)
        FROM pg_constraint c JOIN pg_class r ON r.oid = c.conrelid JOIN pg_namespace n ON n.oid = r.relnamespace
        CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY k(num, ord)
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.num
        LEFT JOIN pg_class f ON f.oid = c.confrelid
        WHERE n.nspname = 'public' AND c.contype IN ('p', 'f')
        GROUP BY c.oid, r.relname, c.contype, f.relname, c.conrelid, c.conkey ORDER BY 1, 2, 3"""):
    if table == 'flyway_schema_history':
        continue
    for col in cols.split(','):
        keys.setdefault((table, col), set()).add('PK' if kind == 'p' else 'FK')
    if kind == 'f':
        links.append((table, cols, target, nullable == 't', unique == 't'))

groups = []
for title, names in re.findall(r'^\| (\d+\. [^|]+?) \| ([^|]+) \| \d+ \|$', (here.parent / 'DB_SCHEMA_59_BANG.md').read_text(encoding='utf-8'), re.M):
    tables = re.findall(r'`(\w+)`', names)
    if tables and all(table in columns for table in tables):
        groups.append((title, tables))
listed = {table for _, tables in groups for table in tables}
missing = sorted(set(columns) - listed)
if missing:
    sys.exit('Bảng chưa có trong mục Danh sách bảng của DB_SCHEMA_59_BANG.md: ' + ', '.join(missing))

lines = ['# Sơ đồ cơ sở dữ liệu', '', f'Sinh từ cơ sở dữ liệu thật bằng `sinh_erd.py`, **không sửa tay**. {len(columns)} bảng, {len(links)} khóa ngoại.',
         'Mỗi nhóm vẽ đủ cột của bảng trong nhóm; bảng thuộc nhóm khác chỉ hiện tên. `PK` là khóa chính, `FK` là khóa ngoại.', '']
for title, tables in groups:
    lines += [f'## {title}', '', '```mermaid', 'erDiagram']
    for table in tables:
        lines.append(f'    {table} {{')
        for name, kind in columns[table]:
            mark = ', '.join(sorted(keys.get((table, name), []), reverse=True))
            lines.append(f'        {kind} {name}' + (f' {mark}' if mark else ''))
        lines.append('    }')
    for table, cols, target, nullable, unique in links:
        if table in tables:
            lines.append(f'    {target} {"|o" if nullable else "||"}--{"o|" if unique else "o{"} {table} : "{cols}"')
    lines += ['```', '']
(here / 'ERD.md').write_text('\n'.join(lines), encoding='utf-8', newline='\n')
print(len(columns), 'bảng,', len(links), 'khóa ngoại')
