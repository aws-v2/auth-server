# Auth Server Deployment Guide

## Prerequisites

- Docker & Docker Compose
- Java 17 (for local development)
- Maven 3.9+ (for local development)
- PostgreSQL 15+ (if running locally without Docker)
- NATS Server (if running locally without Docker)

## Quick Start with Docker Compose

### 1. Configure Environment Variables

Copy the example environment file:
```bash
cp .env.example .env
```

Edit `.env` and update:
- `MAIL_USERNAME` - Your Gmail address
- `MAIL_PASSWORD` - Your Gmail app password (not your regular password)
- Other variables as needed

### 2. Build and Run

```bash
# Build and start all services
docker-compose up --build

# Or run in detached mode
docker-compose up -d --build

# View logs
docker-compose logs -f auth-server

# Stop all services
docker-compose down

# Stop and remove volumes (WARNING: deletes all data)
docker-compose down -v
```

### 3. Access the Services

- **Auth Server**: http://localhost:8081
- **PostgreSQL**: localhost:5432
- **NATS**: localhost:4222 (client), localhost:8222 (monitoring)
- **Eureka**: http://localhost:8761
- **Redis**: localhost:6379

### 4. Health Check

```bash
curl http://localhost:8081/actuator/health
```

## Docker Build Only

To build just the Docker image:

```bash
# Build the image
docker build -t auth-server:latest .

# Run the container
docker run -p 8081:8081 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e DEV_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/auth_db_dev \
  auth-server:latest
```

## Local Development (without Docker)

### 1. Setup Database

```bash
# Create database
createdb auth_db_dev

# Run migrations (handled automatically by Flyway on startup)
```

### 2. Configure Application

Update `src/main/resources/.env` with your local settings.

### 3. Run Application

```bash
# Using Maven
./mvnw spring-boot:run

# Or using Maven wrapper on Windows
mvnw.cmd spring-boot:run

# With specific profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Production Deployment

### SSL/TLS Configuration

#### Option 1: Load Balancer SSL Termination (Recommended)
Deploy behind AWS ALB, NGINX, or Traefik that handles SSL.
No SSL configuration needed in the application.

#### Option 2: Application-Level SSL
1. Generate or obtain SSL certificate
2. Convert to PKCS12 format:
   ```bash
   openssl pkcs12 -export \
     -in certificate.crt \
     -inkey private.key \
     -out keystore.p12 \
     -name auth-server
   ```
3. Set environment variables:
   ```bash
   SSL_ENABLED=true
   SSL_KEYSTORE_PATH=/path/to/keystore.p12
   SSL_KEYSTORE_PASSWORD=your_password
   SSL_KEY_ALIAS=auth-server
   ```

### Environment Variables for Production

```bash
# Profile
SPRING_PROFILES_ACTIVE=prod

# Database
PROD_DATASOURCE_URL=jdbc:postgresql://prod-db-host:5432/auth_db_prod
PROD_DATASOURCE_USERNAME=prod_user
PROD_DATASOURCE_PASSWORD=strong_password

# JWT
PROD_JWT_SECRET=generate_a_strong_secret_here
JWT_SECRET_EXPIRATION=86400000

# NATS
PROD_NATS_URL=nats://prod-nats-host:4222
PROD_NATS_USERNAME=auth-server
PROD_NATS_PASSWORD=strong_password

# Mail
PROD_MAIL_HOST=smtp.gmail.com
PROD_MAIL_PORT=587
PROD_MAIL_USERNAME=your-email@domain.com
PROD_MAIL_PASSWORD=your_app_password

# Frontend
PROD_FRONTEND_URL=https://yourdomain.com

# SSL (if not using load balancer)
SSL_ENABLED=false

# JVM Options
JAVA_OPTS="-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### Kubernetes Deployment

Example deployment:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-server
spec:
  replicas: 3
  selector:
    matchLabels:
      app: auth-server
  template:
    metadata:
      labels:
        app: auth-server
    spec:
      containers:
      - name: auth-server
        image: your-registry/auth-server:latest
        ports:
        - containerPort: 8081
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        # Add other env vars from ConfigMap/Secrets
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 5
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
```

## Rate Limiting

The application includes rate limiting on authentication endpoints:

- **Auth endpoints** (login, register, etc.): 10 requests/minute per IP
- **MFA endpoints**: 5 requests/minute per IP
- **API endpoints**: 100 requests/minute per user

Rate limits are stored in:
- **Development**: In-memory cache (Caffeine)
- **Production**: Redis (configure in docker-compose or separately)

## Monitoring

### Health Checks

- **Liveness**: `GET /actuator/health/liveness`
- **Readiness**: `GET /actuator/health/readiness`
- **General Health**: `GET /actuator/health`

### Logs

Logs are written to:
- **Console**: All logs
- **File**: `logs/auth-server.log` (rolling, 30-day retention)
- **Error File**: `logs/auth-server-error.log`
- **Audit File**: `logs/auth-server-audit.log` (90-day retention)

Log locations in Docker:
- Mounted to `./logs` on host machine

## Security Considerations

1. **Secrets Management**
   - Use AWS Secrets Manager, HashiCorp Vault, or Kubernetes Secrets
   - Never commit secrets to version control
   - Rotate secrets regularly

2. **Database Security**
   - Use strong passwords
   - Enable SSL for database connections
   - Restrict network access

3. **Network Security**
   - Deploy behind a firewall
   - Use private networks for internal services
   - Enable SSL/TLS for all external communications

4. **Rate Limiting**
   - Adjust rate limits based on your requirements
   - Monitor for abuse

5. **Audit Logs**
   - Regularly review audit logs
   - Set up alerts for suspicious activity

## Troubleshooting

### Application won't start

```bash
# Check logs
docker-compose logs auth-server

# Check if dependencies are healthy
docker-compose ps

# Verify database connection
docker-compose exec postgres psql -U root -d auth_db_dev -c "SELECT 1"
```

### Database connection issues

```bash
# Check if PostgreSQL is running
docker-compose ps postgres

# Check database logs
docker-compose logs postgres

# Verify connection string in .env file
```

### Rate limiting not working

```bash
# Check Redis is running
docker-compose ps redis

# Verify Redis connection
docker-compose exec redis redis-cli -a redis-password ping
```

## Performance Tuning

### JVM Options

Adjust based on your available memory:

```bash
# Development (512MB - 1GB)
JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

# Production (1GB - 4GB)
JAVA_OPTS="-Xms1g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError"
```

### Database Connection Pool

Configure in application properties:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

## Backup and Recovery

### Database Backup

```bash
# Backup
docker-compose exec postgres pg_dump -U root auth_db_dev > backup.sql

# Restore
docker-compose exec -T postgres psql -U root auth_db_dev < backup.sql
```

### Log Backup

Logs are automatically rotated. Archive old logs to S3 or similar storage.

## Support

For issues or questions:
1. Check the logs
2. Review this documentation
3. Contact the development team
