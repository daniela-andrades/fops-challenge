# Fusion Operations Challenge — Lógica de negocio, arquitectura y estado actual

## 1. Objetivo del sistema

El sistema debe funcionar como un gestor mínimo de inventario y cumplimiento de pedidos para un entorno de fabricación o distribución.

La aplicación debe permitir:
- crear usuarios
- crear productos / items
- registrar órdenes de compra o demanda
- registrar movimientos de inventario
- intentar satisfacer órdenes con el stock disponible
- mantener trazabilidad entre órdenes y movimientos
- notificar al usuario cuando una orden queda completada
- mostrar el estado de completitud de cada orden

Este reto no es solo un CRUD. La parte crítica es la lógica de negocio de fulfillment y la trazabilidad del inventario.

---

## 2. Regla de negocio central

La lógica principal del sistema es:

- una orden pide una cantidad de un item
- el inventario disponible intenta satisfacer esa cantidad
- si el stock es suficiente, la orden se completa
- si el stock es parcial, la orden queda parcialmente completada
- si no hay stock, la orden queda pendiente
- si llega nuevo inventario, el sistema intenta asignarlo a las órdenes pendientes o parcialmente completadas
- un movimiento de inventario debe poder relacionarse con las órdenes que lo consumieron
- cuando una orden llega al 100%, se envía un email al usuario que la generó

---

## 3. Entidades del dominio

### 3.1 User
Atributos:
- id: Long
- name: String
- email: String
- createdAt: LocalDateTime

Reglas:
- email debe ser único
- name no puede estar vacío

### 3.2 Item
Atributos:
- id: Long
- name: String
- sku: String
- stockOnHand: Integer
- createdAt: LocalDateTime

Reglas:
- sku debe ser único
- stockOnHand no puede ser negativo

### 3.3 Order
Atributos:
- id: Long
- user: User
- item: Item
- requestedQuantity: Integer
- fulfilledQuantity: Integer
- remainingQuantity: Integer
- status: OrderStatus
- createdAt: LocalDateTime
- completedAt: LocalDateTime nullable

Estados:
- PENDING
- PARTIALLY_FULFILLED
- COMPLETED

Reglas:
- requestedQuantity > 0
- fulfilledQuantity >= 0
- remainingQuantity = requestedQuantity - fulfilledQuantity
- si remainingQuantity == 0 => COMPLETED
- si remainingQuantity > 0 y fulfilledQuantity > 0 => PARTIALLY_FULFILLED
- si fulfilledQuantity == 0 => PENDING

### 3.4 InventoryMovement
Atributos:
- id: Long
- item: Item
- quantity: Integer
- movementType: MovementType
- order: Order nullable
- createdAt: LocalDateTime
- reason: String nullable

Tipos:
- IN
- OUT

Reglas:
- quantity > 0
- si movementType = IN, suma al stock del item
- si movementType = OUT, resta del stock del item
- si el movimiento está asociado a una orden, debe quedar registrado la relación

---

## 4. Enums

### OrderStatus
```java
public enum OrderStatus {
    PENDING,
    PARTIALLY_FULFILLED,
    COMPLETED
}
```

### MovementType
```java
public enum MovementType {
    IN,
    OUT
}
```

---

## 5. Cálculo de completitud

La completitud de una orden debe calcularse con la siguiente fórmula:

$$
completionPercent = \frac{fulfilledQuantity}{requestedQuantity} \times 100
$$

Ejemplos:
- requested = 20, fulfilled = 20 => 100%
- requested = 20, fulfilled = 10 => 50%
- requested = 20, fulfilled = 0 => 0%

Reglas de estado:
- 0% => PENDING
- 0% < x < 100% => PARTIALLY_FULFILLED
- 100% => COMPLETED

---

## 6. Flujo de creación de una orden

### Caso 1: inventario suficiente
Entrada:
- Item A -> stockOnHand = 50
- Order requestedQuantity = 10

Resultado:
- fulfilledQuantity = 10
- remainingQuantity = 0
- status = COMPLETED
- stockOnHand = 40
- se registra InventoryMovement OUT con quantity = 10, asociado a la orden

### Caso 2: inventario parcial
Entrada:
- Item A -> stockOnHand = 7
- Order requestedQuantity = 10

