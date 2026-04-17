# Auth Server Skeleton Implementation Plan

This plan outlines the creation of the core components for a standalone Authentication and Identity service. The goal is to provide a complete directory and file structure with pseudo-functions and comments to guide final implementation.

## Proposed Changes

### [Component] Persistence Layer
#### [NEW] [UserRepository.java](auth-server/src/main/java/org/serwin/auth_server/repository/UserRepository.java)
- Standard JpaRepository for [User](auth-server/src/main/java/org/serwin/auth_server/entities/User.java#11-43) entity.

---

### [Component] Service Layer
#### [NEW] [AuthService.java](auth-server/src/main/java/org/serwin/auth_server/service/AuthService.java)
- Contains logic for registration, login, and password management.
- Pseudo-functions for hashing passwords and generating tokens.

#### [NEW] [JwtService.java](auth-server/src/main/java/org/serwin/auth_server/service/JwtService.java)
- Logic for JWT generation, parsing, and validation.

#### [NEW] [MfaService.java](auth-server/src/main/java/org/serwin/auth_server/service/MfaService.java)
- Logic for TOTP (Google Authenticator) or SMS/Email codes.

---

### [Component] Security Configuration
#### [NEW] [SecurityConfig.java](auth-server/src/main/java/org/serwin/auth_server/config/SecurityConfig.java)
- Spring Security configuration to permit auth endpoints and secure others.

#### [NEW] [JwtAuthenticationFilter.java](auth-server/src/main/java/org/serwin/auth_server/config/JwtAuthenticationFilter.java)
- Filter to intercept requests and validate JWT tokens.

---

### [Component] API Layer
#### [MODIFY] [AuthController.java](auth-server/src/main/java/org/serwin/auth_server/controller/AuthController.java)
- Updated with pseudo-calls to `AuthService`.

#### [NEW] [LoginResponse.java](auth-server/src/main/java/org/serwin/auth_server/dto/LoginResponse.java)
#### [NEW] [UserDto.java](auth-server/src/main/java/org/serwin/auth_server/dto/UserDto.java)

## Verification Plan

### Automated Tests
- Run `./mvnw compile` to ensure the skeleton is syntactically correct despite being "pseudo".

### Manual Verification
- Review the generated files to ensure comments are clear and instructional.
- Verify the [project_context.md](docs/project_context.md) provides sufficient background.
