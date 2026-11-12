# Archivos de esta pasada: reembolsos por articulo

Lista de fuentes y documentos modificados en esta implementacion. No incluye los cambios previos de intentos de reembolso ni los compilados de target.

## Nuevos

- [DTO/ItemRefundRequestDTO.java](src/main/java/com/api/crud/DTO/ItemRefundRequestDTO.java)
- [DTO/ItemRefundLineRequestDTO.java](src/main/java/com/api/crud/DTO/ItemRefundLineRequestDTO.java)
- [DTO/RefundItemResponseDTO.java](src/main/java/com/api/crud/DTO/RefundItemResponseDTO.java)
- [models/entity/RefundItemAllocation.java](src/main/java/com/api/crud/models/entity/RefundItemAllocation.java)
- [validation/WholeQuantityDeserializer.java](src/main/java/com/api/crud/validation/WholeQuantityDeserializer.java)
- [2026-11-11-item-refunds.sql](src/main/resources/db/manual/2026-11-11-item-refunds.sql)
- Este documento: ITEM_REFUNDS_CHANGES.md

## Modificados

- [DTO/OrderItemResponseDTO.java](src/main/java/com/api/crud/DTO/OrderItemResponseDTO.java)
- [DTO/OrderItemAdminDetailDTO.java](src/main/java/com/api/crud/DTO/OrderItemAdminDetailDTO.java)
- [DTO/PaymentRefundResponseDTO.java](src/main/java/com/api/crud/DTO/PaymentRefundResponseDTO.java)
- [models/entity/OrderItem.java](src/main/java/com/api/crud/models/entity/OrderItem.java)
- [models/entity/PaymentRefund.java](src/main/java/com/api/crud/models/entity/PaymentRefund.java)
- [models/enums/PaymentStatus.java](src/main/java/com/api/crud/models/enums/PaymentStatus.java)
- [repositories/PaymentRefundDao.java](src/main/java/com/api/crud/repositories/PaymentRefundDao.java)
- [repositories/impl/PaymentRefundDaoImp.java](src/main/java/com/api/crud/repositories/impl/PaymentRefundDaoImp.java)
- [services/PaymentRefundPersistence.java](src/main/java/com/api/crud/services/PaymentRefundPersistence.java)
- [services/PaymentRefundService.java](src/main/java/com/api/crud/services/PaymentRefundService.java)
- [services/PaymentConfirmationService.java](src/main/java/com/api/crud/services/PaymentConfirmationService.java)
- [services/PhysicalReturnService.java](src/main/java/com/api/crud/services/PhysicalReturnService.java)
- [services/DigitalDeliveryService.java](src/main/java/com/api/crud/services/DigitalDeliveryService.java)
- [services/OrderService.java](src/main/java/com/api/crud/services/OrderService.java)
- [services/StoreSaleService.java](src/main/java/com/api/crud/services/StoreSaleService.java)
- [controllers/AdminPaymentController.java](src/main/java/com/api/crud/controllers/AdminPaymentController.java)
- [config/ModelMapperConfig.java](src/main/java/com/api/crud/config/ModelMapperConfig.java)
- [PaymentRefundTests.java](src/test/java/com/api/crud/PaymentRefundTests.java)
- [PaymentConfirmationTests.java](src/test/java/com/api/crud/PaymentConfirmationTests.java)
- [PhysicalReturnTests.java](src/test/java/com/api/crud/PhysicalReturnTests.java)
- [OrderSecurityTests.java](src/test/java/com/api/crud/OrderSecurityTests.java)
- [DigitalDeliveryServiceTests.java](src/test/java/com/api/crud/DigitalDeliveryServiceTests.java)
- [OrderServiceTests.java](src/test/java/com/api/crud/OrderServiceTests.java)
- [PAYMENTS.md](PAYMENTS.md)
- [RETURNS.md](RETURNS.md)

No se eliminaron archivos fuente ni se ejecuto SQL contra la base del usuario.

Los parciales se implementan para pagos online de Mercado Pago. El reembolso presencial sigue siendo total. La resolucion completa de contracargos sigue pendiente.

## Verificacion

237 pruebas locales aprobadas, sin fallos ni errores. Se excluyo CrudApplicationTests para no iniciar la aplicacion con la base/correo reales. Las pruebas usan dobles de repositorio/proveedor: no sustituyen la prueba de reembolsos en el sandbox de Mercado Pago ni la concurrencia sobre MySQL real.
