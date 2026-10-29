#!/usr/bin/env python3
"""Pruebas estáticas de los workflows de E0.6 (revisión post-merge, ronda 1).

Cada prueba cubre un hallazgo: contrato de migration-order, pipefail en infra-ci, concurrencia de
review-counter, revision-agente por etiqueta, major-gate sin segundo usuario, guardas de directorio, auto-merge apagado y secretos de WIF documentados.
Uso: python3 scripts/test/workflows.test.py   (requiere PyYAML; en CI lo instala workflows-ci)
"""
import re
import subprocess
import unittest
from pathlib import Path

import yaml

RAIZ = Path(__file__).resolve().parents[2]
WF = RAIZ / ".github" / "workflows"


def cargar(nombre):
    return yaml.safe_load((WF / nombre).read_text(encoding="utf-8"))


def disparadores(wf):
    return wf.get("on", wf.get(True))  # PyYAML lee `on:` como True


def pasos(wf):
    return [p for job in wf["jobs"].values() for p in job.get("steps", [])]


class MigrationOrder(unittest.TestCase):
    def test_usa_base_ref_no_argumento_posicional(self):
        texto = (WF / "migration-order.yml").read_text(encoding="utf-8")
        self.assertIn("scripts/check-migration-order.sh --base-ref origin/dev", texto)
        self.assertIsNone(re.search(r"check-migration-order\.sh\s+origin/", texto))

    def test_el_script_de_e04_acepta_la_bandera(self):
        script = RAIZ / "scripts" / "check-migration-order.sh"
        if not script.exists():
            self.skipTest("scripts/check-migration-order.sh lo agrega E0.4")
        self.assertIn("--base-ref)", script.read_text(encoding="utf-8"))


class InfraCi(unittest.TestCase):
    def test_tee_no_enmascara_el_fallo(self):
        paso = next(p for p in pasos(cargar("infra-ci.yml")) if p.get("id") == "checks")
        self.assertIn("| tee", paso["run"])
        seguro = paso.get("shell", "").startswith("bash") or "pipefail" in paso["run"]
        self.assertTrue(seguro, "el paso con `| tee` necesita shell: bash o set -o pipefail")

    def test_bash_con_pipefail_propaga_el_codigo(self):
        # Reproduce el hallazgo: `bash -e` devuelve 0; `shell: bash` de Actions (-eo pipefail), 3.
        cmd = "{ exit 3; } 2>&1 | tee /dev/null"
        sin = subprocess.run(["bash", "-e", "-c", cmd]).returncode
        con = subprocess.run(["bash", "-e", "-o", "pipefail", "-c", cmd]).returncode
        self.assertEqual((sin, con), (0, 3))


class ReviewCounter(unittest.TestCase):
    def test_concurrencia_no_es_de_workflow(self):
        self.assertNotIn("concurrency", cargar("review-counter.yml"))

    def test_el_job_del_merge_serializa_sin_cancelar_y_no_comparte_grupo(self):
        jobs = cargar("review-counter.yml")["jobs"]
        merge = jobs["review-counter"]["concurrency"]
        self.assertIs(merge["cancel-in-progress"], False)
        otros = jobs["pruebas"].get("concurrency", {}).get("group", "")
        self.assertNotEqual(merge["group"], otros)
        self.assertNotIn("pull_request.number", merge["group"])


class RevisionAgente(unittest.TestCase):
    """La revisión la hace el agente del workflow; el check solo exige la etiqueta revisado-agente."""

    def test_no_llama_a_ninguna_api_ni_usa_secretos(self):
        texto = (WF / "revision-agente.yml").read_text(encoding="utf-8")
        self.assertNotIn("ANTHROPIC_API_KEY", texto)
        self.assertNotIn("secrets.", texto)
        self.assertFalse((RAIZ / "scripts" / "revision-agente").exists())

    def test_exige_la_etiqueta_y_reacciona_a_labeled(self):
        wf = cargar("revision-agente.yml")
        tipos = disparadores(wf)["pull_request_target"]["types"]
        self.assertIn("labeled", tipos)
        self.assertIn("unlabeled", tipos)
        run = pasos(wf)[0]["run"]
        self.assertIn("revisado-agente", run)
        self.assertIn("exit 1", run)


class MajorGate(unittest.TestCase):
    def test_pasa_con_la_etiqueta_del_agente_y_no_pide_otro_usuario(self):
        texto = (WF / "major-gate.yml").read_text(encoding="utf-8")
        self.assertIn("revisado-agente", texto)
        self.assertNotIn("user.login", texto)
        self.assertNotIn("/reviews", texto)

    def test_gates_corren_desde_la_base_sin_checkout(self):
        # C-A1: con `pull_request` un PR reescribe su propio gate; con `pull_request_target` no.
        for nombre in ("revision-agente.yml", "major-gate.yml"):
            with self.subTest(nombre):
                wf = cargar(nombre)
                self.assertIn("pull_request_target", disparadores(wf))
                self.assertNotIn("pull_request", disparadores(wf))
                self.assertFalse([p for p in pasos(wf) if "uses" in p], "sin checkout ni acciones")
                paso = pasos(wf)[-1]
                self.assertIn("gobernanza", paso["run"])
                self.assertIn("proteger-ramas", paso["run"])
                self.assertIn("exit 1", paso["run"])


