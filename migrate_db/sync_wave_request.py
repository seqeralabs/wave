import os
import json
import io
import requests
import psycopg2

SURREAL_URL = os.getenv("SURREAL_URL", "http://surrealdb:8000/sql")
SURREAL_USER = os.getenv("SURREAL_USER", "root")
SURREAL_PASS = os.getenv("SURREAL_PASS", "root")
SURREAL_NS = os.getenv("SURREAL_NS", "seqera")
SURREAL_DB = os.getenv("SURREAL_DB", "wave")

PG_HOST = os.getenv("PG_HOST", "postgres")
PG_PORT = int(os.getenv("PG_PORT", 5432))
PG_DB = os.getenv("PG_DB", "wave")
PG_USER = os.getenv("PG_USER", "postgres")
PG_PASS = os.getenv("PG_PASS", "postgres")

def fix_request_id(sid):
    if sid.startswith("wave_request:"):
        if "⟨" in sid and "⟩" in sid:
            return sid.split("⟨")[1].split("⟩")[0]
        return sid.split("wave_request:")[1]
    return sid


res = requests.post(
    SURREAL_URL,
    headers={
        "NS": SURREAL_NS,
        "DB": SURREAL_DB,
        "Accept": "application/json",
        "Content-Type": "application/sql",
    },
    auth=(SURREAL_USER, SURREAL_PASS),
    data="SELECT * FROM wave_request;"
)

if res.status_code != 200:
    print("Error querying SurrealDB:", res.text)
    exit(1)

records = res.json()

rows = []
for record in records:
    if isinstance(record, dict) and "result" in record and isinstance(record["result"], list):
        rows.extend(record["result"])

print(f"Fetched {len(rows)} records from SurrealDB")

seen_ids = set()
duplicate = 0

# Write to a temp file
with tempfile.NamedTemporaryFile("w+", delete=False, suffix=".csv") as tmpfile:
    for row in rows:
        sid = fix_request_id(row.get("id", ""))
        if sid in seen_ids:
            duplicate += 1
            continue
        seen_ids.add(sid)
        json_str = json.dumps(row, ensure_ascii=False)
        escaped = json_str.replace('"', '""')  # escape for CSV
        tmpfile.write(f'{sid},"{escaped}"\n')
    temp_path = tmpfile.name

print(f"Prepared {len(seen_ids)} unique records, {duplicate} duplicates found")
print(f"Data written to temp file: {temp_path}")

pg_conn = psycopg2.connect(
    host=PG_HOST,
    port=PG_PORT,
    dbname=PG_DB,
    user=PG_USER,
    password=PG_PASS
)
pg_cur = pg_conn.cursor()
pg_cur.execute("""
               CREATE TABLE IF NOT EXISTS wave_request_staging (LIKE wave_request INCLUDING ALL);
               TRUNCATE wave_request_staging;
               """)

with open(temp_path, "r") as f:
    pg_cur.copy_expert("COPY wave_request_staging (id, data) FROM STDIN WITH CSV", f)

    pg_cur.execute("""
                   INSERT INTO wave_request (id, data)
                   SELECT DISTINCT id, data FROM wave_request_staging
                       ON CONFLICT (id) DO NOTHING;
                   """)

pg_conn.commit()

print("Imported into PostgreSQL")

pg_cur.close()
pg_conn.close()

os.remove(temp_path)
