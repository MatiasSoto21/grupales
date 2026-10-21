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

    // =========================================================
    // INICIALIZADOR
    // =========================================================

    @Override
    @Transactional
    public void run(String... args) {

        Long cantidad = em.createQuery(
                "SELECT COUNT(f) FROM FacturaVenta f", Long.class
        ).getSingleResult();

        if (cantidad > 0) {
            System.out.println(
                    "Ya existen facturas. Se omite la carga inicial."
            );
            return;
        }

        LocalDate hoy = LocalDate.now();

        // =====================================================
        // USUARIO ADMIN
        // =====================================================

        List<Usuario> administradores = em.createQuery(
                        "SELECT u FROM Usuario u WHERE u.usuario = :usuario",
                        Usuario.class
                )
                .setParameter("usuario", "admin")
                .setMaxResults(1)
                .getResultList();

        Usuario admin;

        if (administradores.isEmpty()) {
            admin = new Usuario();
            admin.setUsuario("admin");
            admin.setClave("1234");
            admin.setNombre("Matias");
            admin.setApellido("Soto");

            em.persist(admin);
            em.flush();
        } else {
            admin = administradores.get(0);
        }

        // =====================================================
        // CONDICIONES DE IVA
        // =====================================================

        List<CondicionIva> condicionesIva = new ArrayList<>();

        CondicionIva responsableInscripto = crearCondicionIva(
                1, "IVA Responsable Inscripto", admin
        );
        condicionesIva.add(responsableInscripto);

        CondicionIva exento = crearCondicionIva(
                4, "IVA Sujeto Exento", admin
        );
        condicionesIva.add(exento);

        CondicionIva consumidorFinal = crearCondicionIva(
                5, "Consumidor Final", admin
        );
        condicionesIva.add(consumidorFinal);

        CondicionIva monotributista = crearCondicionIva(
                6, "Monotributista", admin
        );
        condicionesIva.add(monotributista);

        // =====================================================
        // MONEDA
        // =====================================================

        TipoMoneda moneda = new TipoMoneda();
        moneda.setCodigoAfip("PES");
        moneda.setDenominacion("Pesos Argentinos");
        moneda.setSimbolo("$");

        auditar(moneda, admin);
        em.persist(moneda);

        // =====================================================
        // PUNTOS DE VENTA
        // =====================================================

        List<PuntoVenta> puntosVenta = new ArrayList<>();

        puntosVenta.add(crearPuntoVenta(
                1,
                "Casa Central",
                "Manual",
                "San Martin 123",
                admin
        ));

        puntosVenta.add(crearPuntoVenta(
                2,
                "Sucursal 1",
                "Manual",
                "Av. Las Heras 450",
                admin
        ));

        puntosVenta.add(crearPuntoVenta(
                3,
                "Sucursal 2",
                "Manual",
                "Av. San Martin 1800",
                admin
        ));

        // =====================================================
        // MARCAS
        // =====================================================

        Marca cocaCola = crearMarca(100, "Coca Cola", admin);
        Marca pepsi = crearMarca(200, "Pepsi", admin);
        Marca sprite = crearMarca(300, "Sprite", admin);
        Marca fanta = crearMarca(400, "Fanta", admin);
        Marca villavicencio = crearMarca(500, "Villavicencio", admin);
        Marca cepita = crearMarca(600, "Cepita", admin);

        // =====================================================
        // RUBROS
        // =====================================================

        Rubro gaseosas = crearRubro(10, "Bebidas", admin);
        Rubro aguas = crearRubro(20, "Aguas", admin);
        Rubro jugos = crearRubro(30, "Jugos", admin);
        Rubro energizantes = crearRubro(40, "Energizantes", admin);

        // =====================================================
        // ARTICULOS
        // =====================================================

        Articulo coca500 = crearArticulo(
                "COCA500", "Coca Cola 500 ml",
                cocaCola, gaseosas, admin
        );

        // Pepsi queda sin facturar para probar la consulta 21.
        crearArticulo(
                "PEPSI500", "Pepsi 500 ml",
                pepsi, gaseosas, admin
        );

        Articulo coca2250 = crearArticulo(
                "COCA2250", "Coca Cola 2.25 L",
                cocaCola, gaseosas, admin
        );

        Articulo sprite500 = crearArticulo(
                "SPRITE500", "Sprite 500 ml",
                sprite, gaseosas, admin
        );

        Articulo sprite1500 = crearArticulo(
                "SPRITE1500", "Sprite 1.5 L",
                sprite, gaseosas, admin
        );

        Articulo fanta500 = crearArticulo(
                "FANTA500", "Fanta naranja 500 ml",
                fanta, gaseosas, admin
        );

        Articulo fanta1500 = crearArticulo(
                "FANTA1500", "Fanta naranja 1.5 L",
                fanta, gaseosas, admin
        );

        Articulo agua500 = crearArticulo(
                "AGUA500", "Agua mineral 500 ml",
                villavicencio, aguas, admin
        );

        Articulo agua1500 = crearArticulo(
                "AGUA1500", "Agua mineral 1.5 L",
                villavicencio, aguas, admin
        );

        Articulo jugoNaranja = crearArticulo(
                "JUGONAR1L", "Jugo de naranja 1 L",
                cepita, jugos, admin
        );

        Articulo jugoMulti = crearArticulo(
                "JUGOMULTI", "Jugo multifruta 1 L",
                cepita, jugos, admin
        );

        Articulo energetica = crearArticulo(
                "ENERG473", "Bebida energetica 473 ml",
                cocaCola, energizantes, admin
        );

        // =====================================================
        // LISTA DE PRECIOS
        // =====================================================

        ListaPrecio listaPrecio = new ListaPrecio();
        listaPrecio.setCodigo("LP01");
        listaPrecio.setDenominacion("Lista General");

        auditar(listaPrecio, admin);
        em.persist(listaPrecio);

        List<ListaPrecioArticulo> productos = new ArrayList<>();

        productos.add(crearPrecio(
                listaPrecio, coca500, 2500, admin
        ));

        productos.add(crearPrecio(
                listaPrecio, coca2250, 4200, admin
        ));

        productos.add(crearPrecio(
                listaPrecio, sprite500, 2300, admin
        ));

        productos.add(crearPrecio(
                listaPrecio, sprite1500, 3900, admin
        ));

        productos.add(crearPrecio(
                listaPrecio, fanta500, 2200, admin
        ));

        productos.add(crearPrecio(
                listaPrecio, fanta1500, 3800, admin
        ));

        productos.add(crearPrecio(
                listaPrecio, agua500, 1200, admin
        ));

        productos.add(crearPrecio(
                listaPrecio, agua1500, 2100, admin
        ));

        productos.add(crearPrecio(
                listaPrecio, jugoNaranja, 1800, admin
        ));

        productos.add(crearPrecio(
                listaPrecio, jugoMulti, 1950, admin
        ));

        productos.add(crearPrecio(
                listaPrecio, energetica, 2800, admin
        ));

        // =====================================================
        // CLIENTES
        // =====================================================

        List<Cliente> clientes = new ArrayList<>();

        clientes.add(crearCliente(
                "20-12345678-9",
                "Juan Perez",
                "juan.perez@gmail.com",
                "2614567890",
                "2615555555",
                "Belgrano", "450", admin
        ));

        clientes.add(crearCliente(
                "30-71234567-8",
                "Empresa Tech S.A.",
                "ventas@empresatech.com",
                "2614000000",
                "2615000000",
                "Las Heras", "1200", admin
        ));

        clientes.add(crearCliente(
                "27-23456789-4",
                "Maria Gonzalez",
                "maria.gonzalez@gmail.com",
                "2614100001",
                "2615100001",
                "San Martin", "235", admin
        ));

        clientes.add(crearCliente(
                "20-34567890-1",
                "Carlos Rodriguez",
                "carlos.rodriguez@gmail.com",
                "2614100002",
                "2615100002",
                "Colon", "780", admin
        ));

        clientes.add(crearCliente(
                "27-45678901-2",
                "Lucia Fernandez",
                "lucia.fernandez@gmail.com",
                "2614100003",
                "2615100003",
                "Rivadavia", "340", admin
        ));

        clientes.add(crearCliente(
                "30-72345678-9",
                "Distribuidora Cuyo S.A.",
                "ventas@distribuidoracuyo.com",
                "2614100004",
                "2615100004",
                "Mitre", "1500", admin
        ));

        clientes.add(crearCliente(
                "20-56789012-3",
                "Diego Martinez",
                "diego.martinez@gmail.com",
                "2614100005",
                "2615100005",
                "Belgrano", "920", admin
        ));

        clientes.add(crearCliente(
                "27-67890123-5",
                "Sofia Lopez",
                "sofia.lopez@gmail.com",
                "2614100006",
                "2615100006",
                "San Juan", "670", admin
        ));

        clientes.add(crearCliente(
                "30-73456789-0",
                "Almacen Los Andes S.R.L.",
                "compras@losandes.com",
                "2614100007",
                "2615100007",
                "Chile", "1100", admin
        ));

        clientes.add(crearCliente(
                "20-78901234-6",
                "Martin Sanchez",
                "martin.sanchez@gmail.com",
                "2614100008",
                "2615100008",
                "Lavalle", "560", admin
        ));

        clientes.add(crearCliente(
                "27-89012345-7",
                "Valentina Torres",
                "valentina.torres@gmail.com",
                "2614100009",
                "2615100009",
                "Peru", "320", admin
        ));

        clientes.add(crearCliente(
                "30-74567890-1",
                "Comercial Mendoza S.A.",
                "ventas@comercialmendoza.com",
                "2614100010",
                "2615100010",
                "Aristides", "850", admin
        ));

        // =====================================================
        // 50 FACTURAS VARIADAS
        // =====================================================

        for (int i = 1001; i <= 1050; i++) {

            LocalDate fecha = hoy.minusDays((i - 1000) * 2L);

            String estado = (i % 10 == 0)
                    ? "ANULADA"
                    : "EMITIDA";

            // Una de cada cuatro facturas será de consumidor final.
            boolean esConsumidorFinal = (i % 4 == 0);

            Cliente cliente = esConsumidorFinal
                    ? null
                    : clientes.get((i - 1001) % clientes.size());

            // Distribuir las facturas entre los tres puntos de venta.
            PuntoVenta puntoVenta = puntosVenta.get(
                    (i - 1001) % puntosVenta.size()
            );

            // Distribuir las condiciones de IVA entre las facturas.
            CondicionIva condicionIva;

            if (esConsumidorFinal) {
                condicionIva = consumidorFinal;
            } else {
                List<CondicionIva> otrasCondiciones = Arrays.asList(
                        responsableInscripto,
                        monotributista,
                        exento
                );

                condicionIva = otrasCondiciones.get(
                        (i - 1001) % otrasCondiciones.size()
                );
            }

            crearFacturaVariada(
                    (long) i,
                    fecha,
                    estado,
                    cliente,
                    puntoVenta,
                    condicionIva,
                    moneda,
                    productos,
                    admin
            );
        }

        System.out.println("\n======================================");
        System.out.println("INICIALIZACION COMPLETADA");
        System.out.println("Clientes: " + clientes.size());
        System.out.println("Productos facturables: " + productos.size());
        System.out.println("Condiciones de IVA: " + condicionesIva.size());
        System.out.println("Puntos de venta: " + puntosVenta.size());
        System.out.println("Facturas: 50");
        System.out.println("Numeros: 1001 a 1050");
        System.out.println("======================================\n");

        // =====================================================
        // PRUEBAS DE CONSULTAS JPQL
        // =====================================================

        List<FacturaVenta> todas =
                ConsultasService.obtenerTodasLasFacturas(em);

        System.out.println("1. Todas las facturas: " + todas.size());

        List<Object[]> resumen =
                ConsultasService.obtenerResumenFacturas(em);

        System.out.println("2. Registros del resumen: " + resumen.size());

        List<Articulo> porRubro =
                ConsultasService.obtenerArticulosPorRubro(em, "Bebidas");

        System.out.println("3. Articulos en Bebidas: " + porRubro.size());

        List<FacturaVenta> porFecha =
                ConsultasService.obtenerFacturasPorRangoFechas(
                        em, hoy.minusDays(3), hoy.plusDays(1)
                );

        System.out.println("4. Facturas recientes: " + porFecha.size());

        List<FacturaVenta> altas =
                ConsultasService.obtenerFacturasValidasAltas(em, 5000);

        System.out.println(
                "5. Facturas validas mayores a $5000: " + altas.size()
        );

        List<Cliente> encontrados =
                ConsultasService.buscarClientesPorNombreOCuit(
                        em, "Perez", "20-"
                );

        System.out.println(
                "6. Clientes coincidentes: " + encontrados.size()
        );

        List<String> estados =
                ConsultasService.obtenerEstadosFacturasUnicos(em);

        System.out.println("7. Estados unicos: " + estados);

        Object[] estadisticas =
                ConsultasService.obtenerEstadisticasFacturasEmitidas(em);

        System.out.println(
                "8. Estadisticas de facturas emitidas: "
                        + Arrays.toString(estadisticas)
        );

        List<PuntoVenta> puntos =
                ConsultasService.obtenerPuntosVentaPorNumeros(
                        em, Arrays.asList(1, 2, 3)
                );

        System.out.println(
                "9. Puntos de venta encontrados: " + puntos.size()
        );

        List<FacturaVenta> porUsuario =
                ConsultasService.obtenerFacturasPorUsuarioCarga(em, "admin");

        System.out.println(
                "10. Facturas del usuario admin: " + porUsuario.size()
        );

        List<FacturaVentaDetalle> detalles =
                ConsultasService.obtenerDetallesPorNumeroPuntoVenta(em, 1);

        System.out.println("11. Detalles del PV 1: " + detalles.size());

        List<Object[]> articulosMarca =
                ConsultasService.obtenerArticulosConMarca(em);

        System.out.println(
                "12. Articulos con marca: " + articulosMarca.size()
        );

        List<FacturaVenta> porMarca =
                ConsultasService.obtenerFacturasPorMarcaArticulo(
                        em, "Coca Cola"
                );

        System.out.println(
                "13. Facturas con Coca Cola: " + porMarca.size()
        );

        List<FacturaVenta> sobrePromedio =
                ConsultasService.obtenerFacturasSuperioresAlPromedio(em);

        System.out.println(
                "14. Facturas sobre el promedio: " + sobrePromedio.size()
        );

        List<FacturaVenta> porCuit =
                ConsultasService.obtenerFacturasPorCuitCliente(
                        em, "20-12345678-9"
                );

        System.out.println(
                "15. Facturas del cliente Juan Perez: " + porCuit.size()
        );

        List<Object[]> porPunto =
                ConsultasService.obtenerResumenFacturacionPorPuntoVenta(em);

        System.out.println(
                "16. Resumen por punto de venta: " + porPunto.size()
        );

        List<String> usuarios =
                ConsultasService.obtenerUsuariosConMasDeNFacturas(em, 0);

        System.out.println("17. Usuarios con facturas: " + usuarios);

        List<Object[]> ventasMarca =
                ConsultasService.obtenerVentasTotalesPorMarca(em);

        System.out.println("18. Marcas con ventas: " + ventasMarca.size());

        List<Object[]> porIva =
                ConsultasService.obtenerTotalFacturadoPorCondicionIva(em);

        System.out.println(
                "19. Resumen por condicion IVA: " + porIva.size()
        );

        List<Marca> marcasFacturadas =
                ConsultasService.obtenerMarcasConArticulosFacturados(em);

        System.out.println(
                "20. Marcas facturadas: " + marcasFacturadas.size()
        );

        List<Articulo> nuncaFacturados =
                ConsultasService.obtenerArticulosNuncaFacturados(em);

        System.out.println(
                "21. Articulos nunca facturados: " + nuncaFacturados.size()
        );

        List<Object[]> categorizadas =
                ConsultasService.obtenerFacturasCategorizadas(em);

        System.out.println(
                "22. Facturas categorizadas: " + categorizadas.size()
        );
    }

    // =========================================================
    // CREACION DE FACTURAS
    // =========================================================

    private FacturaVenta crearFacturaVariada(
            Long numero,
            LocalDate fecha,
            String estado,
            Cliente cliente,
            PuntoVenta puntoVenta,
            CondicionIva condicionIva,
            TipoMoneda moneda,
            List<ListaPrecioArticulo> productos,
            Usuario admin) {

        FacturaVenta f = new FacturaVenta();

        f.setNumero(numero);
        f.setFechaEmision(fecha);
        f.setPuntoVenta(puntoVenta);
        f.setCliente(cliente);
        f.setCondicionIva(condicionIva);
        f.setTipoMoneda(moneda);
        f.setEstado(estado);

        double total = 0;

        // Entre 1 y 4 detalles por factura.
        int cantidadItems = 1 + (int) (numero % 4);

        for (int j = 0; j < cantidadItems; j++) {

            int indice = (int) (
                    (numero - 1001 + j * 3) % productos.size()
            );

            ListaPrecioArticulo producto = productos.get(indice);

            double cantidad = 1 + ((numero + j) % 5);
            double subtotal = cantidad * producto.getPrecioVenta();

            FacturaVentaDetalle detalle = new FacturaVentaDetalle();

            detalle.setListaPrecioArticulo(producto);
            detalle.setDescripcion(
                    producto.getArticulo().getDenominacion()
            );
            detalle.setCantidad(cantidad);
            detalle.setPrecioUnitario(producto.getPrecioVenta());
            detalle.setPorcentajeBonificacion(0);
            detalle.setImporteNeto(subtotal);
            detalle.setImporteIva(0);
            detalle.setImporteSubtotal(subtotal);

            f.addDetalle(detalle);

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

        auditar(f, admin);

        em.persist(f);

        return f;
    }

    // =========================================================
    // CREACION DE CLIENTES
    // =========================================================

    private Cliente crearCliente(
            String cuit,
            String denominacion,
            String email,
            String telefono,
            String celular,
            String calle,
            String numeroCalle,
            Usuario admin) {

        Contacto contacto = new Contacto();
        contacto.setEmail(email);
        contacto.setTelefono(telefono);
        contacto.setCelular(celular);
        em.persist(contacto);

        Domicilio domicilio = new Domicilio();
        domicilio.setNombreCalle(calle);
        domicilio.setNumeroCalle(numeroCalle);
        em.persist(domicilio);

        Cliente cliente = new Cliente();
        cliente.setCuitCuil(cuit);
        cliente.setDenominacion(denominacion);
        cliente.setContacto(contacto);
        cliente.setDomicilio(domicilio);

        auditar(cliente, admin);

        em.persist(cliente);

        return cliente;
    }

    // =========================================================
    // CREACION DE ARTICULOS Y PRECIOS
    // =========================================================

    private Marca crearMarca(
            int codigo,
            String denominacion,
            Usuario admin) {

        Marca marca = new Marca();
        marca.setCodigo(codigo);
        marca.setDenominacion(denominacion);

        auditar(marca, admin);

        em.persist(marca);

        return marca;
    }

    private Rubro crearRubro(
            int codigo,
            String denominacion,
            Usuario admin) {

        Rubro rubro = new Rubro();
        rubro.setCodigo(codigo);
        rubro.setDenominacion(denominacion);

        auditar(rubro, admin);

        em.persist(rubro);

        return rubro;
    }

    private Articulo crearArticulo(
            String codigo,
            String denominacion,
            Marca marca,
            Rubro rubro,
            Usuario admin) {

        Articulo articulo = new Articulo();

        articulo.setCodigo(codigo);
        articulo.setDenominacion(denominacion);
        articulo.setMarca(marca);
        articulo.setRubro(rubro);

        auditar(articulo, admin);

        em.persist(articulo);

        return articulo;
    }

    private ListaPrecioArticulo crearPrecio(
            ListaPrecio listaPrecio,
            Articulo articulo,
            double precio,
            Usuario admin) {

        ListaPrecioArticulo registro = new ListaPrecioArticulo();

        registro.setListaPrecio(listaPrecio);
        registro.setArticulo(articulo);
        registro.setPrecioVenta(precio);

        auditar(registro, admin);

        em.persist(registro);

        return registro;
    }

    // =========================================================
    // CREACION DE CONDICIONES DE IVA
    // =========================================================

    private CondicionIva crearCondicionIva(
            int codigoAfip,
            String denominacion,
            Usuario admin) {

        CondicionIva condicion = new CondicionIva();

        condicion.setCodigoAfip(codigoAfip);
        condicion.setDenominacion(denominacion);

        auditar(condicion, admin);

        em.persist(condicion);

        return condicion;
    }

    // =========================================================
    // CREACION DE PUNTOS DE VENTA
    // =========================================================

    private PuntoVenta crearPuntoVenta(
            int numero,
            String descripcion,
            String tipoEmision,
            String domicilio,
            Usuario admin) {

        PuntoVenta punto = new PuntoVenta();

        punto.setNumero(numero);
        punto.setDescripcion(descripcion);
        punto.setTipoEmision(tipoEmision);
        punto.setDomicilioComercial(domicilio);

        auditar(punto, admin);

        em.persist(punto);

        return punto;
    }

    // =========================================================
    // AUDITORIA
    // =========================================================

    private void auditar(AuditoriaApp entidad, Usuario admin) {

        entidad.setFechaAlta(LocalDate.now());
        entidad.setFechaModificacion(LocalDate.now());
        entidad.setUsuarioCarga(admin);
        entidad.setUsuarioModificacion(admin);
    }
}
