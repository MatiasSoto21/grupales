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
- `POST /api/facturas/{id}/reactivar` restores the previous state (including AUTORIZADA, RECHAZADA or ERROR_AFIP), preserving fiscal evidence and clearing cancellation/baja audit fields.
- Cancelled invoices cannot be edited; reactivate through the dedicated action first. Edit never changes state or reactivates a record.

## Creating clients while invoicing
- `POST /api/clientes` creates a client with its required contact and address records and audit fields.
- CUIT/CUIL, denomination, and VAT condition are required; email, phone, mobile and address values are optional.
- Reject duplicate CUIT/CUIL values with HTTP 409.
- The invoice form adds the created client to its selector and selects it without discarding the invoice draft.
- Client VAT condition is stored on the client and auto-selected on future invoices. Existing clients without a stored condition require a manual selection on their first invoice; that choice is saved for future invoices.

## Invoice form and list consistency
- Item quantities must be positive integers in both frontend and backend; discount percentages may be decimal.
- Keep the dashboard summary aligned with pagination: filtered result count is global, while invoice sum is for the current page.
- Validate date, receipt number and amount filters consistently before listing and exporting.
- Return HTTP 400 for invalid invoice data and HTTP 404 when an invoice being updated does not exist.
- Creation and editing of invoices run in a modal opened from the listing, using the existing `factura-form.html` as modal content.
- Creating a client and adding an invoice item use nested modals; a new invoice starts with no preloaded items.

## Invoice amount consistency
- `importeCobrado` is not user-input data: it must always equal the calculated invoice `importeTotal`.
- `importeSaldo` remains zero for invoices created or edited through this application.
- Do not show an editable collected amount in the invoice form; display only `Total`, while the backend owns payment consistency.

## Automatic invoice identification
- For ARCA-authorized invoices, ARCA's `FECompUltimoAutorizado + 1` is authoritative; never present the local database maximum as a definitive preview for that number.
- The new invoice form queries `FECompUltimoAutorizado + 1` for the selected point of sale and shows that ARCA number; refresh it when the point of sale changes. The create request rechecks ARCA, and the saved response remains authoritative.
- Ordinary creation uses the backend Argentina business date; invoice edits preserve the original date and number. A new linked fiscal issuance accepts separately validated operation and fiscal dates, without rewriting the source.
- Number and date are read-only for ordinary creation/editing and are sent on edit without replacing stored values. Only the separate new fiscal-issuance draft allows choosing operation/fiscal dates within backend-provided bounds; the number always remains read-only.
- Keep invoice forms free of instructional helper text, placeholders, and explanatory hints; show only labels and action/error content.

## Invoice list sorting
- Invoice sorting is executed by the backend before pagination.
- Allowed REST fields are `numero`, `fechaEmision`, and `total`; allowed directions are `asc` and `desc`.
- Default sorting is `fechaEmision=desc` and an invoice ID is used as a stable tie-breaker.
- The UI uses one dedicated sort button and a native modal; invoice table headers are not standalone sort buttons.
- Changing sorting resets pagination to page 1 and only the latest asynchronous listing response may update the table.
- PDF and Excel exports receive the same sorting parameters as the paginated listing.

## Modal interaction conventions
- Use native `<dialog>` elements for forms and confirmations.
- Keep the invoice list visible underneath the invoice modal and reload it after a successful save.
- Preserve invoice draft state when the client modal is closed or a new client is selected.
- Product entries are created through the product modal; validate article, positive integer quantity and discount before adding the line.

## Theme interaction
- The frontend supports light and dark modes using `data-theme` on the root element.
- The selected theme is persisted in `localStorage` and shared by the listing and invoice form.
- Theme controls must use native buttons and keep the same icon/action in the listing, standalone form and nested modals.
- Keep visual colors in the shared CSS variables; use accessible contrast for text, controls, tables, states and modal surfaces.

## Product price lists
- `Articulo` is the product; `ListaPrecio` groups sale-price variants, and `ListaPrecioArticulo` connects each article to a list and stores `precioVenta`.
- Invoice details must send `articuloId` and the exact `listaPrecioArticuloId`; the frontend must not send or treat an arbitrary price as authoritative.
- Article prices are retrieved through the paginated article explorer. Catalogos never includes full client or article collections, even when legacy incluirClientes/incluirArticulos flags are true. No article should be listed without a finite positive price in the selected list.
- Creation and editing resolve the selected `ListaPrecioArticulo`, validate that it belongs to the article and has a finite positive price, then persist the unit price in `FacturaVentaDetalle`.
- Editing recalculates totals from the selected price but existing invoice detail values remain historical and are not replaced by later price updates.

