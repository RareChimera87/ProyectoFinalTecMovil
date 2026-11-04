-- E2.6 · Perfil básico y edad (B0). La ficha pide en la lista cerrada de universidades UIS, UNAB,
-- UPB, UDES, UTS, USTA, UCC, UNIPAZ y 'Otra'; V2 sembró todas menos UNIPAZ. Las demás que sembró V2
-- (UMB, UDI, UNISANGIL, SENA) siguen activas hasta que el equipo confirme la lista
-- (docs/pendientes-humanos.md, E2.6): para ocultar una basta `update universities set active = false`.
insert into universities (id, short_name, name) values
    (12, 'UNIPAZ', 'Instituto Universitario de la Paz (UNIPAZ)')
on conflict (id) do nothing;
