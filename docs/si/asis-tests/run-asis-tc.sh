#!/usr/bin/env bash
# AS-IS 테스트 시나리오(TC-101~TC-126) 실행 스크립트.
# 전제: `docker compose -f asis-runtime/compose.yaml up -d --build` 로 깨끗한 DB가 떠 있다.
# 결과: 각 TC의 관찰값을 표준 출력에 `TC-xxx | 관찰 | 판정` 형식으로 남긴다.
# 판정은 "현행 동작이 01 문서의 기술과 일치하는가"다(PASS = 기술과 일치, DIFF = 불일치).
set -u
BASE="http://localhost:8080/BuildYourHealth"
COMPOSE="docker compose -f asis-runtime/compose.yaml"
TMP="$(mktemp -d)"
A="$TMP/a.jar"; B="$TMP/b.jar"; N="$TMP/anon.jar"

sql() { $COMPOSE exec -T db mysql -uroot -p1234 -N -B HealthDB -e "$1" 2>/dev/null; }
get() { curl -s -b "$1" -c "$1" "$BASE$2"; }
code() { curl -s -o /dev/null -w '%{http_code}' -b "$1" -c "$1" "$BASE$2"; }
post() { local jar="$1" path="$2"; shift 2; curl -s -b "$jar" -c "$jar" -o /dev/null -w '%{http_code}' "$@" "$BASE$path"; }
report() { printf '%s | %s | %s\n' "$1" "$2" "$3"; }
# 한글 값은 파일로 넘긴다. Windows(Git Bash)에서 네이티브 curl 인자로 직접 넘기면
# 콘솔 코드페이지(CP949)로 바뀌어 서버(UTF-8)에 깨진 값이 들어간다.
# $(u8 …)는 서브셸에서 실행되므로 카운터 대신 mktemp로 매번 다른 파일을 만든다.
u8() { local f; f="$(mktemp "$TMP/u8-XXXXXX")"; printf '%s' "$1" > "$f"
  if command -v cygpath >/dev/null 2>&1; then cygpath -w "$f"; else echo "$f"; fi; }
hexof() { printf '%s' "$1" | od -An -tx1 | tr -d ' \n' | tr 'a-f' 'A-F'; }
judge() { if [ "$1" = "$2" ]; then echo PASS; else echo "DIFF(기대 $2)"; fi; }

signup() { # jar id name age
  post "$1" /member/processAddMember.jsp --data-urlencode "id=$2" --data-urlencode "password=pw1234" \
    --data-urlencode "name@$(u8 "$3")" --data-urlencode "gender@$(u8 남)" --data-urlencode "ageGroup=$4" \
    --data-urlencode "mail1=t" --data-urlencode "mail2=gmail.com" >/dev/null
}
login() { post "$1" /member/processLoginMember.jsp --data-urlencode "id=$2" --data-urlencode "password=pw1234" >/dev/null; }

# --- 회원 ---
signup "$A" tester_a "테스터A" 20s
r=$(sql "select concat(water_intake,'/',sleep_hours,'/',remaining_water) from member where id='tester_a'")
report TC-101 "가입 후 권장 물/수면/남은 물 = $r" "$(judge "$r" "2.5/8.0/0.0")"

before=$(sql "select count(*) from member where id='tester_a'")
signup "$N" tester_a "중복" 30s
after=$(sql "select count(*) from member where id='tester_a'")
report TC-102 "중복 ID 가입 시도 전후 행 수 = $before→$after" "$(judge "$after" "1")"

login "$A" tester_a
ok=$(get "$A" /user/welcome.jsp | grep -c "테스터A")
bad=$(curl -s -o /dev/null -w '%{redirect_url}' -b "$N" -c "$N" --data-urlencode "id=tester_a" --data-urlencode "password=wrong" "$BASE/member/processLoginMember.jsp" | grep -c "error=1")
report TC-103 "정상 로그인 후 환영 화면 이름 표시=$ok, 오답 비밀번호 시 error=1로 이동=$bad" "$(judge "$ok/$bad" "1/1")"

pct=$(get "$A" /user/welcome.jsp | grep -o 'aria-valuenow="[0-9]*"' | head -1 | grep -o '[0-9]*')
report TC-104 "가입 직후(기록 0건) 물 달성률 = ${pct}%" "$(judge "$pct" "100")"

post "$A" /user/updateUserMetrics.jsp -d consumedWater=1.0 -d sleptHours=2.0 >/dev/null
r1=$(sql "select concat(count(*),'/',sum(consumed_water)) from user_records where user_id='tester_a'")
m1=$(sql "select remaining_water from member where id='tester_a'")
post "$A" /user/updateUserMetrics.jsp -d consumedWater=2.0 -d sleptHours=0 >/dev/null
r2=$(sql "select count(*) from user_records where user_id='tester_a'")
report TC-105 "1.0L 기록 후 기록=$r1 남은 물=$m1, 초과(누적 3.0L>2.5L) 시도 후 기록 수=$r2" "$(judge "$m1/$r2" "1.5/1")"