## ARCA receipt PDF
- `GET /api/facturas/{id}/comprobante.pdf` serves an individual invoice PDF inline. Show `Ver` for every invoice; authorized invoices with a complete fiscal snapshot include their CAE and ARCA verification QR.
- Invoices without a verifiable CAE snapshot may be viewed as previews, clearly marked as not authorized by ARCA; never display seeded/fabricated CAEs as real authorization data.
- Homologation documents must be visibly marked `SIN VALIDEZ FISCAL`. This application does not issue production-valid receipts.
- Invoice emission for this receipt workflow must remain restricted to `HOMOLOGACION`.
- Configure issuer denomination, VAT status, and address with `FACTURACION_EMISOR_DENOMINACION`, `FACTURACION_EMISOR_CONDICION_IVA`, and `FACTURACION_EMISOR_DOMICILIO` to enable the PDF. Missing PDF display data must not prevent invoice saving or ARCA authorization. `FACTURACION_ARCA_CUIT_EMISOR` is shared with WSFE and the receipt; do not use a different CUIT.
- Do not permit editing invoices with any CAE, evidence of authorization, ERROR_AFIP or ANULADA state. The local cancel/reactivate workflow does not revoke a CAE at ARCA.
- Seeded invoices are sample data, not ARCA authorizations; do not assign them fabricated CAEs.

## Backend (Java)
- **Build**: `.\mvnw.cmd clean package`
- **Test**: `.\mvnw.cmd test` (downloads dependencies first time)
- **Run**: On Windows, if the project path contains non-ASCII characters, map the project root to an unused ASCII drive first; Maven otherwise corrupts the forked Java classpath. From the project root:
  ```powershell
  subst R: "$PWD"
  Set-Location R:\backend
  .\mvnw.cmd spring-boot:run
  ```
  After stopping the app, remove the mapping with `subst R: /d`.
- **ARCA test runner**: Disabled by default; enable only intentionally with `facturacion.arca-test.enabled=true`
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


## Unified educational homologation workflow
- One normal startup, without demo profiles or recipient-mode switches. Send the selected client's declared CUIT/CUIL and VAT condition to WSFE; never substitute an anonymous recipient for a selected customer.
- With the configured Responsable Inscripto issuer scenario, automatically use Factura A (1) for recipient VAT codes 1, 6, 13, 16; Factura B (6) for codes 4, 5, 7, 8, 9, 10, 15.
- Number previews and creation use the same point of sale and receipt class; refresh on customer, VAT or point-of-sale changes.
- No client means anonymous Consumidor Final (DocTipo=99, DocNro=0, VAT=5), subject to ARCA identification limits. Invalid local input returns 400 before emission.
- Explicit WSFE business rejections persist as RECHAZADA with resultadoAfip=R and motivoRechazo. Unconfirmed communication or incomplete-response failures persist as ERROR_AFIP. Neither gets a CAE or an assigned receipt number.
- Only confirmed authorization yields AUTORIZADA, a real homologation CAE and, with a complete issuer snapshot, a matching PDF verification QR. Every document remains SIN VALIDEZ FISCAL.
- Preserve receptorPrueba only for legacy records and their original fiscal snapshots. New invoices always store false. Never rewrite historical authorizations or seeded invoices.
- Writing browser tests are opt-in with RUN_WRITING_TESTS=1. They verify an explicit rejection for a fictional CUIT and a separate authorized consumer-final purchase. Never automatically reissue after an ambiguous failure.
- See README.md for setup and test execution. Personal demonstration guides remain local and are not repository dependencies.

## Internal X documents
- The same form supports fiscal A/B or an explicit internal X document. X is never a fallback for an ARCA rejection.
- comprobanteInterno=true is persisted using tipoComprobanteAfip=0 as an application-only marker (never a WSFE code), with a separate local number per point of sale protected through transaction commit.
- X never calls WSAA/WSFE, never receives CAE or ARCA QR, and PDFs say COMPROBANTE X and DOCUMENTO NO VALIDO COMO FACTURA prominently. X PDFs are final internal documents, not failed fiscal previews.
- Preserve local structural validation and authoritative prices. New/edited X documents calculate an internal amount without adding fiscal VAT. Number/date and document class remain immutable on ordinary edit; X point of sale also remains immutable. Emitting A/B from X creates a separate linked fiscal record.
- Lists and reports prefix internal numbers with X-. No production fiscal validity is implied.

