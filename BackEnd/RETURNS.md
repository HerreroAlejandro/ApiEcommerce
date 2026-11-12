# Devoluciones físicas y reembolsos

Las devoluciones físicas también se aplican a ventas presenciales sin cuenta de comprador.
En ese caso, la devolución de dinero se realiza fuera de la API y ADMIN la registra con
`POST /admin/store-sales/{orderId}/refund`, según [STORE_SALES.md](STORE_SALES.md).
Los reembolsos online siguen el endpoint de pagos de Mercado Pago descrito abajo.

Se admiten recepciones parciales acumuladas por articulo. El reembolso total exige recibir todos los articulos fisicos que debian entregarse (o registrar una excepcion). El reembolso online parcial solo exige las unidades elegidas. En pedidos mixtos, el parcial revoca solo los digitales seleccionados; el total revoca todo el acceso digital.

## Antes del envío

ADMIN solicita el reembolso con `POST /admin/payments/{paymentId}/refund` y un motivo. La solicitud pone el pago principal en revisión y bloquea el despacho. Cuando Mercado Pago confirma la devolución del dinero, se cancela el pedido no enviado y se libera su reserva una sola vez. Un error de conexión no debe interpretarse como devolución confirmada.

## Después del envío

El pedido conserva `SHIPPED` o `DELIVERED`. Una devolución física se registra por separado y no cambia automáticamente el estado del pago.

1. ADMIN registra la solicitud:

   `POST /admin/orders/{orderId}/physical-return`

   ```json
   { "reason": "Devolución total acordada con el cliente" }
   ```

2. Cuando llegan artículos físicos, ADMIN registra la recepción y la inspección:

   `POST /admin/orders/{orderId}/physical-return/receive`

   ```json
   {
     "reason": "Dos unidades recibidas; una está dañada",
     "items": [
       { "orderItemId": 21, "receivedQuantity": 2, "restockedQuantity": 1 }
     ]
   }
   ```

   Incluí las líneas recibidas, sin duplicados. Las cantidades son acumuladas: recibir una unidad y luego otra se registra primero como 1 y después como 2. Podés omitir líneas que todavía no llegaron. No se puede superar la cantidad que debía entregarse, descontando las unidades excluidas antes del envío. `orderItemId` es el identificador del artículo **de la orden**, no el del producto. La cantidad que vuelve a stock puede ser cero y nunca superar la recibida. Las cantidades quedan registradas. Repetir la misma recepción no repone stock otra vez. Una recepción posterior agrega solo el incremento al stock; las cantidades acumuladas no pueden disminuir y no se puede reclasificar como vendible una unidad ya registrada como no vendible.

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

Para devolver dinero por unidades seleccionadas, usar
`POST /admin/payments/{paymentId}/refund/items`, según [PAYMENTS.md](PAYMENTS.md).
La recepción acumulada debe cubrir esas unidades más las ya solicitadas de esa
misma línea; una unidad recibida no permite reembolsar dos veces. Las unidades
excluidas antes del envío no requieren recepción física. Recibir artículos nunca
cambia automáticamente el estado del cobro ni llama a Mercado Pago.
La excepcion administrativa tambien puede cerrar el retorno pendiente despues
de una recepcion parcial. Conserva las lineas ya recibidas y el stock repuesto;
no repone las unidades que no regresaron. Al autorizarla se cierra la recepcion
de ese pedido, con motivo y auditoria.


Un pedido SHIPPED/DELIVERED con pago CHARGED_BACK tambien permite registrar
una devolucion fisica. Solo se repone lo recibido y vendible; no se ejecuta otro
reembolso ni se modifica el resultado financiero. Ver [CHARGEBACKS.md](CHARGEBACKS.md).

## Listado administrativo

ADMIN y SUPPORT pueden consultar `GET /admin/physical-returns?status=REQUESTED&page=0&size=20`.
El filtro status es opcional (REQUESTED, RECEIVED o WAIVED); sin el se listan todos.
La pagina empieza en cero, tiene 20 registros por defecto y un maximo de 100.
El orden es requestedAt descendente y luego id descendente.

Se devuelve PhysicalReturnListDTO, con identificadores, estado y fechas. Para ver
motivos, responsables y articulos se conserva `GET /admin/orders/{orderId}/physical-return`.
REQUESTED significa que todavia no hubo recepcion ni excepcion. RECEIVED puede
ser parcial: no se interpreta automaticamente que deban devolverse los demas
articulos que el comprador decidio conservar.

El historial de recepciones, excepciones y cambios financieros se consulta en
`GET /admin/orders/{orderId}/history?page=0&size=20`. Estas lecturas no producen
nuevas auditorias ni modifican stock, reembolsos o estados.