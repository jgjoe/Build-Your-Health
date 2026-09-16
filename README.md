# Build Your Health

**JSP/Servlet만으로 회원·상품·주문·리뷰·게시판·관리자까지 구현한 건강 관리 웹 애플리케이션 (업무 도메인 8개, JSP 54개)**

[![Stack](https://img.shields.io/badge/Java-JSP%20%2F%20Servlet-007396?logo=openjdk&logoColor=white)](#기술-스택)
[![DB](https://img.shields.io/badge/MySQL-JDBC-4479A1?logo=mysql&logoColor=white)](#기술-스택)
[![Server](https://img.shields.io/badge/Tomcat-10.1-F8DC75?logo=apachetomcat&logoColor=black)](#실행)
[![Scope](https://img.shields.io/badge/scope-풀스택%20단독-informational)](#범위와-조건)

물 섭취량과 수면 시간을 기록하고, 건강 상품을 탐색·주문하고, 게시판에서 정보를 나누는 웹 애플리케이션입니다.
화면·서버·DB·관리자 기능까지 **혼자 만들었습니다.**

---

## 프레임워크 없이 만들었고, 그래서 알게 된 것

학부 수업 과제로 시작해 **Spring 없이 JSP와 Servlet만으로** 구성했습니다.
프레임워크가 대신해 주던 일을 직접 해야 했고, 그 과정에서 다음을 손으로 만들었습니다.

- **요청 흐름** — 요청을 받아 DB에 닿고 화면으로 돌아가는 경로를 화면마다 직접 연결
- **데이터 접근 분리** — 게시판은 `mvc/model/BoardDAO`+`BoardDTO`, 상품 조회 일부는 `dao/ProductRepository`+`dto/Product`로 JSP 밖으로 뺐습니다 (전부는 아닙니다 — 아래 설계 판단 참조)
- **공통 처리** — 서블릿 필터로 모든 요청의 접속 IP·URL 경로·**처리 소요 시간(ms)** 을 남기도록 구성 (`filter/LogFilter`, `LogFileFilter`)

나중에 Spring MVC를 쓸 때 `DispatcherServlet`이 무엇을 대신하고 있는지, 필터가 요청 앞단에서 왜 필요한지를
**이 구조를 직접 만들어 본 경험 위에서** 이해하게 됐습니다.

## 주요 기능

**사용자**

| 도메인 | 기능 |
|---|---|
| 건강 기록 | 하루 물 섭취량·수면 시간 입력, 목표 달성률 시각화 |
| 상품 | 건강 상품 목록·상세 조회 |
| 장바구니 | 담기·수량 변경·삭제 |
| 주문 | 배송 정보 입력, 주문 확인, 주문 내역, 주문 취소 |
| 리뷰 | 상품 후기 작성·수정·삭제 |
| 게시판 | 건강 정보 글쓰기·조회 |
| 회원 | 가입·로그인·정보 수정·탈퇴 |

**관리자**

상품 등록·수정·삭제, 추천 콘텐츠 등록·수정·삭제 관리

## 설계 판단

### 도메인별로 경로를 나눴다

`member` · `product` · `cart` · `order` · `review` · `board` · `content` · `user` 를 각각의 디렉토리로 분리했습니다.
기능이 하나 늘어도 다른 도메인 화면을 건드리지 않는 것이 목적이었습니다.

### 데이터 접근을 걷어내기 시작했지만, 대부분은 아직 JSP 안에 있다

JSP 안에 쿼리를 쓰면 화면 수정이 곧 데이터 수정이 됩니다. 그래서 **게시판은 `mvc/model/BoardDAO`+`BoardDTO`로,
상품 조회 일부는 `dao/ProductRepository`+`dto/Product`로** 옮겨 화면이 값만 받도록 했습니다.

**여기까지가 전부입니다.** `ProductRepository`를 실제로 쓰는 화면은 `product/product.jsp`와 `cart/addCart.jsp`
두 곳뿐이고, **나머지 화면 대부분은 JSP 안에서 직접 SQL을 실행합니다.** 회원 관련 화면 3곳은
JSTL SQL 태그(`sql:setDataSource`·`sql:query`)로, 그 외 다수는 JSP 안의 JDBC 코드로 조회합니다.

**한 프로젝트 안에 세 가지 방식이 공존합니다** — DAO 경유, JSP 내 JDBC, JSTL SQL 태그.
과제를 진행하며 방식을 바꿔 갔는데 앞서 만든 화면을 되돌려 정리하지 못한 결과입니다.
한 방식으로 모으는 것이 이 프로젝트의 가장 큰 정리 대상입니다.

### 요청 처리 시간을 로그로 남겼다

필터에서 요청 시작·종료 시각과 소요 시간을 찍도록 해서, 어떤 요청이 오래 걸리는지 눈으로 확인할 수 있게 했습니다.
느린 화면을 감이 아니라 로그로 찾는 습관이 여기서 시작됐습니다.

## 기술 스택

| 영역 | 기술 |
|---|---|
| 언어 | Java |
| 서버 | JSP, Servlet, Servlet Filter, Apache Tomcat 10.1 |
| 프론트 | HTML5, CSS3, JavaScript |
| 데이터 | MySQL (JDBC, 커넥터 8.0) |
| 구조 | DAO / DTO 분리, 도메인별 화면 구성 |

## 프로젝트 구조

```text
BuildYourHealth/src/main/
├── java/
│   ├── bundle/    한/영 메시지 프로퍼티
│   ├── dao/       상품 데이터 접근
│   ├── dto/       데이터 전달 객체
│   ├── filter/    요청 로깅 필터 2종
│   └── mvc/       게시판 컨트롤러·모델·DB 유틸
└── webapp/
    ├── member/ product/ cart/ order/ review/ board/ content/ user/   업무 도메인 8개
    ├── templates/   공통 메뉴·푸터·로그인·예외 화면과 DB 연결 include
    ├── resources/   CSS, JS, 이미지, SQL
    └── WEB-INF/     web.xml (서블릿 매핑·필터·에러 페이지)
```

## 실행

요구 환경: JDK · Apache Tomcat 10.1 · MySQL 8.0

1. `src/main/webapp/resources`의 SQL로 스키마와 초기 데이터 생성
2. DB 접속 정보 설정 — ⚠️ **16개 파일에 흩어져 있습니다.** `mvc/database/DBConnection.java`와 `templates/dbconn.jsp` 외에 회원·콘텐츠·건강 기록 화면의 JSP 14개가 각자 접속 정보를 들고 있습니다. 전부 바꿔야 동작합니다
3. Tomcat에 배포 후 `/user/welcome.jsp` 접속

## 범위와 조건

- **학부 과제로 만든 개인 프로젝트입니다.** 성능·부하 지표는 측정하지 않았습니다.
- 요청 처리 방식이 도메인마다 다릅니다 — 게시판만 서블릿 컨트롤러(`*.do`)를 두고, 나머지는 JSP 처리 페이지가 직접 처리합니다.
- **DB 접속 정보가 16개 파일에 하드코딩돼 있고, 데이터 접근 방식도 세 가지가 섞여 있습니다.** 과제 진행 중 방식을 바꾸면서 앞서 만든 화면을 되돌리지 못한 결과입니다. 지금 다시 만든다면 접속 설정을 한 곳으로 모으고 접근 계층을 하나로 통일하는 것부터 하겠습니다.
- 필터는 **요청 로깅용**입니다. 인증·인가는 필터가 아니라 각 화면의 세션 확인으로 처리했습니다.
- 저장소의 DB 계정은 **로컬 데모용 값**이며 운영 자격증명이 아닙니다.
- 커넥션 풀 없이 요청마다 직접 연결하고, 일부 화면은 커넥션을 닫지 않아 누수가 남습니다. 운영으로 옮긴다면 커넥션 풀과 트랜잭션 경계, CSRF 대응, 필터 기반 접근 제어가 먼저입니다 — **이 구조를 직접 만들어 봤기 때문에 무엇이 왜 필요한지 압니다.**

## 만든 사람

**Jigwan Joe** — Backend

- GitHub: [@jgjoe](https://github.com/jgjoe)
- Email: jigwan.joe@gmail.com
