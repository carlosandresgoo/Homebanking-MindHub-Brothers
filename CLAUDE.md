# Proyecto: Homebanking MindHub Brothers

Aplicación de homebanking: API Spring Boot (`/backend`, Gradle) y frontend Angular 21 (`/frontend`), desplegados por separado.
Estado actual: Fases 0–2 completadas (reestructura, Boot 3.5, migración a Angular). Pendiente: Fase 3 (seguridad). Ver `MIGRATION_PLAN.md`.

## Comandos

En Windows usar `.\gradlew.bat`. Requiere JDK 21 (`JAVA_HOME=C:\Program Files\Java\jdk-21.0.12` en esta máquina) y Node 24.

### Backend
- Compilar y testear: `./gradlew clean build`
- Solo tests: `./gradlew test`
- Arrancar: `./gradlew :backend:bootRun` (http://localhost:8080)
- Consola H2: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:homebanking`)

### Frontend
- Instalar: `cd frontend && npm ci`
- Desarrollo: `npm start` (http://localhost:4200, proxy `/api` → http://localhost:8080)
- Build: `npm run build` (salida en `frontend/dist/homebanking-frontend/browser`)
- Tests: `npm test` (Vitest; `npx ng test --watch=false` para una sola pasada)

### Docker
- `docker compose up --build` → front en http://localhost:8081 (nginx hace de proxy de `/api` al backend)

## Estructura
- Raíz: `settings.gradle` (`include 'backend'`), `gradlew*`, `gradle/wrapper`, `gradle/libs.versions.toml`, `docker-compose.yml`
- `backend/src/main/java/com/mindhub/homebanking/`
  - `controller/` -> REST controllers (`/api/clients`, `/accounts`)
  - `dto/`        -> DTOs expuestos por la API
  - `domain/`     -> entidades JPA (`Client`, `Account`)
  - `repository/` -> Spring Data JPA (también expuestos por Spring Data REST en `/rest`)
  - `HomebankingApplication.java` -> arranque + datos de prueba (`CommandLineRunner`)
- `backend/src/main/resources/application.properties` -> config (H2 en memoria, base-path REST `/rest`)
- `frontend/src/app/`
  - `core/models` (interfaces = DTOs del backend), `core/api` (servicios HttpClient), `core/interceptors`, `core/utils`
  - `features/{home,accounts,manager}` -> páginas, cargadas en diferido desde `app.routes.ts`
- `frontend/public/assets` -> imágenes; `frontend/src/styles.css` -> estilos globales
- `docs/` -> documentación adicional

## Stack
- Java 21 (toolchain), Spring Boot 3.5.16 (Jakarta EE, Hibernate 6), Gradle 8.14.5 (wrapper)
- Spring Web, Spring Data JPA, Spring Data REST, H2 (en memoria, se pierde al reiniciar)
- Angular 21 (zoneless, application builder con esbuild), Bootstrap 5.3 (npm), Vitest

## Convenciones backend
- Nunca exponer entidades JPA en los controllers: usar DTOs (`new XxxDTO(entity)`)
- Validación con Bean Validation (`@Valid`) en todas las entradas (requiere añadir `spring-boot-starter-validation`)
- Errores con `@RestControllerAdvice`; devolver 404 en lugar de `null` cuando no existe el recurso
- Inyección por constructor en código nuevo (el código existente usa `@Autowired` en campos; migrarlo al tocarlo)
- Tests con JUnit 5; si se añade Spring Security, testear con `spring-security-test`
- Paquetes por capas: `controller`, `service`, `repository`, `domain`, `dto`, `mapper`, `config`, `security`, `exception`
- Versiones nuevas siempre en `gradle/libs.versions.toml`, nunca en `build.gradle`

## Convenciones frontend
- Standalone components, sin NgModules; `ChangeDetectionStrategy.OnPush` en todos
- Signals para el estado; control flow `@if` / `@for` / `@let` (no `*ngIf` / `*ngFor`)
- Cargas de datos con `toSignal(toLoadState(...))` (`core/utils/load-state.ts`) y ramas loading/loaded/error en la plantilla
- Formularios reactivos (`NonNullableFormBuilder`); HttpClient con interceptors funcionales
- URLs de la API relativas vía `environment.apiUrl` (`/api`), nunca `http://localhost:8080`
- TypeScript estricto, sin `any`; cada componente/servicio con su `.spec.ts`

## Seguridad (reglas fijas)
- Nunca commitear secretos, tokens ni contraseñas; usar variables de entorno
- CORS restrictivo, sin `"*"`
- Contraseñas con BCrypt/Argon2 cuando se añada autenticación
- No loguear datos sensibles (datos de clientes, saldos, credenciales) ni en backend ni en `console.*`
- La consola H2 y Spring Data REST (`/rest`) exponen datos sin autenticación: solo para desarrollo

## Reglas de trabajo
- Usar siempre el wrapper (`./gradlew`), nunca Gradle global
- Commits pequeños y descriptivos
- Ejecutar build y tests (backend y frontend) antes de dar una tarea por terminada
- No borrar archivos ni cambiar versiones mayores de dependencias (Java, Spring Boot, Angular) sin preguntar
- Ante la duda, preguntar en vez de asumir
