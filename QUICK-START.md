# Quick Start Guide

## Minimal Setup (No Environment Variables Required)

The application now has sensible defaults. You can start it with minimal configuration:

### Option 1: Docker Compose (Recommended - Zero Config)
```bash
# Just run this!
docker-compose up -d --build

# Check logs
docker-compose logs -f auth-server

# Health check
curl http://localhost:8081/actuator/health
```

That's it! All dependencies (PostgreSQL, NATS, Redis, Eureka) start automatically.

### Option 2: Docker Only (Minimal)
```bash
# Build
docker build -t auth_service:latest .

# Run (connects to localhost services)
docker run -p 8081:8081 \
  -e SPRING_PROFILES_ACTIVE=dev \
  auth_service:latest
```

### Option 3: Local Maven (No Docker)
```bash
# Start PostgreSQL first (or use docker-compose for just the DB)
docker-compose up -d postgres

# Run the app
./mvnw spring-boot:run
```

## Default Configuration

The application comes with these defaults (no env vars needed):

| Setting | Default Value |
|---------|---------------|
| **Server Port** | 8081 |
| **Database URL** | jdbc:postgresql://localhost:5432/auth_db_dev |
| **Database User** | root |
| **Database Password** | root |
| **NATS URL** | nats://localhost:4222 |
| **NATS User** | auth-server |
| **NATS Password** | auth-secret |
| **JWT Secret** | (default provided - change in production!) |
| **JWT Expiration** | 86400000 (24 hours) |
| **Mail Host** | smtp.gmail.com |
| **Mail Port** | 587 |
| **Frontend URL** | http://localhost:5173 |
| **Eureka** | Disabled by default |

## When You Need Custom Config

### Mail Configuration (for email features)
Only set these if you want email verification/password reset to work:

```bash
# Docker Compose: Add to .env file
DEV_MAIL_USERNAME=your-email@gmail.com
DEV_MAIL_PASSWORD=your-app-password

# Docker run: Add these flags
-e DEV_MAIL_USERNAME=your-email@gmail.com \
-e DEV_MAIL_PASSWORD=your-app-password
```

### Production Settings
```bash
# Use production profile
SPRING_PROFILES_ACTIVE=prod

# Override defaults as needed
PROD_DATASOURCE_URL=jdbc:postgresql://prod-db:5432/auth_db_prod
PROD_DATASOURCE_USERNAME=prod_user
PROD_DATASOURCE_PASSWORD=secure_password
PROD_JWT_SECRET=your-production-secret-key
```

### Enable Eureka Discovery
```bash
# Set this to true
EUREKA_ENABLED=true
DEV_EUREKA_HOSTNAME=eureka
```

## Testing Endpoints

### Health Check
```bash
curl http://localhost:8081/actuator/health
```

### Register a User
```bash
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Password123!",
    "confirmPassword": "Password123!"
  }'
```

### Login
```bash
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Password123!"
  }'
```

## Troubleshooting

### Database Connection Error
Make sure PostgreSQL is running:
```bash
docker-compose up -d postgres
# Or
docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=root postgres:15-alpine
```

### Can't Connect to NATS
```bash
docker-compose up -d nats
```

### Mail Features Not Working
Mail is optional! The app works without it, but:
- Email verification won't send
- Password reset won't send emails

To fix: Set `DEV_MAIL_USERNAME` and `DEV_MAIL_PASSWORD`

### Port Already in Use
Change the port:
```bash
# Docker
docker run -p 9090:8081 -e AUTH_SERVICE_PORT=8081 auth_service:latest

# Docker Compose: Edit docker-compose.yaml
ports:
  - "9090:8081"
```

## Next Steps

1. ✅ Start the app (done above)
2. 📧 Configure email (optional, for verification features)
3. 🔐 Change JWT secret for production
4. 🚀 Deploy to your preferred platform

See **README-DEPLOYMENT.md** for complete production deployment guide.

## Quick Reference

```bash
# Start everything
docker-compose up -d --build

# Stop everything
docker-compose down

# View logs
docker-compose logs -f auth-server

# Rebuild after code changes
docker-compose up -d --build auth-server

# Clean restart (deletes data!)
docker-compose down -v && docker-compose up -d --build
```

That's it! You're ready to go! 🚀
