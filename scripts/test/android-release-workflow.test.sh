#!/usr/bin/env bash
# Pruebas estructurales de .github/workflows/android-release.yml (E10.2): los secretos solo viven en
# environments, el disparo manual no publica ramas sin revisar y la concurrencia no desplaza corridas.
set -euo pipefail
raiz="$(cd "$(dirname "$0")/../.." && pwd)"
python3 - "$raiz/.github/workflows/android-release.yml" <<'PY'
import re, sys, yaml

wf = yaml.safe_load(open(sys.argv[1]))
texto = open(sys.argv[1]).read()
fallos = 0

def comprueba(nombre, condicion):
    global fallos
    print(("ok   - " if condicion else "FALLA - ") + nombre)
    if not condicion:
        fallos += 1

jobs = wf["jobs"]
pub = jobs["publicar"]

# 1. Los secretos de firma y de Play solo se leen en el job con environment.
for nombre, job in jobs.items():
    usa = re.search(r"secrets\.(UPLOAD_|PLAY_SERVICE|WIF_)", yaml.dump(job))
    if nombre == "publicar":
        comprueba("publicar declara environment", "environment" in job)
    else:
        comprueba(f"{nombre} no lee secretos de firma ni de Play", usa is None)
comprueba("environment alpha separado (con aprobación)", "play-alpha" in str(pub["environment"]) and "'play'" in str(pub["environment"]))
comprueba("publicar espera a parametros (validación sin secretos)", "parametros" in pub["needs"])
comprueba("parametros usa scripts/release-params.sh", "scripts/release-params.sh" in yaml.dump(jobs["parametros"]))
comprueba("parametros sin environment", "environment" not in jobs["parametros"])
comprueba("el PR nunca publica", "!= 'pull_request'" in str(pub.get("if", "")) or "!= 'pull_request'" in str(jobs["parametros"].get("if", "")))
comprueba("permisos globales de solo lectura", wf["permissions"] == {"contents": "read"})
comprueba("id-token solo en publicar", pub["permissions"].get("id-token") == "write" and "permissions" not in jobs["pruebas"])

# 2. Sin validación ad hoc de la ref dentro del workflow: la hace el script probado.
comprueba("sin secretos de repo en el paso de parámetros", "secrets." not in yaml.dump(jobs["parametros"]))

# 3. Concurrencia: grupos separados y comentario veraz.
grupo = wf["concurrency"]["group"]
comprueba("grupo propio para workflow_run (por commit del tag)", "workflow_run.head_sha" in grupo)
comprueba("grupo por pista en disparos manuales", "inputs.pista" in grupo)
comprueba("grupo por PR", "pull_request.number" in grupo)
comprueba("no cancela subidas en curso", wf["concurrency"]["cancel-in-progress"] is False)
comprueba("el comentario ya no promete una cola", "las corridas se encolan" not in texto)

# 3b. workflow_run corre con GITHUB_REF=refs/heads/main: play-alpha debe admitir main y la
# documentación de pendientes debe pedirlo; la primera corrida no usa la rama feature.
docs = open(sys.argv[1].replace(".github/workflows/android-release.yml", "docs/pendientes-humanos.md")).read()
comprueba("el workflow documenta que play-alpha admite main", "tags v* y\n# main" in texto)
comprueba("pendientes pide main en las políticas de play-alpha", "environments/play-alpha/deployment-branch-policies -f name=main" in docs)
comprueba("pendientes no manda correr desde la rama feature", "--ref feature/" not in docs)
comprueba("pendientes valida la primera corrida desde dev", "gh workflow run android-release --ref dev" in docs)

# 4. Credenciales de Play: WIF por defecto, sin llaves en el repo.
comprueba("usa WIF (google-github-actions/auth)", "google-github-actions/auth@" in texto)

sys.exit(1 if fallos else 0)
PY
