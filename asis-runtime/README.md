# asis-runtime — BuildYourHealth AS-IS 실행 환경

`BuildYourHealth/`(JSP/Servlet + MySQL, 2024년 학부 프로젝트)를 **한 줄도 수정하지 않고** Docker Compose로
로컬에서 실행하기 위한 환경이다. 리라이트 이전의 "AS-IS 베이스라인"을 관찰하는 것이 목적이므로,
앱의 버그·하드코딩·깨진 기능은 고치지 않고 여기(README)에 기록만 한다.

- 애플리케이션 소스는 빌드 시점에 그대로 복사해서 컴파일만 한다 (Maven/Gradle 없음 → 이미지 안의 `javac` 사용).
- DB는 앱이 하드코딩한 값(`jdbc:mysql://localhost:3306/HealthDB`, `root`/`1234`)에 맞춰 띄운다.

## 요구 사항

- Docker Desktop / Docker Engine + Compose v2 (`docker compose`)
- 레지스트리 접근 가능 (이미지 pull). 첫 실행 시 `mysql:8.0`, `tomcat:10.1-jdk17` 다운로드 + 앱 컴파일로 수 분 걸릴 수 있다.

## 시작

저장소 루트에서:

```bash
docker compose -f asis-runtime/compose.yaml up -d --build
```

- 수동 SQL 실행, 파일 복사, 초기 세팅이 필요 없다. MySQL 데이터 디렉터리가 비어 있으면
  `asis-runtime/db/init/10-schema.sql`이 원본 SQL들을 FK 순서대로 로드하고 시드 데이터를 넣는다.
- 앱 서비스는 `db`의 healthcheck(TCP 3306 ping)가 통과한 뒤에 시작한다.

접속 URL:

- <http://localhost:8080/BuildYourHealth/user/welcome.jsp> (진입 페이지)
- 상품 목록 <http://localhost:8080/BuildYourHealth/product/products.jsp>
- 추천 컨텐츠 <http://localhost:8080/BuildYourHealth/content/contents.jsp>
- 게시판 <http://localhost:8080/BuildYourHealth/BoardListAction.do?pageNum=1>

포트는 호스트 `8080`(Tomcat)만 열린다. MySQL 3306은 호스트에 공개하지 않는다.

## 검증

```bash
bash asis-runtime/smoke.sh
```

180초까지 앱 응답을 폴링한 뒤 5개 검사를 순서대로 수행하고, 처음 실패한 지점에서 exit code 1로 중단한다.

1. `GET /BuildYourHealth/user/welcome.jsp` → 200
2. `GET /BuildYourHealth/product/products.jsp` → 200 + 본문에 `동원샘물` (JSP → JDBC → MySQL 한글 읽기)
3. `GET /BuildYourHealth/content/contents.jsp` → 200 + 본문에 `물 섭취의 중요성`
4. `GET /BuildYourHealth/BoardListAction.do?pageNum=1` → 200 + 본문에 `Exception` 없음 (컴파일된 `mvc.controller.BoardController`)
5. `GET /BuildYourHealth/product/product.jsp?id=P001` → 200 + `동원샘물` (컴파일된 `dao.ProductRepository`)

## 중지 / 초기화

```bash
docker compose -f asis-runtime/compose.yaml down -v
```

`-v`는 MySQL 볼륨(`buildyourhealth-asis_db-data`)까지 지운다. 스키마/시드 데이터를 처음부터 다시 만들려면
`down -v` 후 다시 `up -d --build`. 컨테이너만 멈추고 데이터를 남기려면 `down`(또는 `stop`).

## 왜 이런 구성인가 (제약)

| 제약 | 이유 |
|---|---|
| `network_mode: "service:db"` (앱) | 16곳에 `jdbc:mysql://localhost:3306/HealthDB`가 하드코딩되어 있다. 별도 네트워크로 분리하면 `localhost`로 DB에 닿을 수 없어서, 앱 컨테이너가 `db`의 네트워크 네임스페이스를 공유한다. 그 대가로 Tomcat의 8080은 `db` 서비스에서 publish한다. |
| `--lower-case-table-names=1` | DDL은 소문자(`member`, `user_records`), 쿼리는 대문자(`MEMBER`, `CONTENTS`)가 섞여 있다. Windows MySQL(대소문자 무시)에서 개발된 코드라 이 옵션으로 그 동작을 재현한다. 이 값은 데이터 디렉터리 최초 초기화 시점에 박히므로 반드시 첫 기동부터 적용되어야 하고, 다른 값으로 만들어진 볼륨이 있으면 `down -v`로 지워야 한다. |
| `mysql:8.0` (8.4 아님) | `--default-authentication-plugin`이 8.4에서 제거됐다. |
| `--default-authentication-plugin=mysql_native_password` | JDBC URL에 `allowPublicKeyRetrieval`이 없어서, 8.0 기본값인 `caching_sha2_password`로는 인증이 실패할 수 있다. |
| `--character-set-server=utf8mb4` / `utf8mb4_unicode_ci` | 시드 데이터(`insert.sql`)에 한글 상품명·컨텐츠 제목이 들어 있다. |
| `tomcat:10.1-jdk17` + `javac` | 빌드 도구가 없다. 웹앱은 `BuildYourHealth/src/main/webapp`을 그대로 펼치고, `src/main/java`를 Tomcat의 `servlet-api.jar` + `WEB-INF/lib/*.jar`로 컴파일해 `WEB-INF/classes`에 넣는다. `bundle/*.properties`는 `<fmt:bundle basename="bundle.message">`가 찾도록 `WEB-INF/classes/bundle/`에 배치한다. |
| 컨텍스트 `/BuildYourHealth` | 일부 링크가 `/BuildYourHealth/...` 절대 경로로 하드코딩되어 있다. `webapps/BuildYourHealth`로 배포해야 하며 ROOT로는 옮길 수 없다. |
| SQL을 `SOURCE`로 로드 | 원본 SQL을 편집하지 않고, 읽기 전용 마운트(`/asis-sql`)한 파일을 FK 의존 순서(member → product → contents → board → orders → order_items → reviews → user_records → insert)로 실행한다. |

