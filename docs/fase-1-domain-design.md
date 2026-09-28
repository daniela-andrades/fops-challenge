# Fase 1: dominio, arquitectura y reglas de negocio

## 1. Stack final propuesto

- Backend: Java 21 + Spring Boot 3.3.x
- Frontend: Angular 21
- Base de datos: PostgreSQL 16 (o MySQL 8 si se quiere mantener compatibilidad con el repo actual)
- Persistencia: Spring Data JPA + Hibernate
- Seguridad: Spring Security + JWT
- Email: Spring Mail / SMTP mock para entorno local
- Contenedores: Docker + Docker Compose
- Pruebas: JUnit 5, Mockito, Testcontainers, Angular unit tests

## 2. Estructura monorepo

```text
fops-challenge/
├── backend/
│   ├── src/main/java/com/fops/
│   ├── src/main/resources/
│   ├── src/test/java/
│   ├── pom.xml
│   └── Dockerfile
├── frontend/
│   ├── src/app/
│   ├── package.json
│   ├── angular.json
│   └── Dockerfile
├── docker-compose.yml
├── README.md
├── docs/
│   └── fase-1-domain-design.md
└── .gitignore
```

## 3. Modelo de dominio

### Entidades

#### User
- id: Long
- name: String
- email: String
- createdAt: LocalDateTime

#### Item
- id: Long
- name: String
- sku: String (opcional, recomendado)
- stock: Integer
- createdAt: LocalDateTime

#### Order
- id: Long
- user: User
- item: Item
- quantity: Integer
- createdAt: LocalDateTime
- status: OrderStatus
- fulfilledQuantity: Integer
- remainingQuantity: Integer

#### InventoryMovement
- id: Long
- item: Item
- quantity: Integer
- movementType: MovementType (IN, OUT)
- order: Order nullable
- createdAt: LocalDateTime
- notes: String nullable

### Enum recomendados

```java
public enum OrderStatus {
    PENDING,
    PARTIALLY_FULFILLED,
    COMPLETED
}

public enum MovementType {
    IN,
    OUT
}
```

## 4. Reglas de negocio

### Regla 1: creación de orden
Cuando se crea una orden:
- se valida que la cantidad sea mayor que 0
- se comprueba el inventario disponible del item
- se intenta satisfacer la orden con stock disponible
- si hay suficiente stock, la orden se completa inmediatamente
- si no hay suficiente stock, se queda pendiente y se registra la cantidad faltante

### Regla 2: creación de movimiento de inventario
Cuando llega un movimiento de entrada o salida:
- se registra el movimiento con su fecha y cantidad
- si la cantidad es de entrada, se intenta asignar a una orden pendiente
- si la cantidad es de salida, se descuenta del stock global
- si la orden queda completa, se genera notificación por email al usuario

### Regla 3: trazabilidad
Cada movimiento debe poder responder:
- a qué orden se asignó
- qué órdenes fueron completadas con ese movimiento
- qué movimientos contribuyeron a completar una orden

### Regla 4: completitud
La completitud se calcula como:

```text
completionPercent = (fulfilledQuantity / order.quantity) * 100
```

Con una escala de 0 a 100, y redondeo a 2 decimales.

### Regla 5: correo al completar orden
Cuando una orden alcanza el 100%:
- se envía correo al email del usuario creador
- se registra en auditoría y logs

## 5. Capa de servicios

### OrderService
Responsable de:
- crear órdenes
- validar cantidades
- calcular completitud
- marcar estados
- disparar notificación de completion

### InventoryService
Responsable de:
- consultar stock actual
- registrar movimientos
- ajustar inventario
- aplicar asignación automática a órdenes pendientes

### AllocationService
Responsable de:
- decidir qué orden recibe el inventario disponible
- mantener trazabilidad entre orden y movimiento

### EmailService
Responsable de:
- enviar correos al completar una orden
- permitir mock local con maildev o SMTP de prueba

## 6. Criterios de aceptación de la fase 1

### Backend
- Existen entidades JPA con relaciones correctas
- Las reglas de negocio están previstas en servicios
- Hay migración de la base de datos con schema inicial
- Se pueden crear usuarios, productos y órdenes
- Se calcula la completitud correctamente
- Existe trazabilidad entre movimientos y órdenes

### Frontend
- Se puede listar usuarios, items y órdenes
- Se puede ver el estado y porcentaje de completitud
- Existen pantallas básicas de CRUD
- La UI presenta los datos relevantes para gestión operativa

### Infraestructura
- Docker Compose levanta backend, frontend y base de datos
- La app arranca con un comando único
- Los puertos están documentados

## 7. Entregables esperados de la fase 1

1. Definición del dominio
2. Arquitectura backend/frontend
3. Modelos JPA y enums
4. Reglas de negocio documentadas
5. Estructura monorepo inicial
6. Criterios de aceptación de la fase 1

## 8. Siguiente paso

La siguiente acción será crear la estructura real del monorepo y preparar la base del backend Spring Boot con las entidades y servicios del dominio.
