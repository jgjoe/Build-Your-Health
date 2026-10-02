# perf 측정 DB (RQ-F-011)

인덱스·실행계획 측정 전용 Oracle 컨테이너와 데이터 로더다. 앱 DB(`../compose.yaml`)와
분리된 컨테이너이고 호스트 포트 1522를 쓴다. 앱 통합 테스트는 스키마를 비우므로 측정 DB와 섞지 않는다.

## 1. 시작

`remake/` 에서:

```sh
docker compose -f perf/compose.yaml up -d
docker compose -f perf/compose.yaml ps      # STATUS 가 healthy 가 될 때까지 대기(첫 기동 1~2분)
```

컨테이너가 처음 만들어질 때(`perf/init/01_grant_select_catalog.sql`, SYS로 실행) `BYH_PERF` 에
`SELECT_CATALOG_ROLE` 을 부여한다. `V$SQL`, `DBMS_XPLAN.DISPLAY_CURSOR` 조회는 이 권한에 의존한다.

## 2. 환경 변수

`load`/`checksum` 은 아래 세 변수만 사용한다(기본값 없음).

PowerShell:

```powershell
$env:PERF_DB_URL      = "jdbc:oracle:thin:@localhost:1522/FREEPDB1"
$env:PERF_DB_USERNAME = "byh_perf"
$env:PERF_DB_PASSWORD = "byhperf123"
```

cmd:

```bat
set PERF_DB_URL=jdbc:oracle:thin:@localhost:1522/FREEPDB1
set PERF_DB_USERNAME=byh_perf
set PERF_DB_PASSWORD=byhperf123
```

비밀번호는 컨테이너의 `PERF_DB_PASSWORD`(기본 `byhperf123`)를 바꿨다면 그 값과 맞춘다.

## 3. load

Flyway(`classpath:db/migration`, V1 스키마 + V2 시드)로 스키마를 만든 뒤 고정 시드(기본
`20261002`)로 `GeneratorConfig.full` 데이터를 적재한다. ORDERS에 행이 이미 있으면 거부한다.
적재 후 DBMS_STATS를 수집하고 적재 행 수와 걸린 시간을 출력한다.

```sh
sh mvnw -q -Pperf test-compile exec:java -Dexec.args="load"
sh mvnw -q -Pperf test-compile exec:java -Dexec.args="load --seed 7"   # 다른 시드
```

## 4. checksum

현재 스키마의 행 수와 ORDER_ID/ITEM_ID에 의존하지 않는 내용 해시를 `key=value` 로 출력한다.
같은 시드로 두 번 적재하면 같은 값이 나와야 한다(TC-214).

```sh
sh mvnw -q -Pperf test-compile exec:java -Dexec.args="checksum"
```

## 5. measure

`full(20261002)` 데이터가 적재된 컨테이너에서 인덱스 시나리오(S0~S2c)마다 Q1·Q2·Q3를 실행하고
클라이언트 응답시간, V$SQL 지표(실행당 buffer gets·DB elapsed·행 수), `DBMS_XPLAN` 실행계획을
`perf/results/<실행 ID>/` 에 남긴다. 시나리오마다 비PK 인덱스를 모두 지우고 그 시나리오의 인덱스만
만든 뒤 측정하고, 실행이 끝나면 다시 지워 PK 인덱스만 남긴다.

```sh
sh mvnw -q -Pperf test-compile exec:java -Dexec.args="measure"
sh mvnw -q -Pperf test-compile exec:java -Dexec.args="measure --run-id r1 --warmup 5 --runs 30 --out perf/results/r1"
```

기본값: run id = 현재 시각 `yyyyMMdd-HHmmss`, warmup 5, runs 30, fetch size 100,
out = `perf/results/<run id>`, 시나리오 S0·S1·S2a·S2b·S2c 전체, 데이터 설정 `GeneratorConfig.full(20261002)`.

출력 파일:

- `raw.csv` — 실행별 응답시간(ms) (run_id, scenario, query, tier, run, elapsed_ms)
- `summary.csv` — 케이스별 중앙값·p95·최소·최대, 실행당 buffer gets·DB elapsed·행 수, sql_id, plan hash, 자식 커서 수, 인덱스 목록
- `plans/<시나리오>_<쿼리>_<등급>.txt` — 자식 커서별 TYPICAL(+PEEKED_BINDS) 계획, `--- ALLSTATS LAST ---`, ALLSTATS 계획
- `environment.txt` — 실행 ID·시각, JVM/JDBC/Oracle 버전, 옵티마이저·SGA·버퍼 캐시 설정, 네 테이블 행 수, 시드·반복 설정, 등급별 대표 회원과 주문 수, Q3 주문 ID

## 6. 초기화

```sh
docker compose -f perf/compose.yaml down -v
```

볼륨까지 지워야 init 스크립트와 시드가 다시 적용된다(`down` 만 하면 데이터가 남는다).
