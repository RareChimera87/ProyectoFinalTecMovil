#!/usr/bin/env python3
"""Contador de features pequeñas para el workflow `review-counter` (E0.6, PLAN.md §5).

Decide si hay que abrir el issue 'Revisión de 3 features pequeñas'. No llama a GitHub: el
workflow le pasa los datos ya descargados con `gh`, así la lógica se prueba con fixtures
(scripts/test/review-counter.test.sh).

Regla:
- Frontera = lo más reciente entre (a) la última revisión de tipo `3 pequeñas` anotada en
  docs/revisiones.md (fecha AAAA-MM-DD; cuenta desde el día siguiente, hora de Bogotá) y
  (b) la creación del último issue de revisión de 3 pequeñas (abierto o cerrado).
- Se cuentan los PR mezclados a dev desde rama `feature/*`, sin la etiqueta `major` y
  mezclados después de la frontera.
- Con 3 o más se abre UN issue con esos PR; como el issue nuevo mueve la frontera, una
  segunda ejecución (o un re-run) no abre otro.
- Dueño de la ola: el dueño (I1, I2, I3) que backlog.md §2 da a la feature que completa el
  lote (la última mezclada); su usuario de GitHub sale de DUENOS_GITHUB (JSON) o, si no
  está, se asigna al dueño del repo (I1).

Uso:
  review-counter.py --revisiones docs/revisiones.md --backlog docs/backlog.md \
      --prs prs.json --issues issues.json [--duenos '{"I1":"user"}']
Salida (stdout): JSON con accion ("abrir" | "nada"), conteo, frontera, prs, y para "abrir"
también titulo, cuerpo, dueno e asignado.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

UMBRAL = 3
TITULO = "Revisión de 3 features pequeñas"
BOGOTA = timezone(timedelta(hours=-5))
DUENO_REPO_POR_DEFECTO = {"I1": "RareChimera87"}


def parse_ts(valor: str) -> datetime:
    return datetime.fromisoformat(valor.replace("Z", "+00:00"))


def frontera_revisiones(texto: str) -> datetime | None:
    """Última fecha de revisión `3 pequeñas` en docs/revisiones.md (línea del contador o tabla)."""
    fechas: list[str] = []
    m = re.search(r"Última revisión de 3 features pequeñas:\s*(\d{4}-\d{2}-\d{2})", texto)
    if m:
        fechas.append(m.group(1))
    for linea in texto.splitlines():
        if not linea.startswith("|"):
            continue
        celdas = [c.strip() for c in linea.strip().strip("|").split("|")]
        if len(celdas) >= 3 and celdas[1].lower().strip("`") == "3 pequeñas":
            f = re.search(r"\d{4}-\d{2}-\d{2}", celdas[2])
            if f:
                fechas.append(f.group(0))
    if not fechas:
        return None
    dia = max(fechas)
    # La revisión cubre todo ese día: se cuenta desde la medianoche siguiente en Bogotá.
    return datetime.fromisoformat(dia).replace(tzinfo=BOGOTA) + timedelta(days=1)


def frontera_issues(issues: list[dict]) -> datetime | None:
    fechas = [parse_ts(i["createdAt"]) for i in issues if i.get("title", "").startswith(TITULO)]
    return max(fechas) if fechas else None


def etiquetas(pr: dict) -> set[str]:
    return {(e["name"] if isinstance(e, dict) else e) for e in pr.get("labels", [])}


def duenos_backlog(texto: str) -> dict[str, tuple[str, str]]:
    """ID de feature -> (ola, dueño Ix) según la tabla 'Orden de ejecución' de backlog.md."""
    res: dict[str, tuple[str, str]] = {}
    for linea in texto.splitlines():
        celdas = [c.strip() for c in linea.strip().strip("|").split("|")]
        if len(celdas) >= 9 and re.fullmatch(r"E\d+\.\d+", celdas[3]):
            dueno = re.search(r"I\d", celdas[8])
            if dueno:
                res[celdas[3]] = (celdas[0], dueno.group(0))
    return res


def decidir(revisiones: str, backlog: str, prs: list[dict], issues: list[dict], duenos: dict[str, str]) -> dict:
    candidatas = [f for f in (frontera_revisiones(revisiones), frontera_issues(issues)) if f]
    frontera = max(candidatas) if candidatas else None

    pequenas = sorted(
        (
            pr for pr in prs
            if pr.get("mergedAt")
            and pr.get("baseRefName", "dev") == "dev"
            and pr.get("headRefName", "").startswith("feature/")
            and "major" not in etiquetas(pr)
            and (frontera is None or parse_ts(pr["mergedAt"]) > frontera)
        ),
        key=lambda pr: parse_ts(pr["mergedAt"]),
    )
    resultado = {
        "accion": "nada",
        "conteo": len(pequenas),
        "frontera": frontera.isoformat() if frontera else None,
        "prs": [pr["number"] for pr in pequenas],
    }
    if len(pequenas) < UMBRAL:
        return resultado

    tabla = duenos_backlog(backlog)
    ultima = pequenas[-1]
    m = re.match(r"(E\d+\.\d+)", ultima.get("title", ""))
    ola, dueno = tabla.get(m.group(1) if m else "", ("?", "I1"))
    mapa = {**DUENO_REPO_POR_DEFECTO, **duenos}
    asignado = mapa.get(dueno) or mapa["I1"]

    lineas = "\n".join(f"- [ ] #{pr['number']} {pr.get('title', '')}" for pr in pequenas)
    cuerpo = (
        f"Se mezclaron {len(pequenas)} features pequeñas (no major) a `dev` desde la última revisión"
        f" ({resultado['frontera'] or 'sin revisiones previas'}):\n\n{lineas}\n\n"
        f"Dueño de la ola {ola}: {dueno} (@{asignado}).\n\n"
        "Qué hacer (PLAN.md §5, punto 3): revisión en busca de errores sobre `dev` con el diff de estos PR,"
        " en la ventana de revisión de la ola. Anota la revisión en `docs/revisiones.md`"
        " (tipo `3 pequeñas`, fecha AAAA-MM-DD) y corrige los hallazgos en ramas `fix/<kebab>`."
        " Cierra este issue al terminar.\n\n_Abierto por el workflow `review-counter`._"
    )
    resultado.update(
        accion="abrir",
        titulo=f"{TITULO} (ola {ola})",
        cuerpo=cuerpo,
        dueno=dueno,
        asignado=asignado,
    )
    return resultado


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--revisiones", required=True)
    p.add_argument("--backlog", required=True)
    p.add_argument("--prs", required=True, help="JSON de gh pr list --json number,title,mergedAt,labels,headRefName,baseRefName")
    p.add_argument("--issues", required=True, help="JSON de gh issue list --json number,title,state,createdAt")
    p.add_argument("--duenos", default="{}", help='JSON {"I1":"login",...}; vacío = solo el dueño del repo')
    a = p.parse_args(argv)

    def leer(ruta: str) -> str:
        f = Path(ruta)
        return f.read_text(encoding="utf-8") if f.exists() else ""

    duenos = json.loads(a.duenos or "{}")
    res = decidir(
        leer(a.revisiones),
        leer(a.backlog),
        json.loads(leer(a.prs) or "[]"),
        json.loads(leer(a.issues) or "[]"),
        {k: v for k, v in duenos.items() if v},
    )
    json.dump(res, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
