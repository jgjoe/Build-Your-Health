#!/usr/bin/env bash
# AS-IS smoke verification for the BuildYourHealth Docker stack.
#
#   bash asis-runtime/smoke.sh            # stack must already be running
#   BASE_URL=http://localhost:8080 bash asis-runtime/smoke.sh
#
# Polls up to 180 s until the app answers, then runs five checks against the
# unmodified application. Exits non-zero on the first failure.
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
CTX="/BuildYourHealth"
WAIT_SECONDS="${WAIT_SECONDS:-180}"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

dump() { # dump <file> : show the beginning of a response body on failure
    echo "--- first 40 lines of the response ---" >&2
    sed -n '1,40p' "$1" >&2
    echo "--------------------------------------" >&2
}

fetch() { # fetch <path> <outfile> ; prints the HTTP status code
    curl -sS --connect-timeout 5 --max-time 60 \
        -o "$2" -w '%{http_code}' "$BASE_URL$1" 2>"$WORK_DIR/curl.err" || true
}

# ---------------------------------------------------------------- ready poll
echo "== waiting for the app: $BASE_URL$CTX/user/welcome.jsp (max ${WAIT_SECONDS}s)"
deadline=$((SECONDS + WAIT_SECONDS))
status="000"
while :; do
    status="$(fetch "$CTX/user/welcome.jsp" "$WORK_DIR/welcome.html")"
    [ "$status" = "200" ] && break
    if [ "$SECONDS" -ge "$deadline" ]; then
        echo "last curl error: $(cat "$WORK_DIR/curl.err" 2>/dev/null)" >&2
        fail "app did not answer with HTTP 200 within ${WAIT_SECONDS}s (last status: $status)"
    fi
    sleep 3
done

# ------------------------------------------------------------------- checks
check_200_with() { # check_200_with <path> <needle> <n>
    local path="$1" needle="$2" n="$3"
    local body="$WORK_DIR/body-$n.html" status
    status="$(fetch "$path" "$body")"
    if [ "$status" != "200" ]; then
        dump "$body"
        fail "GET $path -> HTTP $status (expected 200)"
    fi
    if ! grep -qF -- "$needle" "$body"; then
        dump "$body"
        fail "GET $path -> body does not contain '$needle'"
    fi
    echo "PASS $n/5 GET $path -> 200 and contains '$needle'"
}

echo "PASS 1/5 GET $CTX/user/welcome.jsp -> 200 (waited $((SECONDS))s)"
check_200_with "$CTX/product/products.jsp" '동원샘물' 2
check_200_with "$CTX/content/contents.jsp" '물 섭취의 중요성' 3

# Compiled servlet mvc.controller.BoardController (url-pattern *.do).
board_body="$WORK_DIR/board-list.html"
status="$(fetch "$CTX/BoardListAction.do?pageNum=1" "$board_body")"
if [ "$status" != "200" ]; then
    dump "$board_body"
    fail "GET $CTX/BoardListAction.do?pageNum=1 -> HTTP $status (expected 200)"
fi
if grep -qF -- "Exception" "$board_body"; then
    grep -nF -- "Exception" "$board_body" | head -5 >&2
    dump "$board_body"
    fail "GET $CTX/BoardListAction.do?pageNum=1 -> body contains 'Exception' (servlet/JSP error)"
fi
echo "PASS 4/5 GET $CTX/BoardListAction.do?pageNum=1 -> 200 and no 'Exception'"

# Compiled dao.ProductRepository + dto.Product via product detail page.
check_200_with "$CTX/product/product.jsp?id=P001" '동원샘물' 5

echo "== all 5 smoke checks passed"
