#!/usr/bin/env bash
# Pruebas de scripts/grant-admin.sh con gcloud y curl falsos (nunca toca GCP).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/bin"
printf '#!/usr/bin/env bash\necho fake-token\n' >"$TMP/bin/gcloud"
# curl falso: responde accounts:lookup según el correo pedido y registra cada accounts:update.
cat >"$TMP/bin/curl" <<'FAKE'
#!/usr/bin/env bash
out=""; url=""; body=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -o) out="$2"; shift ;;
    -d) body="$2"; shift ;;
    http*) url="$1" ;;
  esac
  shift
done
echo "$url $body" >>"$FAKE_LOG"
case "$url" in
  *accounts:lookup)
    case "$body" in
      *ana@x.co*) echo '{"users":[{"localId":"uid-ana","customAttributes":"{\"plan\":\"x\"}"}]}' >"$out" ;;
      *beto@x.co*) echo '{"users":[{"localId":"uid-beto","customAttributes":"{\"admin\":true,\"plan\":\"y\"}"}]}' >"$out" ;;
      *) echo '{}' >"$out" ;;
    esac ;;
  *) echo '{}' >"$out" ;;
esac
printf '200'
FAKE
chmod +x "$TMP/bin/gcloud" "$TMP/bin/curl"
export FAKE_LOG="$TMP/log"

pass=0; failed=0
check() { # check <descripción> <condición>
  if eval "$2"; then pass=$((pass + 1)); echo "  ✓ $1"; else failed=$((failed + 1)); echo "  ✗ $1"; echo "$out" | sed 's/^/      /'; fi
}
run() { : >"$FAKE_LOG"; out="$(PATH="$TMP/bin:$PATH" "$ROOT/scripts/grant-admin.sh" "$@" 2>&1)"; rc=$?; }

echo "grant-admin.sh"
run dev
check "sin correos muestra el uso (2)" '[ "$rc" -eq 2 ]'
run qa ana@x.co
check "un entorno desconocido muestra el uso (2)" '[ "$rc" -eq 2 ]'

run dev ana@x.co
check "otorga admin conservando los otros claims" 'grep -q "accounts:update" "$FAKE_LOG" && grep -q "customAttributes" "$FAKE_LOG" && grep -q "plan" "$FAKE_LOG" && grep -q "uid-ana" "$FAKE_LOG" && [ "$rc" -eq 0 ]'
check "usa el proyecto parcheu-dev" 'grep -q "projects/parcheu-dev/accounts" "$FAKE_LOG"'

run --dry-run prod ana@x.co
check "dry-run no escribe y usa parcheu-prod" '! grep -q "accounts:update" "$FAKE_LOG" && grep -q "projects/parcheu-prod/accounts:lookup" "$FAKE_LOG" && grep -q "dry-run" <<<"$out" && [ "$rc" -eq 0 ]'

run --revoke dev beto@x.co
check "revoke quita admin y conserva plan" 'grep -q "accounts:update" "$FAKE_LOG" && grep -q "QUITA admin" <<<"$out" && grep -q "{\"plan\":\"y\"}" <<<"$out"'

run dev nadie@x.co ana@x.co
check "una cuenta inexistente falla (1) pero sigue con las demás" '[ "$rc" -eq 1 ] && grep -q "SIN CUENTA" <<<"$out" && grep -q "OTORGA admin a ana@x.co" <<<"$out"'

echo "$pass pasaron, $failed fallaron"
[ "$failed" -eq 0 ]
