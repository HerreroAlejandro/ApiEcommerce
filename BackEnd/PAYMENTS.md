# Pagos con Mercado Pago: Checkout Pro y Orders API

Checkout Pro es la pantalla de pago alojada por Mercado Pago. Tu API crea una
order de pago y devuelve su `checkout_url`; el comprador abre ese enlace y paga
allí. No guardamos números de tarjeta ni CVV. La Public Key no se utiliza en
este flujo de redirección.

## Estado actual

El código usa `POST /v1/orders` con `type=online` y `processing_mode=manual`.
La confirmación se consulta con `GET /v1/orders/{id}`. Ya no se crean preferencias.
Las pruebas locales simulan Mercado Pago. En desarrollo también se comprobó una
compra física, su pago con el comprador de prueba y la confirmación automática
por webhook. Los reembolsos administrativos tienen pruebas locales; aún falta
comprobar una devolución real dentro del entorno de pruebas de Mercado Pago.

La integración sigue deshabilitada por defecto. Tener `MP_ACCESS_TOKEN` no basta
para activarla. La línea `${MP_ENABLED:false}` de application.properties usa false
cuando no existe esa variable de entorno.

## Variables de entorno

Se configuran en Windows o en el entorno que inicia Spring Boot, igual que las
credenciales de tu base de datos. Reiniciá el IDE después de cambiarlas desde
Windows. No copies valores privados a application.properties ni al repositorio.

| Variable | Para qué sirve |
| --- | --- |
| MP_ENABLED | false inicialmente; true cuando prepares la prueba |
| MP_ACCESS_TOKEN | Token privado de la aplicación, ya configurado localmente |
| MP_COLLECTOR_ID | ID numérico de la cuenta vendedora asociada al token |
| MP_CURRENCY_ID | Moneda de los precios; ARS por defecto |
| MP_LIVE_MODE | false en pruebas; true únicamente para producción |
| MP_WEBHOOK_SECRET | Firma secreta obtenida al configurar Webhooks en Developers |
| MP_NOTIFICATION_URL | URL HTTPS pública de tu API terminada en /payments/mercadopago/webhook |
| MP_RETURN_URL | Página HTTPS de retorno del futuro frontend; opcional y sin parámetros |

Para una primera prueba sin Webhooks, se necesitan token, ID del vendedor,
MP_ENABLED=true y MP_LIVE_MODE=false. El ID del vendedor puede consultarse con
GET https://api.mercadopago.com/users/me usando Bearer Token en Postman; es el
campo id de la respuesta. No publiques el token. Las credenciales deben ser las
que el panel indique para probar Checkout Pro con Orders; su prefijo APP_USR
por sí solo no demuestra que una operación sea de prueba.

Sin Webhooks, la orden local no se actualiza automáticamente. Un ADMIN puede
pedir la sincronización del checkout para la prueba. Con MP_LIVE_MODE=true el
backend exige además firma secreta y URL de notificaciones al iniciar.

## Base de datos

El script `src/main/resources/db/manual/2026-11-08-mercadopago-payments.sql`
actualiza la base existente, seleccionada en MySQL. No crea otro schema.
Crea payment_checkouts y payments si faltan; adapta columnas existentes aunque
Hibernate ya haya creado parte de la estructura con ddl-auto=update.
No borra órdenes ni inventa pagos históricos. Se conserva cualquier columna
preferenceId anterior, pero no se copia a providerOrderId porque son recursos
distintos. Checkouts antiguos sin requestBody no deben reutilizarse para una
compra nueva: requieren revisar su situación antes de migrar. Para las pruebas
actuales usá órdenes nuevas.

payment_checkouts guarda la referencia local, el ID ORD... de Mercado Pago,
el importe, la moneda, el vendedor, el vencimiento y la solicitud original.
Guardar esa solicitud permite reenviar exactamente los mismos datos y la misma
X-Idempotency-Key después de un corte de conexión. No contiene credenciales.
payments guarda los intentos PAY..., sus estados normalizados y los importes
verificados. Una order remota puede contener varios intentos.

