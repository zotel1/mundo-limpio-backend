package com.mundolimpio.application.receipt.service;

import com.mundolimpio.application.receipt.dto.OcrResult;
import com.mundolimpio.application.receipt.dto.ProductLineDto;
import com.mundolimpio.application.receipt.exception.OcrProcessingException;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WHAT: Implementación concreta de ReceiptOcrService usando Tess4J (Tesseract).
 * WHY: Tesseract es el motor de OCR open-source más maduro para texto en español.
 *      Tess4J provee un wrapper Java via JNA, evitando llamadas a proceso externo.
 * 
 * DIFFERENCES con Google Cloud Vision / AWS Textract:
 * - Tesseract es gratuito y funciona offline (no requiere internet).
 * - Menor precisión que servicios cloud, pero suficiente para tickets de compra.
 * - Requiere language pack español (spa) instalado en el sistema.
 *
 * FLUJO INTERNO:
 * 1. Preprocesar imagen (escala de grises + mejora de contraste)
 * 2. Ejecutar Tesseract con idioma español
 * 3. Parsear texto crudo en líneas de producto estructuradas
 * 4. Validar confianza mínima
 */
@Service
public class TesseractOcrService implements ReceiptOcrService {

    private final ITesseract tesseract;

    /**
     * Inyección por constructor del motor Tesseract.
     * WHY: Constructor injection es la práctica recomendada por Spring
     *      (testing más fácil, estado inmutable post-construcción).
     */
    public TesseractOcrService(ITesseract tesseract) {
        this.tesseract = tesseract;
    }

    /**
     * Procesa una imagen de ticket y extrae texto estructurado.
     *
     * @param imageData Bytes de la imagen (JPEG/PNG)
     * @return OcrResult con texto crudo y líneas de producto detectadas
     * @throws OcrProcessingException si no se detecta texto o la confianza es muy baja
     */
    @Override
    public OcrResult process(byte[] imageData) {
        if (imageData == null || imageData.length == 0) {
            throw new OcrProcessingException("No image data provided");
        }

        try {
            // Paso 1: Preprocesar imagen para mejorar OCR
            BufferedImage processedImage = preprocessImage(imageData);

            // Paso 2: Ejecutar Tesseract con idioma español
            tesseract.setLanguage("spa");
            String rawText = tesseract.doOCR(processedImage);

            // Paso 3: Validar que se detectó texto
            if (rawText == null || rawText.isBlank()) {
                throw new OcrProcessingException("No text detected in image");
            }

            // Paso 4: Parsear texto crudo en líneas estructuradas
            List<ProductLineDto> lines = parseText(rawText);

            // Paso 5: Validar confianza mínima
            if (lines.isEmpty()) {
                throw new OcrProcessingException(
                        "No product lines detected — receipt may be empty or unreadable"
                );
            }
            if (lines.stream().allMatch(l -> l.confidence() < 0.3)) {
                throw new OcrProcessingException(
                        "All detected lines have confidence below 0.3 — image may be too blurry"
                );
            }

            return new OcrResult(rawText.trim(), lines);

        } catch (TesseractException e) {
            throw new OcrProcessingException("OCR processing failed: " + e.getMessage());
        }
    }

