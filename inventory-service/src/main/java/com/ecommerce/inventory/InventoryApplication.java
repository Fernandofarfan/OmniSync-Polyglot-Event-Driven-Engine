package com.ecommerce.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import java.util.function.Function;
import java.util.Map;
import java.util.HashMap;

@SpringBootApplication
public class InventoryApplication {
    private static final Logger log = LoggerFactory.getLogger(InventoryApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }

    // Function (consume y produce para Saga Coreography con Idempotencia)
    @Bean
    @Transactional
    public Function<Message<Map<String, Object>>, Message<Map<String, Object>>> processOrderEvents(
            InventoryRepository inventoryRepository, 
            ProcessedEventRepository eventRepository) {
            
        return message -> {
            log.info("Received Order Message with headers: {}", message.getHeaders());
            Map<String, Object> payload = message.getPayload();
            
            try {
                String eventId = (String) payload.get("eventId");
                
                // 1. Validar Idempotencia (Patrón Inbox)
                if (eventId != null && eventRepository.existsById(eventId)) {
                    log.info("Event {} already processed. Ignoring to maintain idempotency.", eventId);
                    // Opcionalmente devolver null detendría la ejecución sin enviar mensaje
                    // pero para la Saga es mejor devolver el estado actual en un escenario avanzado.
                    return null; 
                }

                Map<String, Object> data = (Map<String, Object>) payload.get("data");
                String productId = (String) data.get("productId");
                Integer quantity = (Integer) data.get("quantity");
                String orderId = (String) data.get("orderId");

                Inventory inventory = inventoryRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Product not found"));

                Map<String, Object> replyData = new HashMap<>();
                replyData.put("orderId", orderId);
                replyData.put("productId", productId);

                Message<Map<String, Object>> replyMessage;

                if (inventory.getQuantity() >= quantity) {
                    inventory.setQuantity(inventory.getQuantity() - quantity);
                    inventoryRepository.save(inventory); // Save lock optimista
                    
                    replyData.put("newQuantity", inventory.getQuantity());
                    replyMessage = buildSagaReply(message, "StockUpdated", replyData);
                } else {
                    replyData.put("reason", "Insufficient stock");
                    replyMessage = buildSagaReply(message, "StockRejected", replyData);
                }

                // Guardar el evento para no volver a procesarlo (Idempotencia en misma transacción)
                if (eventId != null) {
                    eventRepository.save(new ProcessedEvent(eventId));
                }

                return replyMessage;

            } catch (Exception e) {
                log.error("Error processing inventory event: ", e);
                throw e; // Lanza para que DLQ lo capture
            }
        };
    }

    private Message<Map<String, Object>> buildSagaReply(Message<?> originalMessage, String eventType, Map<String, Object> data) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", java.util.UUID.randomUUID().toString());
        event.put("type", eventType);
        event.put("data", data);

        // Propagar traceIds y headers si existen
        return MessageBuilder.withPayload(event)
            .copyHeaders(originalMessage.getHeaders()) 
            .build();
    }
}