## 파일 구성

```
asis-runtime/
├── compose.yaml            # db + app 서비스, healthcheck, 포트/볼륨
├── Dockerfile              # Tomcat 이미지 + javac 컴파일 + 웹앱 조립
├── db/init/10-schema.sql   # 원본 SQL을 순서대로 SOURCE
├── smoke.sh                # AS-IS 스모크 검증 (5개)
└── README.md
```

## Known AS-IS limitations

앱을 수정할 수 없어 Linux/Docker에서 동작하지 않거나, 원래부터 깨져 있는 것들. **고치지 않고 기록만 한다.**

1. **상품 이미지 업로드 불가 (HTTP 500)** — `product/processAddProduct.jsp:9`, `product/processUpdateProduct.jsp:10`이
   `C:\Users\crush\eclipse-workspace\...\wtpwebapps\BuildYourHealth\resources\images`라는 Windows 절대 경로에
   COS(`com.oreilly.servlet`)로 파일을 쓴다. Linux에는 그런 디렉터리가 없어 폼 제출 시
   `java.lang.IllegalArgumentException: Not a directory: C:\Users\crush\...` → 500이 난다.
   (상품 등록/편집 **화면**은 열리지만, `multipart/form-data` 제출은 실패해 DB에 아무것도 들어가지 않는다. 실측 확인함.)
2. **로그 파일 위치** — `WEB-INF/web.xml`의 `LogFileFilter` 초기 파라미터가 `c:\logs\healthlog.log`다.
   Linux에서는 이 문자열이 그대로 파일명이 되어 Tomcat 작업 디렉터리에 `c:\logs\healthlog.log`라는
   파일이 생긴다. `C:\logs` 같은 경로는 만들어지지 않는다.
3. **게시판 첨부파일 휘발** — `BoardController`가 `getServletContext().getRealPath("/uploads")`에 파일을 쓰므로
   전개된 웹앱 디렉터리에 저장된다. 컨테이너를 재생성하면 사라지고, 별도 볼륨으로 보존되지 않는다.
4. **시드 데이터가 비어 있는 도메인** — `insert.sql`은 `product` 1건과 `CONTENTS` 6건만 넣는다.
   `member`/`board`/`reviews`/`orders`는 비어 있어서, 게시판 글쓰기·리뷰·주문·장바구니는 회원가입 후에만 확인 가능하다.
   `admin` 계정도 시드에 없다. 다만 관리자 판별이 "로그인 ID가 `admin`인가"뿐이라 회원가입 화면에서 ID `admin`으로 가입하면
   관리자 화면(컨텐츠 관리)에 들어갈 수 있고, 상품 등록/편집/삭제 처리는 서버에서 로그인 여부조차 확인하지 않는다
   (`docs/si/01_현행분석서.md` C-05).
5. **평문 비밀번호** — `member.password`에 평문 저장, 로그인도 평문 비교. (AS-IS)
6. **SQL 문자열 결합** — `mvc/model/BoardDAO`의 검색 조건(`items`/`text`)이 문자열로 이어 붙는다. (AS-IS)
7. **커넥션 누수** — `templates/dbconn.jsp`는 커넥션을 열기만 하고, 닫는 일은 이를 include한 상품 화면 6개에 맡긴다.
   그중 4개는 `finally` 없이 정상 경로에서만 닫고, `product/processUpdateProduct.jsp`는 닫는 코드가 없다. (AS-IS)
8. **구식 드라이버 클래스명** — 일부 JSP는 `Class.forName("com.mysql.jdbc.Driver")`(8.0.33에 남아 있는 deprecated shim)를 쓴다. (AS-IS)
9. **로컬 검증 전용** — HTTP(비TLS), root 계정 + 약한 비밀번호 하드코딩, 에러 스택 나열 등 운영 환경에 그대로 쓰면 안 된다.
10. **첫 요청이 느림** — JSP는 톰캣이 최초 요청 시 컴파일한다. 스모크 스크립트가 180초까지 폴링하는 이유다.

## 불변 조건

- `BuildYourHealth/` 아래의 파일은 이 환경 때문에 **변경되지 않는다** (`git status --porcelain -- BuildYourHealth/`가 비어 있어야 한다).
- 이 디렉터리(`asis-runtime/`) 밖에는 아무것도 추가하지 않는다.