post "$A" /user/resetUserMetrics.jsp >/dev/null
r=$(sql "select concat((select count(*) from user_records where user_id='tester_a'),'/',remaining_water) from member where id='tester_a'")
report TC-106 "초기화 후 기록 수/남은 물 = $r" "$(judge "$r" "0/2.5")"

# --- 상품·장바구니 ---
list=$(get "$N" /product/products.jsp | grep -c "동원샘물")
detail=$(get "$N" "/product/product.jsp?id=P001" | grep -c '{"sizes"')
[ "$list" -ge 1 ] && list=1
report TC-107 "목록에 시드 상품 표시(1=있음)=$list, 상세 옵션이 JSON 조각으로 표시=$detail" "$(judge "$list/$detail" "1/1")"

get "$A" "/cart/addCart.jsp?id=P001" >/dev/null; get "$A" "/cart/addCart.jsp?id=P001" >/dev/null
sum=$(get "$A" /cart/cart.jsp | grep -o '<th>[0-9]*</th>' | grep -o '[0-9]*' | tail -1)
report TC-108 "같은 상품 2회 담기 후 합계 = $sum (할인가 19490 × 2)" "$(judge "$sum" "38980")"

cid=$(get "$A" /cart/cart.jsp | grep -o 'deleteCart.jsp?cartId=[^"]*' | head -1)
get "$A" "/cart/$cid" >/dev/null
left=$(get "$A" /cart/cart.jsp | grep -c "removeCart.jsp?id=P001")
report TC-109 "'전체 삭제' 링크 실행 후 장바구니 잔여 항목 = $left" "$(judge "$left" "1")"

post "$A" /cart/updateCart.jsp -d id=P001 -d quantity=-3 >/dev/null
sum=$(get "$A" /cart/cart.jsp | grep -o '<th>-\?[0-9]*</th>' | grep -o '\-\?[0-9][0-9]*' | tail -1)
report TC-110 "수량 -3 서버 요청 후 합계 = $sum" "$(judge "$sum" "-58470")"
post "$A" /cart/updateCart.jsp -d id=P001 -d quantity=2 >/dev/null

# --- 주문 ---
post "$A" /order/processShippingInfo.jsp --data-urlencode "cartId=x" --data-urlencode "name@$(u8 테스터A)" \
  -d shippingDate=2026-12-01 --data-urlencode "country@$(u8 대한민국)" -d zipCode=12345 --data-urlencode "addressName@$(u8 '서울시 테스트로 1')" >/dev/null
get "$A" /order/orderConfirmation.jsp >/dev/null
get "$A" /order/thankCustomer.jsp >/dev/null
r=$(sql "select concat(count(*),'/',max(total_price)) from orders where user_id='tester_a'")
i=$(sql "select concat(count(*),'/',sum(quantity)) from order_items oi join orders o using(order_id) where o.user_id='tester_a'")
report TC-111 "주문 확정 후 주문 수/총액 = $r, 주문상품 행/수량 = $i" "$(judge "$r|$i" "1/38980|1/2")"

get "$A" /order/thankCustomer.jsp >/dev/null
r=$(sql "select group_concat(total_price order by order_id) from orders where user_id='tester_a'")
report TC-112 "감사 화면 새로고침 후 주문 총액 목록 = $r" "$(judge "$r" "38980,0")"

h=$(get "$A" /order/orderHistory.jsp | grep -c "주문번호:")
report TC-113 "주문 내역 화면의 주문 수 = $h" "$(judge "$h" "2")"

oid=$(sql "select min(order_id) from orders where user_id='tester_a'")
signup "$B" tester_b "테스터B" 30s; login "$B" tester_b
get "$B" "/order/deleteOrder.jsp?orderId=$oid" >/dev/null
r=$(sql "select concat((select count(*) from orders where order_id=$oid),'/',(select count(*) from order_items where order_id=$oid))")
report TC-114 "다른 회원(B)이 A의 주문 $oid 삭제 요청 후 주문/주문상품 행 = $r" "$(judge "$r" "1/0")"

sql "insert into product(product_id,product_name,regular_price,discount_price,manufacturer,arrival_date) values('P999','delete-test',1000,900,'test','2026-12-31')"
get "$N" "/product/deleteProduct.jsp?id=P999" >/dev/null
r=$(sql "select count(*) from product where product_id='P999'")
report TC-115 "비로그인 상태로 상품 삭제 처리 호출 후 P999 행 수 = $r" "$(judge "$r" "0")"

# --- 리뷰 ---
post "$A" /review/processReview.jsp -d productId=P001 --data-urlencode "review@$(u8 'A가 쓴 시험 리뷰입니다')" >/dev/null
rid=$(sql "select max(review_id) from reviews where user_id='tester_a'")
get "$B" "/review/deleteReview.jsp?reviewId=$rid&productId=P001" >/dev/null
r=$(sql "select count(*) from reviews where review_id=$rid")
report TC-116 "B가 A의 리뷰 $rid 삭제 요청 후 행 수 = $r" "$(judge "$r" "0")"

