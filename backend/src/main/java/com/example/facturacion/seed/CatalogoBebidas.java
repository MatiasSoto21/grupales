package com.example.facturacion.seed;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Invented sample combinations; each flavor, size and package identifies a distinct article. */
public final class CatalogoBebidas {
    private CatalogoBebidas() { }
    public record Producto(String codigo, String denominacion, String marca, String rubro, String denominacionAnterior) { }
    private record Familia(String bebida, String rubro, List<String> marcas, List<String> sabores, List<String> presentaciones) { }

    public static List<Producto> productos() {
        var gaseosas = List.of("330 ml - Lata", "1.25 L - Botella PET", "2 L - Botella PET", "3 L - Botella PET");
        var jugos = List.of("200 ml - Tetra Pak", "330 ml - Botella", "1.5 L - Tetra Pak", "2 L - Tetra Pak");
        var individuales = List.of("250 ml - Lata", "355 ml - Lata", "500 ml - Botella", "750 ml - Botella");
        var familias = List.of(
            new Familia("Gaseosa", "Gaseosas", List.of("Coca-Cola", "Pepsi", "Cunnington", "Secco", "Brisa Negra"),
                List.of("Original", "Sin azucar", "Vainilla", "Cereza", "Cafe"), gaseosas),
            new Familia("Gaseosa", "Gaseosas", List.of("Sprite", "7 Up", "Fanta", "Paso de los Toros", "Lima Brava"),
                List.of("Limon", "Lima", "Pomelo", "Naranja", "Mandarina"), gaseosas),
            new Familia("Jugo", "Jugos", List.of("Cepita", "Baggio", "Citric", "Puro Sol", "Valle Fresco"),
                List.of("Naranja", "Manzana", "Multifruta", "Anana", "Pomelo rosado"), jugos),
            new Familia("Nectar", "Nectares", List.of("Tropicana", "Frutal", "Huerta Viva", "Nativa", "Sol de Fruta"),
                List.of("Durazno", "Pera", "Mango", "Damasco", "Guayaba"), jugos),
            new Familia("Bebida de soja", "Bebidas vegetales", List.of("Ades", "Soyana", "NotCo", "Raiz Verde", "Alma Vegetal"),
                List.of("Manzana", "Naranja", "Vainilla", "Chocolate", "Coco"),
                List.of("200 ml - Tetra Pak", "330 ml - Tetra Pak", "1 L - Tetra Pak", "1.5 L - Tetra Pak")),
            new Familia("Aperitivo sin alcohol", "Aperitivos sin alcohol", List.of("Terma", "Amargo Obrero Cero", "Monte Serrano", "Hierba Noble", "Sierra Clara"),
                List.of("Serrano", "Cuyano", "Patagonico", "Pomelo y hierbas", "Naranja amarga"),
                List.of("500 ml - Botella", "750 ml - Botella", "1.35 L - Botella", "2 L - Botella")),
            new Familia("Agua saborizada", "Aguas saborizadas", List.of("Levite", "Aquarius", "Villa del Sur", "Ser", "Manantial Azul"),
                List.of("Pera", "Manzana", "Pomelo", "Limonada", "Frutos rojos"),
                List.of("500 ml - Botella", "750 ml - Botella", "1.5 L - Botella", "2.25 L - Botella")),
            new Familia("Bebida isotonica", "Isotonicas", List.of("Gatorade", "Powerade", "Electro Rio", "Activa Sport", "Pulso Andino"),
                List.of("Naranja", "Frutas tropicales", "Lima limon", "Uva", "Frutilla"), individuales),
            new Familia("Bebida energizante", "Energizantes", List.of("Monster", "Red Bull", "Speed", "Voltio", "Furia Austral"),
                List.of("Original", "Sin azucar", "Mango tropical", "Sandia", "Maracuya"), individuales),
            new Familia("Te frio", "Te frio", List.of("Fuze Tea", "Lipton", "Verde Hoja", "Oasis Tea", "Jardin Oriental"),
                List.of("Limon", "Durazno", "Te verde y menta", "Frutos del bosque", "Jengibre y miel"),
                List.of("330 ml - Lata", "500 ml - Botella", "1.25 L - Botella", "1.5 L - Botella"))
        );
        var productos = new ArrayList<Producto>();
        for (var familia : familias) {
            for (String marca : familia.marcas()) {
                for (String sabor : familia.sabores()) {
                    for (String presentacion : familia.presentaciones()) {
                        String nombre = familia.bebida() + " " + marca + " " + sabor + " " + presentacion;
                        String codigo = "BEB-" + Normalizer.normalize(nombre, Normalizer.Form.NFD)
                                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-");
                        String nombreCorto = marca + " " + sabor + " " + presentacion.substring(0, presentacion.indexOf(" - "));
                        productos.add(new Producto(codigo, nombreCorto, marca, familia.rubro(), nombre));
                    }
                }
            }
        }
        return List.copyOf(productos);
    }
}