    /**
     * Preprocesa la imagen para mejorar la precisión del OCR.
     * 
     * WHAT: Convierte a escala de grises y mejora el contraste.
     * WHY: Tesseract funciona mejor con imágenes en escala de grises de alto contraste.
     *      El ruido de color y bajo contraste reducen significativamente la precisión.
     * 
     * @param imageData Bytes de la imagen original
     * @return BufferedImage preprocesada en escala de grises
     */
    BufferedImage preprocessImage(byte[] imageData) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageData));
            if (original == null) {
                throw new OcrProcessingException("Cannot decode image — unsupported format");
            }

            // Convertir a escala de grises
            BufferedImage grayscale = new BufferedImage(
                    original.getWidth(), original.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = grayscale.createGraphics();
            g.drawImage(original, 0, 0, null);
            g.dispose();

            // Mejorar contraste (factor 1.5, offset 0)
            RescaleOp rescaleOp = new RescaleOp(1.5f, 0, null);
            return rescaleOp.filter(grayscale, null);

        } catch (IOException e) {
            throw new OcrProcessingException("Failed to preprocess image: " + e.getMessage());
        }
    }

    /**
     * Parsea el texto crudo de Tesseract en líneas de producto estructuradas.
     * 
     * WHAT: Extrae nombre de producto, cantidad, precio unitario y confianza
     *       de cada línea del ticket que parezca contener un producto.
     * WHY: Tesseract retorna texto plano sin estructura; necesitamos datos
     *      estructurados para que el admin pueda revisarlos y confirmarlos.
     * 
     * ALGORITMO:
     * 1. Dividir en líneas
     * 2. Para cada línea, intentar matchear patrón: "nombre... $precio cantidad"
     * 3. Calcular confianza basada en qué tan bien matchea el patrón
     * 
     * @param rawText Texto crudo extraído por Tesseract
     * @return Lista de líneas de producto detectadas (vacía si no hay productos)
     */
    List<ProductLineDto> parseText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }

        List<ProductLineDto> lines = new ArrayList<>();
        String[] textLines = rawText.split("\\r?\\n");

        for (String line : textLines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }

            // Saltar líneas que son claramente header/footer del ticket
            if (isHeaderOrFooter(trimmed)) {
                continue;
            }

            ProductLineDto productLine = tryParseProductLine(trimmed);
            if (productLine != null) {
                lines.add(productLine);
            }
        }

        return lines;
    }

    /**
     * Determina si una línea es header/footer del ticket (no un producto).
     */
    private boolean isHeaderOrFooter(String line) {
        String upper = line.toUpperCase();
        return upper.contains("TOTAL") || upper.contains("SUBTOTAL")
                || upper.contains("IVA") || upper.contains("CUIT")
                || upper.contains("GRACIAS") || upper.contains("VUELVA")
                || upper.contains("FECHA:") || upper.contains("FECHA ")
                || upper.contains("HORA:");
    }

    /**
     * Intenta parsear una línea como producto con precio y cantidad.
     * 
     * FORMATOS SOPORTADOS:
     * - "Producto $1.250,50 3" (precio + cantidad al final)
     * - "Producto 3 x $1.250,50" (cantidad + 'x' + precio)
     * - "Producto 3 $1.250,50" (cantidad + precio)
     * 
     * @param line Línea de texto del ticket
     * @return ProductLineDto parseado, o null si no matchea ningún formato
     */
    private ProductLineDto tryParseProductLine(String line) {
        // Patrón 1: "nombre... $precio cantidad" al final de la línea
        // Ej: "LAVANDINA 5L $1.250,50 2"
        Pattern pattern1 = Pattern.compile(
                "^(.+?)\\s+\\$?([\\d]{1,3}(?:\\.[\\d]{3})*(?:,[\\d]{1,2})?)\\s+(\\d+)\\s*$");
        Matcher m1 = pattern1.matcher(line);
        if (m1.matches()) {
            String name = m1.group(1).trim();
            BigDecimal price = parsePrice(m1.group(2));
            int qty = Integer.parseInt(m1.group(3));
            double confidence = calculateConfidence(name, price, qty);
            return new ProductLineDto(name, qty, price, confidence, null);
        }

        // Patrón 2: "nombre... cantidad x $precio"
        // Ej: "CLORO GEL 2 x $450,00"
        Pattern pattern2 = Pattern.compile(
                "^(.+?)\\s+(\\d+)\\s*x\\s*\\$?([\\d]{1,3}(?:\\.[\\d]{3})*(?:,[\\d]{1,2})?)\\s*$");
        Matcher m2 = pattern2.matcher(line);
        if (m2.matches()) {
            String name = m2.group(1).trim();
            int qty = Integer.parseInt(m2.group(2));
            BigDecimal price = parsePrice(m2.group(3));
            double confidence = calculateConfidence(name, price, qty);
            return new ProductLineDto(name, qty, price, confidence, null);
        }

        // Patrón 3: "nombre... cantidad $precio" (sin 'x')
        // Ej: "DETERGENTE 3 $890,50"
        Pattern pattern3 = Pattern.compile(
                "^(.+?)\\s+(\\d+)\\s+\\$?([\\d]{1,3}(?:\\.[\\d]{3})*(?:,[\\d]{1,2})?)\\s*$");
        Matcher m3 = pattern3.matcher(line);
        if (m3.matches()) {
            String name = m3.group(1).trim();
            int qty = Integer.parseInt(m3.group(2));
            BigDecimal price = parsePrice(m3.group(3));
            double confidence = calculateConfidence(name, price, qty);
            return new ProductLineDto(name, qty, price, confidence, null);
        }

        return null; // No matcheó ningún formato de producto
    }

    /**
     * Parsea un precio en formato argentino (ej: "1.250,50" → 1250.50).
     * 
     * @param rawPrice String con el precio (puede incluir separadores de miles y coma decimal)
     * @return BigDecimal con el valor numérico
     */
    private BigDecimal parsePrice(String rawPrice) {
        // Remover separadores de miles (puntos) y reemplazar coma decimal por punto
        String normalized = rawPrice.replace(".", "").replace(",", ".");
        return new BigDecimal(normalized);
    }

    /**
     * Calcula la confianza de una línea parseada.
     * 
     * WHAT: Asigna un score 0-1 basado en qué tan bien formada está la línea.
     * WHY: Permite filtrar líneas con baja confianza (ruido/garbled text).
     * 
     * HEURÍSTICA:
     * - Base 0.8 si el nombre tiene al menos 3 caracteres
     * - +0.1 si el precio es razonable (entre 1 y 999999)
     * - +0.1 si la cantidad es positiva
     * - Penalización si el nombre contiene solo números/símbolos
     * 
     * @param name  Nombre del producto detectado
     * @param price Precio unitario
     * @param qty   Cantidad
     * @return Confianza entre 0.0 y 1.0
     */
    private double calculateConfidence(String name, BigDecimal price, int qty) {
        double confidence = 0.0;

        // Nombre significativo (al menos 3 letras)
        if (name != null && name.replaceAll("[^a-zA-ZáéíóúñÁÉÍÓÚÑ]", "").length() >= 3) {
            confidence += 0.5;
        } else if (name != null && name.length() >= 3) {
            confidence += 0.3;
        }

        // Precio razonable
        if (price != null && price.compareTo(BigDecimal.ONE) >= 0
                && price.compareTo(new BigDecimal("999999")) <= 0) {
            confidence += 0.25;
        }

        // Cantidad positiva
        if (qty > 0 && qty <= 1000) {
            confidence += 0.25;
        }

        return Math.min(1.0, confidence);
    }
}
