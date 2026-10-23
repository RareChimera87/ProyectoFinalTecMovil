#!/usr/bin/env python3
"""Pruebas estáticas de los workflows de E0.6 (revisión post-merge, ronda 1).

Cada prueba cubre un hallazgo: contrato de migration-order, pipefail en infra-ci, concurrencia de
review-counter, revision-agente con la base, salvaguarda de auto-merge y secretos de WIF documentados.
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
    def test_corre_con_el_codigo_de_la_base(self):
        wf = cargar("revision-agente.yml")
        on = disparadores(wf)
        self.assertIn("pull_request_target", on)
        self.assertNotIn("pull_request", on)
        for p in pasos(wf):
            if str(p.get("uses", "")).startswith("actions/checkout"):
                self.assertNotIn("ref", p.get("with", {}), "no hacer checkout del head del PR")

    def test_trae_el_head_solo_para_el_diff(self):
        texto = (WF / "revision-agente.yml").read_text(encoding="utf-8")
        self.assertIn("refs/pull/", texto)


class AutoMerge(unittest.TestCase):
    def setUp(self):
        self.wf = cargar("auto-merge.yml")
        self.texto = (WF / "auto-merge.yml").read_text(encoding="utf-8")

    def test_workflow_de_la_base(self):
        self.assertIn("pull_request_target", disparadores(self.wf))

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
