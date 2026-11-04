#!/usr/bin/env node
// C-B9: genera public/.well-known/assetlinks.json para un entorno a partir de assetlinks/<entorno>.json.
//   dev:  solo me.santiagoreina.parcheu.dev con la huella de debug.
//   prod: solo me.santiagoreina.parcheu; sustituye el marcador PLAY_APP_SIGNING_SHA256 por la huella de
//         Play App Signing (variable de entorno del mismo nombre) y falla si falta o está mal formada.
// Uso: node render-assetlinks.mjs <dev|prod> [--stdout]
import { readFileSync, writeFileSync } from 'node:fs';

const MARCADOR = 'PLAY_APP_SIGNING_SHA256';
const HUELLA = /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/;

export function render(entorno, env = process.env) {
  if (!['dev', 'prod'].includes(entorno)) throw new Error('uso: render-assetlinks.mjs <dev|prod> [--stdout]');
  const fuente = JSON.parse(readFileSync(new URL(`./assetlinks/${entorno}.json`, import.meta.url), 'utf8'));
  for (const s of fuente) {
    s.target.sha256_cert_fingerprints = s.target.sha256_cert_fingerprints.map((f) => {
      if (f !== MARCADOR) return f;
      const valor = (env[MARCADOR] ?? '').trim().toUpperCase();
      if (!HUELLA.test(valor)) throw new Error(`${MARCADOR} falta o no es una huella SHA-256 (AA:BB:...)`);
      return valor;
    });
  }
  return JSON.stringify(fuente, null, 2) + '\n';
}

if (import.meta.url === `file://${process.argv[1]}`) {
  try {
    const salida = render(process.argv[2]);
    if (process.argv.includes('--stdout')) process.stdout.write(salida);
    else writeFileSync(new URL('./public/.well-known/assetlinks.json', import.meta.url), salida);
  } catch (e) {
    console.error(e.message);
    process.exit(1);
  }
}