## Primera prueba con Postman

1. Prepará las variables de pruebas y el esquema; iniciá el backend.
2. Con un cliente de tu API, creá una orden nueva desde el carrito como venías
   haciendo. Si tiene productos físicos, enviá su dirección.
3. Con ese mismo JWT, llamá POST /orders/{orderId}/checkout sin body.
4. La respuesta contiene reference, orderId, providerOrderId, checkoutUrl,
   amount, currency y expiresAt. Para el flujo que funcionó en estas pruebas,
   iniciá sesión en una ventana privada con el comprador de prueba de Mercado
   Pago, distinto del vendedor, y abrí checkoutUrl en esa ventana. Usá su saldo
   ficticio o sus medios precargados, según la indicación del soporte en el
   ticket WCS-58061. El usuario comprador de MP es distinto del cliente de tu API.
5. Si aún no configuraste Webhooks, con JWT de ADMIN llamá
   POST /admin/payment-checkouts/{providerOrderId}/sync, sin body. Usá el ID
   ORD... de Mercado Pago, no el número de tu orden local. Devuelve una lista
   de pagos; si todavía no existen intentos, devuelve [].
6. Consultá tu orden y GET /orders/{orderId}/payments para ver el resultado.

La redirección al volver del pago no confirma nada por sí sola. El backend
consulta a Mercado Pago y verifica referencia, vendedor, moneda e importe.

## Configurar las notificaciones después

Para recibir avisos mientras el backend corre en tu computadora necesitás un
túnel HTTPS hacia localhost:8080. En Developers, abrí tu aplicación, entrá a
Configuración de Webhooks, elegí Prueba y el evento Order (Mercado Pago), y guardá
la URL pública con el path /payments/mercadopago/webhook. Copiá la firma secreta
a MP_WEBHOOK_SECRET y la URL a MP_NOTIFICATION_URL; reiniciá la aplicación.

MP_NOTIFICATION_URL describe y valida la URL esperada: el código no registra
Webhooks en tu cuenta ni la envía como notification_url al crear una order.
Hay que guardarla en el panel. Si cambia el túnel, actualizá ambos lugares.
El webhook admite los tópicos order y orders_v2; los avisos antiguos de Payment
no se procesan. IPN no está implementado.

El webhook verifica HMAC-SHA256 sobre data.id, x-request-id y ts. No confía en el
importe ni el estado del body. Se comprueba la firma con el ID original y con
su representación en minúsculas: en las pruebas de integración observamos esta
diferencia entre el simulador y los avisos automáticos. Ambas comprobaciones
exigen la misma firma secreta, request ID y timestamp; la consulta a Orders
conserva el identificador original.
Sin firma configurada o válida no confirma pagos.
Después consulta la order canónica y procesa sus intentos en una transacción,
bajo un único bloqueo de la orden local. Las notificaciones repetidas no vuelven
a habilitar descargas ni a devolver stock; las versiones antiguas no pisan las
más recientes. Los fallos de verificación/persistencia permiten que MP reintente.

## Reglas de negocio que se conservan

- Se aceptan crédito, débito y dinero en cuenta. Se excluyen otros tipos según
  los medios del vendedor; su disponibilidad final depende de Mercado Pago.
- Productos y envío se cobran por el importe histórico de la orden. El cliente
  no envía precios al crear el checkout.
- Una aprobación válida permite marcar PAID y habilitar las entregas digitales
  una vez. No resucita una orden cancelada o cuyo stock ya se liberó.
- Los identificadores de prueba ORDTST... se distinguen de los de
  producción; si MP devuelve live_mode también se comprueba su coherencia.
- Si Orders no aporta fecha precisa de aprobación, se usa conservadoramente
  last_updated_date. Una primera confirmación cuya fecha ya supera la reserva
  queda en revisión. Esa fecha no pretende ser un comprobante bancario exacto.
