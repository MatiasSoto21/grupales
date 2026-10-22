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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@SpringBootTest
class FacturacionApplicationTests {

	@Autowired
	private FacturaService facturaService;

	@Autowired
	private FacturaVentaRepository facturaVentaRepository;

	@Autowired
	private ClienteService clienteService;

	@Autowired
	private ClienteRepository clienteRepository;

	@Test
	void contextLoads() {
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
				.filter(factura -> factura.getCliente() == null)
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
							.filter(found -> found.getCliente() != null
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
				0,
				0,
				"EMITIDA",
				"Test cantidad fraccionaria",
				List.of(detail));

		IllegalArgumentException error = Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> facturaService.crearFactura(request));
		Assertions.assertEquals("La cantidad debe ser un número entero mayor que cero", error.getMessage());
	}
}
