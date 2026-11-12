# Contracargos online

Seguimos en modo de prueba. No se cambiaron credenciales ni se habilito produccion.
Las pruebas locales usan respuestas simuladas del proveedor; falta comprobar un
caso real disponible en el entorno de Mercado Pago. Su documentacion de casos
describe el uso de credenciales de produccion: no reemplazarlas para forzar una
prueba ni usar dinero real como parte de este desarrollo.

## Deteccion y entrega

El webhook firmado obtiene la order canonica de Mercado Pago. Del recurso se
extraen los PAY de las transacciones, sus referencias numericas y los case_id.
El contenido financiero del body del webhook nunca determina el resultado.
Tambien se admite el topico chargebacks con data.id de la order ORD.

Si afecta al pago principal:

- PENDING/CONFIRMED: se cancela la entrega y se libera el stock reservado restante
  una sola vez. Se excluyen las unidades que ya habian sido canceladas por un
  parcial, evitando reponerlas otra vez. El pago sigue REVIEW mientras se resuelve.
- SHIPPED/DELIVERED: se conserva el estado, la direccion y el seguimiento. El
  contracargo no afirma que la mercaderia regreso y no repone stock.
- EXPIRED/CANCELED: no se vuelve a liberar stock ni se reactiva el pedido.
- Se revocan las descargas y sus tokens. Un archivo ya descargado no puede recuperarse.

Un contracargo de un pago duplicado se registra contra ese pago y no cancela la
compra financiada por otra transaccion. No se bloquea automaticamente la cuenta
del comprador: el reclamo no demuestra por si solo fraude del usuario registrado.

## Caso y resultado financiero

Cada vinculo pago/case_id tiene un registro unico. Se conserva la documentacion
requerida, su fecha limite, motivo, monto y fecha de verificacion del proveedor.
El case_id numerico no es el identificador CBK ni el identificador PAY.

Se consulta GET /v1/chargebacks/{case_id} con el Access Token configurado y
X-Caller-Id del vendedor. Ademas de la propiedad validada por Mercado Pago, se
comprueban moneda, entorno y pertenencia de la referencia numerica del pago.

| Resolucion local | Evidencia del proveedor | Consecuencia |
| --- | --- | --- |
| OPEN | coverage_applied ausente/null | Pago en revision; no se permite otro reembolso |
| WON | coverage_applied=true | Resultado favorable; todavia se comprueba el estado del dinero |
| LOST | coverage_applied=false | El pago principal queda CHARGED_BACK; no se devuelve dinero nuevamente |

Un resultado favorable no equivale a dinero recuperado. Se exige tambien una
transaccion canonica acreditada o charged_back/reimbursed antes de habilitar la
resolucion operativa. Si existen varios casos del mismo pago, todos deben estar
resueltos favorablemente para recuperar su uso.

- Si la entrega se cancelo o el pedido vencio: permanece igual. El pago queda
  REVIEW/LATE_APPROVAL para gestionar el reembolso del dinero recuperado mediante
  el flujo existente. No se llama automaticamente al endpoint de reembolso ni se
  transfiere el pago a otra orden. El comprador debe realizar una compra nueva.
- Si ya se envio/entrego y no hay reembolsos pendientes ni discrepancias: vuelve
  a PAID o PARTIALLY_REFUNDED, conservando la entrega. Se restaura solo el acceso
  digital no reembolsado: un intento ya consumido sigue consumido y los tokens
  anteriores no reviven.
- Si ya se verifico el reembolso de todo el dinero, se conserva REFUNDED.
- Ante resultados incompatibles o desconocidos se mantiene la revision.

Los eventos repetidos no duplican casos ni stock. Los resultados viejos no
sobrescriben uno nuevo y una respuesta posterior sin decision no reabre un caso
cerrado. Una correccion explicita mas reciente del proveedor se conserva y aplica.

