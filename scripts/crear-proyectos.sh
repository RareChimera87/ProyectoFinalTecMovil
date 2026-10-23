#!/usr/bin/env bash
# Crea los proyectos de GCP de ParcheU y los vincula a la cuenta de facturación.
# Usa el ADC/cuenta activa de gcloud del usuario (no una cuenta de servicio).
#
# Uso:
#   BILLING_ACCOUNT=XXXXXX-XXXXXX-XXXXXX scripts/crear-proyectos.sh [dev] [prod]
#
# Si el ID preferido (parcheu-<env>) está ocupado por otra organización, prueba
# con sufijos (parcheu-<env>-<n>) y escribe el ID real en ~/.parcheu/proyectos.env.
# También crea el bucket versionado del estado de Terraform (<proyecto>-tfstate).
# Ese ID es el que debe quedar en infra/terraform/envs/<env>/main.tf y en
# docs/arquitectura.md §12.
set -euo pipefail

: "${BILLING_ACCOUNT:?Define BILLING_ACCOUNT (gcloud billing accounts list)}"
ENVS=("$@")
[ ${#ENVS[@]} -eq 0 ] && ENVS=(dev prod)
OUT_DIR="${PARCHEU_HOME:-$HOME/.parcheu}"
OUT_FILE="$OUT_DIR/proyectos.env"
mkdir -p "$OUT_DIR"
touch "$OUT_FILE"
ERR="$(mktemp)"
trap 'rm -f "$ERR"' EXIT

project_is_mine() {
  gcloud projects describe "$1" --format='value(projectId)' >/dev/null 2>&1
}

for env in "${ENVS[@]}"; do
  case "$env" in dev|prod) ;; *) echo "Entorno inválido: $env" >&2; exit 2 ;; esac
  base="parcheu-$env"
  name="ParcheU ${env}"
  chosen=""
  for candidate in "$base" "$base-1" "$base-2" "$base-3" "$base-$RANDOM"; do
    if project_is_mine "$candidate"; then
      echo "El proyecto $candidate ya existe y es tuyo; se reutiliza."
      chosen="$candidate"; break
    fi
    if gcloud projects create "$candidate" --name="$name" --labels="app=parcheu,env=$env" 2>"$ERR"; then
      chosen="$candidate"; break
    fi
    echo "No se pudo crear $candidate: $(tail -1 "$ERR")" >&2
  done
  [ -n "$chosen" ] || { echo "No se pudo crear ningún proyecto para $env" >&2; exit 1; }

  current="$(gcloud billing projects describe "$chosen" --format='value(billingAccountName)' 2>/dev/null || true)"
  if [ "$current" != "billingAccounts/$BILLING_ACCOUNT" ]; then
    if ! gcloud billing projects link "$chosen" --billing-account="$BILLING_ACCOUNT" >/dev/null; then
      echo "No se pudo vincular la facturación de $chosen. Si el error es 'Cloud billing quota exceeded'," >&2
      echo "desvincula un proyecto que no uses (gcloud billing projects unlink <id>) o pide más cupo:" >&2
      echo "https://support.google.com/code/contact/billing_quota_increase" >&2
      exit 1
    fi
  fi
  # APIs mínimas para que Terraform pueda arrancar (el resto las activa modules/platform).
  gcloud services enable cloudresourcemanager.googleapis.com serviceusage.googleapis.com \
    iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com storage.googleapis.com \
    cloudbilling.googleapis.com billingbudgets.googleapis.com --project="$chosen"

  # Bucket del estado de Terraform (versionado). bootstrap/ lo importa y lo gestiona
  # después; se crea aquí para que el backend gcs exista desde el primer init.
  bucket="$chosen-tfstate"
  if ! gcloud storage buckets describe "gs://$bucket" >/dev/null 2>&1; then
    gcloud storage buckets create "gs://$bucket" --project="$chosen" --location=us-central1 \
      --uniform-bucket-level-access --public-access-prevention
  fi
  gcloud storage buckets update "gs://$bucket" --versioning >/dev/null

  var="PROJECT_ID_$(echo "$env" | tr '[:lower:]' '[:upper:]')"
  sed -i "/^$var=/d" "$OUT_FILE"
  echo "$var=$chosen" >>"$OUT_FILE"
  echo "Listo: $env → $chosen (registrado en $OUT_FILE)"
done
