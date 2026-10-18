# PostgreSQL 18 + pg_bigm（全文検索の部分一致用）
FROM postgres:18 AS build
RUN apt-get update \
 && apt-get install -y --no-install-recommends build-essential postgresql-server-dev-18 libicu-dev
ADD https://github.com/pgbigm/pg_bigm.git#v1.2-20250903 /src/pg_bigm
RUN make -C /src/pg_bigm USE_PGXS=1 \
 && make -C /src/pg_bigm USE_PGXS=1 install DESTDIR=/out

FROM postgres:18
COPY --from=build /out/ /
COPY initdb/ /docker-entrypoint-initdb.d/