class AccionesFijadas(unittest.TestCase):
    """C-M3: toda acción externa va fijada a un SHA de 40 hex con comentario `# vX.Y.Z`."""

    def test_ningun_uses_externo_sin_sha(self):
        sin_fijar = []
        for f in sorted(WF.glob("*.yml")):
            for n, linea in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
                m = re.match(r"\s*(?:-\s+)?uses:\s*(\S+)(.*)$", linea)
                if not m or m.group(1).startswith("./"):
                    continue
                if not (re.search(r"@[0-9a-f]{40}$", m.group(1)) and re.match(r"\s*#\s*v\d+\.\d+", m.group(2))):
                    sin_fijar.append(f"{f.name}:{n}: {m.group(1)}")
        self.assertEqual(sin_fijar, [])

    def test_dependabot_actualiza_las_acciones_cada_semana(self):
        d = yaml.safe_load((RAIZ / ".github" / "dependabot.yml").read_text(encoding="utf-8"))
        act = [u for u in d["updates"] if u["package-ecosystem"] == "github-actions"]
        self.assertEqual([u["schedule"]["interval"] for u in act], ["weekly"])


class GuardasDeDirectorio(unittest.TestCase):
    # (archivo marcador, clave del filtro). Se comprueba un archivo y no el directorio porque E0.1
    # deja un README en cada carpeta antes de que exista el proyecto.
    CASOS = {
        "backend-ci.yml": ('-f "backend/gradlew"', "backend"),
        "contract-ci.yml": ('-f "contract/gradlew"', "contract"),
        "rules-ci.yml": ('-f "firebase/package.json"', "firebase"),
        "migration-order.yml": ('-d "backend/src/main/resources/db/migration"', "migraciones"),
        "infra-ci.yml": ('-f "infra/terraform/envs/dev/main.tf"', "infra"),
    }

    def test_verificar_se_omite_si_no_existe_el_directorio(self):
        for nombre, (directorio, clave) in self.CASOS.items():
            with self.subTest(nombre):
                wf = cargar(nombre)
                cambios = wf["jobs"]["cambios"]
                self.assertIn("existe", cambios["outputs"])
                paso = next(p for p in cambios["steps"] if p.get("id") == "existe")
                self.assertIn(directorio, paso["run"])
                verificar = wf["jobs"]["verificar" if "verificar" in wf["jobs"] else "static"]
                self.assertIn(f"needs.cambios.outputs.{clave} == 'true'", verificar["if"])
                self.assertIn("needs.cambios.outputs.existe == 'true'", verificar["if"])

    def test_plan_no_recibe_token_con_escritura(self):
        # C-M2: el job que ejecuta el HCL del PR no tiene pull-requests: write ni GH_TOKEN.
        wf = cargar("infra-ci.yml")
        plan = wf["jobs"]["plan-dev"]
        self.assertNotIn("pull-requests", plan["permissions"])
        self.assertNotIn("GH_TOKEN", plan.get("env", {}))
        self.assertIn("-lockfile=readonly", str(plan["steps"]))
        comenta = wf["jobs"]["comentario-plan-dev"]
        self.assertEqual(comenta["permissions"]["pull-requests"], "write")
        self.assertFalse([p for p in comenta["steps"] if "terraform" in str(p.get("uses", "")) or "checkout" in str(p.get("uses", ""))])

    def test_plan_dev_se_omite_sin_wif(self):
        wf = cargar("infra-ci.yml")
        self.assertIn("needs.cambios.outputs.wif == 'true'", wf["jobs"]["plan-dev"]["if"])
        self.assertNotIn("Requiere los secretos del bootstrap", str(wf["jobs"]["plan-dev"]["steps"]))


class SmokeE2e(unittest.TestCase):
    """E10.6: deploy-dev corre el smoke e2e tras desplegar y falla si el smoke falla."""

    def setUp(self):
        self.wf = cargar("deploy-dev.yml")
        self.job = self.wf["jobs"]["e2e"]
        self.texto = (WF / "deploy-dev.yml").read_text(encoding="utf-8")

    def test_corre_despues_del_despliegue_y_sin_tolerar_fallos(self):
        self.assertEqual(self.job["needs"], "api")
        self.assertNotIn("continue-on-error", self.texto)
        corridas = [p.get("run", "") for p in self.job["steps"]]
        self.assertTrue(any("./gradlew --stacktrace e2eTest" in c for c in corridas))

    def test_con_secretos_el_smoke_es_obligatorio_y_sin_ellos_se_omite(self):
        self.assertIn("E2E_REQUIRED=true", self.texto)
        guarda = next(p for p in self.job["steps"] if p.get("id") == "guarda")
        self.assertIn("listo=false", guarda["run"])
        self.assertNotIn("exit 1", guarda["run"])
        gradle = next(p for p in self.job["steps"] if p.get("name") == "Smoke e2e")
        self.assertIn("steps.guarda.outputs.listo == 'true'", gradle["if"])

    def test_no_expone_credenciales_en_el_flujo(self):
        self.assertNotRegex(self.texto, r"credentials_json")
        self.assertIn("::add-mask::", self.texto)


