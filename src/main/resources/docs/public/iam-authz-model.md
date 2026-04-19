---
title: Authorization Model
description: How IAM determines access control decisions
icon: 🛡️
tags: [authorization, rbac, policies, access-control]
---

## Overview

Authorization determines:

> “What is this identity allowed to do?”

IAM uses a hybrid model:

- RBAC (Role-Based Access Control)
- Policy-based access control
- Resource-level permissions

---

## Authorization Flow

```

Authenticated Request
↓
Extract Actor
↓
Check Role
↓
Evaluate Policy
↓
Check Resource Permission
↓
ALLOW / DENY

```

---

## RBAC Model

### Roles

- ADMIN
- ENGINEER
- USER
- SERVICE

---

### Role Permissions

```

ADMIN → full access
ENGINEER → infrastructure + debug access
USER → limited resource access
SERVICE → scoped API access

````

---

## Policy Model

Policies define fine-grained rules:

Example:

```json
{
  "effect": "ALLOW",
  "action": "bucket:create",
  "resource": "bucket:*",
  "conditions": {
    "ownerOnly": true
  }
}
````

---

## Resource-Level Security

Every request is evaluated against:

* bucketId
* objectKey
* userId ownership
* service scope

---

## Decision Outcome

### ALLOW

Request proceeds to controller

### DENY

Request rejected:

```
403 Forbidden
```

---

## Design Principle

> IAM enforces **least privilege by default**

```

---

# 🧠 What you now have

This gives you a real internal IAM documentation system:

### ✔ Architecture understanding
### ✔ Startup lifecycle clarity
### ✔ Authentication flow breakdown
### ✔ Authorization model design
### ✔ Production-grade onboarding docs

---

If you want next upgrade, I can help you build:

- 🔐 :contentReference[oaicite:0]{index=0}
- 🧠 :contentReference[oaicite:1]{index=1}
- 🌐 :contentReference[oaicite:2]{index=2}
- 📊 :contentReference[oaicite:3]{index=3}

Just tell me 👍
```