- La duración enviada a MP queda fija para los reintentos. Como es relativa a
  su creación remota y se expresa en segundos enteros (sin fracciones), no
  garantiza el mismo segundo de vencimiento local. Se descarta la fracción
  restante en vez de redondear hacia arriba. La
  API siempre hace cumplir el vencimiento local y rechaza la aprobación tardía.
- Un segundo pago aprobado queda registrado como duplicado y no entrega dos veces.
- Un reembolso parcial o contracargo exige revisión y revoca el acceso digital
  del pago afectado. Un reembolso total verificado revoca accesos y cancela y
  repone stock una vez si la orden aún no fue enviada.
- Los reembolsos de mercancía enviada no reponen stock automáticamente.
- El endpoint manual de estados no puede sobrescribir un checkout online.

En providerStatus se usan estados normalizados: processed/accredited pasa a
approved, failed a rejected, processing a in_process y canceled a cancelled.
Los reembolsos se suman por transaction_id únicamente cuando están processed;
los estados desconocidos no habilitan entregas. No se admiten pagos divididos
entre varios métodos: cada intento debe cubrir el importe completo.

La API puede solicitar un reembolso administrativo del importe restante completo
de un pago específico. Mercado Pago ejecuta la devolución; el webhook o la
consulta canónica verifica su resultado. Los contracargos se gestionan como
disputas en Mercado Pago: no se resuelven forzando PAID ni usando el endpoint de
reembolso. Las operaciones en REVIEW requieren seguimiento administrativo.

## Reembolsos administrativos

Las ventas presenciales ya cobradas tienen un flujo separado: [STORE_SALES.md](STORE_SALES.md).
Los cobros de caja o terminal no se registran como pagos de Mercado Pago. El antiguo
endpoint genérico de cambio de estado de pago queda bloqueado incluso sin checkout;
se utiliza la venta presencial o la confirmación verificada del proveedor, según el canal.

Para el pago principal de un pedido físico ya enviado o entregado, primero debe
registrarse la recepción de la devolución o una excepción administrativa con motivo.
Los pagos duplicados no exigen devolver la compra original. El flujo, los endpoints
y las cantidades que vuelven a stock se describen en [RETURNS.md](RETURNS.md).

Aplicá `src/main/resources/db/manual/2026-11-10-payment-refunds.sql` en la misma
base de datos. Solo agrega payment_refunds y tolera que Hibernate ya la haya
creado. No modifica usuarios ni genera movimientos en Mercado Pago.

Con JWT de ADMIN (no el Access Token de Mercado Pago), enviá:

```http
POST /admin/payments/{paymentId}/refund
Content-Type: application/json

{"reason":"Devolución de pago tardío"}
```

paymentId es el id numérico del registro Payment que devuelve la consulta de
pagos de tu API. No es el idOrder, ORD..., PAY... ni un identificador de usuario.
La petición no acepta un importe: el servidor calcula lo pagado menos lo ya
reembolsado y apunta exclusivamente a esa transacción. Por ello, devolver un
pago duplicado no devuelve también el pago principal ni detiene su compra.

Se guardan solicitudes comerciales secuenciales por pago, con administrador, fecha,
motivo e importe originales. Cada intento de ejecución tiene su propio UUID
estable como clave de idempotencia, responsable y motivo. La solicitud y el
intento se confirman en la base antes de llamar a Mercado Pago. Reenviar una
operación incierta conserva su clave. Crear un intento distinto requiere un
rechazo verificado, autorización explícita de ADMIN y una nueva comprobación
financiera. El primer intento conserva el UUID histórico de la solicitud.

| Estado de solicitud | Significado |
| --- | --- |
| REQUESTED | Intención registrada; el envío puede no haberse completado o su resultado ser incierto |
| PROCESSING | Mercado Pago aceptó la solicitud; falta verificar la devolución completa |
| CONFIRMED | El importe acumulado devuelto fue verificado consultando Orders |
| REJECTED | Esta solicitud fue rechazada con un código reconocido y una consulta posterior no mostró dinero adicional devuelto ni reembolsos pendientes para el pago |

