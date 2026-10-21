# Project Setup and Development Guide

## Backend (Java)
- **Build**: `.\mvnw.cmd clean package` 
- **Test**: `.\mvnw.cmd test` (downloads dependencies first time)
- **Lombok**: Already configured in pom.xml with optional=true
- **VS Code**: Settings updated with Lombok support enabled

## Frontend (TypeScript)
- **Build**: `npm run build` (compiles TypeScript)
- **Dev**: `npm run dev` (watches, rebuilds, runs server)
- **Start**: `npm start` (builds and runs server)

## Project Structure
- **Backend**: Java 21, Spring Boot 3.3.4 in `backend/`
  - Main app: `src/main/java/com/example/facturacion/FacturacionApplication.java`
  - Tests: `src/test/java/com/example/facturacion/FacturacionApplicationTests.java`
- **Frontend**: Node.js/TypeScript in `frontend/`
  - Server: `frontend/src/server.ts`
  - API client: `frontend/src/api.ts`

## Dependencies
- **Backend**: Spring Boot, Spring Data JPA, PostgreSQL, Lombok, iText7, Apache POI
- **Frontend**: Express.js, TypeScript, concurrently, nodemon

## Test Prerequisites
- PostgreSQL database required at `localhost:5432/TP_GRUPAL_ORM`
- Test data auto-generates if database is empty

## Configuration
- **Backend**: `backend/src/main/resources/application.properties`
- **Frontend**: `frontend/package.json`, `frontend/tsconfig.json`
- **VS Code**: `backend/.gitignore`, `frontend/.gitignore`

## Key Notes
- Maven wrapper (`mvnw`) available for cross-platform builds
- Test initialization runs comprehensive data generation (50+ records)
- Frontend serves static files from `public/` directory
- Backend API base: `http://localhost:8080/api`
