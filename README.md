# Homebanking MindHub Brothers

Aplicación de homebanking: API Spring Boot (`/backend`) y frontend Angular 21 (`/frontend`).
Migración en curso — ver [MIGRATION_PLAN.md](MIGRATION_PLAN.md).

## Requisitos
- JDK 21 (`JAVA_HOME` apuntando a él)
- Node 24 LTS (Angular 21 soporta ^20.19, ^22.12 y ^24)
- Opcional: Docker con Compose

## Ejecución en desarrollo
```powershell
# Backend: http://localhost:8080
.\gradlew.bat :backend:bootRun

# Frontend: http://localhost:4200 (proxy /api -> :8080)
cd frontend
npm ci
npm start
```

Rutas del front: `/` (home), `/accounts`, `/manager`.
API: `GET /api/clients`, `GET /api/clients/{id}`.

## Build y tests
```powershell
.\gradlew.bat clean build                 # backend
cd frontend; npm run build; npm test      # frontend
```

## Docker
```powershell
docker compose up --build   # http://localhost:8081
```
nginx sirve el front y reenvía `/api` al contenedor del backend (que no se publica al host).

## Estructura
```
backend/                   Spring Boot (controller, dto, domain, repository) + Dockerfile
frontend/                  Angular 21 (core/, features/) + Dockerfile + nginx.conf
docs/                      documentación adicional
gradle/libs.versions.toml  versiones centralizadas
settings.gradle            include 'backend'
docker-compose.yml         backend + frontend
```
