-- E7.1 (rev. PR #70): los eventos de compra cuyo token es de una cuenta ya borrada (lápida) no deben conservar
-- el token ni el payload completo (arquitectura.md §10.2). Cubre lo que quedó de antes de que la RTDN
-- redactara los eventos de una lápida: solo tipo y fecha, sin token ni usuario.
update purchase_events
set payload = case when payload->'redacted' is not distinct from 'true'::jsonb then payload
              else jsonb_strip_nulls(jsonb_build_object(
                  'redacted', true,
                  'eventTimeMillis', payload->'eventTimeMillis',
                  'notificationType', payload#>'{subscriptionNotification,notificationType}')) end,
    purchase_token = null,
    user_id = null
where user_id in (select id from users where firebase_uid like 'deleted:%')
   or purchase_token in (
       select en.purchase_token
       from entitlements en
       join users u on u.id = en.user_id
       where u.firebase_uid like 'deleted:%');