## State and editing consistency
- Current states are EMITIDA (X/internal or historical local records), AUTORIZADA, RECHAZADA, ERROR_AFIP and ANULADA. Payment states are not produced because no payment workflow exists; expose legacy PENDIENTE/PAGADA filter options only if actual historical records use them.
- The backend catalog owns status filter options. Reject invalid status filters for listing and both report exports.
- Editing an active rejected invoice preserves RECHAZADA, its reason and lack of CAE. Saving corrections is not resubmission. Block direct edit URLs and forged API state transitions.
- Cancellation is local archival for all records, including rejected/uncertain attempts. Preserve the prior state; reactivation uses confirmed fiscal evidence first, then that state. Never turn reactivation into fiscal emission.
- ERROR_AFIP is not proof of rejection and stays read-only until reconciliation; no automatic retry or reconciliation is implemented.
- On startup repair legacy EMITIDA labels only when stored resultadoAfip and CAE conclusively prove AUTORIZADA or RECHAZADA; preserve cancelled records and their fiscal snapshots.

## Concurrent edits and corrected previews
- FacturaVenta has an optimistic @Version column. GET /api/facturas/{id} returns version; the invoice form sends it when editing, and stale updates return HTTP 409 without discarding the draft. ORM commit conflicts also return 409.
- Preserve compatibility for API clients that omit the form version; overlapping database writes are still version protected. New clients should always send the loaded version.
- Edits validate PES and positive point-of-sale numbers consistently with creation. Correcting recipient VAT on an unverified fiscal record updates the A/B preview type while preserving its fiscal result and absence of CAE.

## Final form and responsive checks
- Lock editable form controls during save and after persistence; retain the close action. On failure restore their original disabled states, including customer VAT and X point-of-sale restrictions.
- Invalidate pending number previews before saving so late replies cannot overwrite the authoritative saved result. Refresh numbering after a failed save.
- Mobile client dialogs inside the invoice iframe use one column; modal theme controls and dark primary/success buttons use accessible shared foreground colors.

## Client explorer
- Client selection in the invoice form and listing uses a native dialog explorer, never a full client select.
- GET /api/clientes supports denominacion, cuitCuil, coincidencia=parcial|exacta, condicionIvaId, sortBy=denominacion|cuitCuil, sortDir=asc|desc, page and size (1 to 100).
- Name matching ignores case; CUIT/CUIL matching ignores spaces and hyphens. Combined filters use AND. Search treats percent and underscore literally.
- Filter and sort before pagination in the database, using ID as the stable tie-breaker. Only the latest search response may update results.
- The frontend requests /api/catalogos?incluirClientes=false and retrieves only the current client page or GET /api/clientes/{id} when editing.
- Selecting or clearing a client preserves the invoice draft, synchronizes VAT and refreshes the number preview. Creating a client selects it directly.

## Article explorer and additional seed products
- Add/change invoice products through the native product dialog. Select the price list first, defaulting to Mayorista (LP02) for new lines, then select an article from that list. Editing a line starts with its current list.
- GET /api/articulos requires listaPrecioId; optional filters are denominacion, codigo, coincidencia=parcial|exacta, sortBy=denominacion|codigo|precioVenta, sortDir=asc|desc, page and size (1 to 100). Matching ignores case and treats percent/underscore literally.
- Search, filter and sort in the database before pagination, with price-record ID as a stable tie-breaker. Return articuloId and the exact listaPrecioArticuloId with each result. Only the latest search response may update the dialog.
- GET /api/articulos/precios/{id} loads the price records needed to edit an invoice without downloading the complete catalog. Saving still resolves and validates prices in FacturaService.
- GET /api/catalogos includes listasPrecio. The frontend passes incluirClientes=false&incluirArticulos=false to avoid loading complete collections.
- Changing price lists clears the selected article. Quantity and discount are validated before confirming a line. Existing invoice rows use Cambiar to reopen the explorer, never full article/price dropdowns.
- DataInitializer ensures exactly 1000 additional deterministic products, with descriptive BEB- codes and unique beverage names from seed/CatalogoBebidas, on empty and existing databases. Each gets Minorista and Mayorista prices and audit fields. Repeated startups repair missing entries without duplicating products or overwriting existing prices. Existing invoice details remain unchanged.

