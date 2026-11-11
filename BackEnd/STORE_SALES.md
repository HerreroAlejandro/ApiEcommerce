# Ventas presenciales ya cobradas

Este flujo registra ventas de productos físicos en el negocio sin exigir una cuenta al comprador. El operario debe autenticarse; su ID se guarda como responsable del cobro, nunca como comprador. No crea un carrito y no admite reservas presenciales sin pagar.

## Registrar una venta

`POST /admin/store-sales`, con JWT de ADMIN o SUPPORT.

```json
{
  "reference": "5e87d37f-1873-41af-abdf-d88acc762dc9",
  "items": [{ "productId": 75, "quantity": 2 }],
  "deliveryMethod": "STORE_PICKUP",
  "deliveredImmediately": true,
  "paymentMethod": "CASH",
  "amountPaid": 200.00,
  "reason": "Venta en mostrador, cobro y entrega realizados"
}
```

El ejemplo supone un precio actual de 100 por unidad. Se calculan los precios y el total en el backend y se exige que `amountPaid` coincida. No se aceptan precios elegidos por el cliente ni ventas de importe cero. El cobro debe haberse realizado antes: este endpoint **no cobra tarjetas ni consulta una terminal**. No enviar números de tarjeta ni códigos de seguridad.

- `CASH`: dinero recibido en caja.
- `CARD_TERMINAL`: cobro realizado en una terminal externa; exige `paymentReference`, por ejemplo `TERMINAL-123`.
- Por ahora no se admiten otros medios, pagos divididos, cuotas internas, crédito del cliente ni ventas digitales sin cuenta.

Se guarda una orden `IN_STORE`, `PAID`, sin vencimiento y con el stock descontado. Un producto inactivo, stock insuficiente, una línea duplicada, datos inválidos o un importe diferente impiden registrar la venta. Stock, orden y auditoría se guardan en una misma transacción.

Si el dinero ya se recibió pero la API rechaza la venta por un cambio de precio o disponibilidad, el operario debe resolver esa diferencia con el comprador; la API no inventa stock ni cambia el importe cobrado. Este flujo no reserva mercancía antes del cobro.

## Entrega

| Caso | Solicitud | Resultado inicial |
|---|---|---|
| Se lleva los productos | `STORE_PICKUP`, `deliveredImmediately: true` | `DELIVERED`; no exige dirección ni contacto |
| Retira después | `STORE_PICKUP`, `deliveredImmediately: false`, `customerName` y `customerPhone` | `CONFIRMED`; ya pagado, no vence |
| Pide envío | `SHIPPING`, `deliveredImmediately: false`, `shippingAddress` | `CONFIRMED`; dirección copiada a la orden |

Para retiro posterior, agregar:

```json
{
  "customerName": "Cliente invitado",
  "customerPhone": "1123456789"
}
```

Al retirar se utiliza el endpoint existente:

`PATCH /admin/orders/{orderId}/status`

```json
{ "status": "DELIVERED", "reason": "Productos retirados por el comprador" }
```

El retiro no pasa por `SHIPPED` ni exige seguimiento. La fecha de retiro queda en `storeSale.deliveredAt` y el cambio queda auditado. Repetir el estado conserva esa fecha.

Para envío, enviar la dirección estructurada:

```json
{
  "shippingAddress": {
    "recipientName": "Cliente invitado",
    "phone": "1123456789",
    "street": "Calle de prueba",
    "streetNumber": "123",
    "city": "Ciudad",
    "region": "Provincia",
    "postalCode": "1234",
    "countryCode": "AR"
  }
}
```

Se aplica la tarifa configurada por el backend: mantener `ORDER_SHIPPING_FLAT_RATE=0.00` para envío gratuito. El envío utiliza los endpoints existentes de seguimiento y estado: `CONFIRMED → SHIPPED → DELIVERED`. Cargar seguimiento no marca automáticamente la salida. Una venta con retiro rechaza una dirección de envío para evitar datos contradictorios.

## Reintentos y consulta

El cliente de la API genera un UUID nuevo para cada venta. Si se pierde la respuesta, **reenvía la misma referencia y los mismos datos**, con el mismo operario. Se devuelve la orden existente sin volver a descontar stock ni registrar el cobro. Cambiar los datos con una referencia ya utilizada devuelve conflicto. No generar otro UUID para reintentar una operación cuyo resultado todavía se desconoce.

