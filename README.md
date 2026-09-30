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
- `melba@gmail.com` (CLIENT): cuentas VIN001 (alias `melba.ahorros`) y VIN002 (`melba.gastos`) con movimientos, tarjetas Gold débito y Titanium crédito, y un préstamo Personal con 2 cuotas pagas.
- `admin@mindhub.com` (ADMIN).

Contraseña: `DEV_SEED_PASSWORD`, o la generada que se imprime una vez en el log al arrancar. También podés crear tu propio usuario en `/register`.

| Ruta del front | Acceso |
|---|---|
| `/` | pública: home |
| `/login` | pública: ingreso (email/contraseña + 2FA opcional) |
| `/register` | pública: alta de clientes (crea CLIENT con cuenta inicial) |
| `/forgot-password`, `/reset-password` | pública: recuperar contraseña |
| `/accounts` | autenticado: "Mis cuentas" (saldo total, abrir y cerrar cuentas, gráficos) |
| `/accounts/:id` | dueño o ADMIN: datos para recibir dinero (CBU, alias, compartir; el dueño cambia el alias) y movimientos (filtros, paginación, CSV, comprobante) |
| `/movements/:id` | autenticado: comprobante imprimible de un movimiento |
| `/transfers` | CLIENT: transferencias a cuentas propias o de terceros por número, CBU o alias (muestra el titular antes de confirmar), con límites diarios y 2FA |
| `/contacts` | CLIENT: destinatarios guardados (crear, renombrar, eliminar) |
| `/cards` | CLIENT: tarjetas de crédito y débito (pedir, desactivar, tipos y colores) |
| `/investments` | CLIENT: plazo fijo (catálogo de plazos, simular, crear, renovación automática) |
| `/loans` | CLIENT: préstamos (catálogo, solicitar, pagar por cuotas) |
| `/profile` | autenticado: datos personales, cambiar contraseña, 2FA (setup/enable/disable) |
| `/audit` | ADMIN: registro de auditoría (filtros por actor, acción, fecha, paginación) |
| `/manager` | ADMIN: gestión de clientes (métricas, tabla con búsqueda/orden/paginación, alta de clientes) |

## API

### Autenticación
| Método | Ruta | Acceso |
|---|---|---|
| POST | `/api/auth/login` | pública (máx. 5 intentos/min por IP); 2FA opcional en el request |
| POST | `/api/auth/register` | pública (máx. 5/min por IP); crea CLIENT, cuenta inicial e inicia sesión (201) |
| POST | `/api/auth/refresh` | cookie `refresh_token` (rotación automática) |
| POST | `/api/auth/logout` | cookie `refresh_token` (lo revoca) |
| POST | `/api/auth/password/forgot` | pública (máx. 5/min por IP); envía link de reset (siempre 202) |
| POST | `/api/auth/password/reset` | pública (máx. 5/min por IP); el enlace vence a los 30 minutos y sirve una vez; cierra todas las sesiones |
| POST | `/api/auth/password` | autenticado; cambiar contraseña (requiere actual, cierra otras sesiones) |

### Clientes y perfiles
| Método | Ruta | Acceso |
|---|---|---|
| GET | `/api/clients/current` | autenticado; datos del usuario logged-in |
| GET / POST | `/api/clients` | ADMIN (listar y dar de alta clientes desde `/manager`) |
| GET | `/api/clients/{id}` | ADMIN |
| PATCH | `/api/clients/{id}/status` | ADMIN (bloquear o desbloquear un cliente; bloquear cierra sus sesiones) |

