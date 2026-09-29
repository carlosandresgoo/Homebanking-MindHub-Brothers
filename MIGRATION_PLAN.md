# Plan de migración — Homebanking MindHub Brothers

Base: rama `master` / `Migration` (commit `f29ca6c`, "task2").

## Objetivos
1. Migrar el frontend de Vue a Angular 21 y eliminar Vue por completo.
2. Reordenar el repositorio en `/backend` (Spring Boot) y `/frontend` (Angular).
3. Endurecer la seguridad del backend.

## Decisiones tomadas
| Tema | Decisión |
|---|---|
| Despliegue del front | **Separado**: Angular servido por nginx (Docker). El backend queda como API pura y el CORS se configura por perfil. |
| Plataforma | **Spring Boot 3.5.x + Java 21 + Gradle 8.14.x** (salto de versión mayor autorizado) |
| Autenticación | **JWT propio**: login con email y contraseña, access token de 15 min y refresh token rotado en cookie HttpOnly |
| Librería de UI | Se mantiene **Bootstrap** (instalado por npm, sin CDN) |
| Alta de usuarios | **Solo ADMIN** (`POST /api/clients` desde `/manager`); sin registro público |
| Usuarios semilla (dev) | Contraseña desde `DEV_SEED_PASSWORD` o generada e impresa una vez |

## Estado (29/09/2026)

| Fase | Estado | Commits (rama `Migration`) |
|---|---|---|
| 0 Análisis | ✅ | `7974806` |
| 1 Reestructuración | ✅ | `7cda712` … `9bf40e3` |
| 1.5 Plataforma (Boot 3.5.16, Java 21, Gradle 8.14.5) | ✅ | `490936a` … `3a37000` |
| 2 Angular 21 + eliminación de Vue | ✅ | `26ae508` … `8f8d070` |
| 3 Seguridad | ✅ (con pendientes abajo) | `fc3c7c7` … |

### Problemas del análisis → resolución
| # | Problema | Resuelto en |
|---|---|---|
| 1–3 | Sin auth, Spring Data REST abierto, consola H2 pública | Fase 3: JWT + deny-by-default, Data REST eliminado, H2 solo en `dev` |
| 4 | Credencial filtrada en `origin/task10` y `origin/task11` | **Pendiente del equipo**: rotar esa contraseña (ramas no tocadas) |
| 5–6 | Entidades expuestas, `@RequestMapping` sin verbo | DTOs records, `@GetMapping`/`@PostMapping`, `/accounts` eliminado |
| 7–8 | `toString` recursivo, saldo `double` | `toString` sin relaciones; `BigDecimal(19,2)` |
| 9–10 | CDN sin SRI, URL hardcodeada | Dependencias por npm; `environment.apiUrl` relativo |
| 11–15 | Validación, 404, EAGER/N+1, `@Autowired`, `console.log` de datos | `@Valid` + ProblemDetail, LAZY + entity graphs, inyección por constructor |
| 16–20 | Bugs y calidad del front Vue | Desaparecen con la migración |

### Pendientes
- **OWASP dependency-check** configurado pero sin ejecutar: la NVD rechaza la descarga sin `NVD_API_KEY`.
- **Docker** (`docker compose up`) sin verificar: Docker no está instalado en la máquina de desarrollo.
- Rate limit en memoria: con varias instancias del backend habría que moverlo a Redis (bucket4j-redis).

---

## Fase 0 — Análisis

### Entorno local (29/09/2026)
| Herramienta | Estado |
|---|---|
| JDK | **No instalado**. Hace falta JDK 21 para la Fase 1.5 en adelante; para compilar el estado actual (Gradle 7.6.1) hace falta JDK 11 o 17 |
| Node | v25.8.1: **no soportado por Angular 21** (^20.19, ^22.12 o ^24). Instalar Node 24 LTS |
| git | No está en el PATH (solo el de GitHub Desktop) |

**Build de línea base:** `./gradlew dependencies` y `./gradlew build` **no se han podido ejecutar** porque no hay JDK. Queda pendiente para el inicio de la Fase 1.

