# Proyecto: Homebanking MindHub Brothers

Aplicación de homebanking con backend Spring Boot (Gradle, módulo único) y frontend Vue 3 servido como estático desde Spring.
Estado actual: proyecto en fase inicial (clientes y cuentas, solo lectura). Objetivo futuro: migrar el front de Vue a Angular (aún no existe `/frontend` ni `MIGRATION_PLAN.md`).

## Comandos

En Windows usar `.\gradlew.bat` (o `.\gradlew` desde PowerShell).

### Backend
- Compilar y testear: `./gradlew clean build`
- Solo tests: `./gradlew test`
- Arrancar: `./gradlew bootRun` (http://localhost:8080)
- Consola H2: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:homebanking`)

### Frontend (actual, Vue 3 por CDN)
- No hay build ni npm: los archivos viven en `src/main/resources/static` y se sirven al arrancar el backend.
- Páginas: `/web/pages/index.html`, `/web/pages/account.html`, `/manager.html`

## Estructura
- `src/main/java/com/mindhub/homebanking/`
  - `Controllers/` -> REST controllers (`/api/clients`, `/api/accounts`)
  - `dto/`         -> DTOs expuestos por la API
  - `models/`      -> entidades JPA (`Client`, `Account`)
  - `repositories/`-> Spring Data JPA (también expuestos por Spring Data REST en `/rest`)
  - `HomebankingApplication.java` -> arranque + datos de prueba (`CommandLineRunner`)
- `src/main/resources/application.properties` -> config (H2 en memoria, base-path REST `/rest`)
- `src/main/resources/static/` -> front Vue 3 + Axios + Bootstrap + AOS (`web/js`, `web/css`, `web/pages`, `web/assets`)
- `src/test/java/...` -> tests (JUnit 5)

## Stack
- Java 11, Spring Boot 2.7.10, Gradle 7.6.1 (wrapper)
- Spring Web, Spring Data JPA, Spring Data REST, H2 (en memoria, se pierde al reiniciar)
- Front: Vue 3 (Options API, `createApp`) y Axios cargados desde CDN

## Convenciones backend
- Nunca exponer entidades JPA en los controllers: usar DTOs (`new XxxDTO(entity)`)
- Validación con Bean Validation (`@Valid`) en todas las entradas (requiere añadir `spring-boot-starter-validation`)
- Errores con `@RestControllerAdvice`; devolver 404 en lugar de `null` cuando no existe el recurso
- Inyección por constructor en código nuevo (el código existente usa `@Autowired` en campos; migrarlo al tocarlo)
- Tests con JUnit 5; si se añade Spring Security, testear con `spring-security-test`
- Mantener los nombres de paquetes existentes (`Controllers`, `models`, `repositories`, `dto`) salvo que se acuerde renombrar

## Convenciones frontend (Vue actual)
- Una app Vue por página (`createApp({...}).mount('#app')`), JS en `web/js/<página>.js`
- Llamadas a la API con Axios usando rutas relativas (`/api/...`), no `http://localhost:8080`
- Si se inicia la migración a Angular: standalone components, signals, `@if`/`@for`, OnPush, formularios reactivos, TypeScript estricto sin `any`

## Seguridad (reglas fijas)
- Nunca commitear secretos, tokens ni contraseñas; usar variables de entorno
- CORS restrictivo, sin `"*"`
- Contraseñas con BCrypt/Argon2 cuando se añada autenticación
- No loguear datos sensibles (datos de clientes, saldos, credenciales)
- La consola H2 y Spring Data REST (`/rest`) exponen datos sin autenticación: solo para desarrollo

## Reglas de trabajo
- Usar siempre el wrapper (`./gradlew`), nunca Gradle global
- Commits pequeños y descriptivos
- Ejecutar build y tests antes de dar una tarea por terminada
- No borrar archivos ni cambiar versiones mayores de dependencias (Java, Spring Boot) sin preguntar
- Ante la duda, preguntar en vez de asumir