### Cuentas
| Método | Ruta | Acceso |
|---|---|---|
| GET / POST | `/api/clients/current/accounts` | autenticado / CLIENT (máx. 3 activas) |
| GET | `/api/accounts/{id}` | dueño o ADMIN (incluye `cbu` y `alias`) |
| GET | `/api/accounts/lookup?key=` | CLIENT (máx. 5/min): titular enmascarado de un número, CBU o alias; 404 si no existe, 422 `INVALID_CBU` / `OTHER_BANK` |
| PATCH | `/api/accounts/{id}/alias` | dueño; 6–20 letras sin tildes, números, `.` o `-`; 409 si está tomado, 422 `ALIAS_RESERVED` si parece un número de cuenta |
| DELETE | `/api/accounts/{id}` | dueño (solo con saldo 0) |

### Movimientos y comprobantes
| Método | Ruta | Acceso | Notas |
|---|---|---|---|
| GET | `/api/accounts/{id}/transactions` | dueño o ADMIN | Filtros: `from`/`to` (yyyy-MM-dd), `type`, `category`, `q` (descripción); paginación; newest first |
| GET | `/api/accounts/{id}/transactions/export` | dueño o ADMIN | CSV UTF-8 con BOM; mismo filtro que list |
| GET | `/api/transactions/{id}` | dueño o ADMIN | Comprobante de un movimiento (printable) |

### Transferencias
| Método | Ruta | Acceso | Notas |
|---|---|---|---|
| GET | `/api/transfers/limits` | CLIENT | Límite diario, usado hoy, disponible, 2FA requerido, umbrales |
| POST | `/api/transfers` | CLIENT | Origen: número de cuenta propio. Destino: número, CBU (con o sin espacios) o alias. Atómica con bloqueo de filas; límite diario + 2FA; admite `Idempotency-Key`. Solo cuentas de este banco (`OTHER_BANK` para otros) |

### Contactos (destinatarios)
| Método | Ruta | Acceso | Notas |
|---|---|---|---|
| GET | `/api/clients/current/contacts` | CLIENT | Agenda de receptores |
| POST | `/api/clients/current/contacts` | CLIENT | 201; la cuenta se indica por número, CBU o alias (se guarda el número); 404 si no existe; 409 duplicado; máx. 50 contactos |
| PATCH | `/api/clients/current/contacts/{id}` | CLIENT | Renombrar alias |
| DELETE | `/api/clients/current/contacts/{id}` | CLIENT | 204 |

### Tarjetas
| Método | Ruta | Acceso | Notas |
|---|---|---|---|
| GET | `/api/clients/current/cards` | CLIENT | Una activa por tipo (débito/crédito) y color (Gold/Silver/Titanium) |
| POST | `/api/clients/current/cards` | CLIENT | 201; tipos y colores disponibles en el body |
| DELETE | `/api/cards/{id}` | dueño | Desactiva tarjeta |

### Préstamos
| Método | Ruta | Acceso | Notas |
|---|---|---|---|
| GET | `/api/loans` | autenticado | Catálogo: Hipotecario, Personal y Automotor (montos máximos y cuotas en Flyway) |
| POST | `/api/loans` | CLIENT | 201; un préstamo activo por tipo; montos dentro del rango |
| GET | `/api/clients/current/loans` | CLIENT | Préstamos vigentes y pagados |
| POST | `/api/clients/current/loans/{id}/payments` | dueño | Paga la próxima cuota |

### Plazo fijo
| Método | Ruta | Acceso | Notas |
|---|---|---|---|
| GET | `/api/fixed-terms/plans` | autenticado | Plazos disponibles (30, 60, 90, 180, 365 días) y TNA |
| GET | `/api/clients/current/fixed-terms` | CLIENT | Activos primero (por vencimiento), luego pagados |
| POST | `/api/clients/current/fixed-terms` | CLIENT | 201 con Idempotency-Key; min. $1000; genera movimiento de débito |
| PATCH | `/api/clients/current/fixed-terms/{id}` | CLIENT | Activa/desactiva renovación automática (solo si está activo) |

### Resumen financiero
| Método | Ruta | Acceso | Notas |
|---|---|---|---|
| GET | `/api/clients/current/summary` | CLIENT | Ingresos vs egresos por mes, gasto por categoría, saldo diario; `?months=1-12` (default 6) |