## Strict server-side listing
- Client, article and invoice listing endpoints filter and sort in the database before paging. All reject size outside 1..100 and offsets exceeding the JPA integer limit. The UI requests 10 records per page.
- /api/catalogos contains small reference catalogs only. clientes and articulos remain empty arrays for response-shape compatibility; legacy flags cannot restore full downloads.
- Frontend invoice query helpers return PageResponse, issue exactly one request, and never traverse all pages. getAllFacturas has been removed. Exports remain backend-generated files with the same filters and sorting.
- Browser code renders the returned page without local filtering/sorting of persisted collections. Only draft invoice lines are calculated/edited locally; backend validates and recalculates them when saving.

## Explicit fiscal issuance from a saved document
- GET /api/facturas/{id} returns puedeEmitirFiscal, origenComprobanteId and comprobanteFiscalDerivadoId. Listing DTOs also include the two relationship IDs.
- POST /api/facturas/{id}/emitir-fiscal accepts a corrected fiscal draft and the required loaded source version. Only an editable active X/EMITIDA or a confirmed RECHAZADA (resultadoAfip=R, no number/CAE) may originate a new attempt.
- This operation creates a distinct A/B record; it never converts the source row in place. The source keeps its number, issue date, details, amount and original fiscal result/reason. A/B to X is forbidden, including rejected A/B; X is never a fallback for fiscal rejection.
- The new fiscal invoice uses the explicitly selected and backend-validated fiscal date, and FECompUltimoAutorizado+1 from ARCA. Its operation date and actual request timestamp are separate. Never copy the X number or silently replace the selected date with today. Only confirmed authorization retains the new number/CAE.
- Source locking, required optimistic version and unique relationships prevent duplicate concurrent fiscal submissions. Every persisted attempt, including RECHAZADA and ERROR_AFIP, prevents another submission from its source. An internal X remains visible/editable after a rejected or uncertain attempt, without enabling automatic resubmission. Once a linked fiscal attempt is confirmed authorized, its X source is replaced in the operational listing and becomes read-only. A fiscal source with a later attempt remains read-only. A confirmed rejection can be edited and explicitly originate its own new attempt; an uncertain ERROR_AFIP cannot.
- The UI separates Guardar cambios from Emitir A/B en ARCA / Nueva emision A/B. The latter opens a fiscal draft with the source operation date (or source date as a proposed value), editable operation/fiscal dates, refreshed number preview and fiscal totals; actual submission requires Emitir en ARCA. Never submit merely by saving corrections, switching a class or reactivating.
- The project treats configured list prices as net amounts and supports a uniform 21% rate for fiscal A/B. X has no fiscal VAT added or discriminated; this internal-document choice does not declare the underlying transaction tax exempt. A adds fiscal VAT; B includes it in its total and displays IVA contenido, with the transparency section in its PDF.
- Do not migrate historical X amounts or rewrite authorized fiscal snapshots automatically. Ordinary saving an editable X applies the current internal amount rule. PDFs hide fiscal net/VAT breakdown for X while keeping its stored amounts and internal-document legends.

## Service and repository boundaries
- Services own business validation, totals, fiscal policy, audit/state changes and transaction orchestration. They must not use EntityManager, JPQL/native queries, Criteria or JPA Specifications directly.
- FacturaVentaConsultasRepository is one concrete repository owning report projections, shared filters, count queries, stable SQL sorting and database paging; there is no matching Impl or Spring Data fragment. FacturaVentaRepository is the separate Spring Data entity repository. The main repository also owns X-number lookup and the pessimistic source lookup for fiscal issuance.
- ClienteRepository and ListaPrecioArticuloRepository own their search specifications and page/sort construction. Services validate input and map page results to response DTOs.
- BloqueoTransaccionalRepository encapsulates PostgreSQL advisory locks and requires an existing service transaction so locks last through commit. ContactoRepository and DomicilioRepository persist the records required for client creation.
- The academic JPQL examples live in repositories/ConsultasRepository, injected into DataInitializer; do not restore the former static ConsultasService taking an EntityManager argument.
- ServiceArchitectureTest guards services against JPA API dependencies. Existing database integration tests remain the regression checks for filters, counts, numbering, ARCA evidence and concurrent submissions.

