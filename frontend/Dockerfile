# Build stage
FROM node:24-alpine AS builder

WORKDIR /build

# Copy package files
COPY frontend/package*.json ./

# Install dependencies
RUN npm ci

# Copy source code
COPY frontend/ .

# Build the application
RUN npm run build

# Runtime stage
FROM nginxinc/nginx-unprivileged:stable-alpine

# Copy nginx configuration from repo root docker/ directory
COPY docker/nginx.conf /etc/nginx/nginx.conf

# Copy the built Angular app from build stage
COPY --from=builder /build/dist/markitdone-web/browser /usr/share/nginx/html

# Expose port
EXPOSE 8080

# nginx-unprivileged already has a non-root user and entrypoint configured
