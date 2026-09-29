# Homebanking MindHub Brothers

Aplicación de homebanking: API Spring Boot (`/backend`) y frontend web.
Migración en curso a Angular 21 + Spring Boot 3.5 — ver [MIGRATION_PLAN.md](MIGRATION_PLAN.md).

## Requisitos
- JDK 21 (`JAVA_HOME` apuntando a él)
- Stack: Spring Boot 3.5.16, Gradle 8.14.5 (wrapper)

## Ejecución
```powershell
.\gradlew.bat clean build          # compila y ejecuta tests
.\gradlew.bat :backend:bootRun     # http://localhost:8080
```

- API: `GET /api/clients`, `GET /api/clients/{id}`
- Front actual: http://localhost:8080/web/pages/index.html

## Estructura
```
backend/                 Spring Boot (controller, dto, domain, repository)
docs/                    documentación adicional
gradle/libs.versions.toml  versiones centralizadas
settings.gradle          include 'backend'
```