### 2FA (TOTP)
| Método | Ruta | Acceso | Notas |
|---|---|---|---|
| POST | `/api/clients/current/2fa/setup` | CLIENT | Step 1: QR y secreto para escanear (409 si ya está activo) |
| POST | `/api/clients/current/2fa/enable` | CLIENT | Step 2: verificar código de la app |
| POST | `/api/clients/current/2fa/disable` | CLIENT | Requiere contraseña actual + código válido |
| DELETE | `/api/clients/{id}/2fa` | ADMIN | Reset para un cliente (si perdió el teléfono) |

### Auditoría
| Método | Ruta | Acceso | Notas |
|---|---|---|---|
| GET | `/api/admin/audit` | ADMIN | Filtros: `actor`, `action`, `from`/`to` (ISO Instant), paginación; newest first; max. 100/página |

### Sistema
| Método | Ruta | Acceso |
|---|---|---|
| GET | `/actuator/health` | pública; nunca muestra detalles |
| GET | `/actuator/info` | ADMIN |

**CBU y alias:** cada cuenta tiene un CBU de 22 dígitos con los dígitos verificadores estándar (entidad `999`, ficticia, y sucursal `0001`) y un alias en minúsculas (al abrirla, tres palabras al azar como `sol.rio.mate`). Las cuentas que existían antes de V12 recibieron un CBU derivado de su id y el alias `cuenta.<id>`, que el dueño puede cambiar.

---

**Convenciones:**
- Recursos de otro cliente: **404** (indistinguible de "no existe").
- Reglas de negocio incumplidas (saldo insuficiente, montos/cuotas fuera de rango): **422** con `code` y parámetros de error.
- Duplicados (email, alias): **409**.
- Demasiados intentos (login, registro, recuperación de contraseña, códigos 2FA): **429**.
- Errores: `application/problem+json` (`status`, `type`, `title`, `detail`, `instance`); sin stack traces.
- **Autenticación:** Access token JWT HS256 (15 min) en `Authorization: Bearer`; Refresh token rotado (7 días) en cookie `HttpOnly; Secure; SameSite=Strict`.
- **Idempotencia:** header opcional `Idempotency-Key` (8–100 caracteres `A-Za-z0-9_-`) en los POST que mueven dinero o crean recursos (transferencias, cuentas, plazos fijos, préstamos): un reintento devuelve la respuesta original con `Idempotent-Replayed: true`.
- **E-mails:** bienvenida, recuperación y cambio de contraseña, 2FA activada o desactivada, transferencias entre clientes (a quien envía y a quien recibe) y plazos fijos (alta y vencimiento). Se envían **después del commit**: si el SMTP falla, la operación no se revierte (solo queda un aviso en el log).