### Backend
| Aspecto | Estado |
|---|---|
| Versiones | Java 11, Spring Boot 2.7.10 (sin soporte OSS), Gradle wrapper 7.6.1 |
| Build | Groovy DSL, módulo único, sin version catalog |
| Plugins | `java`, `org.springframework.boot`, `io.spring.dependency-management 1.0.15` |
| Dependencias | data-jpa, data-rest, web, h2, starter-test |
| Paquetes | `Controllers`, `dto`, `models`, `repositories` |
| Autenticación | Ninguna |
| Base de datos | H2 en memoria, `ddl-auto` por defecto, sin Flyway ni Liquibase; datos semilla en `HomebankingApplication` |
| Perfiles | Ninguno |
| Tests | Solo `contextLoads` |
| CI / Docker | No existen |

### Endpoints
| Método | Ruta | Devuelve | Acceso |
|---|---|---|---|
| Cualquiera | `/api/clients` | `List<ClientDTO>` | público |
| Cualquiera | `/api/clients/{id}` | `ClientDTO` o `null` | público |
| Cualquiera | `/accounts` | `List<Account>` (entidad) | público |
| CRUD completo | `/rest/clients`, `/rest/accounts` | Spring Data REST | público |
| — | `/h2-console` | consola H2 | público |

### Frontend (Vue 3 por CDN, sin build)
| Página | JS | Función |
|---|---|---|
| `web/pages/index.html` | `index.js` | Landing estática (logo, navbar, "About us"), AOS |
| `web/pages/account.html` | `account.js` | Clientes con sus cuentas (número, fecha, saldo) |
| `manager.html` | `manager.js` | Formulario "Add client" (sin implementar), volcado JSON y tabla de clientes |

No hay router ni store. Las llamadas HTTP son `axios.get('http://localhost:8080/api/clients')`, duplicadas en dos archivos. Librerías: Bootstrap 5.3.0-alpha2, Popper, AOS (cargado dos veces) y animate.css.

### Problemas encontrados

#### Críticos
1. No hay autenticación ni autorización en ningún endpoint (`build.gradle:20-26`).
2. Spring Data REST expone CRUD completo sin protección: cualquiera puede crear, modificar o borrar clientes y cuentas, y cambiar saldos (`ClientRepository.java:8`, `application.properties:6`).
3. Consola H2 habilitada y pública (`application.properties:1-3`).
4. **Credencial filtrada en el remoto**: `spring.datasource.password=homebankingapp` en las ramas `origin/task10` y `origin/task11` (commit `216f95b`). Hay que cambiar esa contraseña donde se use.

#### Altos
5. Se exponen entidades JPA: `AccountController.java:19` y `ClientDTO.java:19,31` (contiene `Set<Account>`).
6. `@RequestMapping` sin método HTTP (`ClientController.java:25,30`, `AccountController.java:18`).
7. `toString` recursivo entre `Client` y `Account` → `StackOverflowError` (`Client.java:77`, `Account.java:78`).
8. Saldo como `double` (`Account.java:17`).
9. Scripts de CDN sin versión ni SRI (`manager.html:77-78`, `index.html:67-68,72`, `account.html:45-46`).

#### Medios
10. `getClient` devuelve `null` con 200 en vez de 404 (`ClientController.java:33`).
11. URL del backend hardcodeada (`account.js:28`, `manager.js:23`).
12. No hay validación de entradas ni manejo global de errores.
13. `FetchType.EAGER` en ambos lados de la relación (`Client.java:20`, `Account.java:19`).
14. Inyección por campo con `@Autowired` (`ClientController.java:22`, `AccountController.java:15`).
15. Datos personales en `console.log` (`manager.js:26`).

#### Bajos
16. `this.dato` no está definido → `TypeError` (`account.js:31`).
17. `@submit="addClients"` llama a un método comentado y no tiene `.prevent` (`manager.html:25`).
18. Enlaces e imágenes rotos (`manager.html:65`) y `animate.css` relativo que no existe (`index.html:12`).
19. CSS inválido: `100 vh` y `2 rem` (`style.css:79,85,97`).
20. Imports sin usar en los DTOs, paquete `Controllers` con mayúscula y código comentado.

