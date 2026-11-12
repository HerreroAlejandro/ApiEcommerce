# Archivos de la implementacion de contracargos

Lista de esta pasada. No incluye los cambios anteriores de parciales, intentos
de reembolso o compilados de target. No se eliminaron fuentes ni se ejecuto SQL.

## Nuevos

- [models/enums/ChargebackResolution.java](src/main/java/com/api/crud/models/enums/ChargebackResolution.java)
- [models/entity/ChargebackCase.java](src/main/java/com/api/crud/models/entity/ChargebackCase.java)
- [DTO/ChargebackResponseDTO.java](src/main/java/com/api/crud/DTO/ChargebackResponseDTO.java)
- [DTO/ChargebackListDTO.java](src/main/java/com/api/crud/DTO/ChargebackListDTO.java)
- [repositories/ChargebackDao.java](src/main/java/com/api/crud/repositories/ChargebackDao.java)
- [repositories/impl/ChargebackDaoImp.java](src/main/java/com/api/crud/repositories/impl/ChargebackDaoImp.java)
- [services/ChargebackLifecycle.java](src/main/java/com/api/crud/services/ChargebackLifecycle.java)
- [services/ChargebackPersistence.java](src/main/java/com/api/crud/services/ChargebackPersistence.java)
- [services/ChargebackService.java](src/main/java/com/api/crud/services/ChargebackService.java)
- [controllers/AdminChargebackController.java](src/main/java/com/api/crud/controllers/AdminChargebackController.java)
- [ChargebackTests.java](src/test/java/com/api/crud/ChargebackTests.java)
- [ChargebackServiceTests.java](src/test/java/com/api/crud/ChargebackServiceTests.java)
- [2026-11-11-chargebacks.sql](src/main/resources/db/manual/2026-11-11-chargebacks.sql)
- [CHARGEBACKS.md](CHARGEBACKS.md)
- Este documento: CHARGEBACK_CHANGES.md

## Modificados

- [models/enums/PaymentStatus.java](src/main/java/com/api/crud/models/enums/PaymentStatus.java)
- [models/enums/PaymentReviewReason.java](src/main/java/com/api/crud/models/enums/PaymentReviewReason.java)
- [models/entity/Payment.java](src/main/java/com/api/crud/models/entity/Payment.java)
- [DTO/PaymentAdminResponseDTO.java](src/main/java/com/api/crud/DTO/PaymentAdminResponseDTO.java)
- [repositories/impl/PaymentDaoImp.java](src/main/java/com/api/crud/repositories/impl/PaymentDaoImp.java)
- [services/MercadoPagoClient.java](src/main/java/com/api/crud/services/MercadoPagoClient.java)
- [services/OrderService.java](src/main/java/com/api/crud/services/OrderService.java)
- [services/DigitalDeliveryService.java](src/main/java/com/api/crud/services/DigitalDeliveryService.java)
- [services/PhysicalReturnService.java](src/main/java/com/api/crud/services/PhysicalReturnService.java)
- [services/PaymentConfirmationService.java](src/main/java/com/api/crud/services/PaymentConfirmationService.java)
- [services/PaymentRefundPersistence.java](src/main/java/com/api/crud/services/PaymentRefundPersistence.java)
- [services/PaymentService.java](src/main/java/com/api/crud/services/PaymentService.java)
- [controllers/MercadoPagoWebhookController.java](src/main/java/com/api/crud/controllers/MercadoPagoWebhookController.java)
- [config/SecurityConfig.java](src/main/java/com/api/crud/config/SecurityConfig.java)
- [config/ModelMapperConfig.java](src/main/java/com/api/crud/config/ModelMapperConfig.java)
- [MercadoPagoClientTests.java](src/test/java/com/api/crud/MercadoPagoClientTests.java)
- [OrderServiceTests.java](src/test/java/com/api/crud/OrderServiceTests.java)
- [DigitalDeliveryServiceTests.java](src/test/java/com/api/crud/DigitalDeliveryServiceTests.java)
- [PhysicalReturnTests.java](src/test/java/com/api/crud/PhysicalReturnTests.java)
- [OrderSecurityTests.java](src/test/java/com/api/crud/OrderSecurityTests.java)
- [PaymentConfirmationTests.java](src/test/java/com/api/crud/PaymentConfirmationTests.java)
- [PaymentRefundTests.java](src/test/java/com/api/crud/PaymentRefundTests.java)
- [MercadoPagoWebhookTests.java](src/test/java/com/api/crud/MercadoPagoWebhookTests.java)
- [PaymentWebhookEndpointTests.java](src/test/java/com/api/crud/PaymentWebhookEndpointTests.java)
- [StoreSaleTests.java](src/test/java/com/api/crud/StoreSaleTests.java)
- [PAYMENTS.md](PAYMENTS.md)
- [RETURNS.md](RETURNS.md)

La presentacion de comprobantes se realiza desde Mercado Pago; no se incorporo
un endpoint de carga de archivos. La API verifica y registra la resolucion.
El modo sigue siendo de pruebas. Quedan pendientes pruebas con casos del
proveedor y concurrencia en MySQL real.

## Verificacion

261 pruebas locales aprobadas, sin fallos ni errores. Se excluyo CrudApplicationTests para no iniciar la aplicacion con la base y correo reales. Las consultas de Mercado Pago se simularon; no se movio dinero ni se probo un contracargo real. git diff --check sin errores.