## Variables de entorno
| Variable | Perfil | Obligatoria | Descripción |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | — | no (defecto `dev`) | `dev` o `prod` |
| `JWT_SECRET` | prod | **sí** | Clave HMAC en Base64, ≥ 256 bits. En `dev` se genera aleatoria y persiste en `./data/dev-jwt.key` |
| `TOTP_ENCRYPTION_KEY` | prod | **sí** | Clave AES en Base64 (256 bits) que cifra los secretos 2FA. Distinta de `JWT_SECRET`. En `dev` se genera en `./data/dev-totp.key` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | prod | **sí** | Conexión a PostgreSQL |
| `FRONTEND_URL` | ambos | no | URL pública del frontend, usada en los enlaces de los e-mails. Default: `http://localhost:4200` |
| `MAIL_HOST` | ambos | no | Servidor SMTP. **Sin él no se envían e-mails**: en `dev` se imprimen en el log y en `prod` se descartan con un aviso |
| `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | ambos | con `MAIL_HOST` | Puerto (defecto 587, STARTTLS) y credenciales SMTP |
| `MAIL_FROM` | ambos | no | Remitente. Default: `MindHub Brothers <no-reply@mindhub.local>` |
| `MAIL_SMTP_AUTH`, `MAIL_STARTTLS_REQUIRED` | ambos | no | `true` por defecto; en `false` para un SMTP local de pruebas como Mailpit (`MAIL_HOST=localhost`, `MAIL_PORT=1025`) |
| `CORS_ALLOWED_ORIGINS` | ambos | no | Orígenes permitidos, separados por comas (vacío = same-origin, ideal con nginx proxy) |
| `DEV_SEED_PASSWORD` | dev | no | Contraseña de los usuarios de prueba. Si falta se genera una aleatoria e imprime en log |
| `NVD_API_KEY` | build | para OWASP | Clave de la NVD para `./gradlew :backend:dependencyCheckAnalyze` |

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
    controller/   Auth, Client, Account, Transfer, Card, Loan, Contact, FixedTerm, Movement, Summary, TwoFactor, Audit
    service/      AccountService, CardService, TransferService, LoanService, FixedTermService, ContactService,
                  MovementService, SummaryService, TwoFactorService, AuthService, PasswordService, AuditService,
                  FixedTermMaturityJob (cron diario), IdempotencyService, AccountNumberGenerator, CardNumberGenerator
      notification/  NotificationService (e-mails tras el commit), SmtpMailer / LoggingMailer / UnconfiguredMailer
    security/     SecurityConfig, JwtConfig, LoginRateLimiter, RefreshTokenCleanup, Totp, SecretCipher,
                  ClientUserDetailsService, KeyMaterial, AccessTokenService, RefreshTokenService
    domain/       Client, Account, Transaction, Card, Loan, ClientLoan, Contact, FixedTerm, FixedTermPlan,
                  RefreshToken, AuditEvent, PasswordResetToken, IdempotencyRecord (JPA entities)
    repository/   Spring Data JPA (con queries personalizadas para row locks, búsquedas, auditoría)
    dto/ mapper/  records de entrada/salida (records) y ClientMapper
    exception/    GlobalExceptionHandler (ProblemDetail), BusinessRuleException, ConflictException, ResourceNotFoundException,
                  SecondFactorException, AccountLockedException, TooManyRequestsException
    config/       SecurityProperties, BankingProperties, DevDataSeeder, ClockConfig
  src/main/resources/  application.yml (perfiles dev/prod), db/migration/ Flyway (V1–V11),
                       templates/mail/ (plantillas Thymeleaf de los e-mails, con layout.html común)
  Dockerfile, build.gradle, gradle.lockfile, dependency-check-suppressions.xml
frontend/
  src/app/core/
    api/              account, card, client, contact, transfer, loan, fixed-term, movement, summary, two-factor, audit (services HTTP)
    auth/             auth.service (session en memory), auth.guards, auth.interceptor (Bearer + 401 retry)
    models/           Client, Account, Card, Loan, Contact, FixedTerm, Transaction, Page, etc.
    interceptors/     authInterceptor (funcional)
    utils/            download, load-state (toSignal helper), validación
    i18n/             paginator-intl (es-AR)
  src/app/features/  home, login, register, forgot-password, reset-password, accounts, account-detail, transfers,
                     contacts, cards, investments (plazo fijo), loans, profile (2FA), audit, manager, receipt
  src/app/shared/    brand, bank-card, confirm-dialog, charts (line, bar, donut), initials
  src/app/layout/    shell (toolbar + user menu) de la zona privada
  src/styles.scss    tema Material 3 (tokens, colores es-AR, modo oscuro automático), estilos globales
  Dockerfile, nginx.conf, security-headers.conf, proxy.conf.json
gradle/libs.versions.toml   versiones centralizadas
docker-compose.yml          db + backend + frontend
docs/                       documentación adicional
```
