# CORS Configuration Specification

## Purpose
Habilitar CORS para que la app Flutter mobile pueda comunicarse con el backend desde orígenes cruzados, manteniendo la seguridad existente.

## Requirements

### Requirement: CORS-CONFIG-001 - CORS Filter Integration
The system MUST enable CORS processing at the Spring Security filter level.

#### Scenario: Preflight OPTIONS from allowed origin returns 200
- GIVEN a preflight OPTIONS to any endpoint with Origin matching an allowed origin
- WHEN the request reaches the server
- THEN response MUST include Access-Control-Allow-Origin matching the request origin
- AND include Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
- AND include Access-Control-Allow-Headers: Authorization, Content-Type, Accept, Origin
- AND include Access-Control-Expose-Headers: Authorization
- AND include Access-Control-Max-Age: 3600
- AND status MUST be 200 OK
- AND the request MUST NOT reach any controller or require authentication

#### Scenario: Preflight OPTIONS from disallowed origin returns 403
- GIVEN a preflight OPTIONS to any endpoint with Origin NOT in the allowed list
- WHEN the request reaches the server
- THEN response MUST NOT include Access-Control-Allow-Origin
- AND MUST have no CORS headers
- AND status SHOULD be 403 Forbidden

### Requirement: CORS-CONFIG-002 - Allowed Origins Externalization
The system MUST allow CORS origins to be configured externally via application properties or environment variables.

#### Scenario: Default origins from application.yml
- GIVEN the system starts with application.yml containing `application.cors.allowed-origins`
- WHEN the application context loads
- THEN the CorsConfigurationSource bean MUST be created with those origins
- AND CORS preflight requests from those origins MUST succeed

#### Scenario: Environment variable override for production
- GIVEN `APPLICATION_CORS_ALLOWED_ORIGINS` env var is set
- WHEN the application context loads
- THEN the CorsConfigurationSource bean MUST use the env var value
- AND the application.yml value MUST be overridden

### Requirement: CORS-CONFIG-003 - No Credentials Mode
The system MUST NOT require credentials (cookies) for CORS since authentication uses JWT Bearer tokens.

#### Scenario: Credentials flag is false
- GIVEN any CORS response
- THEN Access-Control-Allow-Credentials MUST NOT be present or MUST be false
- AND this MUST allow wildcard or multi-value allowed origins without restriction

### Requirement: CORS-CONFIG-004 - Security Filter Chain Integration
The CORS filter MUST execute BEFORE authentication in the security filter chain.

#### Scenario: CORS before authentication
- GIVEN an OPTIONS preflight from an allowed origin to a protected endpoint
- WHEN the request reaches the server
- THEN response MUST include CORS headers
- AND status MUST be 200 OK (not 401 Unauthorized)
- AND the authentication filter MUST NOT be invoked

#### Scenario: Actual request from allowed origin still requires auth
- GIVEN a GET/POST/PUT/DELETE from an allowed origin with Origin header
- WHEN the request reaches the server
- THEN CORS headers MUST be present in the response
- AND if no valid JWT token is provided, response MUST be 401 Unauthorized
- AND CORS headers MUST still be present even in the 401 response

### Requirement: CORS-CONFIG-005 - Existing Tests Unchanged
The system MUST NOT break any existing functionality or tests.

#### Scenario: Existing integration tests pass with CORS enabled
- GIVEN all existing integration tests
- WHEN the tests run with the new CORS configuration
- THEN all existing tests MUST pass without modification
- (TestRestTemplate does not send Origin headers, so CORS is transparent)

### Requirement: CORS-CONFIG-006 - Code Comments in Spanish
All new and modified code MUST include Spanish comments explaining WHAT, WHY, and HOW.

#### Scenario: CorsConfig.java has Spanish comments
- GIVEN the CorsConfig.java file
- WHEN the file is created
- THEN every configuration decision MUST have a Spanish comment explaining WHAT, WHY, and HOW (e.g., UrlBasedCorsConfigurationSource path registration)

#### Scenario: SecurityConfig.java changes have Spanish comments
- GIVEN the SecurityConfig.java modification
- THEN the added `.cors()` line MUST have a Spanish comment explaining why it's needed and how Spring auto-detects the CorsConfigurationSource bean
