# One-shot container for docker compose: waits for the backend, then loads the demo data through the public API
# with seed-demo-data.sh, the same script used by hand. The script loads nothing into a database that already has
# users or items, so running it on every `docker compose up` never duplicates data. SEED_DEMO_DATA=false skips it.
FROM python:3.12-alpine
RUN apk add --no-cache bash curl
WORKDIR /seed
COPY seed-demo-data.sh .
CMD ["bash", "-c", "if [ \"$SEED_DEMO_DATA\" = false ]; then echo 'SEED_DEMO_DATA=false: demo data not loaded.'; exit 0; fi; for _ in $(seq 1 150); do curl -sf -o /dev/null \"$API_URL/dashboard/summary\" && exec bash seed-demo-data.sh; sleep 2; done; echo 'Backend not ready after 5 minutes; demo data not loaded.' >&2; exit 1"]
