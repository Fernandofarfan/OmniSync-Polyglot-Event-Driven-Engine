import express from 'express';
import amqp from 'amqplib';
import mongoose from 'mongoose';
import { v4 as uuidv4 } from 'uuid';
import swaggerUi from 'swagger-ui-express';

const app = express();
app.use(express.json());

// Swagger Docs Mocks Config
const swaggerDocument = {
  openapi: '3.0.0',
  info: { title: 'Order Service API', version: '1.0.0' },
  paths: {
    '/api/orders': {
      post: {
        summary: 'Create a new order',
        requestBody: {
          content: {
            'application/json': {
              schema: {
                type: 'object',
                properties: {
                  productId: { type: 'string', example: 'notebook-gaming-01' },
                  quantity: { type: 'integer', example: 1 },
                  customerId: { type: 'string', example: 'user-01' }
                }
              }
            }
          }
        },
        responses: {
          '202': { description: 'Order created, pending inventory check' }
        }
      }
    }
  }
};

app.use('/api-docs', swaggerUi.serve, swaggerUi.setup(swaggerDocument));

const RABBITMQ_URL = process.env.RABBITMQ_URL || 'amqp://guest:guest@localhost:5672';
const MONGO_URL = process.env.MONGO_URL || 'mongodb://root:password@localhost:27017/orders?authSource=admin';

// MongoDB Schema
const orderSchema = new mongoose.Schema({
    orderId: { type: String, required: true, unique: true },
    productId: String,
    quantity: Number,
    customerId: String,
    status: { type: String, enum: ['PENDING', 'CONFIRMED', 'CANCELLED'], default: 'PENDING' }
}, { timestamps: true });

const Order = mongoose.model('Order', orderSchema);

let channel: amqp.Channel;

async function connectToDatabases() {
    try {
        await mongoose.connect(MONGO_URL);
        console.log('Connected to MongoDB');
    } catch (err) {
        console.error('Mongo connection error:', err);
    }
}

async function connectRabbitMQ() {
    try {
        const connection = await amqp.connect(RABBITMQ_URL);
        channel = await connection.createChannel();
        
        const exchangeStock = 'stock-events'; // Respuestas del inventario

        await channel.assertExchange(exchangeStock, 'topic', { durable: true });
        
        // Cola para escuchar las repuestas (Saga Pattern)
        const queueRes = await channel.assertQueue('order-service.stock-response', { durable: true });
        await channel.bindQueue(queueRes.queue, exchangeStock, '#');

        channel.consume(queueRes.queue, async (msg) => {
            if (msg) {
                const event = JSON.parse(msg.content.toString());
                console.log('Received Reply Event (Saga):', event);
                
                // Actualizar DB basado en el evento (Coreography)
                if (event.type === 'StockUpdated') {
                    await Order.findOneAndUpdate({ orderId: event.data.orderId }, { status: 'CONFIRMED' });
                } else if (event.type === 'StockRejected') {
                    await Order.findOneAndUpdate({ orderId: event.data.orderId }, { status: 'CANCELLED' });
                }
                channel.ack(msg);
            }
        });

    } catch (error) {
        console.error('RabbitMQ connection error:', error);
        setTimeout(connectRabbitMQ, 5000);
    }
}

app.post('/api/orders', async (req, res) => {
    const { productId, quantity, customerId } = req.body;
    const orderId = uuidv4();
    const traceId = req.headers['x-b3-traceid'] || uuidv4(); // OpenTelemetry basic propagation

    // 1. Guardar en Base de Datos
    const newOrder = new Order({ orderId, productId, quantity, customerId });
    await newOrder.save();
    
    // 2. Event Payload
    const eventPayload = {
        eventId: uuidv4(),
        type: 'OrderCreated',
        timestamp: new Date().toISOString(),
        data: { orderId, productId, quantity, customerId }
    };

    // 3. Publicar (simulación simplificada Outbox)
    if(channel) {
        channel.publish('order-events', '', Buffer.from(JSON.stringify(eventPayload)), {
            headers: { traceparent: `00-${traceId}-0000000000000001-01`, traceId: traceId } // Inject Distributed Tracing Headers
        });
    }

    res.status(202).json({ 
        message: 'Order created, pending inventory check', 
        orderId: orderId,
        traceId: traceId 
    });
});

app.listen(3000, async () => {
    console.log('Order Service running on port 3000');
    await connectToDatabases();
    await connectRabbitMQ();
});