Resultado:
- fulfilledQuantity = 7
- remainingQuantity = 3
- status = PARTIALLY_FULFILLED
- stockOnHand = 0
- se registra InventoryMovement OUT con quantity = 7, asociado a la orden

### Caso 3: inventario insuficiente
Entrada:
- Item A -> stockOnHand = 0
- Order requestedQuantity = 10

Resultado:
- fulfilledQuantity = 0
- remainingQuantity = 10
- status = PENDING
- no se consume stock

---

## 7. Flujo de ingreso de inventario

Cuando se registra un movimiento de entrada IN:

1. Se valida la cantidad
2. Se actualiza el stock del item
3. Se busca una o más órdenes pendientes o parcialmente completadas del mismo item
4. Se intenta asignar el nuevo inventario a esas órdenes por orden de antigüedad
5. Se actualiza fulfilledQuantity y remainingQuantity de cada orden
6. Se crea la relación movimiento -> orden
7. Si una orden llega a 100%, se marca como COMPLETED
8. Se dispara evento de completitud
9. Se envía email al usuario creador

### Política de asignación recomendada
- FIFO por fecha de creación de la orden
- ordenar por createdAt asc
- asignar la mínima cantidad posible entre stock disponible y remainingQuantity

---

## 8. Trazabilidad: orden ↔ movimiento

El sistema debe permitir consultar ambos lados.

### Desde la orden
Conocer:
- qué movimientos de inventario la cubrieron
- cuánto de cada movimiento se usó
- fecha y cantidad de cada afectación
- si la orden fue completada por un conjunto de movimientos o por uno solo

### Desde el movimiento
Conocer:
- a qué orden está asociado
- si fue entrada o salida
- qué item afectó
- si esa operación contribuyó a completar una orden

### Requisito importante
No basta con guardar el stock actual. El sistema necesita historial de cumplimiento para auditoría y validación.

---

## 9. Notificación por email

Cuando una orden pasa a COMPLETED:
- se debe obtener el usuario creador
- se debe enviar un email con el detalle de la orden
- se debe registrar la notificación como enviada
- se debe evitar duplicación por reintentos o re-ejecuciones

### Regla de idempotencia
Si el mismo evento de finalización se procesa dos veces, no se debe mandar un correo dos veces.

---

## 10. Reglas operativas de negocio

### Regla 1: validación de cantidades
- requestedQuantity debe ser > 0
- fulfilledQuantity debe ser >= 0
- quantity en InventoryMovement debe ser > 0

### Regla 2: stock nunca negativo
- el sistema debe evitar stockOnHand < 0

### Regla 3: trazabilidad obligatoria
- movimientos OUT deben estar ligados a una orden cuando se consumen inventario
- al menos un movimiento de entrada o salida debe quedar registrado para cada cambio

### Regla 4: completitud automática
- no se deben completar órdenes manualmente si la lógica de stock lo hace automáticamente

### Regla 5: notificación al completarse
- la señal de completion debe disparar la notificación por email

---

## 11. Event flow recomendado

Aunque el proyecto es pequeño, conviene modelarlo con eventos de dominio para mantener el diseño limpio.

### Eventos posibles
- OrderCreated
- InventoryAdded
- InventoryAllocated
- OrderPartiallyFulfilled
- OrderCompleted
- NotificationSent

### Secuencia sugerida
```text
OrderCreated
  -> validate quantity
  -> check stock
  -> allocate stock if present
  -> create inventory movement OUT
  -> update order status
  -> if completed => OrderCompleted
  -> send email notification
```

### Ventaja
Esto desacopla la lógica de negocio de la capa HTTP y facilita testeo y extensibilidad.

---

## 12. Arquitectura propuesta

### 12.1 Backend
Tecnologías:
- Java 21
- Spring Boot 3.x
- Spring Web
- Spring Data JPA
- Spring Validation
- Spring Security
- Spring Mail
- PostgreSQL
- JUnit 5 + Mockito + Testcontainers

### 12.2 Frontend
Tecnologías:
- Angular 21
- Angular Material o Bootstrap para UI
- RxJS
- HttpClient
- modular feature architecture