## Fiscal operation date, document date and submission time
- FechaFacturacionPolicy and an injected Clock in America/Argentina/Buenos_Aires own the calendar rules. Small facturacion catalog metadata supplies fechaActual, fechaFiscalMinima and fechaFiscalMaxima to the UI; never derive fiscal defaults from the browser clock.
- Ordinary new X/fiscal records use the server date. A new X leaves fechaOperacion unspecified because it may be a budget; its own fechaEmision remains immutable. New fiscal documents persist fechaOperacion. No historical dates are backfilled or migrated.
- The dedicated emitir-fiscal operation accepts fechaOperacion and fechaEmision. If absent, propose the source operation date, falling back to its date. The user must correct that proposed operation date when an old X was only a budget; never silently move an invalid original date to today.
- The current product-only workflow (Concepto=1) supports fiscal dates from business today minus 5 calendar days through today. Future dates are intentionally unsupported. Operation dates cannot be future; fiscal date cannot precede the recorded operation or move that operation into another calendar month. A prior-month fiscal date is supported if still inside the 5-day window and chronological sequence.
- Before FECAESolicitar, under the existing numbering transaction lock, consult FECompUltimoAutorizado and FECompConsultar to check the last authorized date in the same POS/class. A chosen date earlier than that returns HTTP 400 without a CAE request or linked attempt. A communication/incomplete lookup failure remains conservatively recorded as ERROR_AFIP; never retry automatically.
- Pass the stored fiscal date explicitly to the dated AfipWsfeService overload: CbteFch, persisted fechaEmision, PDF and QR must match. The SOAP layer must not replace that explicit date with LocalDate.now().
- fechaSolicitudAfip is a backend Instant recorded immediately before the fiscal request, independently of the document date. Ordinary edits preserve all these dates; X/source records remain unchanged after linked issuance.
- Fixed-clock regression tests cover Thursday sale/Saturday submission, Thursday budget/Saturday sale, calendar limits, later authorized dates, timezone boundary, SOAP date and PDF/QR agreement.

## Usability and linked working documents
- Listing and exports show the latest fiscal attempt and preserve an unreplaced X, using SQL before pagination/counting. Only confirmed authorization replaces/hides the X. `incluirVinculados=true` shows the full document history. `puntoVentaId` and `claseDocumento=X|FISCAL` are shared backend filters.
- References include document class, five-digit point of sale and eight-digit number, plus branch description where space permits. Unnumbered attempts use their record ID.
- An authorized A/B and its replaced X stay immutable. A linked X with no confirmed authorization stays editable, but cannot originate a duplicate fiscal submission. Ver X always serves that X's own stored document, never its child.
- Controllers only bind HTTP input/output and call services. CatalogosService maps small reference data obtained by CatalogosRepository; no controller queries the database. AfipDiagnosticoService owns the optional homologation diagnostic used by its controller and runner.
- ServiceArchitectureTest guards services against JPA query APIs and controllers against entity/repository/JPA dependencies.

## Compact invoice listing
- The search UI exposes the point-of-sale filter, without minimum amount, document class or current/history selectors. Existing API parameters remain compatible.
- The invoice table uses the stored number without zero padding or fiscal class prefix, and X- for internal documents. Repeated numbers across branches include the branch in parentheses. The backend detects repetitions across pages; the browser never downloads other pages for this.
- Full fiscal references remain available in PDFs, exports and action confirmations.

## Listing action simplification
- Use one Ver link for the row's own PDF and one Editar button only when that same record is editable. Do not add linked-source view/edit variants or edit an X through an authorized A/B row.
- Keep local Anular/Reactivar and Motivo ARCA actions where applicable. Desktop columns wrap long text, and the action column stays visible while scrolling narrow screens.
- Confirmed authorization replaces the visible X with A/B, retaining X as a read-only antecedent. Rejected/uncertain attempts preserve X visibility; uncertain attempts never allow automatic retries.

## Confirmed authorization replaces X
- `FacturaVenta.reemplazadoPorFiscal` is authoritative backend metadata. On confirmed authorization, the emission transaction marks the originating X, including X -> rejected -> authorized chains. Both authorized fiscal and replaced X reject edits with 409.
- Rejected and uncertain attempts preserve X's stored number, dates, details and visibility. The saved attempt and its reason remain available; never enable a duplicate emission from an X that already has an attempt.
- Listing, counts, duplicate-number detection and exports share SQL visibility rules: latest attempts plus unreplaced X. Full history remains API-compatible and read-only for replaced X.
- Startup repairs only this derived replacement flag for existing chains with confirmed authorization evidence, without altering historical dates, amounts, fiscal snapshots or states. The repair increments the optimistic version.

