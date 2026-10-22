# Project Setup and Development Guide

## New Feature: Filter by Client and Receipt Number

### Backend Commands
- **Filter facturas:** `GET /api/facturas?clienteId=123`
- **Filter by exact number:** `GET /api/facturas?numero=1045`
- **Filter by number range:** `GET /api/facturas?numeroDesde=1000&numeroHasta=1100`
- **Filter invoices without a client:** `GET /api/facturas?sinCliente=true`
- **Combined filters:** `GET /api/facturas?clienteId=123&numeroDesde=1000&numeroHasta=1100&fechaDesde=2024-01-01`

### Frontend API Functions
```typescript
// Filter by customer
getFacturas('', { clienteId: 123 })

// Filter by exact receipt number
getFacturas('', { numero: 1045 })

// Filter by receipt number range
getFacturas('', { numeroDesde: 1000, numeroHasta: 1100 })

// Combined filters
getFacturas('', {
    clienteId: 123,
    numeroDesde: 1000,
    numeroHasta: 1100,
    fechaDesde: '2024-01-01'
})
```

### Implementation Notes
- Filters support chaining with existing date, status, and amount filters
- New query parameters: `clienteId`, `numero`, `numeroDesde`, `numeroHasta`
- `sinCliente=true` selects invoices whose optional client relationship is null.
- Full backward compatibility - all existing filters continue to work
- The UI filters support exact receipt number and an inclusive number range, individually or together.
- PDF/Excel reports automatically reflect applied filters, including client and receipt filters.

## Factura cancellation and reactivation
- `DELETE /api/facturas/{id}` performs a logical cancellation.
- `POST /api/facturas/{id}/reactivar` restores a cancelled invoice to `EMITIDA` and clears cancellation/baja audit fields.
- Cancelled invoices can also be edited; choose `EMITIDA`, `PENDIENTE` or `PAGADA` to reactivate during the edit.

## Creating clients while invoicing
- `POST /api/clientes` creates a client with its required contact and address records and audit fields.
- CUIT/CUIL and denomination are required; email, phone, mobile and address values are optional.
- Reject duplicate CUIT/CUIL values with HTTP 409.
- The invoice form adds the created client to its selector and selects it without discarding the invoice draft.

## Invoice form and list consistency
- Item quantities must be positive integers in both frontend and backend; discount percentages may be decimal.
- Keep the dashboard summary aligned with pagination: filtered result count is global, while invoice sum is for the current page.
- Validate date, receipt number and amount filters consistently before listing and exporting.
- Return HTTP 400 for invalid invoice data and HTTP 404 when an invoice being updated does not exist.
- Keep invoice creation in its dedicated form page; the listing page remains focused on search and management.

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