HTTP 200 devuelve un resultado CONFIRMED o REJECTED; hay que leer `status`.
HTTP 202 devuelve una solicitud aún pendiente.
Un timeout puede devolver 503 aunque MP haya recibido la petición. Consultá
GET /admin/payments/{paymentId}/refund y sincronizá la order si es necesario;
no crees otra solicitud con una clave distinta. Repetir el mismo POST reutiliza
la clave del intento actual. Cuando ya está PROCESSING, CONFIRMED o REJECTED no
se vuelve a enviar ese intento. Para un REJECTED puede solicitarse uno nuevo
mediante el endpoint específico descrito abajo.

Si se devuelve el pago principal, la orden queda en REVIEW mientras se tramita,
se bloquea el despacho y se revocan permisos digitales. Esto no afirma que el
dinero ya haya vuelto al comprador. Al confirmar, pasa a REFUNDED y se cancela
y libera stock una vez si aún no se envió. Una orden EXPIRED permanece EXPIRED
y no repone stock dos veces; una SHIPPED/DELIVERED conserva ese historial y
requiere gestionar por separado la devolución física.

No se aceptan contracargos, pagos pendientes/rechazados ni discrepancias de
vendedor, moneda o entorno. Las devoluciones parciales previas se contemplan
para devolver todo el resto; los parciales por articulo se describen al final. Si durante una solicitud pendiente se devuelve parte del dinero
desde otro canal, la API no cambia el importe ni genera otra clave: bloquea el
reintento por discrepancia y requiere conciliar la operación en Mercado Pago.
El listado /admin/payments/review también incluye solicitudes sin confirmar.

Los rechazos del proveedor no habilitan por sí solos a liberar el pedido ni a
suponer que nunca recibió la solicitud.

### Rechazo verificado de una solicitud