# --- 게시판 ---
curl -s -b "$A" -c "$A" -o /dev/null -F "id=tester_a" -F "name=<$(u8 테스터A)" -F "subject=<$(u8 '첫 글')" -F "content=<$(u8 본문입니다)" "$BASE/BoardWriteAction.do"
curl -s -b "$A" -c "$A" -o /dev/null -F "id=tester_a" -F "name=<$(u8 테스터A)" -F "subject=<$(u8 '둘째 글')" -F "content=<$(u8 본문2)" "$BASE/BoardWriteAction.do"
r=$(sql "select concat(count(*),'/',ifnull(max(regist_day),'NULL')) from board")
listed=$(get "$A" "/BoardListAction.do?pageNum=1" | grep -c "첫 글\|둘째 글")
report TC-117 "글 2건 작성 후 board 행 수/최근 등록일 = $r, 목록에 제목 표시 = $listed" "$( [ "${r%%/*}/$listed" = "2/2" ] && echo PASS || echo 'DIFF(기대 2건/2)')"

normal=$(get "$A" "/BoardListAction.do?pageNum=1&items=subject&text=zzz_none" | grep -c "BoardViewAction.do")
inj=$(curl -s -b "$A" -G "$BASE/BoardListAction.do" --data-urlencode "pageNum=1" --data-urlencode "items=subject" \
  --data-urlencode "text=zzz_none%' OR '1%'='1" | grep -c "BoardViewAction.do")
report TC-118 "검색어 일치 없음 결과 링크 수=$normal, 같은 검색어에 조건 문자열 덧붙인 결과 링크 수=$inj" "$(judge "$normal/$inj" "0/2")"

num=$(sql "select min(num) from board")
get "$B" "/BoardDeleteAction.do?num=$num&pageNum=1" >/dev/null
r=$(sql "select count(*) from board where num=$num")
report TC-119 "B가 A의 글 $num 삭제 요청 후 행 수 = $r" "$(judge "$r" "0")"

post "$B" /member/processUpdateMember.jsp -d id=tester_a -d password=pw1234 --data-urlencode "name@$(u8 변조됨)" \
  -d ageGroup=20s -d sleepHours=8 -d waterIntake=2.5 >/dev/null
r=$(sql "select hex(name) from member where id='tester_a'")
report TC-120 "B가 id=tester_a로 회원정보 수정 요청 후 A의 이름(hex) = $r" "$(judge "$r" "$(hexof 변조됨)")"

post "$B" /user/updateUserMetrics.jsp -d consumedWater=0.5 -d sleptHours=1 >/dev/null
get "$B" /member/deleteMember.jsp >/dev/null
r=$(sql "select count(*) from member where id='tester_b'")
report TC-121 "건강 기록 1건이 있는 B의 탈퇴 요청 후 회원 행 수 = $r" "$(judge "$r" "1")"

c=$(curl -s -o /dev/null -w '%{http_code}' -L -b "$A" "$BASE/content/manageContents.jsp")
report TC-122 "일반 회원의 콘텐츠 관리 접근 최종 응답 코드 = $c" "$(judge "$c" "404")"

X="$TMP/x.jar"; signup "$X" admin "아무나" 40s; login "$X" admin
c=$(get "$X" /content/manageContents.jsp | grep -c "editContent.jsp?id=")
report TC-123 "일반 가입 절차로 만든 ID 'admin' 계정의 콘텐츠 관리 화면 수정 링크 수 = $c" "$( [ "$c" -ge 1 ] && echo PASS || echo 'DIFF(기대 1 이상)')"

l=$($COMPOSE logs app 2>/dev/null | grep -c "요청 처리 소요 시간")
report TC-124 "앱 로그의 요청 처리 소요 시간 기록 줄 수 = $l" "$( [ "$l" -ge 1 ] && echo PASS || echo 'DIFF(기대 1 이상)')"

sql "insert into product(product_id,product_name,regular_price,discount_price,manufacturer) values('P998','no-arrival-date',1000,900,'test')"
r=$(get "$N" "/product/product.jsp?id=P998" | grep -c "도착 예정</b>: null")
report TC-125 "도착 예정일이 NULL인 상품 P998 상세 화면에 '도착 예정: null' 표시 = $r" "$(judge "$r" "1")"

c=$(curl -s -o "$TMP/p.html" -w '%{http_code}' -F productId=P997 -F productName=upload-test -F regularPrice=1000 \
  -F discountPrice=900 -F manufacturer=test -F "productImage=@$(u8 not-an-image)" "$BASE/product/processAddProduct.jsp")
m=$(grep -c "Not a directory" "$TMP/p.html"); r=$(sql "select count(*) from product where product_id='P997'")
report TC-126 "비로그인 상품 등록(이미지 첨부) 응답 코드/업로드 경로 오류 표시/저장 행 수 = $c/$m/$r" "$(judge "$c/$r" "500/0")"

rm -rf "$TMP"
