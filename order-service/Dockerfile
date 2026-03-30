# Multi-stage build para Node.js
# Etapa 1: Dependencias y transpilación
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
# Asumiendo que usas tsc para compilar
RUN npx tsc

# Etapa 2: Producción
FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production

COPY package*.json ./
RUN npm install --omit=dev
COPY --from=builder /app/dist ./dist

# Crear usuario y darle permisos antes de cambiar
RUN adduser -D nonroot && chown -R nonroot:nonroot /app
USER nonroot

EXPOSE 3000
CMD ["node", "dist/index.js"]