Se reconocen exclusivamente estas respuestas del POST de reembolso, según la
[referencia de Mercado Pago](https://www.mercadopago.com.ar/developers/es/reference/online-payments/checkout-pro-orders/refund-order/post):

- HTTP 400 con `refund_amount_exceeds`.
- HTTP 404 con `transaction_not_found`.
- HTTP 409 con `cannot_refund_order`.

La respuesta por sí sola no alcanza: antes de guardar REJECTED se consulta la
order remota. Deben coincidir el checkout, vendedor, moneda, entorno, importe y
pago; no puede haber un reembolso sin resolver para esa transacción, el importe
ya devuelto debe seguir igual y la información no puede ser anterior a la guardada.
Si falta esa verificación, se conserva REQUESTED y el resultado sigue incierto.
No se guarda texto libre del proveedor: `rejectionCode` y `rejectionReason`
contienen códigos conocidos y explicaciones locales; `rejectedAt` registra cuándo
la API pudo comprobar el rechazo. Los tres campos aparecen en la consulta existente
`GET /admin/payments/{paymentId}/refund`. Se audita `REFUND_REJECTED`.

No se clasifican como rechazo los timeouts, errores de conexión, 401, 403, 429,
5xx, datos malformados o códigos desconocidos. Tampoco `order_already_refunded`,
`order_refund_already_in_process` ni `idempotency_key_already_used`: requieren
consultar el estado financiero. Las transacciones de reembolso con estados no
confirmados se consideran sin resolver; esta fase no interpreta automáticamente
estados asíncronos de fallo que no estén documentados para este flujo.

REJECTED cierra el envío de esta solicitud, pero no la situación comercial:
la compra afectada permanece bloqueada, no se restaura PAID, no se repone stock
ni se rehabilitan descargas. Un pago duplicado rechazado no afecta la compra
principal. El listado de revisión conserva el caso. No existe una operación para
cancelar la intención de devolver dinero ni continuar la compra.

Si después se verifica que el dinero efectivamente fue devuelto, la solicitud
puede pasar a CONFIRMED: se conservan los datos del rechazo anterior y su auditoría.
La verdad financiera prevalece sobre el registro previo; no se envía otro reembolso.

### Intentos manuales después de un rechazo

ADMIN consulta primero `GET /admin/payments/{paymentId}/refund`. La respuesta
incluye `currentAttemptId` y `attemptNumber`. Para retomar un rechazo verificado:

```http
POST /admin/payments/{paymentId}/refund/retry
Content-Type: application/json

{
  "previousAttemptId": "7126952c-0de6-4d25-a07d-717c496cd30c",
  "reason": "Se revisó y corrigió la causa del rechazo"
}
```

`previousAttemptId` debe ser el UUID real devuelto por la API, no uno inventado
para la petición. El servidor bloquea la orden, sincroniza y verifica de nuevo
el importe disponible, vendedor, moneda, entorno y ausencia de un reembolso
pendiente para ese pago. Mantiene el mismo importe y el mismo pago: no permite
cambiar el destino del dinero ni pasar por alto una devolución física pendiente.

Si la verificación falla, no genera una clave nueva. Si el dinero ya se devolvió,
se conserva la confirmación financiera y no se vuelve a enviarlo. REQUESTED o
PROCESSING no autorizan otro intento: se consulta y, cuando corresponde, se
reenvía la operación existente con su clave.

Repetir la petición sobre el mismo intento anterior devuelve su sucesor sin
crear otro. Esto también se aplica si el sucesor fue rechazado: para intentar
otra vez hay que indicar expresamente el nuevo `currentAttemptId`. Una petición
obsoleta no puede saltar intentos posteriores. Se conservan los motivos y
responsables originales; un reenvío HTTP no los sustituye. Restricciones únicas
en la base impiden dos sucesores de un mismo intento y dos números iguales
dentro de la solicitud.

El historial paginado se consulta con:

```http
GET /admin/payments/{paymentId}/refund/attempts?page=0&size=20
```

Requiere ADMIN, al igual que crear o reintentar el reembolso; `size` admite
entre 1 y 100. Cada entrada tiene UUID, número, intento anterior, responsable,
motivo, fechas, estado y referencia del proveedor cuando existe. No se mezclan
los resultados de intentos rechazados con los nuevos. Si el dinero se devuelve
por otro canal después de un rechazo, la solicitud puede quedar CONFIRMED y el
intento históricamente rechazado conserva REJECTED. La confirmación financiera
no borra lo ocurrido antes.

Para confirmar un intento en particular se exige su referencia de reembolso
aceptada y que esa referencia figure procesada en la consulta de Mercado Pago.
Que el total ya haya sido devuelto por otra operación confirma la solicitud
financiera, pero no inventa un resultado exitoso para un intento distinto. En
ese caso el historial puede conservar un intento no confirmado y la solicitud
estar CONFIRMED; no se permite enviar otro reembolso por esa diferencia.

Migración adicional: `src/main/resources/db/manual/2026-11-11-refund-attempts.sql`.
Agrega la tabla de intentos, copia los reembolsos históricos conservando sus UUID
y enlaza el intento actual. Requiere las migraciones anteriores. Si Hibernate ya
creó columnas o la tabla, ejecutar solo el DDL faltante, pero no omitir la copia
de datos. La API bloquea el despacho de un reembolso antiguo sin historial
migrado en lugar de generar otra clave. El script no se ejecutó desde Codex.

Esta etapa cubre los intentos del reembolso online por el importe restante.
Los reembolsos parciales por articulo se describen a continuacion. El seguimiento y la resolucion verificada de contracargos se describen en CHARGEBACKS.md.

Migración: `src/main/resources/db/manual/2026-11-11-refund-rejections.sql`.
Agrega el estado y los tres campos; inspeccionar primero la tabla si Hibernate ya
la actualizó. El script no se ejecutó desde Codex ni devuelve dinero por sí mismo.

No hay saldo a favor, transferencia de pagos entre órdenes ni cambios a UserModel.

## Endpoints

| Método y ruta | Permiso |
| --- | --- |
| POST /orders/{orderId}/checkout | Dueño de la orden |
| GET /orders/{orderId}/payments | Dueño de la orden |
| POST /payments/mercadopago/webhook | Público, con firma y verificación obligatorias |
| GET /admin/orders/{orderId}/payments | ADMIN / SUPPORT |
| GET /admin/payments/review?page=0&size=20 | ADMIN / SUPPORT |
| POST /admin/payment-checkouts/{providerOrderId}/sync | ADMIN |
| POST /admin/payments/{paymentId}/refund | ADMIN |
| GET /admin/payments/{paymentId}/refund | ADMIN |
| POST /admin/payments/{paymentId}/refund/retry | ADMIN |
| GET /admin/payments/{paymentId}/refund/attempts?page=0&size=20 | ADMIN |

El antiguo POST /admin/payments/{paymentId}/sync se reemplazó por la
sincronización de la order remota completa.

## Diagnóstico de errores

Si Postman devuelve 503 con el mensaje genérico de Mercado Pago, revisá la consola
del backend o logs/application.log. La línea `Mercado Pago rechazo la solicitud`
incluye método, ruta, estado HTTP, códigos del proveedor, nombres de campos
conocidos y requestId. No registra el Access Token, el body ni los mensajes
originales del proveedor. Los fallos de red indican solamente el tipo de error.
El detalle público se mantiene genérico; un 503 por sí solo no identifica si
fallaron las credenciales, el contenido de la solicitud o la conexión.

## Documentación oficial

- [Crear una order y obtener checkout_url](https://www.mercadopago.com.ar/developers/es/docs/checkout-pro-orders/create-order)
- [Consultar una order](https://www.mercadopago.com.ar/developers/es/reference/online-payments/checkout-pro/get-order/get)
- [Configurar y verificar Webhooks](https://www.mercadopago.com.ar/developers/es/docs/checkout-pro-orders/notifications)
- [Probar la integración](https://www.mercadopago.com.ar/developers/es/docs/checkout-pro-orders/integration-test-introduction)
- [Reembolsar una order](https://www.mercadopago.com.ar/developers/es/reference/online-payments/checkout-pro-orders/refund-order/post)

Seguimos en pruebas. Faltan pruebas de reembolsos con MP, devoluciones parciales
externas, contracargos y concurrencia real en MySQL entre pago y vencimiento.

## Reembolsos online por artículo

ADMIN puede solicitar `POST /admin/payments/{paymentId}/refund/items`:

```json
{
  "reference": "1e57e7cc-b4e7-4376-b9bd-b66613f43b90",
  "reason": "Devolver una unidad acordada con el comprador",
  "items": [{ "orderItemId": 21, "quantity": 1 }]
}
```

`orderItemId` identifica la línea histórica de la orden, no el producto. Se aceptan
cantidades enteras positivas, hasta las unidades que no tengan una devolución
solicitada. El servidor usa el precio unitario histórico y excluye el envío.
No se aceptan importes calculados por el cliente. El endpoint total existente
continúa devolviendo el dinero restante, incluido el envío pendiente de devolver.

Conservar `reference` al reenviar la misma solicitud, incluso después de un timeout.
Cambiar los artículos, cantidades o motivo conservando la referencia produce 409.
Una referencia nueva representa otra solicitud: solo se acepta cuando la anterior
está CONFIRMED. Un rechazo definitivo conserva la intención y sus unidades;
se resuelve con `/refund/retry` y `previousAttemptId`, sin reservar stock otra vez.

Antes del envío, al registrar la intención se excluyen esas unidades de la entrega
y se reintegran al stock una vez. No se reanudará la entrega de esas unidades si
el proveedor rechaza el intento: la solicitud sigue pendiente de resolución.
Después del envío no se repone stock desde el reembolso. Primero deben recibirse
las unidades físicas correspondientes o registrarse una excepción administrativa.
La recepción física controla por separado qué cantidades son vendibles.

Mientras se resuelve, el pago principal queda REVIEW y se bloquea el despacho.
Solo se revocan las descargas de los artículos seleccionados. Al verificar el
dinero devuelto, el pago pasa a PARTIALLY_REFUNDED y el resto de la compra puede
continuar; si se devolvió todo el dinero, se aplica REFUNDED. Las líneas originales,
sus cantidades compradas, nombres y precios se conservan. En la respuesta de
artículos se agregan refundRequestedQuantity, refundedQuantity, canceledQuantity
y fulfillmentQuantity (unidades que no fueron excluidas de la entrega).

`GET /admin/payments/{paymentId}/refund` devuelve la solicitud más reciente.
`GET /admin/payments/{paymentId}/refund/history` devuelve todas las solicitudes.
Para consultar los intentos de una solicitud anterior:
`GET /admin/payments/{paymentId}/refund/{refundId}/attempts?page=0&size=20`.
Los tres endpoints requieren ADMIN. El retry identifica la solicitud por el
intento anterior, aunque ya existan otros reembolsos históricos del mismo pago.

La confirmación financiera de la solicitud y la del intento individual siguen
siendo distintas: el dinero devuelto puede resolver la solicitud, pero solo una
referencia REF verificada confirma que ese intento concreto lo devolvió.

Migración pendiente de ejecutar: `src/main/resources/db/manual/2026-11-11-item-refunds.sql`.
Permite varias solicitudes por pago, crea sus líneas y los contadores históricos.
Inspeccionar antes el esquema si Hibernate ya creó columnas. El script no mueve dinero.

Este endpoint corresponde a pagos online de Mercado Pago. La venta presencial
con efectivo/terminal mantiene por ahora su registro de reembolso total.
Los parciales inesperados desde otros canales siguen sujetos
a revisión; no se fuerzan a un estado utilizable por coincidir solo con un importe.

## Contracargos

Ver [CHARGEBACKS.md](CHARGEBACKS.md): cancelacion de entregas no despachadas,
casos OPEN/WON/LOST, recuperacion verificada de dinero, notas y endpoints.
CHARGED_BACK es un resultado financiero; no equivale a REFUNDED ni borra una entrega.
No se devuelve dinero otra vez cuando el contracargo se perdio. Los comprobantes
se presentan desde la gestion de Mercado Pago; la API no sube archivos.

## Cierre de revisiones generales de compras previamente pagadas

Una correccion de importe, moneda, vendedor, entorno o estado inesperado ya puede
cerrar REVIEW cuando la compra habia sido aceptada previamente por esta API.
Se usa el mismo webhook firmado o la sincronizacion administrativa existente;
no hay un endpoint que permita forzar PAID ni se agrego un scheduler.

`payments.acceptedAt` registra la aceptacion local original. No equivale a la
fecha de aprobacion de Mercado Pago ni a la fecha de la ultima consulta. Se
conserva al entrar en revision, para no confundir una recuperacion con un pago
nuevo, tardio o vinculado a una orden que nunca se acepto como pagada.
El dato se incluye en la respuesta administrativa del pago.

Para continuar se exige:

- Datos canonicos compatibles con el checkout y el total historico de la orden.
- Pago principal aprobado con un medio admitido y una fecha de aprobacion valida.
- Evidencia de aceptacion local, inventario descontado y orden PENDING,
  CONFIRMED, SHIPPED o DELIVERED.
- Ningun reembolso pendiente, rechazado sin resolver o intencion de devolucion
  total. Los parciales confirmados deben explicar todo el dinero devuelto.
- Ningun contracargo sin resolver: ese recorrido conserva su propio cierre
  verificado descrito en CHARGEBACKS.md y tiene prioridad sobre este mecanismo.

La compra vuelve a PAID o PARTIALLY_REFUNDED, segun corresponda. No se cambia su
estado de entrega, no se descuenta stock otra vez ni se compra inventario nuevo.
Que haya vencido el checkout no convierte en tardia una compra ya aceptada y
pagada que conserva su inventario; ese plazo controla la aprobacion inicial.
Las ordenes CANCELED/EXPIRED siguen iguales y quedan orientadas al reembolso.
Si falta evidencia de aceptacion o inventario, se mantiene REVIEW.

La restauracion digital se comparte con el cierre favorable de contracargos:
las descargas revocadas no utilizadas pueden volver a AVAILABLE; las utilizadas
vuelven a USED conservando sus intentos y fechas. No se reactivan articulos
reembolsados, con devolucion solicitada o excluidos de la entrega. Los permisos
anteriores permanecen invalidados: el cliente solicita un token nuevo.
Se pueden crear permisos faltantes solo para articulos historicos elegibles.
Si falta su contenido historico, la transaccion no fuerza una recuperacion.

El cambio de REVIEW a un estado utilizable genera PAYMENT_REVIEW_RESOLVED en
order_change_audit. Repetir una notificacion ya resuelta no agrega otro cierre
ni vuelve a habilitar intentos de descarga.

Migracion: `src/main/resources/db/manual/2026-11-12-payment-review-recovery.sql`.
No se ejecuto contra la base del usuario. Si Hibernate ya creo acceptedAt,
omitir el ALTER y ejecutar solo el backfill conservador. Este backfill reconoce
compras actualmente pagadas con datos compatibles; no inventa una aceptacion
para registros viejos que ya estan en REVIEW sin evidencia suficiente.
Esos registros permanecen bloqueados para revision de su historial.
Verificacion de esta pasada: 278 pruebas locales aprobadas, sin fallos ni errores.
Se excluyo CrudApplicationTests para no iniciar la aplicacion con base/correo
reales. Mercado Pago se simulo; no se movio dinero ni se ejecuto la migracion.
En el backfill, acceptedAt toma la observacion verificada de una compra pagada;
no reconstruye una fecha original que no haya quedado registrada.

## Reintentar un pago rechazado

Los checkouts nuevos envian `config.online.retries.allowed=true`. Si el primer
pago es rechazado, el comprador puede volver al mismo checkout mientras la
orden siga PENDING, su pago sea PENDING/FAILED y conserve su reserva vigente.
Se reutilizan la referencia, la order de Mercado Pago y la URL originales.
El reintento no crea otra reserva, no descuenta stock nuevamente y no extiende
el vencimiento. Las compras pagadas, canceladas, vencidas o en revision no
pueden abrir el checkout mediante la API.

Cada transaccion verificada conserva su propio registro. Un rechazo anterior,
incluso si su aviso se repite despues, no reemplaza un pago exitoso de otro
intento de la misma compra. Se mantiene la proteccion de pagos duplicados.

Esto es distinto de reenviar la creacion por un timeout: ese reenvio conserva
el UUID de idempotencia y el body exacto que ya se guardo. Los checkouts viejos
no se reescriben; si fueron creados con retries.allowed=false, conservan esa
configuracion. El cambio no modifica una order ya creada en Mercado Pago.
Para comprobar la nueva configuracion se necesita una compra y checkout nuevos.

No se agregaron endpoints ni cambios de esquema. Seguimos en pruebas. Queda
pendiente comprobar rechazo y pago posterior exitoso dentro del checkout del
proveedor, con el comprador de prueba; las pruebas locales simulan esas respuestas.
No se configuraron cobros recurrentes ni un proceso de reintentos automaticos
del backend. La experiencia y los limites efectivos del checkout deben
confirmarse en el entorno de prueba de Mercado Pago.
Verificacion de reintentos: 55 pruebas relacionadas aprobadas sin fallos ni errores
(MercadoPagoClientTests, PaymentCheckoutTests y PaymentConfirmationTests). Las
respuestas del proveedor fueron simuladas; no se realizaron cobros reales.
