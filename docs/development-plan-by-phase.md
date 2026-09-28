# Plan de desarrollo por fases

## Objetivo
Construir una solución funcional y bien estructurada para el Fusion Operations Challenge, alineada con la lógica de negocio, la arquitectura propuesta y con el perfil Senior Full-Stack que se busca en la oferta.

El proyecto se desarrollará en dos bloques principales:
- Backend con Java 21 + Spring Boot 3
- Frontend con Angular 21
- Base de datos PostgreSQL
- Infraestructura Docker Compose

---

## Fase 0 — Preparación del repositorio y base del proyecto

### Objetivo
Crear la estructura monorepo y dejar el entorno listo para trabajar con backend + frontend + BD.

### Tareas
- crear estructura de carpetas
- crear backend con Spring Initializr o manualmente
- crear frontend con Angular 21
- definir docker-compose.yml
- preparar .gitignore
- preparar README principal
- preparar configuración base de entorno

### Entregables
- estructura del proyecto montada
- backend inicial arrancable
- frontend inicial arrancable
- BD con conexión por Docker
- documentación mínima de arranque

### Criterio de aceptación
- backend y frontend pueden arrancar con Docker Compose
- la aplicación responde en local
- la base de datos está conectada correctamente

---

## Fase 1 — Modelo de dominio y persistencia

### Objetivo
Definir las entidades, relaciones y reglas esenciales del negocio en el backend.

### Tareas
- crear entidades User, Item, Order, InventoryMovement
- definir enums OrderStatus y MovementType
- mapear relaciones JPA
- definir atributos básicos de auditoría
- crear repositories
- preparar schema inicial de PostgreSQL
- validar que las relaciones sean correctas

### Entregables
- entidades JPA definidas
- repositorios creados
- base de datos con tablas iniciales
- seed data opcional

### Criterio de aceptación
- se pueden crear y consultar usuarios, items, órdenes y movimientos
- las relaciones están correctas
- la base de datos persiste datos sin errores

---

## Fase 2 — Lógica de negocio central

### Objetivo
Implementar la lógica de cumplimiento de inventario y estados de órdenes.

### Tareas
- construir OrderService
- construir InventoryService
- construir FulfillmentService
- calcular remainingQuantity y fulfilledQuantity
- implementar estado PENDING / PARTIALLY_FULFILLED / COMPLETED
- realizar asignación de stock a órdenes abiertas
- registrar movimientos OUT y mantener trazabilidad
- validar casuística de stock insuficiente

### Entregables
- servicios de dominio funcionales
- cálculo de completitud
- política de autoasignación de inventario
- estado de la orden correctamente calculado

### Criterio de aceptación
- una orden con stock suficiente se completa automáticamente
- una orden con stock parcial queda parcial
- una orden sin stock queda pendiente
- el stock global se ajusta correctamente tras cada movimiento

---

## Fase 3 — Trazabilidad, eventos y notificaciones

### Objetivo
Agregar la parte clave del reto: seguimiento de movimientos y notificación por email.

### Tareas
- crear relación entre InventoryMovement y Order
- implementar trazabilidad de orden a movimientos y viceversa
- definir eventos del dominio
- implementar OrderCompletedEvent
- crear email service
- enviar email al usuario al completar una orden
- evitar notificaciones duplicadas

### Entregables
- historial de asignación entre orden y movimientos
- API de trazabilidad
- email de notificación funcional
- logs del proceso de completion

### Criterio de aceptación
- una orden puede consultar los movimientos que la completaron
- un movimiento puede consultar a qué orden está asociado
- se envía un correo cuando la orden llega a 100%
- no se duplica el correo en reintentos

---

## Fase 4 — API REST y DTOs

### Objetivo
Exponer el backend con endpoints claros y bien diseñados para el frontend.

### Tareas
- crear controllers para users, items, orders, inventory movements, dashboard
- definir DTOs de entrada y salida
- crear excepciones y manejo de errores
- validar REST API con casos reales
- preparar endpoints de consulta para completitud y trazabilidad

### Entregables
- API REST funcional
- DTOs bien definidos
- endpoints documentados
- manejo de errores centralizado

### Criterio de aceptación
- la API responde correctamente a CRUD y queries de negocio
- los payloads son consistentes
- errores de validación se devuelven con formato claro

---

## Fase 5 — Frontend Angular 21

### Objetivo
Construir la capa de usuario para gestionar el negocio y visualizar la información útil.

### Tareas
- crear estructura modular por features
- crear servicios HTTP para API backend
- desarrollar dashboard con indicadores clave
- crear pantallas para usuarios, items, órdenes e inventario
- construir barras de progreso y estado visual
- conectar trazabilidad de movimientos
- mostrar stock actual y órdenes pendientes

### Entregables
- dashboard operativo
- CRUD UI para entidades clave
- vista de completitud de orden
- vista de historial de movimientos

### Criterio de aceptación
- se pueden crear y listar usuarios/items/ordenes
- se puede ver el estado de cada orden
- la UI muestra la completitud y el stock actual
- se ve la trazabilidad de las órdenes y movimientos

---

## Fase 6 — Testing, calidad y estabilidad

### Objetivo
Garantizar la estabilidad del sistema y la lógica de negocio mediante pruebas reales.

### Tareas
- tests unitarios para OrderService, InventoryService, FulfillmentService
- tests de integración para persistencia y flujo de negocio
- validar casos límite
- tests de API REST
- pruebas de email y eventos
- validación final del flujo completo

### Entregables
- suite de tests ejecutada
- casos de negocio cubiertos
- regression checks para órdenes y stock

### Criterio de aceptación
- todos los tests principales pasan
- la lógica de completitud está validada
- no hay regresiones de inventario ni trazabilidad

---

## Fase 7 — Docker, despliegue local y documentación

### Objetivo
Dejar la aplicación ejecutable en local con una sola entrada y documentada para evaluación.

### Tareas
- preparar Dockerfile backend
- preparar Dockerfile frontend
- ajustar docker-compose.yml
- dejar variables de entorno
- documentar arranque local
- preparar README del proyecto
- documentar diseño de negocio y arquitectura

### Entregables
- aplicación arrancable con Docker Compose
- documentación clara de ejecución
- instrucciones de entorno y uso

### Criterio de aceptación
- al ejecutar docker compose up la aplicación inicia correctamente
- frontend y backend se comunican entre sí
- la base de datos se levanta y funciona correctamente

---

## Orden recomendado de ejecución

1. Fase 0: preparación del repo
2. Fase 1: dominio y persistencia
3. Fase 2: lógica de negocio central
4. Fase 3: trazabilidad y notificación
5. Fase 4: API REST
6. Fase 5: Angular 21 UI
7. Fase 6: tests y calidad
8. Fase 7: Docker y documentación

---

## Criterios de finalización del proyecto

El proyecto se considerará terminado cuando:
- el backend gestiona correctamente órdenes e inventario
- se mantiene trazabilidad entre movimientos y órdenes
- las órdenes cambian de estado según stock disponible
- se envía email al completar la orden
- el frontend permite operar con el sistema
- la app corre con Docker Compose
- la lógica de negocio está cubierta por pruebas relevantes

---

## Recomendación práctica de entrega

Para este reto, lo ideal es seguir este plan en sprints cortos, con validación al final de cada fase:
- Sprint 1: dominio y persistencia
- Sprint 2: fulfillment + inventario
- Sprint 3: trazabilidad + email + API
- Sprint 4: frontend + tests + docker

Eso mantiene claridad, reduce riesgo y nos asegura que la base del negocio esté funcionando antes de volcar UI y validaciones visuales.
