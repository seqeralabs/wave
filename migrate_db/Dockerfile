FROM python:3.11-slim

RUN pip install --no-cache-dir requests psycopg2-binary

WORKDIR /app
COPY sync_wave_request.py .

CMD ["python", "sync_wave_request.py"]
