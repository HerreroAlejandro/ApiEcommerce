# Registro y verificacion de correo

`POST /users/register` conserva su DTO y devuelve 201 con un mensaje de cuenta
pendiente. Guarda la contrasena cifrada, asigna CLIENT y deja el estado en
`PENDING_VERIFICATION`. No entrega JWT ni permite iniciar sesion o comprar.

El token dura 30 minutos, es de un solo uso y la base solo guarda su hash SHA-256.
El correo se intenta enviar despues del commit: si falla SMTP, la cuenta queda
pendiente y puede solicitarse otro correo. No hay cola durable de envio: ante un
cierre del proceso antes de enviarlo, tambien debe solicitarse un reenvio.

## Endpoints publicos

Confirmar: `POST /users/email-verification/confirm`, Content-Type application/json:

```json
{"token":"TOKEN_DEL_CORREO"}
```

Un token valido activa exclusivamente una cuenta pendiente y registra la
transicion en la auditoria. Vencido, usado, reemplazado, inexistente o asociado a
una cuenta bloqueada/desactivada devuelve 400. El futuro front debe pedir
confirmacion y ejecutar este POST; abrir un enlace por GET no activa la cuenta.

Reenviar: `POST /users/email-verification/resend`, Content-Type application/json:

```json
{"email":"cliente@example.com"}
```

Devuelve un mensaje generico 200, tambien para correos inexistentes o cuentas que
no admiten la operacion. Un reenvio aceptado reemplaza el token anterior.
Se admite un envio por minuto y hasta cinco por hora por cuenta, incluyendo el
inicial. Los contadores persisten en la base. Una solicitud limitada no envia
correo ni invalida el token vigente. El limite general por IP/registro/login
sigue pendiente y no es reemplazado por este control.

## Futuro enlace al frontend

Sin configuracion adicional el correo contiene el token para las pruebas actuales.
Cuando exista la pantalla, configurar por ejemplo:

```text
EMAIL_VERIFICATION_FRONTEND_URL=http://localhost:5173/verify-email
```

Se enviara ese enlace con `?token=...`. Debe ser una URL HTTP/HTTPS sin parametros,
credenciales ni fragmento. La pantalla debe retirar el token de la barra una vez
leido y evitar enviarlo a recursos de terceros.

## Cuentas existentes y esquema

Las cuentas anteriores conservan su estado y acceso, sin exigir verificacion
retroactiva. `emailVerified` permite recordar si una cuenta bloqueada estaba
pendiente: desbloquearla la devuelve a pendiente, sin activarla. La verificacion
no levanta bloqueos ni desactivaciones. Recuperar la contrasena no verifica el
correo ni activa la cuenta; la recuperacion de cuenta sigue siendo un flujo
separado para cuentas desactivadas.

El script `src/main/resources/db/manual/2026-11-12-email-verification.sql` prepara
los enums de usuario/auditoria, el indicador y la tabla de tokens. Inspeccionar el
esquema antes de ejecutarlo: ddl-auto=update puede haber creado parte de la
estructura. Este cambio no ejecuta SQL automaticamente contra MySQL.

## Disponibilidad de productos

El detalle publico agrega `stockProduct`: entero para fisicos, null para digitales.
No se agrega una columna ni un atributo available. El filtro publico inStock=true
incluye fisicos con stock positivo y digitales, tanto en resultados como en
conteo, excluyendo desactivados. Carrito y compra vuelven a validar disponibilidad.
Los filtros administrativos no cambian.
