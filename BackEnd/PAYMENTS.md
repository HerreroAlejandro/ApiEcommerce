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

Se guarda una sola solicitud por pago, con UUID estable como clave de
idempotencia, administrador, fecha, motivo, importe y operación remota. La
solicitud se confirma en la base antes de enviar la petición a Mercado Pago.
Los reintentos conservan la solicitud y el motivo originales. Se auditan la
solicitud, la aceptación del proveedor y la confirmación verificada.

| Estado de solicitud | Significado |
| --- | --- |
| REQUESTED | Intención registrada; el envío puede no haberse completado o su resultado ser incierto |
| PROCESSING | Mercado Pago aceptó la solicitud; falta verificar la devolución completa |
| CONFIRMED | El importe acumulado devuelto fue verificado consultando Orders |

HTTP 200 significa CONFIRMED; HTTP 202 devuelve una solicitud aún pendiente.
Un timeout puede devolver 503 aunque MP haya recibido la petición. Consultá
GET /admin/payments/{paymentId}/refund y sincronizá la order si es necesario;
no crees otra solicitud con una clave distinta. Repetir el mismo POST reutiliza
la clave. Cuando ya está PROCESSING no se vuelve a enviar el reembolso.

Si se devuelve el pago principal, la orden queda en REVIEW mientras se tramita,
se bloquea el despacho y se revocan permisos digitales. Esto no afirma que el
dinero ya haya vuelto al comprador. Al confirmar, pasa a REFUNDED y se cancela
y libera stock una vez si aún no se envió. Una orden EXPIRED permanece EXPIRED
y no repone stock dos veces; una SHIPPED/DELIVERED conserva ese historial y
requiere gestionar por separado la devolución física.

No se aceptan contracargos, pagos pendientes/rechazados ni discrepancias de
vendedor, moneda o entorno. Las devoluciones parciales previas se contemplan
para devolver todo el resto; no se incorpora un formulario de devoluciones
parciales. Si durante una solicitud pendiente se devuelve parte del dinero
desde otro canal, la API no cambia el importe ni genera otra clave: bloquea el
reintento por discrepancia y requiere conciliar la operación en Mercado Pago.
El listado /admin/payments/review también incluye solicitudes sin confirmar.

Los rechazos del proveedor no habilitan por sí solos a liberar el pedido ni a
suponer que nunca recibió la solicitud. Revisá el estado y resolvé el incidente
mediante sincronización, reintento con la misma clave o gestión en Mercado Pago.
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
