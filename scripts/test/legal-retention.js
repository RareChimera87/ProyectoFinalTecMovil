// E8.4. Compara la tabla de retención de docs/arquitectura.md §10.2 con la de /privacidad:
// mismas filas, en el mismo orden, y los mismos números (plazos) en las columnas
// "Plazo o evento" y "Al borrar la cuenta". La redacción difiere a propósito (está escrita para
// el usuario), pero un "90 días" cambiado por "9 días" debe fallar.
// Uso: node legal-retention.js <arquitectura.md> <privacidad.html>. Imprime el nº de filas o el error.
const fs = require("fs");
const [arq, html] = process.argv.slice(2);

const filasArq = [];
let dentro = false;
for (const l of fs.readFileSync(arq, "utf8").split("\n")) {
  if (/^### 10\.2 /.test(l)) { dentro = true; continue; }
  if (/^### 10\.3 /.test(l)) dentro = false;
  if (dentro && /^\| /.test(l) && !/^\| Dato /.test(l) && !/^\|---/.test(l)) {
    filasArq.push(l.split("|").slice(1, -1).map((c) => c.trim()));
  }
}
const filasHtml = [...fs.readFileSync(html, "utf8").matchAll(/<tr><td>(.*?)<\/td><\/tr>/g)]
  .map((m) => m[1].split("</td><td>"));

if (filasArq.length !== filasHtml.length) {
  console.log(`arquitectura §10.2 tiene ${filasArq.length} filas y /privacidad ${filasHtml.length}`);
  process.exit(1);
}
const nums = (celdas) => (celdas.join(" ").match(/\d+/g) || []).join(",");
const malas = [];
filasArq.forEach((f, i) => {
  const a = nums(f.slice(2, 4));
  const h = nums(filasHtml[i].slice(2, 4));
  if (a !== h) malas.push(`fila ${i + 1} (${f[0]}): plazos [${a}] en arquitectura y [${h}] en /privacidad`);
});
if (malas.length) { console.log(malas.join("; ")); process.exit(1); }
console.log(filasArq.length);
