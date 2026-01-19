# Project Context: AWS-Clone Ecosystem

## Overview
This project is a simplified clone of Amazon Web Services (AWS), providing core infrastructure services through a unified dashboard. The architecture is designed to be modular, with each service running as an independent backend component.

## Architecture

### 1. Auth Server (IAM)
The central Identity and Access Management service. It handles user authentication, session management, and authorization across all other services.
- **Technology**: Spring Boot, JWT, MFA (TOTP).
- **Responsibility**: Issuing tokens and providing verification endpoints for other microservices.

### 2. S3 Server (Storage)
An object storage service similar to Amazon S3. 
- **Responsibility**: Handling file uploads, presigned URLs, and multipart uploads.

### 3. Lambda Service (Compute)
A serverless execution engine for running functions.
- **Responsibility**: Isolation of code, managing execution environments, and scaling.

### 4. Frontend Dashboard
A Vue.js application that provides a unified UI for managing all services.
- **Theme**: Retro black-and-white aesthetic.

## Auth Server Implementation Goals
The `auth-server` needs to be implemented with the following in mind:
- **Scalability**: Stateless JWT-based authentication.
- **Security**: Default MFA for all users, Bcrypt password hashing.
- **Integration**: Must be easily consumable by `s3-server` and `lambda-service` for token verification.
- **Developer Experience**: Clear API documentation and simple integration hooks.

## User Flow
1. User registers via Frontend.
2. User logs in and receives a JWT.
3. User enables MFA for enhanced security.
4. User uses the JWT to interact with S3 and Lambda APIs.
