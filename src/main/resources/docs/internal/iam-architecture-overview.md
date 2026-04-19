---
title: IAM Architecture Overview
description: Internal architecture of IAM service and how identity flows through the system
icon: 🏗️
tags: [iam, architecture, identity, security]
---

## System Overview

The IAM service is the central identity provider for all microservices in the platform. It is responsible for:

- Authentication (who you are)
- Authorization (what you can do)
- Token validation (JWT / API keys)
- Policy enforcement integration
- Event emission for security tracking

---

## High-Level Architecture

```

Client / Microservices
↓
IAM Service (Spring Boot)
↓
┌───────────────┬────────────────┐
│               │                │
PostgreSQL     NATS           Token Layer
(identity)   (events)        (JWT validation)

```

---

## Core Responsibilities

### 1. Identity Management
- User and service identity storage
- Role assignment
- Permission mapping

### 2. Authentication Layer
- JWT validation
- API key validation
- Token lifecycle management

### 3. Authorization Layer
- RBAC (Role-Based Access Control)
- Policy-based checks
- Resource-level permissions

---

## External Dependencies

- PostgreSQL → identity storage
- NATS → event streaming
- Other services → IAM validation calls

---

## Design Principle

> IAM is a **stateless validation layer**, with PostgreSQL as the source of truth.

---

## Key Insight

IAM does NOT enforce business logic — it only answers:

> “Is this request allowed?”
```
