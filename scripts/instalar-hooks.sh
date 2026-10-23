#!/usr/bin/env bash
# Instala el hook commit-msg local que ejecuta scripts/commit-lint.sh.
# El directorio de hooks es común a todos los worktrees, así que el hook no guarda una
# ruta fija: resuelve la raíz del worktree donde se hace el commit al ejecutarse.
set -euo pipefail
HOOK="$(git rev-parse --git-path hooks/commit-msg)"
mkdir -p "$(dirname "$HOOK")"
cat >"$HOOK" <<'EOF'
#!/usr/bin/env bash
# Instalado por scripts/instalar-hooks.sh.
root="$(git rev-parse --show-toplevel)"
lint="$root/scripts/commit-lint.sh"
if [[ ! -x "$lint" ]]; then
  echo "commit-msg: no existe $lint en este worktree (rama anterior a E0.1); se omite la validación del mensaje." >&2
  exit 0
fi
exec "$lint" "$1"
EOF
chmod +x "$HOOK"
echo "Hook commit-msg instalado en $HOOK"
