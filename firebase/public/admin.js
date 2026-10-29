// Página de moderación (E8.5). Inicia sesión con Firebase Auth (Google) y exige el claim `admin`
// (lo otorga scripts/grant-admin.sh). La configuración sale de /__/firebase/init.json, que Firebase
// Hosting sirve solo para el proyecto donde está desplegada. Todo texto de usuario se pinta con
// textContent: el snapshot de mensajes es contenido no confiable. El SDK (compat, 11.0.2) se carga
// con SRI desde admin.html y la CSP no permite scripts en línea. La API se llama siempre al mismo
// origen (/v1/**, rewrite de firebase.json a Cloud Run): el token de admin no sale a otra URL.

const $ = (id) => document.getElementById(id);
const ACCIONES = [
  ["DISMISS", "Descartar", ""],
  ["HIDE_MESSAGE", "Ocultar mensaje", ""],
  ["SUSPEND_24H", "Suspender 24 h", "danger"],
  ["BAN", "Banear", "danger"],
];
let auth;
let user = null;
let nextCursor = null;

function aviso(texto, error = false) {
  const el = $("aviso");
  el.hidden = !texto;
  el.textContent = texto || "";
  el.className = error ? "error" : "";
}

async function llamar(ruta, opciones = {}) {
  const token = await user.getIdToken();
  const res = await fetch(ruta, {
    ...opciones,
    headers: { Authorization: "Bearer " + token, "Content-Type": "application/json" },
  });
  const cuerpo = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(`${res.status} ${cuerpo.code || ""} ${cuerpo.message || ""}`.trim());
  return cuerpo;
}

function fecha(iso) { return iso ? new Date(iso).toLocaleString("es-CO") : ""; }

function el(tag, texto, clase) {
  const nodo = document.createElement(tag);
  if (texto !== undefined) nodo.textContent = texto;
  if (clase) nodo.className = clase;
  return nodo;
}

function pintarSnapshot(reporte) {
  const mensajes = Array.isArray(reporte.snapshot) ? reporte.snapshot : [];
  if (!mensajes.length) return null;
  const caja = el("div", undefined, "snap");
  for (const m of mensajes) {
    const p = el("p", m.hidden ? "(mensaje oculto)" : m.text, m.hidden ? "oculto" : "");
    if (m.id === reporte.messageId) p.classList.add("objetivo");
    const quien = reporte.kind === "AI_CONTENT" ? "IA" : m.senderId === null ? "sistema" : m.senderId === reporte.targetId ? "reportado" : "otra persona";
    p.title = `${quien} · ${fecha(m.createdAt)}`;
    if (!m.hidden) p.prepend(el("strong", quien + ": "));
    caja.append(p);
  }
  return caja;
}

function pintarReporte(r) {
  const tarjeta = el("article", undefined, "card" + (r.priority > 0 ? " alta" : ""));
  const cab = el("div");
  cab.append(el("span", r.kind, "tag"), el("span", r.reason, "tag"), el("span", r.source, "tag"));
  if (r.priority > 0) cab.append(el("span", "prioridad " + r.priority, "tag"));
  tarjeta.append(cab);
  tarjeta.append(el("p", `Reporte ${r.id} · ${fecha(r.createdAt)}`, "meta"));
  if (r.details) tarjeta.append(el("p", r.details));
  const ids = [];
  if (r.targetId) ids.push("reportado " + r.targetId);
  if (r.messageId) ids.push("mensaje " + r.messageId);
  if (r.simulationId) ids.push(`simulación ${r.simulationId} ` + (r.turnIndex === -1 ? "informe completo" : `turno ${r.turnIndex}`));
  if (ids.length) tarjeta.append(el("p", ids.join(" · "), "meta"));
  const snap = pintarSnapshot(r);
  if (snap) tarjeta.append(snap);

  if (r.status === "RESOLVED") {
    tarjeta.append(el("p", `Resuelto ${fecha(r.resolvedAt)}: ${r.resolution || ""}`, "meta"));
    return tarjeta;
  }
  const nota = el("input");
  nota.placeholder = "Nota (opcional)";
  nota.maxLength = 500;
  const fila = el("div", undefined, "acciones");
  fila.append(nota);
  for (const [accion, rotulo, clase] of ACCIONES) {
    const b = el("button", rotulo, clase);
    b.addEventListener("click", () => resolver(r, accion, nota.value, fila));
    fila.append(b);
  }
  tarjeta.append(fila);
  return tarjeta;
}

async function resolver(reporte, accion, nota, fila) {
  const sancion = accion === "SUSPEND_24H" || accion === "BAN";
  if (sancion && !confirm(`${accion} al usuario reportado. ¿Continuar?`)) return;
  fila.querySelectorAll("button").forEach((b) => (b.disabled = true));
  try {
    await llamar(`/v1/admin/reports/${encodeURIComponent(reporte.id)}/resolve`, {
      method: "POST",
      body: JSON.stringify({ action: accion, note: nota.trim() || null }),
    });
    aviso(`Reporte resuelto con ${accion}.`);
    await cargar(true);
  } catch (e) {
    aviso("No se pudo resolver: " + e.message, true);
    fila.querySelectorAll("button").forEach((b) => (b.disabled = false));
  }
}

async function cargar(reiniciar) {
  if (reiniciar) { nextCursor = null; $("lista").replaceChildren(); }
  const q = new URLSearchParams({ status: $("status").value, limit: "20" });
  if ($("kind").value) q.set("kind", $("kind").value);
  if (nextCursor) q.set("cursor", nextCursor);
  try {
    const pagina = await llamar("/v1/admin/reports?" + q);
    pagina.items.forEach((r) => $("lista").append(pintarReporte(r)));
    nextCursor = pagina.nextCursor || null;
    $("mas").hidden = !nextCursor;
    if (!$("lista").children.length) $("lista").append(el("p", "No hay reportes con este filtro.", "meta"));
  } catch (e) {
    aviso("No se pudo cargar: " + e.message, true);
  }
}

function mostrar(sesion, esAdmin) {
  $("entrar").hidden = !!sesion;
  $("salir").hidden = !sesion;
  $("quien").textContent = sesion ? sesion.email : "";
  $("panel").hidden = !(sesion && esAdmin);
}

async function iniciar() {
  let config;
  try {
    config = await (await fetch("/__/firebase/init.json")).json();
  } catch {
    aviso("No se pudo leer la configuración de Firebase: abre esta página desde Firebase Hosting.", true);
    return;
  }
  firebase.initializeApp(config);
  auth = firebase.auth();
  $("entrar").addEventListener("click", () => auth.signInWithPopup(new firebase.auth.GoogleAuthProvider()).catch((e) => aviso(e.message, true)));
  $("salir").addEventListener("click", () => auth.signOut());
  $("recargar").addEventListener("click", () => cargar(true));
  $("kind").addEventListener("change", () => cargar(true));
  $("status").addEventListener("change", () => cargar(true));
  $("mas").addEventListener("click", () => cargar(false));
  auth.onAuthStateChanged(async (sesion) => {
    user = sesion;
    aviso("");
    if (!sesion) return mostrar(null, false);
    const resultado = await sesion.getIdTokenResult(true);
    const esAdmin = resultado.claims.admin === true;
    mostrar(sesion, esAdmin);
    if (!esAdmin) aviso("Esta cuenta no tiene el permiso de administración (claim admin).", true);
    else cargar(true);
  });
}
iniciar();
