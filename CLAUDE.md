# Proyecto: Homebanking MindHub Brothers

Aplicación de homebanking: API Spring Boot (`/backend`, Gradle) y frontend Angular 21 (`/frontend`), desplegados por separado.
Estado: migración completada (Fases 0–3). Historial y decisiones en `MIGRATION_PLAN.md`; guía de uso en `README.md`.

## Comandos

En Windows usar `.\gradlew.bat`. Requiere JDK 21 (`JAVA_HOME=C:\Program Files\Java\jdk-21.0.12` en esta máquina) y Node 24.

### Backend
- Compilar y testear: `./gradlew clean build`
- Solo tests: `./gradlew test`
- Arrancar (perfil `dev` por defecto): `./gradlew :backend:bootRun` (http://localhost:8080)
- Consola H2 (solo `dev`): http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:homebanking`)
- Vulnerabilidades: `./gradlew :backend:dependencyCheckAnalyze` (requiere `NVD_API_KEY`)
- Tras cambiar dependencias: `./gradlew :backend:dependencies --write-locks`

### Frontend
- Instalar: `cd frontend && npm ci`
- Desarrollo: `npm start` (http://localhost:4200, proxy `/api` → http://localhost:8080)
- Build: `npm run build` (salida en `frontend/dist/homebanking-frontend/browser`)
- Tests: `npx ng test --watch=false` (Vitest)

### Docker
- `docker compose up --build` con un `.env` (ver `.env.example`) → http://localhost:8081

## Estructura
- Raíz: `settings.gradle` (`include 'backend'`), `gradlew*`, `gradle/libs.versions.toml`, `docker-compose.yml`, `.env.example`
- `backend/src/main/java/com/mindhub/homebanking/`
  - `controller/` (`AuthController`, `ClientController`), `service/`, `repository/`, `domain/`, `dto/` (records), `mapper/`
  - `security/` (`SecurityConfig`, `JwtConfig`, `AccessTokenService`, `RefreshTokenService`, `LoginRateLimiter`)
  - `exception/` (`GlobalExceptionHandler` → ProblemDetail), `config/` (`SecurityProperties`, `DevDataSeeder`)
- `backend/src/main/resources/application.yml` (perfiles `dev`/`prod`), `db/migration/` (Flyway)
- `backend/src/test/.../support/IntegrationTest` + `TestData`: base de los tests MockMvc (perfil `test`)
- `frontend/src/app/core/` (`models`, `api`, `auth` [servicio + guards], `interceptors`, `utils`); `features/{home,login,accounts,manager}`

## Stack
- Java 21, Spring Boot 3.5.16, Gradle 8.14.5, Spring Security 6 + oauth2-resource-server (JWT HS256), Flyway, Bucket4j
- H2 (dev/test) y PostgreSQL (prod)
- Angular 21 (zoneless, esbuild), Bootstrap 5.3, Vitest

## Convenciones backend
- Nunca exponer entidades JPA: DTOs (records) mapeados en `mapper/`
- Entradas con records + Bean Validation (`@Valid`); solo los campos que el usuario puede fijar (sin mass assignment)
- Errores con `GlobalExceptionHandler` y `ProblemDetail`; nada de stack traces ni mensajes internos
- Inyección por constructor; servicios `@Transactional(readOnly = true)` por defecto
- Cada endpoint con `@PreAuthorize`; rutas nuevas deben añadirse a `SecurityConfig` (deny-by-default)
- Cambios de esquema solo con una nueva migración Flyway `V{n}__*.sql` (`ddl-auto=validate`)
- Tests de seguridad con MockMvc extendiendo `IntegrationTest` (401/403/400 para cada endpoint nuevo)
- Versiones en `gradle/libs.versions.toml`, nunca en `build.gradle`; regenerar `gradle.lockfile`

## Convenciones frontend
- Standalone components, `ChangeDetectionStrategy.OnPush`, signals, `@if`/`@for`/`@let`
- Datos con `toSignal(toLoadState(...))`; formularios reactivos; interceptors funcionales
- El access token solo en memoria (`AuthService`); nunca en localStorage/sessionStorage
- Rutas protegidas con `authGuard` / `roleGuard` (UX; la autorización real está en el backend)
- URLs relativas vía `environment.apiUrl`; TypeScript estricto sin `any`; cada pieza con su `.spec.ts`

## Seguridad (reglas fijas)
- Nunca commitear secretos: variables de entorno / `.env` (git-ignored)
- CORS restrictivo por perfil (`app.security.cors.allowed-origins`), sin `"*"`
- Contraseñas con `DelegatingPasswordEncoder` (bcrypt)
- No loguear datos sensibles (contraseñas, tokens, datos personales, saldos); `toString` sin relaciones ni secretos
- Consola H2 y datos semilla solo en el perfil `dev`

## Reglas de trabajo
- Usar siempre el wrapper (`./gradlew`), nunca Gradle global
- Commits pequeños y descriptivos
- Ejecutar build y tests (backend y frontend) antes de dar una tarea por terminada
- No borrar archivos ni cambiar versiones mayores de dependencias (Java, Spring Boot, Angular) sin preguntar
- Ante la duda, preguntar en vez de asumir
