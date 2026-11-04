// C-B9: assetlinks por entorno. Lo publicado en el repo es el de dev; prod se genera al desplegar.
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { render } from '../render-assetlinks.mjs';

const publicado = readFileSync(new URL('../public/.well-known/assetlinks.json', import.meta.url), 'utf8');
const PLAY = 'AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99';
const paquetes = (txt) => JSON.parse(txt).map((s) => s.target.package_name);

describe('assetlinks por entorno', () => {
  it('lo publicado en el repo es exactamente el de dev', () => {
    assert.equal(publicado, render('dev', {}));
    assert.deepEqual(paquetes(publicado), ['me.santiagoreina.parcheu.dev']);
  });

  it('prod no autoriza el paquete .dev ni conserva el marcador', () => {
    const prod = render('prod', { PLAY_APP_SIGNING_SHA256: PLAY });
    assert.deepEqual(paquetes(prod), ['me.santiagoreina.parcheu']);
    assert.ok(!prod.includes('PLAY_APP_SIGNING_SHA256'));
    assert.ok(JSON.parse(prod)[0].target.sha256_cert_fingerprints.includes(PLAY));
  });

  it('prod falla sin la huella de Play App Signing o con una mal formada', () => {
    assert.throws(() => render('prod', {}), /PLAY_APP_SIGNING_SHA256/);
    assert.throws(() => render('prod', { PLAY_APP_SIGNING_SHA256: 'abc' }), /PLAY_APP_SIGNING_SHA256/);
  });

  it('dev no lleva la llave de debug en prod', () => {
    const debug = JSON.parse(render('dev', {}))[0].target.sha256_cert_fingerprints;
    const prod = JSON.parse(render('prod', { PLAY_APP_SIGNING_SHA256: PLAY }))[0].target.sha256_cert_fingerprints;
    for (const f of debug) assert.ok(!prod.includes(f));
  });
});
