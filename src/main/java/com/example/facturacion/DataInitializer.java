package com.example.facturacion;

import com.example.facturacion.entities.*;
import com.example.facturacion.services.ConsultasService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    @PersistenceContext
    private EntityManager em;

    private FacturaVenta crearFactura(Long numero, LocalDate fecha, String estado, Cliente cliente,
                                      PuntoVenta puntoVenta, CondicionIva condicionIva, TipoMoneda tipoMoneda,
                                      ListaPrecioArticulo listaPrecioArticulo, Usuario admin,
                                      double... cantidades) {
        FacturaVenta f = new FacturaVenta();
        f.setNumero(numero);
        f.setFechaEmision(fecha);
        f.setPuntoVenta(puntoVenta);
        f.setCliente(cliente);
        f.setCondicionIva(condicionIva);
        f.setTipoMoneda(tipoMoneda);
        f.setEstado(estado);

        double total = 0;
        for (double cantidad : cantidades) {
            double subtotal = cantidad * listaPrecioArticulo.getPrecioVenta();
            FacturaVentaDetalle d = new FacturaVentaDetalle();
            d.setListaPrecioArticulo(listaPrecioArticulo);
            d.setDescripcion(listaPrecioArticulo.getArticulo().getDenominacion());
            d.setCantidad(cantidad);
            d.setPrecioUnitario(listaPrecioArticulo.getPrecioVenta());
            d.setPorcentajeBonificacion(0);
            d.setImporteNeto(subtotal);
            d.setImporteIva(0);
            d.setImporteSubtotal(subtotal);
            f.addDetalle(d);
            total += subtotal;
        }
        f.setImporteTotal(total);

        if ("ANULADA".equals(estado)) {
            f.setImporteCobrado(0);
            f.setImporteSaldo(0);
            f.setFechaAnulacion(fecha.plusDays(1));
            f.setObservaciones("Factura anulada de prueba");
        } else {
            f.setImporteCobrado(total);
            f.setImporteSaldo(0);
            f.setCae("7000000000" + numero);
            f.setCaeFechaVencimiento(fecha.plusDays(10));
            f.setResultadoAfip("A");
            f.setObservaciones("Factura de prueba");
        }

        f.setFechaAlta(LocalDate.now());
        f.setFechaModificacion(LocalDate.now());
        f.setUsuarioCarga(admin);
        f.setUsuarioModificacion(admin);

        em.persist(f);
        return f;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {

        // Evita duplicar los datos de prueba en cada arranque
        Long facturasCargadas = em.createQuery("SELECT COUNT(f) FROM FacturaVenta f", Long.class).getSingleResult();
        if (facturasCargadas > 0) {
            System.out.println("Datos de prueba ya cargados: se omite la carga inicial.");
            return;
        }

        // ==========================
        // USUARIO (Auditoría)
        // ==========================
        Usuario admin = new Usuario();
        admin.setUsuario("admin");
        admin.setClave("1234");
        admin.setNombre("Matias");
        admin.setApellido("Soto");

        em.persist(admin);

        // ==========================
        // CONDICIÓN IVA
        // ==========================
        CondicionIva condicionIva = new CondicionIva();
        condicionIva.setCodigoAfip(1);
        condicionIva.setDenominacion("IVA Responsable Inscripto");
        condicionIva.setFechaAlta(LocalDate.now());
        condicionIva.setFechaModificacion(LocalDate.now());
        condicionIva.setUsuarioCarga(admin);
        condicionIva.setUsuarioModificacion(admin);

        em.persist(condicionIva);

        // ==========================
        // TIPO MONEDA
        // ==========================
        TipoMoneda tipoMoneda = new TipoMoneda();
        tipoMoneda.setCodigoAfip("PES");
        tipoMoneda.setDenominacion("Pesos Argentinos");
        tipoMoneda.setSimbolo("$");
        tipoMoneda.setFechaAlta(LocalDate.now());
        tipoMoneda.setFechaModificacion(LocalDate.now());
        tipoMoneda.setUsuarioCarga(admin);
        tipoMoneda.setUsuarioModificacion(admin);

        em.persist(tipoMoneda);

        // ==========================
        // PUNTO DE VENTA
        // ==========================
        PuntoVenta puntoVenta = new PuntoVenta();
        puntoVenta.setNumero(1);
        puntoVenta.setDescripcion("Casa Central");
        puntoVenta.setTipoEmision("Manual");
        puntoVenta.setDomicilioComercial("San Martín 123");
        puntoVenta.setFechaAlta(LocalDate.now());
        puntoVenta.setFechaModificacion(LocalDate.now());
        puntoVenta.setUsuarioCarga(admin);
        puntoVenta.setUsuarioModificacion(admin);

        em.persist(puntoVenta);

        // ==========================
        // MARCAS
        // ==========================
        Marca marca1 = new Marca();
        marca1.setCodigo(100);
        marca1.setDenominacion("Coca Cola");
        marca1.setFechaAlta(LocalDate.now());
        marca1.setFechaModificacion(LocalDate.now());
        marca1.setUsuarioCarga(admin);
        marca1.setUsuarioModificacion(admin);

        em.persist(marca1);

        // Marca no facturada para probar consulta 21
        Marca marca2 = new Marca();
        marca2.setCodigo(200);
        marca2.setDenominacion("Pepsi");
        marca2.setFechaAlta(LocalDate.now());
        marca2.setFechaModificacion(LocalDate.now());
        marca2.setUsuarioCarga(admin);
        marca2.setUsuarioModificacion(admin);

        em.persist(marca2);

        // ==========================
        // RUBRO
        // ==========================
        Rubro rubro = new Rubro();
        rubro.setCodigo(10);
        rubro.setDenominacion("Bebidas");
        rubro.setFechaAlta(LocalDate.now());
        rubro.setFechaModificacion(LocalDate.now());
        rubro.setUsuarioCarga(admin);
        rubro.setUsuarioModificacion(admin);

        em.persist(rubro);

        // ==========================
        // ARTÍCULOS
        // ==========================
        Articulo articulo1 = new Articulo();
        articulo1.setCodigo("COCA500");
        articulo1.setDenominacion("Coca Cola 500 ml");
        articulo1.setMarca(marca1);
        articulo1.setRubro(rubro);
        articulo1.setFechaAlta(LocalDate.now());
        articulo1.setFechaModificacion(LocalDate.now());
        articulo1.setUsuarioCarga(admin);
        articulo1.setUsuarioModificacion(admin);

        em.persist(articulo1);

        // Artículo no facturado para probar consulta 21
        Articulo articulo2 = new Articulo();
        articulo2.setCodigo("PEPSI500");
        articulo2.setDenominacion("Pepsi 500 ml");
        articulo2.setMarca(marca2);
        articulo2.setRubro(rubro);
        articulo2.setFechaAlta(LocalDate.now());
        articulo2.setFechaModificacion(LocalDate.now());
        articulo2.setUsuarioCarga(admin);
        articulo2.setUsuarioModificacion(admin);

        em.persist(articulo2);

        // ==========================
        // LISTA DE PRECIOS
        // ==========================
        ListaPrecio listaPrecio = new ListaPrecio();
        listaPrecio.setCodigo("LP01");
        listaPrecio.setDenominacion("Lista General");
        listaPrecio.setFechaAlta(LocalDate.now());
        listaPrecio.setFechaModificacion(LocalDate.now());
        listaPrecio.setUsuarioCarga(admin);
        listaPrecio.setUsuarioModificacion(admin);

        em.persist(listaPrecio);

        // ==========================
        // LISTA PRECIO ARTÍCULO
        // ==========================
        ListaPrecioArticulo listaPrecioArticulo = new ListaPrecioArticulo();
        listaPrecioArticulo.setListaPrecio(listaPrecio);
        listaPrecioArticulo.setArticulo(articulo1);
        listaPrecioArticulo.setPrecioVenta(2500);
        listaPrecioArticulo.setFechaAlta(LocalDate.now());
        listaPrecioArticulo.setFechaModificacion(LocalDate.now());
        listaPrecioArticulo.setUsuarioCarga(admin);
        listaPrecioArticulo.setUsuarioModificacion(admin);

        em.persist(listaPrecioArticulo);

        // ==========================
        // CONTACTO
        // ==========================
        Contacto contacto = new Contacto();
        contacto.setEmail("cliente@gmail.com");
        contacto.setTelefono("2614567890");
        contacto.setCelular("2615555555");

        em.persist(contacto);

        // ==========================
        // DOMICILIO
        // ==========================
        Domicilio domicilio = new Domicilio();
        domicilio.setNombreCalle("Belgrano");
        domicilio.setNumeroCalle("450");

        em.persist(domicilio);

        // ==========================
        // CLIENTE
        // ==========================
        Cliente cliente = new Cliente();
        cliente.setCuitCuil("20-12345678-9");
        cliente.setDenominacion("Juan Pérez");
        cliente.setContacto(contacto);
        cliente.setDomicilio(domicilio);
        cliente.setFechaAlta(LocalDate.now());
        cliente.setFechaModificacion(LocalDate.now());
        cliente.setUsuarioCarga(admin);
        cliente.setUsuarioModificacion(admin);

        em.persist(cliente);

        // ==========================
        // FACTURA CABECERA
        // ==========================
        FacturaVenta factura = new FacturaVenta();
        factura.setNumero(1001L);
        factura.setFechaEmision(LocalDate.now());
        factura.setPuntoVenta(puntoVenta);
        factura.setCliente(cliente);
        factura.setCondicionIva(condicionIva);
        factura.setTipoMoneda(tipoMoneda);

        factura.setImporteCobrado(5000);
        factura.setImporteSaldo(0);
        factura.setImporteTotal(7500);

        factura.setCae("12345678901234");
        factura.setCaeFechaVencimiento(LocalDate.now().plusDays(10));

        factura.setResultadoAfip("A");
        factura.setEstado("EMITIDA");
        factura.setObservaciones("Factura de prueba");

        factura.setFechaAlta(LocalDate.now());
        factura.setFechaModificacion(LocalDate.now());
        factura.setUsuarioCarga(admin);
        factura.setUsuarioModificacion(admin);

        // DETALLES
        FacturaVentaDetalle detalle1 = new FacturaVentaDetalle();
        detalle1.setFactura(factura);
        detalle1.setListaPrecioArticulo(listaPrecioArticulo);
        detalle1.setDescripcion("Coca Cola 500 ml");
        detalle1.setCantidad(2);
        detalle1.setPrecioUnitario(2500);
        detalle1.setPorcentajeBonificacion(0);
        detalle1.setImporteNeto(5000);
        detalle1.setImporteIva(0);
        detalle1.setImporteSubtotal(5000);

        FacturaVentaDetalle detalle2 = new FacturaVentaDetalle();
        detalle2.setFactura(factura);
        detalle2.setListaPrecioArticulo(listaPrecioArticulo);
        detalle2.setDescripcion("Coca Cola 500 ml");
        detalle2.setCantidad(1);
        detalle2.setPrecioUnitario(2500);
        detalle2.setPorcentajeBonificacion(0);
        detalle2.setImporteNeto(2500);
        detalle2.setImporteIva(0);
        detalle2.setImporteSubtotal(2500);

        List<FacturaVentaDetalle> detalles = new ArrayList<>();
        detalles.add(detalle1);
        detalles.add(detalle2);

        factura.setDetalles(detalles);

        em.persist(factura);

        // ==========================
        // FACTURAS ADICIONALES (para probar filtros y reportes)
        // ==========================
        Contacto contactoTech = new Contacto();
        contactoTech.setEmail("ventas@empresatech.com");
        contactoTech.setTelefono("2614000000");
        contactoTech.setCelular("2615000000");
        em.persist(contactoTech);

        Domicilio domicilioTech = new Domicilio();
        domicilioTech.setNombreCalle("Las Heras");
        domicilioTech.setNumeroCalle("1200");
        em.persist(domicilioTech);

        Cliente clienteTech = new Cliente();
        clienteTech.setCuitCuil("30-71234567-8");
        clienteTech.setDenominacion("Empresa Tech S.A.");
        clienteTech.setContacto(contactoTech);
        clienteTech.setDomicilio(domicilioTech);
        clienteTech.setFechaAlta(LocalDate.now());
        clienteTech.setFechaModificacion(LocalDate.now());
        clienteTech.setUsuarioCarga(admin);
        clienteTech.setUsuarioModificacion(admin);
        em.persist(clienteTech);

        // 5 ítems, cliente empresa, hace 30 días
        crearFactura(1002L, LocalDate.now().minusDays(30), "EMITIDA", clienteTech,
                puntoVenta, condicionIva, tipoMoneda, listaPrecioArticulo, admin, 1, 2, 1, 3, 2);

        // Sin cliente (Consumidor Final), anulada, hace 10 días
        crearFactura(1003L, LocalDate.now().minusDays(10), "ANULADA", null,
                puntoVenta, condicionIva, tipoMoneda, listaPrecioArticulo, admin, 1);

        // Sin cliente (Consumidor Final), emitida, hace 2 días
        crearFactura(1004L, LocalDate.now().minusDays(2), "EMITIDA", null,
                puntoVenta, condicionIva, tipoMoneda, listaPrecioArticulo, admin, 1, 1, 1);

        System.out.println("==================================");
        System.out.println("FACTURA GUARDADA CORRECTAMENTE");
        System.out.println("Número: " + factura.getNumero());
        System.out.println("Cliente: " + factura.getCliente().getDenominacion());
        System.out.println("Condición IVA: " + factura.getCondicionIva().getDenominacion());
        System.out.println("Moneda: " + factura.getTipoMoneda().getSimbolo());
        System.out.println("Detalles: " + factura.getDetalles().size());
        System.out.println("Importe Total: $" + factura.getImporteTotal());
        System.out.println("==================================\n");

        // =========================================================================
        // PRUEBAS DE LAS CONSULTAS JPQL (1 AL 22)
        // =========================================================================

        System.out.println("--- PRUEBAS NIVEL 1 ---");

        // 1.
        List<FacturaVenta> f1 = ConsultasService.obtenerTodasLasFacturas(em);
        System.out.println("1. Todas las facturas: " + f1.size());

        // IMPRIMIR DETALLE COMPLETO DE CADA FACTURA
        System.out.println("\n================ LISTADO DETALLADO ================");
        for (FacturaVenta f : f1) {
            System.out.println("Factura N°: " + f.getNumero() + " | Fecha: " + f.getFechaEmision() + " | Estado: " + f.getEstado());
            System.out.println("Cliente: " + (f.getCliente() != null
                    ? f.getCliente().getDenominacion() + " (CUIT: " + f.getCliente().getCuitCuil() + ")"
                    : "Consumidor Final"));
            System.out.println("Punto de Venta: " + f.getPuntoVenta().getDescripcion());
            System.out.println("Condición IVA: " + f.getCondicionIva().getDenominacion());

            System.out.println("  Detalles:");
            for (FacturaVentaDetalle d : f.getDetalles()) {
                System.out.println("   - Item: " + d.getDescripcion() +
                        " | Cant: " + d.getCantidad() +
                        " | P.Unit: $" + d.getPrecioUnitario() +
                        " | Subtotal: $" + d.getImporteSubtotal());
            }
            System.out.println("TOTAL FACTURA: $" + f.getImporteTotal());
            System.out.println("--------------------------------------------------");
        }
        System.out.println("==================================================\n");

        // 2.
        List<Object[]> f2 = ConsultasService.obtenerResumenFacturas(em);
        System.out.println("2. Resumen Factura N° " + f2.get(0)[0] + " | Fecha: " + f2.get(0)[1] + " | Total: $" + f2.get(0)[2]);

        // 3.
        List<Articulo> f3 = ConsultasService.obtenerArticulosPorRubro(em, "Bebidas");
        System.out.println("3. Artículos en 'Bebidas': " + f3.size());

        // 4.
        List<FacturaVenta> f4 = ConsultasService.obtenerFacturasPorRangoFechas(em, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        System.out.println("4. Facturas en rango de fechas: " + f4.size());

        System.out.println("\n--- PRUEBAS NIVEL 2 ---");

        // 5.
        List<FacturaVenta> f5 = ConsultasService.obtenerFacturasValidasAltas(em, 5000);
        System.out.println("5. Facturas válidas mayores a $5000: " + f5.size());

        // 6.
        List<Cliente> f6 = ConsultasService.buscarClientesPorNombreOCuit(em, "Pérez", "20-");
        System.out.println("6. Clientes coincidentes: " + f6.get(0).getDenominacion());

        // 7.
        List<String> f7 = ConsultasService.obtenerEstadosFacturasUnicos(em);
        System.out.println("7. Estados únicos: " + f7);

        // 8.
        Object[] f8 = ConsultasService.obtenerEstadisticasFacturasEmitidas(em);
        System.out.println("8. Cantidad: " + f8[0] + " | Suma: $" + f8[1] + " | Promedio: $" + f8[2]);

        // 9.
        List<PuntoVenta> f9 = ConsultasService.obtenerPuntosVentaPorNumeros(em, Arrays.asList(1, 2, 3));
        System.out.println("9. Puntos de venta encontrados: " + f9.size());

        System.out.println("\n--- PRUEBAS NIVEL 3 ---");

        // 10.
        List<FacturaVenta> f10 = ConsultasService.obtenerFacturasPorUsuarioCarga(em, "admin");
        System.out.println("10. Facturas del usuario admin: " + f10.size());

        // 11.
        List<FacturaVentaDetalle> f11 = ConsultasService.obtenerDetallesPorNumeroPuntoVenta(em, 1);
        System.out.println("11. Detalles del PV 1: " + f11.size());

        // 12.
        List<Object[]> f12 = ConsultasService.obtenerArticulosConMarca(em);
        System.out.println("12. Artículo: " + f12.get(0)[0] + " | Marca: " + f12.get(0)[1]);

        // 13.
        List<FacturaVenta> f13 = ConsultasService.obtenerFacturasPorMarcaArticulo(em, "Coca Cola");
        System.out.println("13. Facturas con marca Coca Cola: " + f13.size());

        // 14.
        List<FacturaVenta> f14 = ConsultasService.obtenerFacturasSuperioresAlPromedio(em);
        System.out.println("14. Facturas sobre el promedio: " + f14.size());

        // 15.
        List<FacturaVenta> f15 = ConsultasService.obtenerFacturasPorCuitCliente(em, "20-12345678-9");
        System.out.println("15. Facturas por CUIT cliente: " + f15.size());

        System.out.println("\n--- PRUEBAS NIVEL 4 ---");

        // 16.
        List<Object[]> f16 = ConsultasService.obtenerResumenFacturacionPorPuntoVenta(em);
        System.out.println("16. PV: " + f16.get(0)[0] + " | Cant: " + f16.get(0)[1] + " | Total: $" + f16.get(0)[2]);

        // 17.
        List<String> f17 = ConsultasService.obtenerUsuariosConMasDeNFacturas(em, 0);
        System.out.println("17. Usuarios con > 0 facturas: " + f17);

        // 18.
        List<Object[]> f18 = ConsultasService.obtenerVentasTotalesPorMarca(em);
        System.out.println("18. Marca: " + f18.get(0)[0] + " | Unidades: " + f18.get(0)[1] + " | Total: $" + f18.get(0)[2]);

        // 19.
        List<Object[]> f19 = ConsultasService.obtenerTotalFacturadoPorCondicionIva(em);
        System.out.println("19. Condición IVA: " + f19.get(0)[0] + " | Total: $" + f19.get(0)[1]);

        System.out.println("\n--- PRUEBAS NIVEL 5 ---");

        // 20.
        List<Marca> f20 = ConsultasService.obtenerMarcasConArticulosFacturados(em);
        System.out.println("20. Marcas facturadas: " + f20.get(0).getDenominacion());

        // 21.
        List<Articulo> f21 = ConsultasService.obtenerArticulosNuncaFacturados(em);
        System.out.println("21. Artículos nunca facturados: " + f21.size());

        // 22.
        List<Object[]> f22 = ConsultasService.obtenerFacturasCategorizadas(em);
        System.out.println("22. Factura N° " + f22.get(0)[0] + " | Categoria: " + f22.get(0)[2]);
    }
}