## Beverage sample catalog
- The 1000 additional products have distinct brand, flavor, size and package names, with matching brand/category entities and descriptive BEB- codes.
- DataInitializer upgrades legacy DEMO products in place by deterministic index: preserve article ID, price-record IDs, existing prices, creation audit and historical invoice details. No sequential demo names/codes remain in the generated catalog.
- Later startups repair missing articles/prices without duplicating records or overwriting custom names/prices of already upgraded products. Catalog combinations are invented sample data, not a statement of actual brand availability.

## Compact article explorer
- Article explorer and single-price DTOs expose codigo as the persisted consecutive numeroCatalogo starting at 1, independently of the article ID, without BEB- or padding. Code searches and code sorting use this number in the database; legacy textual SKU searches remain accepted.
- Seeded drink names use brand + flavor + volume, omitting the category and package. Startup shortens only the previous generated names, preserving custom edits and all article/price IDs and historical invoice details. Internal BEB- seed keys remain stable and hidden from the explorer.
- Product result columns wrap inside the modal, including on mobile.

## Consecutive catalog codes
- Articulo.numeroCatalogo is the unique persisted public numeric code. The initializer assigns 1..N to the existing catalog and appends new codes after MAX without renumbering existing ones. Internal IDs and seed keys never serve as public codes.
- ArticuloRepository assigns missing codes in SQL under the startup transaction lock. Initial priced products precede unpriced ones, so the original purchasable catalog starts at 1.
- The article modal defaults to codigo asc; the backend sorts by numeroCatalogo numerically before pagination and searches that same number. Page 1 shows 1..10 and page 2 11..20 for the original catalog, rather than alphabetical names with unrelated IDs.

## User feedback and form loading
- showAlert reveals and focuses the result at the top of the current page or active native dialog; errors and successes have accessible alert/status roles.
- initValidationFeedback names the invalid control using its label or aria-label and supplies Spanish validation messages, including dynamic quantity and discount fields.
- Saving preserves the draft on HTTP errors. A failed number refresh must not overwrite the save error. No automatic fiscal resubmission is allowed.
- Success survives invoice modal closure through a same-origin message and standalone navigation through a one-time sessionStorage confirmation. A failed listing refresh must distinguish saved data from a failed refresh.
- While initialization or saving is pending, lock form controls; preserve the close action during initialization. Restore the original restrictions after loading or failed saving.
- Disabled selects must look read-only and have no dropdown arrow. X-to-A/B enables fiscal dates and point of sale; document class and the selected client's stored VAT remain locked.
- Preserve a manually chosen fiscal date when the operation date changes. Update it automatically only while it still follows the previous operation date.
- Guard cancellation/reactivation against repeated requests; focus Cancel in the confirmation. Empty listings report page 0 of 0.

## Publication and local configuration
- The repository is https://github.com/MatiasSoto21/grupales; publish the reviewed source to main without force-pushing or changing unrelated repositories. Verify the Git root before staging: extracted project copies may inherit a Desktop Git repository.
- Never commit PostgreSQL passwords, private ARCA keys, local certificates/configuration, WSAA tickets, node_modules, target, dist, logs or test artifacts. Keep db-local.properties ignored; application.properties accepts DB_PASSWORD.
- Keep .vscode/settings.json local and ignored. Its agreed preferences disable null annotation analysis and hide constructor/repository/version recommendations; they do not change the model or dependencies.
- Last frontend validation (2026-11-14): npm test passed 7 checks; npm run test:e2e -- invoices.spec.ts passed 64 browser scenarios. Saves/emissions use intercepted responses. Read-only API checks covered catalogs, pagination, invalid filters and PDF/Excel; no real invoice was emitted in this validation.

## Repository content
- Track application sources, tests, build/dependency manifests and lockfiles, Maven wrappers, README.md, AGENTS.md and frontend/INSTRUCCIONES.md. Keep .gitignore and .gitattributes versioned.
- FUNCIONAMIENTO-ARCA.md, GUIA-EXPOSICION.md, MEMORY.md, prueba.http and .vscode settings are personal local files: keep them on disk and ignored, and do not link to them from published documentation.
- Remove accidentally tracked local files with git rm --cached, preserving their working copies. Publish cleanup commits normally; do not force-push or rewrite history.
