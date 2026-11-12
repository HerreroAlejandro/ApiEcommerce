# Archivos de las consultas administrativas

Esta pasada agrega historiales y el listado paginado de devoluciones. No modifica
las auditorias guardadas, no cambia entidades y no requiere migracion SQL.

## Nuevos

- [DTO/OrderHistoryResponseDTO.java](src/main/java/com/api/crud/DTO/OrderHistoryResponseDTO.java)
- [DTO/CartHistoryResponseDTO.java](src/main/java/com/api/crud/DTO/CartHistoryResponseDTO.java)
- [DTO/PhysicalReturnListDTO.java](src/main/java/com/api/crud/DTO/PhysicalReturnListDTO.java)
- [services/AdministrativeHistoryService.java](src/main/java/com/api/crud/services/AdministrativeHistoryService.java)
- [AdministrativeHistoryTests.java](src/test/java/com/api/crud/AdministrativeHistoryTests.java)
- Este documento: ADMIN_QUERIES_CHANGES.md

## Modificados

- [repositories/OrderWorkflowDao.java](src/main/java/com/api/crud/repositories/OrderWorkflowDao.java)
- [repositories/CartChangeAuditDao.java](src/main/java/com/api/crud/repositories/CartChangeAuditDao.java)
- [repositories/PhysicalReturnDao.java](src/main/java/com/api/crud/repositories/PhysicalReturnDao.java)
- [repositories/impl/OrderWorkflowDaoImp.java](src/main/java/com/api/crud/repositories/impl/OrderWorkflowDaoImp.java)
- [repositories/impl/CartChangeAuditDaoImp.java](src/main/java/com/api/crud/repositories/impl/CartChangeAuditDaoImp.java)
- [repositories/impl/PhysicalReturnDaoImp.java](src/main/java/com/api/crud/repositories/impl/PhysicalReturnDaoImp.java)
- [controllers/AdminCartController.java](src/main/java/com/api/crud/controllers/AdminCartController.java)
- [controllers/AdminOrderController.java](src/main/java/com/api/crud/controllers/AdminOrderController.java)
- [controllers/AdminPhysicalReturnController.java](src/main/java/com/api/crud/controllers/AdminPhysicalReturnController.java)
- [services/PhysicalReturnService.java](src/main/java/com/api/crud/services/PhysicalReturnService.java)
- [config/ModelMapperConfig.java](src/main/java/com/api/crud/config/ModelMapperConfig.java)
- [config/SecurityConfig.java](src/main/java/com/api/crud/config/SecurityConfig.java)
- [OrderSecurityTests.java](src/test/java/com/api/crud/OrderSecurityTests.java)
- [PhysicalReturnTests.java](src/test/java/com/api/crud/PhysicalReturnTests.java)
- [AdminCartQueryTests.java](src/test/java/com/api/crud/AdminCartQueryTests.java)
- [README.md](README.md)
- [RETURNS.md](RETURNS.md)

## Verificacion

38 pruebas relacionadas aprobadas sin errores ni fallos. Incluyen permisos,
paginacion, conversion DTO y rutas previas. Se simulo persistencia; las consultas
SQL con datos reales quedan para la integracion. No se ejecuto CrudApplicationTests
ni se conecto a la base/correo reales. No se eliminaron archivos fuente.
