# Homebanking MindHub Brothers

Aplicación de homebanking: API REST Spring Boot (`/backend`) y SPA Angular 21 (`/frontend`), desplegadas por separado (nginx sirve el front y hace de proxy de `/api`).

La interfaz usa **Angular Material 3** (tema azul/cian, tipografía Inter, modo oscuro automático según el sistema) y está en español con formato es-AR.

## Requisitos
- JDK 21 (`JAVA_HOME` apuntando a él)
- Node 24 LTS (Angular 21 soporta ^20.19, ^22.12 y ^24)
- Opcional: Docker con Compose (stack de producción con PostgreSQL)

## Instalación
```powershell
git clone <repo>; cd Homebanking-MindHub-Brothers
cd frontend; npm ci; cd ..
```

## Ejecución en desarrollo
Perfil `dev` (por defecto): H2 en memoria, consola H2, datos de prueba y CORS para `http://localhost:4200`.

```powershell
# Backend: http://localhost:8080
$env:DEV_SEED_PASSWORD = 'elige-una-contraseña'   # opcional
.\gradlew.bat :backend:bootRun

# Frontend: http://localhost:4200 (proxy /api -> :8080)
cd frontend; npm start
```

Usuarios de prueba (solo `dev`):
- `melba@gmail.com` (CLIENT): cuentas VIN001 y VIN002 con movimientos, tarjetas Gold débito y Titanium crédito, y un préstamo Personal con 2 cuotas pagas.
- `admin@mindhub.com` (ADMIN).

Contraseña: `DEV_SEED_PASSWORD`, o la generada que se imprime una vez en el log al arrancar. También podés crear tu propio usuario en `/register`.

| Ruta del front | Acceso |
|---|---|
| `/` | pública |
| `/login`, `/register` | pública (ingreso y alta de clientes) |
| `/accounts` | usuario logueado: "Mis cuentas" (saldo total, abrir cuenta) |
| `/accounts/:id` | dueño o ADMIN: movimientos, transferir, cerrar cuenta |
| `/transfers` | CLIENT: transferir (datos → confirmación → comprobante) |
| `/cards` | CLIENT: tarjetas (pedir, ver, desactivar) |
| `/loans` | CLIENT: préstamos (catálogo, solicitar, pagar cuotas) |
| `/manager` | ADMIN: "Clientes" (métricas, tabla con búsqueda/orden/paginación, alta en diálogo) |

## API
| Método | Ruta | Acceso |
|---|---|---|
| POST | `/api/auth/login` | pública (máx. 5 intentos/min por IP) |
| POST | `/api/auth/register` | pública (máx. 5/min por IP); crea un CLIENT con una cuenta e inicia sesión |
| POST | `/api/auth/refresh`, `/api/auth/logout` | cookie `refresh_token` |
| GET | `/api/clients/current` | autenticado |
| GET / POST | `/api/clients`, GET `/api/clients/{id}` | ADMIN |
| GET / POST | `/api/clients/current/accounts` | autenticado / CLIENT (máx. 3 activas) |
| GET / DELETE | `/api/accounts/{id}` | dueño o ADMIN / dueño (cierra con saldo 0) |
| POST | `/api/transfers` | CLIENT (origen propio; atómica y con bloqueo) |
| GET / POST | `/api/clients/current/cards` | CLIENT (una activa por tipo y color) |
| DELETE | `/api/cards/{id}` | dueño |
| GET | `/api/loans` | autenticado (catálogo) |
| POST | `/api/loans` | CLIENT (un préstamo activo por tipo) |
| GET | `/api/clients/current/loans` | CLIENT |
| POST | `/api/clients/current/loans/{id}/payments` | dueño (paga la próxima cuota) |
| GET | `/actuator/health` | pública; `/actuator/info` solo ADMIN |

Los recursos de otro cliente responden **404** (no se puede averiguar si existen). Reglas de negocio incumplidas (saldo insuficiente, montos o cuotas no permitidos) responden **422**; duplicados, **409**.

Autenticación: access token JWT (15 min) en `Authorization: Bearer`, y refresh token rotatorio (7 días) en una cookie `HttpOnly; Secure; SameSite=Strict`. Los errores se devuelven como `application/problem+json`.

## Variables de entorno
| Variable | Perfil | Obligatoria | Descripción |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | — | no (defecto `dev`) | `dev` o `prod` |
| `JWT_SECRET` | prod | **sí** | Clave HMAC en Base64, ≥ 256 bits. En `dev` se genera una aleatoria si falta |
| `TOTP_ENCRYPTION_KEY` | prod | **sí** | Clave AES en Base64 (256 bits) que cifra los secretos 2FA. Distinta de `JWT_SECRET` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | prod | **sí** | Conexión a PostgreSQL |
| `CORS_ALLOWED_ORIGINS` | ambos | no | Orígenes permitidos, separados por comas (vacío = mismo origen) |
| `DEV_SEED_PASSWORD` | dev | no | Contraseña de los usuarios de prueba |
| `NVD_API_KEY` | build | para OWASP | Clave de la NVD para `dependencyCheckAnalyze` |

Plantilla en [.env.example](.env.example). **Nunca** subas el `.env` real (está en `.gitignore`).

## Build, tests y análisis
```powershell
.\gradlew.bat clean build                         # backend + tests de seguridad
cd frontend; npm run build; npx ng test --watch=false
$env:NVD_API_KEY='...'; .\gradlew.bat :backend:dependencyCheckAnalyze   # OWASP (falla con CVSS >= 7)
.\gradlew.bat :backend:dependencies --write-locks  # tras cambiar dependencias (dependency locking)
```

## Docker (producción)
```powershell
Copy-Item .env.example .env   # rellena DB_PASSWORD y JWT_SECRET
docker compose up --build     # http://localhost:8081
```
Levanta PostgreSQL, el backend con el perfil `prod` (Flyway migra el esquema; `ddl-auto=validate`; sin consola H2) y nginx con el front y las cabeceras de seguridad. El backend no se publica al host.

## Estructura
```
backend/
  src/main/java/com/mindhub/homebanking/
    controller/   Auth, Client, Account, Transfer, Card, Loan
    service/      casos de uso (+ generadores de números de cuenta y tarjeta)
    security/     SecurityConfig, JwtConfig, tokens, rate limiter
    domain/       Client, Account, Transaction, Card, Loan, ClientLoan, RefreshToken (JPA)
    repository/   Spring Data JPA
    dto/ mapper/  records de entrada/salida y mapeo
    exception/    GlobalExceptionHandler (ProblemDetail)
    config/       SecurityProperties, DevDataSeeder, ClockConfig
  src/main/resources/  application.yml (perfiles dev/prod), db/migration (Flyway)
  Dockerfile, gradle.lockfile, dependency-check-suppressions.xml
frontend/
  src/app/core/      models, api, auth (servicio + guards), interceptors, utils
  src/app/features/  home, login, register, accounts, account-detail, transfers, cards, loans, manager
  src/app/shared/    brand, bank-card, confirm-dialog
  src/app/layout/    shell del área privada (toolbar + menú de usuario)
  src/styles.scss    tema Material 3 y estilos globales
  Dockerfile, nginx.conf, security-headers.conf, proxy.conf.json
gradle/libs.versions.toml   versiones centralizadas
docker-compose.yml          db + backend + frontend
docs/                       documentación adicional
```