class AutoMerge(unittest.TestCase):
    def setUp(self):
        self.wf = cargar("auto-merge.yml")
        self.texto = (WF / "auto-merge.yml").read_text(encoding="utf-8")

    def test_workflow_de_la_base(self):
        self.assertIn("pull_request_target", disparadores(self.wf))

    def test_apagado_salvo_variable_explicita(self):
        self.assertIn("vars.AUTO_MERGE_ENABLED == 'true'", self.wf["jobs"]["auto-merge"]["if"])

    def test_exige_la_proteccion_de_dev_antes_de_activar(self):
        self.assertIn("revision-agente major-gate", self.texto)
        self.assertLess(self.texto.index("required_status_checks"), self.texto.index("--auto --squash"))

    def test_pr_que_tocan_la_ci_no_se_mezclan_solos(self):
        patron = re.search(r"sensibles='([^']+)'", self.texto).group(1)
        rx = re.compile(patron)
        for ruta in [".github/workflows/android-ci.yml", "scripts/revision-agente/revisar.py",
                     "scripts/review-counter.py", ".github/CODEOWNERS"]:
            self.assertTrue(rx.search(ruta), ruta)
        for ruta in ["android/app/build.gradle.kts", "docs/backlog.md", "scripts/check-plan.sh"]:
            self.assertFalse(rx.search(ruta), ruta)
        self.assertLess(self.texto.index("sensibles="), self.texto.index("--auto --squash"))


    def test_falla_cerrado_si_gh_falla(self):
        paso = self.wf["jobs"]["auto-merge"]["steps"][0]
        self.assertTrue(paso.get("shell", "").startswith("bash"), "necesita shell: bash (pipefail)")
        self.assertNotRegex(self.texto, r"gh pr diff[^\n]*\|\s*grep")
        self.assertRegex(self.texto, r'archivos="\$\(gh api --paginate[^\n]*\)" \|\| exit 1')

    def test_desactiva_el_auto_merge_previo_al_salir_sin_activarlo(self):
        run = self.wf["jobs"]["auto-merge"]["steps"][0]["run"]
        desactivar = 'gh pr merge "$PR" -R "$GITHUB_REPOSITORY" --disable-auto'
        self.assertEqual(run.count(desactivar), 2, "protección ausente y archivos sensibles")
        self.assertLess(run.index(desactivar), run.index("--add-label major"))
        self.assertLess(run.rindex(desactivar), run.rindex("--auto --squash"))

    def test_falla_cerrado_se_comporta_asi_en_bash(self):
        cmd = 'a="$(false)" || exit 1; echo siguio'
        r = subprocess.run(["bash", "-e", "-o", "pipefail", "-c", cmd], capture_output=True, text=True)
        self.assertEqual((r.returncode, r.stdout), (1, ""))


class PendientesHumanos(unittest.TestCase):
    def test_no_recomienda_code_owners_con_una_sola_cuenta(self):
        doc = (RAIZ / "docs" / "pendientes-humanos.md").read_text(encoding="utf-8")
        paso0 = doc[doc.index("0. **Correcciones"):doc.index("1. **Mezclar antes")]
        self.assertNotRegex(paso0, r"activa en la protecci[oó]n de dev\s+\*Require review from Code Owners\*")
        self.assertIn("--admin", paso0)
        self.assertIn("enforce_admins", paso0)


class SecretosWif(unittest.TestCase):
    def test_los_secretos_de_gcp_estan_documentados(self):
        usados = set()
        for f in WF.glob("*.yml"):
            usados |= set(re.findall(r"secrets\.((?:WIF|TF)_[A-Z_]+)", f.read_text(encoding="utf-8")))
        self.assertTrue(usados)
        arq = (RAIZ / "docs" / "arquitectura.md").read_text(encoding="utf-8")
        # Expande `NOMBRE_{DEV,PROD}` a los dos secretos.
        arq = re.sub(r"`(\w+)_\{DEV,PROD\}`", r"`\1_DEV` `\1_PROD`", arq)
        for nombre in usados:
            self.assertIn(f"`{nombre}`", arq, f"{nombre} falta en arquitectura.md §12")

    def test_ningun_secreto_es_una_llave_json(self):
        for f in WF.glob("*.yml"):
            self.assertNotRegex(f.read_text(encoding="utf-8"), r"credentials_json")


if __name__ == "__main__":
    unittest.main(verbosity=2)
