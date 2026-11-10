# Devoluciones físicas y reembolsos

Esta primera versión admite la devolución de **todos los artículos físicos del pedido**, con una sola recepción definitiva. No admite devoluciones ni reembolsos parciales elegidos por artículo. En pedidos mixtos se reciben todos los artículos físicos y el reembolso total también revoca el acceso digital.

## Antes del envío

ADMIN solicita el reembolso con `POST /admin/payments/{paymentId}/refund` y un motivo. La solicitud pone el pago principal en revisión y bloquea el despacho. Cuando Mercado Pago confirma la devolución del dinero, se cancela el pedido no enviado y se libera su reserva una sola vez. Un error de conexión no debe interpretarse como devolución confirmada.

## Después del envío

El pedido conserva `SHIPPED` o `DELIVERED`. Una devolución física se registra por separado y no cambia automáticamente el estado del pago.

1. ADMIN registra la solicitud:

   `POST /admin/orders/{orderId}/physical-return`

   ```json
   { "reason": "Devolución total acordada con el cliente" }
   ```

2. Cuando llegan **todos** los artículos físicos, ADMIN registra la recepción y la inspección:

   `POST /admin/orders/{orderId}/physical-return/receive`

   ```json
   {
     "reason": "Dos unidades recibidas; una está dañada",
     "items": [
       { "orderItemId": 21, "receivedQuantity": 2, "restockedQuantity": 1 }
     ]
   }
   ```

   Debe incluir cada línea física del pedido, sin duplicados, con la cantidad total comprada. `orderItemId` es el identificador del artículo **de la orden**, no el del producto. La cantidad que vuelve a stock puede ser cero y nunca superar la recibida. Las cantidades quedan registradas. Repetir la misma recepción no repone stock otra vez; intentar cambiarla devuelve conflicto.

3. ADMIN solicita el reembolso por el endpoint de pagos existente. La recepción no mueve dinero por sí sola. Se devuelve todo el importe restante de ese pago, incluido el envío cobrado si lo hubiera. Actualmente el envío está configurado en cero. La confirmación se verifica con Mercado Pago.

SUPPORT y ADMIN pueden consultar `GET /admin/orders/{orderId}/physical-return`. Las escrituras requieren ADMIN.

## Excepción: reembolso sin retorno físico

Después de registrar la solicitud, ADMIN puede autorizar expresamente:

`POST /admin/orders/{orderId}/physical-return/waive`

```json
{ "reason": "Producto destruido; se autoriza reembolso sin retorno" }
```

Esta excepción queda como `WAIVED`, con fecha, responsable y motivo. No repone stock ni mueve dinero; habilita solicitar el reembolso por separado. No puede sustituir una recepción ya registrada y no permite registrar después una recepción sobre el mismo caso.

## Protecciones

- Estados de la devolución: `REQUESTED`, `RECEIVED`, `WAIVED`. Un caso por pedido.
- Un pedido físico ya enviado requiere recepción o excepción antes de solicitar el reembolso de su pago principal.
- Los pagos duplicados se devuelven sin exigir devolver la compra original. Las órdenes vencidas con pagos tardíos conservan el flujo de revisión y reembolso existente.
- Un reembolso externo o contracargo puede ocurrir sin pasar por estos endpoints. Registrar una recepción real sigue siendo posible si el pago ya quedó reembolsado o en revisión; nunca se inventa una recepción a partir de una notificación financiera.
- Los bloqueos siguen el orden pedido → productos ordenados por ID, igual que el checkout. Recepción y reposición están en una misma transacción.
- El historial de solicitud, recepción y excepción queda auditado. Reintentos no cambian el motivo original ni duplican la resolución.

## Base de datos y pruebas

El script manual `src/main/resources/db/manual/2026-11-10-physical-returns.sql` crea `physical_returns` y `physical_return_lines` en el esquema existente. No ejecuta reembolsos ni modifica el stock histórico. No fue ejecutado desde Codex.

Las pruebas usan repositorios y Mercado Pago simulados. Falta probar la migración, los bloqueos reales y el recorrido de reembolso en sandbox con una base aislada.
