---
title: Authentication Flow
description: How requests are authenticated in IAM service
icon: 🔐
tags: [auth, jwt, api-key, security]
---

## Overview

Authentication determines **who is making the request**.

IAM supports:

- JWT authentication
- API Key authentication (service-to-service)

---

## Authentication Flow

```

Request
↓
Auth Filter
↓
Extract Token / API Key
↓
Validate Signature
↓
Load Identity
↓
Attach Principal to Context
↓
Forward to Controller

```

---

## JWT Flow

### Step 1: Extract Token
```

Authorization: Bearer <token>

```

---

### Step 2: Validate JWT

- Signature check
- Expiration check
- Issuer validation

---

### Step 3: Load User Identity

- userId
- roles
- permissions

---

### Step 4: Attach Context

Request becomes:

```

actor = {
id: "user-123",
roles: ["ADMIN"],
permissions: ["bucket:read"]
}

```

---

## API Key Flow (Service-to-Service)

Used by microservices:

- S3 service
- Analytics service
- Event consumers

Validation:

- Key lookup in DB
- Scope validation
- Service identity mapping

---

## Security Rule

> Authentication does NOT grant access — it only identifies the caller.
```

--- 