---

## Fases

| Fase | Contenido | Esfuerzo | Riesgo |
|---|---|---|---|
| 1 | Reestructuración | ~3 h | Bajo |
| 1.5 | Boot 3.5 / Java 21 / Gradle 8 | ~3 h | Medio |
| 2 | Angular 21 | ~10-14 h | Medio |
| 3 | Seguridad | ~14-18 h | Medio-alto |

### Fase 1 — Reestructuración (sin cambios de comportamiento)
1. Mover `src/` y `build.gradle` a `backend/`. `settings.gradle` con `include 'backend'`. Crear `docs/` y `README.md`.
2. Crear `gradle/libs.versions.toml` con las versiones actuales.
3. Paquetes por capas: `controller`, `dto`, `domain`, `repository` (más `config`, `security`, `exception`, `service`, `mapper` cuando se usen).
4. `./gradlew clean build` en verde. Un commit por paso.

**Riesgos:** IDE con rutas antiguas; referencias a los estáticos.

### Fase 1.5 — Plataforma
1. Gradle wrapper 8.14.x.
2. Spring Boot 3.5.x, dependency-management 1.1.x y toolchain Java 21.
3. Cambiar `javax.persistence` por `jakarta.persistence`, y `@GenericGenerator` por `GenerationType.IDENTITY`.
4. Build en verde y `GET /api/clients` con la misma respuesta.

**Riesgos:** cambios de Hibernate 6 (generadores, dialectos) y ruptura de dependencias transitivas.

### Fase 2 — Angular 21
- `ng new frontend` (standalone, strict, application builder con esbuild, sin SSR).
- `core/` (modelos, servicios, interceptors, guards), `shared/`, `features/{home,accounts,manager}`.
- Orden de trabajo: interfaces → `ClientService` (HttpClient) → interceptor funcional de errores → rutas con `loadComponent` → componentes (OnPush, signals, control flow `@if`/`@for`, Reactive Forms).
- `proxy.conf.json` (`/api` → `localhost:8080`) y environments.
- **Página piloto: `accounts`**, que se valida antes de migrar el resto.
- `frontend/Dockerfile` (nginx con fallback SPA) y `docker-compose.yml`.
- Eliminar `backend/src/main/resources/static` y verificar que no quedan referencias a Vue.

**Riesgos:** diferencias visuales al quitar el CDN y reescribir el CSS; Node no soportado.

### Fase 3 — Seguridad
- Modelo: `Client` con `password` (hash) y `role`; DTOs de entrada como records con `@Valid`; `AccountDTO` anidado en `ClientDTO`; `BigDecimal` para el saldo.
- Eliminar Spring Data REST.
- `SecurityFilterChain` stateless, deny-by-default, `@EnableMethodSecurity` y `@PreAuthorize`; `/api/clients/current` para el cliente; `/api/clients` solo ADMIN.
- JWT con `oauth2-resource-server`, secreto en `JWT_SECRET`, refresh token rotado en cookie `HttpOnly; Secure; SameSite=Strict`.
- `DelegatingPasswordEncoder` (BCrypt).
- `@RestControllerAdvice` con `ProblemDetail`, sin stack traces.
- CORS por perfil, cabeceras de seguridad (CSP, HSTS, nosniff, frame DENY) y Bucket4j en el login.
- Perfiles `dev` (H2) y `prod` (Postgres por variables de entorno, `ddl-auto=validate`, sin consola H2, Actuator solo con health/info), más `open-in-view=false`.
- Flyway `V1__init.sql`; datos semilla solo en `dev`.
- Front: `authInterceptor`, `authGuard` y página de login.
- OWASP dependency-check, `dependencyInsight` y dependency locking.
- Tests con `spring-security-test` (401, 403, acceso a datos de otro cliente, login, rate limit, 400).

**Riesgos:** complejidad del refresh token; los cambios de modelo requieren migración de datos (ahora no hay datos persistentes, así que el impacto es bajo).

### Cierre
Actualizar README (requisitos, variables de entorno, comandos, estructura) y `CLAUDE.md`.
