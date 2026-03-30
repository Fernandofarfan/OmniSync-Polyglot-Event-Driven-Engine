# Multi-stage build para Node.js
# Etapa 1: Dependencias y transpilación
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# Asumiendo que usas tsc para compilar
RUN npx tsc

# Etapa 2: Producción
FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production

# Usar usuario no root
RUN adduser -D nonroot
USER nonroot

COPY package*.json ./
RUN npm ci --only=production
COPY --from=builder /app/dist ./dist

EXPOSE 3000
CMD ["node", "dist/index.js"]