### 12.3 Infraestructura
- Docker
- Docker Compose
- PostgreSQL container
- Backend container
- Frontend container

---

## 13. Arquitectura por capas

### Capa API
Responsable de:
- recibir requests HTTP
- validar payloads
- delegar a servicios de aplicación
- devolver DTOs

#### Ejemplos de endpoints
- POST /api/users
- GET /api/users
- POST /api/items
- GET /api/items
- POST /api/orders
- GET /api/orders
- POST /api/inventory-movements
- GET /api/inventory-movements
- GET /api/orders/{id}/progress
- GET /api/dashboard/summary

### Capa de aplicación
Responsable de:
- orchestration de casos de uso
- lógica de coordinación
- invocación de servicios del dominio

#### Servicios sugeridos
- UserService
- ItemService
- OrderService
- InventoryService
- FulfillmentService
- NotificationService

### Capa de dominio
Responsable de:
- entidades
- enums
- reglas de negocio
- validaciones de dominio
- eventos del dominio

### Capa de persistencia
Responsable de:
- repositories JPA
- queries para consultar órdenes y movimientos
- consultas por item, usuario y estado

### Capa de integración
Responsable de:
- email
- logging
- observability
- external integrations

---

## 14. Estructura recomendada de paquetes

```text
com.fops
├── api
│   ├── controller
│   ├── dto
│   └── exception
├── application
│   ├── order
│   ├── inventory
│   ├── notification
│   └── dashboard
├── domain
│   ├── model
│   ├── enums
│   ├── events
│   └── service
├── infrastructure
│   ├── persistence
│   ├── mail
│   ├── config
│   └── security
└── FopsApplication.java
```

---

## 15. Estructura recomendada del frontend Angular

```text
frontend/
├── src/
│   ├── app/
│   │   ├── core/
│   │   │   ├── api
│   │   │   ├── auth
│   │   │   ├── interceptors
│   │   │   └── routing
│   │   ├── features
│   │   │   ├── users
│   │   │   ├── items
│   │   │   ├── orders
│   │   │   ├── inventory
│   │   │   └── dashboard
│   │   └── shared
│   │       ├── components
│   │       ├── models
│   │       ├── pipes
│   │       └── services
│   ├── assets
│   └── styles.scss
├── angular.json
├── package.json
├── tsconfig.json
└── README.md
```

---

## 16. JSON de ejemplo de dominio

### Order DTO
```json
{
  "id": 1,
  "userId": 5,
  "itemId": 2,
  "requestedQuantity": 20,
  "fulfilledQuantity": 12,
  "remainingQuantity": 8,
  "status": "PARTIALLY_FULFILLED",
  "createdAt": "2026-09-26T10:30:00"
}
```

### InventoryMovement DTO
```json
{
  "id": 7,
  "itemId": 2,
  "orderId": 1,
  "quantity": 12,
  "movementType": "OUT",
  "createdAt": "2026-09-26T10:35:00"
}
```

---

## 17. Casos de prueba clave

### Caso 1: orden completada con stock disponible
- stock 50
- orden 10
- resultado esperado: completed

### Caso 2: orden parcialmente completada
- stock 7
- orden 10
- resultado esperado: partial + remaining = 3

### Caso 3: stock entra y llena una orden pendiente
- orden pendiente de 8
- stock previo 0
- movimiento IN de 10
- resultado esperado: orden completada, stock restante 2

### Caso 4: email enviado solo al completar
- total = 100%
- evento debe disparar email
- no debe dispararse nuevamente si el evento se procesa de nuevo

### Caso 5: trazabilidad del movimiento
- un movimiento OUT debe tener una referencia a la orden correspondiente
- consultar la orden debe devolver el movimiento

---

## 18. Qué lo hace “senior”

Una solución correcta para este reto debe demostrar:
- pensamiento de dominio y no solo CRUD
- manejo de estados y transiciones
- lógica de inventario y cumplimiento
- trazabilidad real
- notificaciones basadas en eventos
- separación de capas
- frontend conectado a API real
- pruebas de negocio y de integración

Eso es exactamente lo que una empresa con un producto MES o de operaciones de fabricación espera ver en un Senior Full-Stack Engineer.

---

