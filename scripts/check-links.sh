#!/usr/bin/env bash
# Verifica que los enlaces internos de los Markdown resuelvan (E10.7).
#
# Uso: scripts/check-links.sh [raíz]   (por defecto la raíz del repo)
#
# Revisa README.md y docs/**/*.md (sin docs/backlog.md y docs/revisiones.md solo si no existen):
#   - enlaces [texto](ruta) relativos: el archivo o directorio debe existir;
#   - anclas (#sección o ruta#sección) hacia archivos .md: debe haber un encabezado con ese slug
#     (reglas de GitHub: minúsculas, sin signos, espacios a guiones).
# Ignora http(s), mailto, enlaces dentro de bloques de código y las rutas con '*' o '<...>'.
# Código 1 si hay enlaces rotos; 2 si falta python3.
set -uo pipefail
command -v python3 >/dev/null || { echo "Se necesita python3" >&2; exit 2; }
RAIZ="$(cd "${1:-$(dirname "${BASH_SOURCE[0]}")/..}" && pwd)"
exec python3 -I - "$RAIZ" <<'PY'
import os, re, sys, unicodedata

raiz = sys.argv[1]
archivos = []
if os.path.isfile(os.path.join(raiz, "README.md")):
    archivos.append(os.path.join(raiz, "README.md"))
for base, _, nombres in os.walk(os.path.join(raiz, "docs")):
    archivos += [os.path.join(base, n) for n in nombres if n.endswith(".md")]
archivos.sort()

def sin_bloques(texto):
    # Quita los bloques de código cercados (la valla abre y cierra al comienzo de línea).
    salida, dentro = [], False
    for linea in texto.split("\n"):
        if re.match(r"^\s*(```|~~~)", linea):
            dentro = not dentro
            continue
        if not dentro:
            salida.append(linea)
    return "\n".join(salida)

def sin_codigo(texto):
    # Además quita el código en línea para no validar ejemplos.
    return re.sub(r"`[^`\n]*`", "", sin_bloques(texto))

def slug(titulo):
    t = re.sub(r"<[^>]+>", "", titulo)
    t = re.sub(r"[`*_]", "", t)
    t = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", t)
    t = t.strip().lower()
    t = "".join(c for c in t if c.isalnum() or c in " -_" or unicodedata.category(c).startswith("M"))
    return t.replace(" ", "-")

cache = {}
def anclas(ruta):
    if ruta not in cache:
        with open(ruta, encoding="utf-8") as f:
            texto = sin_bloques(f.read())
        vistos, res = {}, set()
        for m in re.finditer(r"^#{1,6}\s+(.+?)\s*#*\s*$", texto, flags=re.M):
            s = slug(m.group(1))
            n = vistos.get(s, 0)
            res.add(s if n == 0 else f"{s}-{n}")
            vistos[s] = n + 1
        cache[ruta] = res
    return cache[ruta]

rotos = 0
total = 0
for arch in archivos:
    with open(arch, encoding="utf-8") as f:
        texto = sin_codigo(f.read())
    for m in re.finditer(r"(?<!!)\[[^\]]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)", texto):
        destino = m.group(1)
        if re.match(r"^(https?:|mailto:|tel:)", destino) or "*" in destino or "<" in destino:
            continue
        ruta, _, ancla = destino.partition("#")
        total += 1
        objetivo = arch if ruta == "" else os.path.normpath(os.path.join(os.path.dirname(arch), ruta))
        rel = os.path.relpath(arch, raiz)
        if not os.path.exists(objetivo):
            print(f"ROTO: {rel}: {destino} (no existe)", file=sys.stderr); rotos += 1; continue
        if ancla and objetivo.endswith(".md") and os.path.isfile(objetivo):
            if ancla.lower() not in anclas(objetivo):
                print(f"ROTO: {rel}: {destino} (no hay encabezado '{ancla}')", file=sys.stderr); rotos += 1

if rotos:
    print(f"check-links: {rotos} enlace(s) roto(s) de {total}", file=sys.stderr)
    sys.exit(1)
print(f"check-links: {total} enlaces internos en {len(archivos)} archivos, todos resuelven")
PY
