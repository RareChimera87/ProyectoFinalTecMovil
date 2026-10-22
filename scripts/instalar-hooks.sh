#!/usr/bin/env bash
# Instala el hook commit-msg local que ejecuta scripts/commit-lint.sh.
set -euo pipefail
RAIZ="$(git rev-parse --show-toplevel)"
HOOK="$(git rev-parse --git-path hooks/commit-msg)"
mkdir -p "$(dirname "$HOOK")"
cat >"$HOOK" <<EOF
#!/usr/bin/env bash
exec "$RAIZ/scripts/commit-lint.sh" "\$1"
EOF
chmod +x "$HOOK"
echo "Hook commit-msg instalado en $HOOK"