## 19. Conclusión

La verdadera esencia del challenge no está en crear cuatro entidades y unas pantallas. La clave es modelar un sistema donde:
- la demanda activa el inventario
- el inventario se asigna a órdenes
- la trazabilidad es obligatoria
- la completitud del pedido se calcula automáticamente
- la notificación se dispara cuando la orden alcanza el 100%

Este diseño es el que mejor encaja con la oferta profesional del puesto y con la intención del reto.

---

## 20. Estado actual del proyecto (septiembre 2026)

### 20.1 Stack confirmado en la implementación

La solución ya quedó definida en su versión ejecutable real con estas tecnologías:

- Backend: Java 21 + Spring Boot 3.3.x
- Frontend: Angular 21
- Persistencia: Hibernate + H2 en desarrollo y PostgreSQL en Docker
- Infraestructura: Docker + Docker Compose
- Email local: MailHog para pruebas sin credenciales externas
- Validación: Maven + JUnit para backend y Angular build para frontend

### 20.2 Estructura real del repositorio

```text
fops_challenge/
├── backend/
│   ├── src/main/java/com/fops/
│   ├── src/test/java/com/fops/
│   ├── pom.xml
│   └── Dockerfile
├── frontend/
│   └── frontend/fops-frontend/
│       ├── src/app
│       ├── package.json
│       ├── Dockerfile
│       └── nginx.conf
├── docker-compose.yml
├── docs/
│   ├── business-logic-and-architecture.md
│   └── development-plan-by-phase.md
└── README.txt
```

### 20.3 Implementación completada hasta ahora

Se ha dejado funcionando la base real del sistema, no solo la estructura inicial:

- dominio de User, Item, Order, InventoryMovement
- enums OrderStatus y MovementType
- lógica de stock y fulfillment en servicios de aplicación
- trazabilidad entre órdenes y movimientos de inventario
- API REST para usuarios, items, órdenes e inventario
- dashboard Angular conectando a la API
- formularios para crear usuarios, items, órdenes y registrar inventario de entrada
- visualización de stock actual, órdenes abiertas y movimientos recientes
- configuración Docker para backend, frontend y base de datos
- configuración de entorno para email local con MailHog

### 20.4 Reglas de negocio ya reflejadas en código

La lógica implementada respeta estas reglas:

- una orden con stock suficiente se completa automáticamente
- una orden con stock parcial queda parcialmente completada
- una orden sin stock queda pendiente
- el inventario nuevo intenta asignarse a pedidos abiertos por antigüedad
- cada movimiento OUT queda asociado a una orden
- el stock nunca debe caer por debajo de cero
- el sistema mantiene un historial de movimientos para trazabilidad

### 20.5 Estado del frontend

El frontend está en una etapa operativa con dashboard funcional, no como un mock estático. Permite:

- consultar usuarios, productos, órdenes y movimientos
- crear usuarios y productos
- registrar nuevas órdenes de demanda
- registrar entrada de inventario
- ver el estado real de abastecimiento y cumplimiento

### 20.6 Estado del backend

El backend ya está preparado para el flujo principal del negocio:

- creación de usuarios
- creación de items
- creación de órdenes
- asignación automática de stock
- registro de movimientos de inventario
- API para consultas y trazabilidad
- integración preparada para notificaciones por email

### 20.7 Validación ejecutada

Se hicieron comprobaciones reales de compilación y arranque del stack:

- backend compilado con Maven
- frontend compilado con Angular production build
- configuración de Docker Compose validada con servicios definidos

Esto confirma que la base técnica actual está sana y lista para continuar con la última fase de refinamiento funcional y notificación local por correo.

### 20.8 Próximo objetivo del proyecto

La última fase restante, en orden lógico, es:

1. integrar MailHog en Docker Compose de forma definitiva
2. implementar NotificationService y envío de email al completar una orden
3. validar el flujo completo con una orden que pase a COMPLETED
4. comprobar en la UI de MailHog que el correo llega exactamente con el contenido esperado
5. dejar la documentación final de arranque para evaluación

Con esto, la solución quedará cerrada como un sistema realista de fulfillment + trazabilidad + notificación, mucho más cercano a una implementación senior que a un CRUD básico.
