package com.example.facturacion;

import com.example.facturacion.dto.FacturaReporteDTO;
import com.example.facturacion.dto.ClienteRequestDTO;
import com.example.facturacion.dto.FacturaDetalleRequestDTO;
import com.example.facturacion.dto.FacturaRequestDTO;
import com.example.facturacion.entities.FacturaVenta;
import com.example.facturacion.entities.Cliente;
import com.example.facturacion.repositories.FacturaVentaRepository;
import com.example.facturacion.repositories.ClienteRepository;
import com.example.facturacion.services.FacturaService;
import com.example.facturacion.services.ClienteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties = {
        "facturacion.arca-test.enabled=false", "debug=false",
        "logging.level.root=WARN", "logging.level.org.springframework=WARN", "logging.level.org.hibernate=WARN",
        "facturacion.comprobante.emisor.denominacion=Emisor de pruebas",
        "facturacion.comprobante.emisor.condicion-iva=Responsable Inscripto",
        "facturacion.comprobante.emisor.domicilio=Calle de pruebas 123"
})
@AutoConfigureMockMvc
class FacturacionApplicationTests {
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.facturacion.services.AfipWsaaService wsaa;
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.facturacion.services.AfipWsfeService wsfe;

    @org.junit.jupiter.api.BeforeEach
    void simulateArcaWithoutIssuingReceipts() throws Exception {
        java.util.concurrent.atomic.AtomicInteger sequence = new java.util.concurrent.atomic.AtomicInteger(9000);
        org.mockito.Mockito.when(wsaa.obtenerTokenYSign(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new String[]{"test-token", "test-sign"});
        org.mockito.Mockito.when(wsfe.obtenerUltimoComprobante(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> sequence.get());
        org.mockito.Mockito.when(wsfe.obtenerFechaComprobanteAutorizado(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(java.time.LocalDate.now().minusDays(5));
        org.mockito.Mockito.when(wsfe.solicitarCaeFactura(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any(java.time.LocalDate.class)))
                .thenAnswer(invocation -> {
                    int number = invocation.getArgument(4);
                    sequence.set(number);
                    return new String[]{"70000000001234", "20300101", String.valueOf(number)};
                });
    }


	@Autowired
	private FacturaService facturaService;

    @Autowired
    private com.example.facturacion.repositories.CondicionIvaRepository condicionIvaRepository;

	@Autowired
	private FacturaVentaRepository facturaVentaRepository;

	@Autowired
	private ClienteService clienteService;

	@Autowired
	private ClienteRepository clienteRepository;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

    @Test
    @Transactional
    void arcaFailureDoesNotPersistAnAttemptedReceiptNumber() throws Exception {
        var existing = facturaVentaRepository.findAll().stream().filter(f -> !f.getDetalles().isEmpty()).findFirst().orElseThrow();
        var detail = existing.getDetalles().get(0);
        var request = new FacturaRequestDTO(null, null, null, condicionIvaRepository.findAll().stream()
                .filter(v -> v.getCodigoAfip() == 5).findFirst().orElseThrow().getId(),
                existing.getTipoMoneda().getId(), existing.getPuntoVenta().getId(), "EMITIDA", "Error simulado",
                List.of(new FacturaDetalleRequestDTO(detail.getListaPrecioArticulo().getArticulo().getId(),
                        detail.getListaPrecioArticulo().getId(), "Producto", 1, 0)));
        org.mockito.Mockito.when(wsfe.solicitarCaeFactura(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any(java.time.LocalDate.class)))
                .thenThrow(new java.io.IOException("Fallo simulado"));
        var saved = facturaService.crearFactura(request);
        Assertions.assertEquals("ERROR_AFIP", saved.getEstado());
        Assertions.assertNull(saved.getNumero());
        Assertions.assertNull(saved.getCae());
        Assertions.assertEquals(saved.getImporteTotal(), saved.getImporteCobrado());
        Assertions.assertEquals(0, saved.getImporteSaldo());
        mockMvc.perform(get("/api/facturas/{id}/comprobante.pdf", saved.getId())).andExpect(status().isOk());
        var originalNumber = saved.getNumero();
        var originalDate = saved.getFechaEmision();
        request.setNumero(99999999L);
        request.setFechaEmision(java.time.LocalDate.of(2000, 1, 1));
        request.setEstado("EMITIDA");
        var editError = Assertions.assertThrows(ResponseStatusException.class,
                () -> facturaService.actualizarFactura(saved.getId(), request));
        Assertions.assertEquals(HttpStatus.CONFLICT, editError.getStatusCode());
        Assertions.assertEquals(originalNumber, saved.getNumero());
        Assertions.assertEquals(originalDate, saved.getFechaEmision());
        Assertions.assertEquals("ERROR_AFIP", saved.getEstado());
        facturaService.anularFactura(saved.getId());
        facturaService.reactivarFactura(saved.getId());
        Assertions.assertEquals("ERROR_AFIP", saved.getEstado());
        Assertions.assertFalse(saved.isEditable());
    }

    @Test
    void invalidFiltersAreRejectedByListAndBothExports() throws Exception {
        for (String path : List.of("/api/facturas", "/api/facturas/pdf", "/api/facturas/excel")) {
            mockMvc.perform(get(path).param("fechaDesde", "2026-11-12").param("fechaHasta", "2026-01-01"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get(path).param("numeroDesde", "20").param("numeroHasta", "10"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get(path).param("numero", "5").param("numeroDesde", "10"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get(path).param("montoMinimo", "NaN")).andExpect(status().isBadRequest());
            mockMvc.perform(get(path).param("clienteId", "1").param("sinCliente", "true"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void invalidPayloadsFailBeforeDatabaseWritesOrArcaCalls() throws Exception {
        String valid = "{\"puntoVentaId\":1,\"condicionIvaId\":1,\"tipoMonedaId\":1,"
                + "\"detalles\":[{\"articuloId\":1,\"listaPrecioArticuloId\":1,\"cantidad\":1}]}";
        for (String invalid : List.of(
                valid.replace("\"cantidad\":1", "\"cantidad\":0"),
                valid.replace("\"articuloId\":1", "\"articuloId\":1.5"),
                valid.replace("\"detalles\":[{\"articuloId\":1,\"listaPrecioArticuloId\":1,\"cantidad\":1}]", "\"detalles\":[null]"),
                valid.replace("\"cantidad\":1", "\"cantidad\":1,\"descripcion\":\"" + "x".repeat(256) + "\""),
                valid.replace("\"puntoVentaId\":1", "\"observaciones\":\"" + "x".repeat(501) + "\",\"puntoVentaId\":1"))) {
            mockMvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/clientes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cuitCuil\":\"20123456789\",\"denominacion\":\"Cliente\",\"condicionIvaId\":1,\"email\":\"invalido\"}"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(wsaa, wsfe);
    }

    @Test
    @Transactional
    void anyStoredCaePreventsEditingWithoutImplyingVerifiedReceipt() throws Exception {
        var invoice = facturaVentaRepository.findAll().stream().filter(f -> f.getNumero() != null).findFirst().orElseThrow();
        invoice.setCae("70000000001234");
        invoice.setResultadoAfip("SIMULADO");
        invoice.setComprobanteAfipDisponible(false);
        facturaVentaRepository.saveAndFlush(invoice);
        var result = mockMvc.perform(get("/api/facturas").param("numero", invoice.getNumero().toString()).param("size", "100"))
                .andExpect(status().isOk()).andReturn();
        var rows = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
        Assertions.assertTrue(rows.findValues("id").stream().anyMatch(value -> value.asLong() == invoice.getId()));
        for (var row : rows) if (row.get("id").asLong() == invoice.getId()) Assertions.assertFalse(row.get("editable").asBoolean());
        invoice.setResultadoAfip("A");
        facturaVentaRepository.saveAndFlush(invoice);
        result = mockMvc.perform(get("/api/facturas").param("numero", invoice.getNumero().toString()).param("size", "100"))
                .andExpect(status().isOk()).andReturn();
        rows = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
        Assertions.assertTrue(rows.findValues("id").stream().anyMatch(value -> value.asLong() == invoice.getId()));
        for (var row : rows) if (row.get("id").asLong() == invoice.getId()) Assertions.assertFalse(row.get("editable").asBoolean());
    }

    @Test
    void diagnosticEmissionIsDisabledByDefault() throws Exception {
        mockMvc.perform(post("/api/afip/emitir-factura-b").param("importeTotal", "121"))
                .andExpect(status().isNotFound());
    }

	@Test
	void contextLoads() {
	}

	@Test
    void catalogNeverReturnsCompleteClientOrArticleCollections() throws Exception {
        for (String flag : new String[] {"false", "true"}) {
            mockMvc.perform(get("/api/catalogos").param("incluirClientes", flag).param("incluirArticulos", flag))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.clientes").isEmpty())
                    .andExpect(jsonPath("$.articulos").isEmpty()).andExpect(jsonPath("$.listasPrecio").isArray());
        }
        mockMvc.perform(get("/api/catalogos")).andExpect(status().isOk())
                .andExpect(jsonPath("$.clientes").isEmpty()).andExpect(jsonPath("$.articulos").isEmpty());
    }

	@Test
	void invoiceSortingRejectsInvalidParameters() throws Exception {
		mockMvc.perform(get("/api/facturas")
				.param("sortBy", "cliente")
				.param("sortDir", "asc"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/facturas")
				.param("sortBy", "numero")
				.param("sortDir", "sideways"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void invoiceNumberSortingIsAppliedBeforePagination() {
		List<FacturaReporteDTO> ascending = facturaService.buscarFacturasFiltradas(
				null, null, null, null, null, false, null, null, null,
				0, 100, "numero", "asc");
		List<Long> numbers = ascending.stream()
				.map(FacturaReporteDTO::getNumeroFactura)
				.filter(java.util.Objects::nonNull)
				.toList();
		Assertions.assertEquals(numbers.stream().sorted().toList(), numbers);

		List<FacturaReporteDTO> descending = facturaService.buscarFacturasFiltradas(
				null, null, null, null, null, false, null, null, null,
				0, 100, "numero", "desc");
		List<Long> descendingNumbers = descending.stream()
				.map(FacturaReporteDTO::getNumeroFactura)
				.filter(java.util.Objects::nonNull)
				.toList();
		Assertions.assertEquals(descendingNumbers.stream().sorted(java.util.Comparator.reverseOrder()).toList(),
				descendingNumbers);
	}

	@Test
	void updatingMissingInvoiceReturnsNotFound() throws Exception {
		mockMvc.perform(put("/api/facturas/{id}", Long.MAX_VALUE)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void invalidInvoiceRequestReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/facturas")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@Transactional
	void invoiceCrudEndpointsReturnExpectedSuccessStatuses() throws Exception {
		FacturaVenta existing = facturaVentaRepository.findAll().stream()
				.filter(invoice -> !invoice.getDetalles().isEmpty())
				.findFirst()
				.orElseThrow();
		FacturaRequestDTO request = new FacturaRequestDTO(
				System.currentTimeMillis(),
				existing.getFechaEmision(),
				existing.getCliente() == null ? null : existing.getCliente().getId(),
				existing.getCondicionIva().getId(),
				existing.getTipoMoneda().getId(),
				existing.getPuntoVenta().getId(),
				"EMITIDA",
				"Prueba de contrato REST",
				List.of(new FacturaDetalleRequestDTO(
						existing.getDetalles().get(0).getListaPrecioArticulo().getArticulo().getId(),
						existing.getDetalles().get(0).getListaPrecioArticulo().getId(),
						"Detalle de prueba",
						1,
						0)));

		MvcResult createdResult = mockMvc.perform(post("/api/facturas")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.numeroFactura").isNumber())
				.andReturn();

		FacturaReporteDTO created = objectMapper.readValue(
				createdResult.getResponse().getContentAsString(),
				FacturaReporteDTO.class);
		String requestJson = objectMapper.writeValueAsString(request);

		mockMvc.perform(put("/api/facturas/{id}", existing.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(existing.getId()));

        mockMvc.perform(put("/api/facturas/{id}", created.getId())
                .contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isConflict());

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.delete("/api/facturas/{id}", created.getId()))
				.andExpect(status().isNoContent());
	}

	@Test
	void paginationCoversAllMatchesWithoutLimitingReports() {
		List<FacturaReporteDTO> all = facturaService.buscarFacturasFiltradas(
				null, null, null, null, null, false, null, null, null);
		long total = facturaService.contarFacturasFiltradas(
				null, null, null, null, null, false, null, null, null);

		Assertions.assertEquals(total, all.size());

		int pageSize = 10;
		List<Long> allIds = all.stream().map(FacturaReporteDTO::getId).toList();
		int totalPages = (int) Math.ceil((double) all.size() / pageSize);
		for (int page = 0; page < totalPages; page++) {
			int start = page * pageSize;
			int end = Math.min(start + pageSize, allIds.size());
			List<Long> pageIds = facturaService.buscarFacturasFiltradas(
					null, null, null, null, null, false, null, null, null, page, pageSize)
					.stream()
					.map(FacturaReporteDTO::getId)
					.toList();

			Assertions.assertEquals(allIds.subList(start, end), pageIds);
		}
	}

	@Test
	void clientAndReceiptFiltersMatchTheSameRecordsInListAndCount() {
		List<FacturaReporteDTO> all = facturaService.buscarFacturasFiltradas(
				null, null, null, null, null, false, null, null, null);
		List<FacturaReporteDTO> unassigned = facturaService.buscarFacturasFiltradas(
				null, null, null, null, null, true, null, null, null);
		Set<Long> expectedUnassignedIds = facturaVentaRepository.findAll().stream()
				.filter(factura -> (factura.getComprobanteFiscalDerivado() == null || (factura.isComprobanteInterno() && !factura.isReemplazadoPorFiscal())) && factura.getCliente() == null)
				.map(FacturaVenta::getId)
				.collect(Collectors.toSet());

		Assertions.assertEquals(expectedUnassignedIds,
				unassigned.stream().map(FacturaReporteDTO::getId).collect(Collectors.toSet()));
		Assertions.assertEquals(expectedUnassignedIds.size(),
				facturaService.contarFacturasFiltradas(
						null, null, null, null, null, true, null, null, null));

		facturaVentaRepository.findAll().stream()
				.filter(factura -> factura.getCliente() != null)
				.findFirst()
				.ifPresent(factura -> {
					Long clienteId = factura.getCliente().getId();
					List<FacturaReporteDTO> byClient = facturaService.buscarFacturasFiltradas(
							null, null, null, null, clienteId, false, null, null, null);
					Set<Long> expectedClientIds = facturaVentaRepository.findAll().stream()
							.filter(found -> (found.getComprobanteFiscalDerivado() == null || (found.isComprobanteInterno() && !found.isReemplazadoPorFiscal())) && found.getCliente() != null
									&& clienteId.equals(found.getCliente().getId()))
							.map(FacturaVenta::getId)
							.collect(Collectors.toSet());
					Assertions.assertEquals(expectedClientIds,
							byClient.stream().map(FacturaReporteDTO::getId).collect(Collectors.toSet()));
					Assertions.assertEquals(byClient.size(), facturaService.contarFacturasFiltradas(
							null, null, null, null, clienteId, false, null, null, null));
				});

		List<Long> invoiceNumbers = all.stream().map(FacturaReporteDTO::getNumeroFactura)
				.filter(java.util.Objects::nonNull).toList();
		if (!invoiceNumbers.isEmpty()) {
			Long numero = invoiceNumbers.get(0);
			List<FacturaReporteDTO> exactNumber = facturaService.buscarFacturasFiltradas(
					null, null, null, null, null, false, numero, null, null);
			Assertions.assertFalse(exactNumber.isEmpty());
			Assertions.assertTrue(exactNumber.stream()
					.allMatch(factura -> numero.equals(factura.getNumeroFactura())));
			Assertions.assertEquals(exactNumber.size(), facturaService.contarFacturasFiltradas(
					null, null, null, null, null, false, numero, null, null));

			Long min = invoiceNumbers.stream().min(Long::compareTo).orElseThrow();
			Long max = invoiceNumbers.stream().max(Long::compareTo).orElseThrow();
			List<FacturaReporteDTO> range = facturaService.buscarFacturasFiltradas(
					null, null, null, null, null, false, null, min, max);
			Assertions.assertEquals(invoiceNumbers.size(), range.size());
		}
	}

	@Test
	@Transactional
	void invoiceNumbersFollowArcaAfterCancellation() {
		FacturaVenta existing = facturaVentaRepository.findAll().stream()
				.filter(invoice -> !invoice.getDetalles().isEmpty())
				.findFirst()
				.orElseThrow();
		FacturaRequestDTO request = new FacturaRequestDTO(
				existing.getNumero(),
				existing.getFechaEmision(),
				existing.getCliente() == null ? null : existing.getCliente().getId(),
				existing.getCondicionIva().getId(),
				existing.getTipoMoneda().getId(),
				existing.getPuntoVenta().getId(),
				"EMITIDA",
				"Prueba de numeración",
				List.of(new FacturaDetalleRequestDTO(
					existing.getDetalles().get(0).getListaPrecioArticulo().getArticulo().getId(),
					existing.getDetalles().get(0).getListaPrecioArticulo().getId(),
					"Detalle de numeración",
					1,
					0)));

		long before = facturaService.consultarSiguienteNumeroArca(existing.getPuntoVenta().getId());
		FacturaVenta first = facturaService.crearFactura(request);
		Assertions.assertEquals(before, first.getNumero());

		facturaService.anularFactura(first.getId());
		FacturaVenta second = facturaService.crearFactura(request);
		Assertions.assertEquals(before + 1, second.getNumero());
		Assertions.assertNotEquals(first.getNumero(), second.getNumero());
	}

	@Test
	@Transactional
	void reactivatingInvoiceRestoresItsActiveStateAndClearsCancellationAudit() {
		FacturaVenta invoice = facturaVentaRepository.findAll().stream()
				.findFirst()
				.orElseThrow();
		invoice.setEstado("ANULADA");
		invoice.setFechaAnulacion(java.time.LocalDate.now());
		invoice.setFechaBaja(java.time.LocalDate.now());
		facturaVentaRepository.saveAndFlush(invoice);

		facturaService.reactivarFactura(invoice.getId());

		Assertions.assertEquals("EMITIDA", invoice.getEstado());
		Assertions.assertNull(invoice.getFechaAnulacion());
		Assertions.assertNull(invoice.getFechaBaja());
		Assertions.assertNull(invoice.getUsuarioBaja());
	}

	@Test
	@Transactional
	void creatingClientPersistsRelatedDataAndRejectsDuplicateCuit() {
		String cuitCuil = String.format("%011d", Math.floorMod(System.nanoTime(), 100_000_000_000L));
		ClienteRequestDTO request = new ClienteRequestDTO();
		request.setCuitCuil(cuitCuil);
		request.setDenominacion("Cliente de prueba");
        request.setCondicionIvaId(facturaVentaRepository.findAll().get(0).getCondicionIva().getId());
		request.setEmail("cliente@example.com");
		request.setTelefono("123456");
		request.setCelular("654321");
		request.setNombreCalle("Calle de prueba");
		request.setNumeroCalle("42");

		var response = clienteService.crearCliente(request);
		Cliente cliente = clienteRepository.findById(response.id()).orElseThrow();

		Assertions.assertEquals("Cliente de prueba", response.denominacion());
		Assertions.assertEquals(cuitCuil, cliente.getCuitCuil());
		Assertions.assertEquals("cliente@example.com", cliente.getContacto().getEmail());
		Assertions.assertEquals("Calle de prueba", cliente.getDomicilio().getNombreCalle());
		Assertions.assertNotNull(cliente.getUsuarioCarga());
		Assertions.assertNull(cliente.getFechaBaja());

		ResponseStatusException duplicate = Assertions.assertThrows(
				ResponseStatusException.class,
				() -> clienteService.crearCliente(request));
		Assertions.assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
        request.setCuitCuil(cuitCuil.substring(0, 2) + "-" + cuitCuil.substring(2, 10) + "-" + cuitCuil.substring(10));
        ResponseStatusException formattedDuplicate = Assertions.assertThrows(ResponseStatusException.class,
                () -> clienteService.crearCliente(request));
        Assertions.assertEquals(HttpStatus.CONFLICT, formattedDuplicate.getStatusCode());
	}

	@Test
	@Transactional
	void invoiceCreationRejectsFractionalItemQuantities() {
		FacturaVenta existing = facturaVentaRepository.findAll().stream()
				.filter(invoice -> !invoice.getDetalles().isEmpty())
				.findFirst()
				.orElseThrow();
		FacturaDetalleRequestDTO detail = new FacturaDetalleRequestDTO(
				existing.getDetalles().get(0).getListaPrecioArticulo().getArticulo().getId(),
				existing.getDetalles().get(0).getListaPrecioArticulo().getId(),
				"Cantidad fraccionaria",
				1.5,
				0);
		FacturaRequestDTO request = new FacturaRequestDTO(
				existing.getNumero(),
				existing.getFechaEmision(),
				existing.getCliente() == null ? null : existing.getCliente().getId(),
				existing.getCondicionIva().getId(),
				existing.getTipoMoneda().getId(),
				existing.getPuntoVenta().getId(),
				"EMITIDA",
				"Test cantidad fraccionaria",
				List.of(detail));

		IllegalArgumentException error = Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> facturaService.crearFactura(request));
		Assertions.assertEquals("La cantidad debe ser un número entero mayor que cero", error.getMessage());
	}

    @Test
    @Transactional
    void clientExplorerSearchesExactPartialAndLiteralWildcards() throws Exception {
        var client = clienteRepository.findAll().get(0);
        client.setDenominacion("Explorer 50%_Test");
        client.setCuitCuil("20-12345678-9");
        var iva = condicionIvaRepository.findAll().get(0);
        client.setCondicionIva(iva);
        clienteRepository.saveAndFlush(client);
        mockMvc.perform(get("/api/clientes").param("denominacion", "explorer 50%_test").param("coincidencia", "exacta")
                .param("cuitCuil", "20123456789").param("condicionIvaId", iva.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(client.getId()))
                .andExpect(jsonPath("$.content[0].condicionIvaDenominacion").value(iva.getDenominacion()));
        mockMvc.perform(get("/api/clientes").param("denominacion", "50%_").param("cuitCuil", "12345678"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/clientes").param("denominacion", "explorer").param("coincidencia", "exacta"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/clientes/" + client.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.cuitCuil").value("20-12345678-9"));
    }

    @Test
    @Transactional
    void clientExplorerSortsBeforePagingAndUsesStableIdOrder() throws Exception {
        var clients = clienteRepository.findAll().subList(0, 3);
        for (int i = 0; i < clients.size(); i++) {
            clients.get(i).setDenominacion("ExplorerPage " + (i == 0 ? "A" : "Z"));
        }
        clienteRepository.saveAllAndFlush(clients);
        mockMvc.perform(get("/api/clientes").param("denominacion", "ExplorerPage").param("sortDir", "desc")
                .param("size", "1").param("page", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.content[0].id").value(clients.get(1).getId()));
        mockMvc.perform(get("/api/clientes").param("denominacion", "ExplorerPage").param("sortDir", "desc")
                .param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(clients.get(2).getId()));
        mockMvc.perform(get("/api/catalogos").param("incluirClientes", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clientes").isEmpty());
    }

    @Test
    void clientExplorerRejectsInvalidQueryOptions() throws Exception {
        for (var parameter : new String[][] { {"page", "-1"}, {"size", "101"}, {"sortBy", "contacto"},
                {"sortDir", "invalid"}, {"coincidencia", "invalid"}, {"condicionIvaId", "0"}, {"cuitCuil", "abc"} }) {
            mockMvc.perform(get("/api/clientes").param(parameter[0], parameter[1])).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/clientes/9223372036854775807")).andExpect(status().isNotFound());
    }

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager articleEm;
    @Autowired
    private com.example.facturacion.DataInitializer initializer;

    @Test @Transactional
    void articleExplorerFiltersByPriceListWithExactPartialAndLiteralMatching() throws Exception {
        var price = articleEm.createQuery("SELECT p FROM ListaPrecioArticulo p WHERE p.articulo.codigo = 'BEB-GASEOSA-COCA-COLA-ORIGINAL-330-ML-LATA' AND p.listaPrecio.codigo = 'LP02'", com.example.facturacion.entities.ListaPrecioArticulo.class).getSingleResult();
        price.getArticulo().setDenominacion("ExplorerArticle 50%_Test"); articleEm.flush();
        mockMvc.perform(get("/api/articulos").param("listaPrecioId", price.getListaPrecio().getId().toString())
                .param("codigo", "beb-gaseosa-coca-cola-original-330-ml-lata").param("denominacion", "explorerarticle 50%_test").param("coincidencia", "exacta"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].listaPrecioArticuloId").value(price.getId()))
                .andExpect(jsonPath("$.content[0].precioVenta").value(price.getPrecioVenta()));
        mockMvc.perform(get("/api/articulos").param("listaPrecioId", price.getListaPrecio().getId().toString())
                .param("denominacion", "50%_"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/articulos").param("listaPrecioId", price.getListaPrecio().getId().toString())
                .param("denominacion", "ExplorerArticle").param("coincidencia", "exacta"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/articulos/precios/" + price.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.articuloId").value(price.getArticulo().getId()));
        mockMvc.perform(get("/api/catalogos").param("incluirClientes", "false").param("incluirArticulos", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.articulos").isEmpty())
                .andExpect(jsonPath("$.listasPrecio").isArray());
    }

    @Test @Transactional
    void articleExplorerSortsAndPaginatesAThousandProductsBeforeReturningResults() throws Exception {
        var list = articleEm.createQuery("SELECT l FROM ListaPrecio l WHERE l.codigo = 'LP02'", com.example.facturacion.entities.ListaPrecio.class).getResultList().get(0);
        var expected = articleEm.createQuery("SELECT p FROM ListaPrecioArticulo p WHERE p.listaPrecio = :lista AND p.articulo.codigo LIKE 'BEB-%' ORDER BY p.precioVenta DESC, p.id", com.example.facturacion.entities.ListaPrecioArticulo.class)
                .setParameter("lista", list).setFirstResult(10).setMaxResults(1).getSingleResult();
        mockMvc.perform(get("/api/articulos").param("listaPrecioId", list.getId().toString()).param("codigo", "BEB-")
                .param("sortBy", "precioVenta").param("sortDir", "desc").param("page", "1").param("size", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1000))
                .andExpect(jsonPath("$.totalPages").value(100)).andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.content[0].codigo").value(String.valueOf(expected.getArticulo().getNumeroCatalogo())));
        var prices = articleEm.createQuery("SELECT p FROM ListaPrecioArticulo p WHERE p.listaPrecio = :lista AND p.articulo.codigo IN ('BEB-GASEOSA-COCA-COLA-ORIGINAL-330-ML-LATA','BEB-GASEOSA-COCA-COLA-ORIGINAL-1-25-L-BOTELLA-PET') ORDER BY p.id", com.example.facturacion.entities.ListaPrecioArticulo.class).setParameter("lista", list).getResultList();
        prices.forEach(p -> p.getArticulo().setDenominacion("ExplorerTie"));
        articleEm.flush();
        mockMvc.perform(get("/api/articulos").param("listaPrecioId", list.getId().toString()).param("denominacion", "ExplorerTie")
                .param("sortDir", "desc").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].listaPrecioArticuloId").value(prices.get(1).getId()));
    }

    @Test @Transactional
    void extraProductSeedIsIdempotentAndPreservesExistingPrices() {
        var price = articleEm.createQuery("SELECT p FROM ListaPrecioArticulo p WHERE p.articulo.codigo = 'BEB-GASEOSA-COCA-COLA-ORIGINAL-330-ML-LATA' AND p.listaPrecio.codigo = 'LP02'", com.example.facturacion.entities.ListaPrecioArticulo.class).getSingleResult();
        price.setPrecioVenta(123.45); articleEm.flush();
        long before = articleEm.createQuery("SELECT count(a) FROM Articulo a", Long.class).getSingleResult();
        initializer.run(); initializer.run();
        Assertions.assertEquals(before, articleEm.createQuery("SELECT count(a) FROM Articulo a", Long.class).getSingleResult());
        Assertions.assertEquals(1000L, articleEm.createQuery("SELECT count(a) FROM Articulo a WHERE a.codigo LIKE 'BEB-%'", Long.class).getSingleResult());
        Assertions.assertEquals(2000L, articleEm.createQuery("SELECT count(p) FROM ListaPrecioArticulo p WHERE p.articulo.codigo LIKE 'BEB-%' AND p.listaPrecio.codigo IN ('LP01','LP02')", Long.class).getSingleResult());
        Assertions.assertEquals(123.45, price.getPrecioVenta());
        var names = articleEm.createQuery("SELECT a.denominacion FROM Articulo a WHERE a.codigo LIKE 'BEB-%'", String.class).getResultList();
        Assertions.assertEquals(1000, new java.util.HashSet<>(names).size());
        Assertions.assertTrue(names.stream().noneMatch(n -> n.matches("(?i).*(demo|variedad) ?[0-9]+.*")));
        Assertions.assertEquals(0L, articleEm.createQuery("SELECT count(a) FROM Articulo a WHERE a.codigo LIKE 'DEMO-%'", Long.class).getSingleResult());

    }

    @Test @Transactional
    void legacyNumberedProductIsRenamedInPlaceWithoutChangingPricesOrInvoiceHistory() {
        var price = articleEm.createQuery("SELECT p FROM ListaPrecioArticulo p WHERE p.articulo.codigo = :codigo AND p.listaPrecio.codigo = 'LP02'", com.example.facturacion.entities.ListaPrecioArticulo.class)
                .setParameter("codigo", com.example.facturacion.seed.CatalogoBebidas.productos().get(0).codigo()).getSingleResult();
        var article = price.getArticulo(); long id = article.getId(), priceId = price.getId();
        double originalPrice = price.getPrecioVenta();
        article.setCodigo("DEMO-0001"); article.setDenominacion("Producto variedad 0001"); articleEm.flush();
        initializer.run(); initializer.run();
        Assertions.assertEquals(id, article.getId()); Assertions.assertEquals(priceId, price.getId());
        Assertions.assertEquals(originalPrice, price.getPrecioVenta());
        Assertions.assertEquals(com.example.facturacion.seed.CatalogoBebidas.productos().get(0).denominacion(), article.getDenominacion());
        Assertions.assertEquals("Coca-Cola", article.getMarca().getDenominacion());
        Assertions.assertEquals("Gaseosas", article.getRubro().getDenominacion());
    }

    @Test @Transactional
    void compactProductCodesAreSearchableAndSortedNumericallyInTheDatabase() throws Exception {
        var list = articleEm.createQuery("SELECT l FROM ListaPrecio l WHERE l.codigo = 'LP02'", com.example.facturacion.entities.ListaPrecio.class).getSingleResult();
        var price = articleEm.createQuery("SELECT p FROM ListaPrecioArticulo p WHERE p.listaPrecio = :lista ORDER BY p.articulo.numeroCatalogo, p.id", com.example.facturacion.entities.ListaPrecioArticulo.class)
                .setParameter("lista", list).setMaxResults(1).getSingleResult();
        String code = String.valueOf(price.getArticulo().getNumeroCatalogo());
        mockMvc.perform(get("/api/articulos").param("listaPrecioId", list.getId().toString()).param("codigo", code).param("coincidencia", "exacta"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].codigo").value(code));
        mockMvc.perform(get("/api/articulos").param("listaPrecioId", list.getId().toString()).param("sortBy", "codigo").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].codigo").value(code));
        var product = com.example.facturacion.seed.CatalogoBebidas.productos().get(0);
        var article = articleEm.createQuery("SELECT a FROM Articulo a WHERE a.codigo = :codigo", com.example.facturacion.entities.Articulo.class).setParameter("codigo", product.codigo()).getSingleResult();
        article.setDenominacion(product.denominacionAnterior()); articleEm.flush(); initializer.run();
        Assertions.assertEquals(product.denominacion(), article.getDenominacion());
        Assertions.assertFalse(article.getDenominacion().contains("Botella"));
    }

    @Test @Transactional
    void productNumbersStartAtOneAreConsecutiveAndPersistAcrossInitialization() throws Exception {
        var before = articleEm.createQuery("SELECT a.numeroCatalogo FROM Articulo a ORDER BY a.numeroCatalogo", Long.class).getResultList();
        Assertions.assertTrue(before.size() >= 1000);
        for (int i = 0; i < before.size(); i++) Assertions.assertEquals((long) i + 1, before.get(i));
        initializer.run(); initializer.run();
        Assertions.assertEquals(before, articleEm.createQuery("SELECT a.numeroCatalogo FROM Articulo a ORDER BY a.numeroCatalogo", Long.class).getResultList());
        var list = articleEm.createQuery("SELECT l FROM ListaPrecio l WHERE l.codigo = 'LP02'", com.example.facturacion.entities.ListaPrecio.class).getSingleResult();
        for (int page = 0; page < 2; page++) {
            var result = mockMvc.perform(get("/api/articulos").param("listaPrecioId", list.getId().toString())
                    .param("sortBy", "codigo").param("sortDir", "asc").param("page", String.valueOf(page)).param("size", "10"))
                    .andExpect(status().isOk()).andReturn();
            var content = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("content");
            for (int i = 0; i < 10; i++) Assertions.assertEquals(String.valueOf(page * 10 + i + 1), content.get(i).get("codigo").asText());
        }
    }

    @Test
    void articleExplorerRejectsInvalidOptions() throws Exception {
        mockMvc.perform(get("/api/articulos")).andExpect(status().isBadRequest());
        for (var parameter : new String[][] {{"listaPrecioId", "0"}, {"page", "-1"}, {"size", "101"},
                {"sortBy", "articulo.id"}, {"sortDir", "invalid"}, {"coincidencia", "invalid"}}) {
            var request = get("/api/articulos");
            if (!parameter[0].equals("listaPrecioId")) request.param("listaPrecioId", "1");
            mockMvc.perform(request.param(parameter[0], parameter[1])).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/articulos/precios/9223372036854775807")).andExpect(status().isNotFound());
    }

    @Test
    void listEndpointsRejectOversizedPagesAndOverflowingOffsets() throws Exception {
        for (String path : new String[] {"/api/facturas", "/api/clientes", "/api/articulos"}) {
            mockMvc.perform(get(path).param("listaPrecioId", "1").param("size", "101")).andExpect(status().isBadRequest());
            mockMvc.perform(get(path).param("listaPrecioId", "1").param("size", "100").param("page", "2147483647"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Autowired
    private com.example.facturacion.repositories.ConsultasRepository consultasRepository;

    @Test @Transactional(readOnly = true)
    void academicQueriesExecuteThroughTheRepository() {
        Assertions.assertEquals(facturaVentaRepository.count(), consultasRepository.obtenerTodasLasFacturas().size());
        for (var result : List.of(
                consultasRepository.obtenerResumenFacturas(),
                consultasRepository.obtenerArticulosPorRubro("Bebidas"),
                consultasRepository.obtenerFacturasPorRangoFechas(java.time.LocalDate.now().minusYears(2), java.time.LocalDate.now()),
                consultasRepository.obtenerFacturasValidasAltas(0),
                consultasRepository.buscarClientesPorNombreOCuit("Perez", "20-"),
                consultasRepository.obtenerEstadosFacturasUnicos(),
                consultasRepository.obtenerPuntosVentaPorNumeros(List.of(1, 2, 3)),
                consultasRepository.obtenerFacturasPorUsuarioCarga("admin"),
                consultasRepository.obtenerDetallesPorNumeroPuntoVenta(1),
                consultasRepository.obtenerArticulosConMarca(),
                consultasRepository.obtenerFacturasPorMarcaArticulo("Coca Cola"),
                consultasRepository.obtenerFacturasSuperioresAlPromedio(),
                consultasRepository.obtenerFacturasPorCuitCliente("20-12345678-9"),
                consultasRepository.obtenerResumenFacturacionPorPuntoVenta(),
                consultasRepository.obtenerUsuariosConMasDeNFacturas(0),
                consultasRepository.obtenerVentasTotalesPorMarca(),
                consultasRepository.obtenerTotalFacturadoPorCondicionIva(),
                consultasRepository.obtenerMarcasConArticulosFacturados(),
                consultasRepository.obtenerArticulosNuncaFacturados(),
                consultasRepository.obtenerFacturasCategorizadas())) {
            Assertions.assertNotNull(result);
        }
        Assertions.assertEquals(3, consultasRepository.obtenerEstadisticasFacturasEmitidas().length);
    }
}
