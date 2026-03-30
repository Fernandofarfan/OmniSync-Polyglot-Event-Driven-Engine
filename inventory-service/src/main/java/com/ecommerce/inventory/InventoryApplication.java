package com.ecommerce.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Function;
import java.util.Map;
import java.util.HashMap;

@SpringBootApplication
public class InventoryApplication {
    private static final Logger log = LoggerFactory.getLogger(InventoryApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }

    // Function (consume y produce para Saga Coreography)
    @Bean
    public Function<Message<Map<String, Object>>, Message<Map<String, Object>>> processOrderEvents(InventoryRepository repository) {
        return message -> {
            log.info("Received Order Message with headers: {}", message.getHeaders());
            Map<String, Object> payload = message.getPayload();
            
            // Simular parseo seguro para el ejemplo
            try {
                Map<String, Object> data = (Map<String, Object>) payload.get("data");
                String productId = (String) data.get("productId");
                Integer quantity = (Integer) data.get("quantity");
                String orderId = (String) data.get("orderId");

                Inventory inventory = repository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Product not found"));

                Map<String, Object> replyData = new HashMap<>();
                replyData.put("orderId", orderId);
                replyData.put("productId", productId);

                if (inventory.getQuantity() >= quantity) {
                    inventory.setQuantity(inventory.getQuantity() - quantity);
                    repository.save(inventory); // Save lock optimista

                    replyData.put("newQuantity", inventory.getQuantity());
                    return buildSagaReply(message, "StockUpdated", replyData);
                } else {
                    replyData.put("reason", "Insufficient stock");
                    return buildSagaReply(message, "StockRejected", replyData);
                }
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