Un mismo caso puede abarcar varios pagos. Cada pago local se vincula por su
referencia comprobada; grouped indica que amount es el monto total del caso, no
una perdida atribuible exclusivamente a ese pago. No sumar esos importes como
si fueran operaciones distintas ni inventar un reparto financiero.

## Operacion administrativa

| Endpoint | Permiso | Uso |
| --- | --- | --- |
| GET /admin/chargebacks?resolution=OPEN&page=0&size=20 | ADMIN/SUPPORT | Lista resumida paginada; resolution es opcional |
| GET /admin/chargebacks/{id} | ADMIN/SUPPORT | Detalle, ultimo resultado y ultima nota |
| POST /admin/chargebacks/{id}/sync | ADMIN | Consulta y verifica el caso y la order nuevamente |
| POST /admin/chargebacks/{id}/notes | ADMIN | Agrega una nota sin cambiar la decision financiera |

id es el identificador local devuelto por la lista, no el case_id de Mercado Pago.
Las escrituras requieren una cuenta activa. Las respuestas no se almacenan en cache.

Body de una nota:

```json
{ "reason": "Comprobante de entrega presentado en Mercado Pago; esperando revision" }
```

La ultima nota aparece en el detalle; cada nota y cambio de resolucion quedan
auditados en order_change_audit, con responsable cuando corresponde. La nota no
permite forzar PAID, WON o LOST ni declarar que se presentaron archivos al proveedor.

## Comprobantes y notificaciones

La presentacion de documentacion se realiza desde la gestion de Mercado Pago.
Esta implementacion no incorpora carga de archivos a su endpoint de documentacion.
Enviar comprobantes y resolver la disputa siguen siendo tareas de administracion
y del proveedor; la API registra y verifica el resultado recibido.

En Developers hay que habilitar tambien el evento de contracargos indicado por
Mercado Pago. La URL del webhook y la verificacion de firma siguen siendo las
existentes. No se cambia MP_LIVE_MODE a true para estas pruebas.

Al recibir un evento o una sincronizacion administrativa se consultan los casos
localmente vinculados a esa order. Para pedidos sin casos no hay consultas
adicionales al proveedor. No se agrego un scheduler que revise todos los pedidos.

Si falla una consulta, la deteccion/cancelacion ya confirmada en su transaccion
se conserva y el webhook responde con error para permitir reintentos. Una order
pagada durante una interrupcion del backend sigue necesitando el mecanismo de
reentrega/sincronizacion discutido antes; no se agrego conciliacion periodica.

Si faltan case_id o referencia numerica en la order remota, se mantiene REVIEW
y el pago sigue visible en la revision administrativa. No se inventa un caso
ni se enlaza uno ingresado arbitrariamente por el cliente.

Una devolucion fisica posterior a un contracargo perdido puede recibirse por los
endpoints existentes. Solo las unidades recibidas y vendibles reponen stock;
esto no ejecuta un segundo reembolso ni cambia CHARGED_BACK.

## Base y validacion

Migracion: src/main/resources/db/manual/2026-11-11-chargebacks.sql, preparada
durante esta pasada. No se ejecuto contra la base del usuario. Revisar el esquema
antes, porque Hibernate puede haber agregado ya columnas o tablas.

Las pruebas cubren identidad, permisos, casos repetidos, resoluciones viejas,
liberacion de reserva, pagos duplicados, reembolsos posteriores y descargas
consumidas. No reemplazan pruebas del proveedor ni concurrencia con MySQL real.

Referencias oficiales:

- [Gestionar casos y documentacion](https://www.mercadopago.com.ar/developers/es/docs/checkout-pro-orders/chargebacks/manage)
- [Notificaciones de contracargos](https://www.mercadopago.com.ar/developers/es/docs/checkout-pro-orders/chargebacks/notifications)
- [Consultar caso por ID](https://www.mercadopago.com.ar/developers/es/reference/online-payments/checkout-pro/chargebacks/get-chargeback/get)
- [Cambios de Payments a Orders](https://www.mercadopago.com.ar/developers/es/docs/checkout-api-orders/resources/migrate-payments-to-orders)
