// C-M4: admin.html no envía el token de admin a una URL escrita a mano, no tiene código en línea
// (la CSP no lleva 'unsafe-inline') y el SDK de gstatic se carga con SRI. No necesita el emulador.
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const leer = (ruta) => readFileSync(new URL(ruta, import.meta.url), 'utf8');
const config = JSON.parse(leer('../firebase.json'));
const configProd = JSON.parse(leer('../firebase.prod.json'));
const html = leer('../public/admin.html');
const js = leer('../public/admin.js');

describe('firebase.json', () => {
  it('dev no reescribe /v1/** (no hay servicio api en Cloud Run de parcheu-dev; el deploy fallaría)', () => {
    assert.ok(!config.hosting.rewrites.some((x) => x.run || x.source === '/v1/**'));
  });

  it('prod reescribe /v1/** al servicio api de Cloud Run y renderiza assetlinks antes de desplegar', () => {
    const r = configProd.hosting.rewrites.find((x) => x.source === '/v1/**');
    assert.deepEqual(r?.run, { serviceId: 'api', region: 'us-central1' });
    assert.deepEqual(configProd.hosting.predeploy, ['node render-assetlinks.mjs prod']);
  });

  it('prod es dev más el rewrite y el predeploy, nada más', () => {
    const base = structuredClone(configProd);
    base.hosting.rewrites = base.hosting.rewrites.filter((x) => x.source !== '/v1/**');
    delete base.hosting.predeploy;
    assert.deepEqual(base, config);
  });

  it('/admin* lleva CSP estricta y cabeceras de aislamiento', () => {
    const h = config.hosting.headers.find((x) => x.source === '/admin*');
    const v = Object.fromEntries(h.headers.map((x) => [x.key, x.value]));
    const csp = v['Content-Security-Policy'];
    for (const d of ["default-src 'self'", "frame-ancestors 'none'", "base-uri 'none'", "form-action 'self'"]) {
      assert.ok(csp.includes(d), d);
    }
    assert.ok(!csp.includes('unsafe-inline') && !csp.includes('unsafe-eval'));
    assert.ok(!csp.includes('*.googleapis.com') && !csp.includes('*.web.app'), 'hosts exactos');
    for (const d of ['https://securetoken.googleapis.com', 'https://identitytoolkit.googleapis.com', 'https://apis.google.com']) {
      assert.ok(csp.includes(d), d);
    }
    assert.equal(v['X-Content-Type-Options'], 'nosniff');
    assert.equal(v['Referrer-Policy'], 'no-referrer');
    assert.equal(v['X-Robots-Tag'], 'noindex');
  });
});

describe('admin.html', () => {
  it('no tiene scripts, estilos ni manejadores en línea', () => {
    for (const m of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/g)) {
      assert.match(m[1], /\bsrc=/, 'script sin src');
      assert.equal(m[2].trim(), '');
    }
    assert.ok(!/<style\b/i.test(html));
    assert.ok(!/\sstyle=|\son[a-z]+=/i.test(html));
  });

  it('los scripts de gstatic llevan SRI y crossorigin', () => {
    const externos = [...html.matchAll(/<script\b[^>]*src="(https:[^"]+)"[^>]*>/g)];
    assert.ok(externos.length >= 2);
    for (const [tag, src] of externos) {
      assert.ok(src.startsWith('https://www.gstatic.com/firebasejs/'), src);
      assert.match(tag, /integrity="sha384-[A-Za-z0-9+/]{64}"/);
      assert.match(tag, /crossorigin="anonymous"/);
    }
  });

  it('llama a la API por rutas relativas, sin campo de URL ni almacenamiento', () => {
    assert.ok(!html.includes('id="api"'));
    assert.ok(!/localStorage|import .* from "https?:/.test(js));
    assert.ok(js.includes('fetch(ruta,'));
    assert.ok(js.includes('/v1/admin/reports'));
  });
});
