# Arquitectura EDA (Event-Driven Architecture) - OmniSync

## Consideraciones de Diseño
1. **Desacoplamiento Total:** Los microservicios no se comunican directamente por HTTP sincrónico para la lógica de negocio core. Todo estado fluye mediante eventos (`OrderCreated`, `StockUpdated`, `OutOfStock`).
2. **Consistencia Eventual:** `order-service` acepta la orden rápidadamente (HTTP 202) y emite un evento. `inventory-service` descuenta el stock independientemente. Si falla, el inventario emitirá un evento compensatorio o un rechazo (`OutOfStock`), que a su vez cancelará la orden de forma asíncrona.
3. **Resiliencia (DLQ):** Ambos servicios están configurados para enrutar mensajes que arrojen excepciones a Dead Letter Queues (DLQ). Esto permite inspeccionar la falla sin bloquear o saturar el broker.

## Esquemas de Evento (JSON)
\`\`\`json
// OrderCreated Event
{
  "eventId": "123e4567-e89b-12d3",
  "type": "OrderCreated",
  "timestamp": "2026-03-29T10:00:00Z",
  "data": {
    "orderId": "abc-123",
    "productId": "prod-999",
    "quantity": 2,
    "customerId": "cust-555"
  }
}

// StockUpdated Event
{
  "eventId": "987fcdeb-e89b-12d3",
  "type": "StockUpdated",
  "timestamp": "2026-03-29T10:00:05Z",
  "data": {
    "productId": "prod-999",
    "newQuantity": 98,
    "orderId": "abc-123" // Contexto de la transacción
  }
}
\`\`\`