`GET /admin/orders/{orderId}` y `GET /admin/orders/getOrders` incluyen las ventas presenciales. El detalle contiene `salesChannel`, `deliveryMethod` y `storeSale` con responsable, contacto, medio, referencia y fechas. El listado indica el canal y la modalidad sin cargar todo el detalle. Los endpoints de cuenta del comprador no permiten consultar órdenes presenciales sin usuario; devuelven 404.

Los precios y nombres se conservan en los artículos de la orden. Cambiar el catálogo después no modifica una venta ya registrada, tampoco en un reintento.

## Registrar un reembolso presencial

`POST /admin/store-sales/{orderId}/refund`, solo ADMIN:

```json
{ "reason": "Importe total devuelto al comprador en caja" }
```

Esta operación **registra una devolución de dinero realizada fuera de la API**. No entrega efectivo ni inicia un reembolso de la terminal. Devolver el dinero una sola vez y consultar el estado ante dudas de conexión; repetir la petición solo devuelve el registro existente.

- Antes de entregar o enviar: cancela la orden y repone stock una sola vez.
- Después de entregar o enviar: requiere recibir la devolución física o autorizar una excepción con motivo, usando [RETURNS.md](RETURNS.md). No repone automáticamente stock ni borra el historial de entrega.
- El reembolso es total. No puede usarse para órdenes online.
- Una venta pagada no se puede cancelar directamente; primero hay que devolver el dinero y registrarlo por este flujo.

Los pagos y reembolsos online conservan los endpoints de Mercado Pago. El antiguo `PATCH /admin/orders/{orderId}/payment-status` queda bloqueado con 409 para ambos canales: no es un atajo para marcar `PAID` o `REFUNDED`.

## Base de datos y alcance de las pruebas

El script `src/main/resources/db/manual/2026-11-11-store-sales.sql` agrega columnas a `orders` y una referencia única. No crea otro esquema, no cambia stock ni importes históricos. Los pedidos existentes quedan como online; también incluye una actualización de la modalidad para los antiguos pedidos solo digitales.

Aplicarlo antes de arrancar el backend actualizado. Si Hibernate ya agregó las columnas mediante `ddl-auto=update`, inspeccionar primero `SHOW CREATE TABLE orders` y ejecutar solo lo que falte. No ejecutar el ALTER completo otra vez si las columnas ya existen. No se ejecutó desde Codex.

Las pruebas automatizadas usan repositorios simulados. Falta verificar la migración y los bloqueos de MySQL en una base aislada. La prueba de competencia por stock simula el bloqueo compartido entre ambos canales; no reemplaza esa comprobación de integración.

## Archivos de esta incorporación

Nuevos:

- `models/enums/SalesChannel.java`, `DeliveryMethod.java`, `StorePaymentMethod.java`.
- `models/entity/StoreSaleDetails.java`.
- `DTO/StoreSaleCreateRequestDTO.java`, `StoreSaleItemRequestDTO.java`, `StoreSaleResponseDTO.java`.
- `repositories/StoreSaleDao.java`, `repositories/impl/StoreSaleDaoImp.java`.
- `services/StoreSaleService.java`, `controllers/AdminStoreSaleController.java`.
- `src/main/resources/db/manual/2026-11-11-store-sales.sql`.
- `src/test/java/com/api/crud/StoreSaleTests.java` y este documento.

Modificados:

- `models/entity/Order.java`.
- `DTO/OrderAdminDetailDTO.java`, `OrderAdminListDTO.java`.
- `config/ModelMapperConfig.java`, `SecurityConfig.java`.
- `services/OrderService.java`, `OrderItemService.java`, `PaymentService.java`, `PaymentCheckoutPersistence.java`, `DigitalDeliveryService.java`.
- `src/test/java/com/api/crud/OrderServiceTests.java`, `OrderExpirationTests.java`, `OrderSecurityTests.java`.
- `PAYMENTS.md`, `RETURNS.md`.

Las rutas de Java, salvo las indicadas como `src/test`, son relativas a `src/main/java/com/api/crud`.
