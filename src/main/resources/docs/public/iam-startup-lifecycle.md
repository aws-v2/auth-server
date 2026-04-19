---
title: IAM Startup Lifecycle
description: How IAM service boots, initializes dependencies, and becomes ready
icon: 🚀
tags: [startup, lifecycle, boot, spring]
---

## Overview

The IAM service startup process ensures that all critical dependencies are validated before accepting traffic.

---

## Startup Flow

```

main()
↓
Load Config
↓
Initialize Logger
↓
Connect PostgreSQL
↓
Initialize NATS
↓
Run Migrations
↓
Initialize Services
↓
Register Routes
↓
Start HTTP Server

```

---

## Step-by-Step Breakdown

### 1. Configuration Loading
- Reads environment variables
- Loads DB, NATS, JWT config

---

### 2. Database Initialization
- Connects to PostgreSQL
- Validates schema
- Runs migrations if required

---

### 3. NATS Initialization
- Establishes event bus connection
- Used for:
  - audit events
  - IAM decisions logging
  - cross-service communication

---

### 4. Service Wiring

Core services initialized:

- AuthService
- UserService
- PolicyService
- TokenService

---

### 5. Controller Registration

REST endpoints mounted:

```

/api/v1/identity/**

```

---

### 6. Server Startup

Spring Boot starts embedded Tomcat:

- Default port: 8080
- Health endpoint becomes active

---

## Failure Handling

If any of the following fails:

- DB connection
- NATS connection
- Migration failure

👉 Service exits immediately

---

## Design Principle

> IAM must never start in a partially functional state.
```

 