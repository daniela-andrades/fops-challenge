# Backend

Este directorio será el backend de la aplicación con Java 21 y Spring Boot 3.

## Objetivo
- Exponer APIs REST para usuarios, items, órdenes y movimientos de inventario.
- Implementar la lógica de negocio del challenge.
- Gestionar persistencia, validación y notificaciones.

## Estructura esperada

```text
backend/
├── src/main/java/com/fops/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── model/
│   ├── dto/
│   ├── config/
│   └── mail/
├── src/main/resources/
│   ├── application.yml
│   └── data.sql
├── src/test/java/
├── pom.xml
└── Dockerfile
```
