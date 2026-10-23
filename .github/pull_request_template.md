## Qué hace

<!-- Resumen en 2-4 líneas. Título del PR: verbo en imperativo en español, p. ej. "Agrega esqueleto del backend". -->

Feature del backlog: <!-- E0.1, E1.2, ... -->

## Criterios de aceptación

<!-- Copia los criterios de la ficha en docs/backlog.md y márcalos. -->
- [ ] ...

## Cómo probar

<!-- Comandos y pasos. -->

## Checklist

- [ ] Commits en español e imperativo (`scripts/commit-lint.sh`)
- [ ] Tests de la parte tocada en verde (Android, backend, contrato, Terraform o reglas)
- [ ] Sin secretos ni `google-services.json`, keystores o `.tfstate` en el diff
- [ ] Capturas Roborazzi adjuntas si hay UI
- [ ] Documentación actualizada (`docs/`, ADR si cambia una decisión)
- [ ] Pasos humanos pendientes anotados en `docs/pendientes-humanos.md`
- [ ] Etiqueta `major` puesta si la feature es major (exige la etiqueta `revisado-agente` por `major-gate`; el revisor par humano la revisa después del merge, en la ventana de la ola)
