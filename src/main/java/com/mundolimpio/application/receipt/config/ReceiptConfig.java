package com.mundolimpio.application.receipt.config;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * WHAT: Configuración de beans para el módulo receipt (OCR + Storage).
 * WHY: Tess4J y AWS S3 SDK no son auto-configurables por Spring Boot.
 *      Necesitamos crear explícitamente los beans ITesseract y S3Client
 *      para que TesseractOcrService y SupabaseStorageService puedan inyectarlos.
 * 
 * DIFFERENCES con otras configuraciones:
 * - ITesseract se configura con datapath (modelos de idioma) y lenguaje español.
 * - S3Client usa StaticCredentialsProvider con credenciales de Supabase.
 * - El endpoint de S3 se configura manualmente porque Supabase no está en el catálogo
 *   de endpoints estándar de AWS.
 */
@Configuration
public class ReceiptConfig {

    @Value("${app.supabase.endpoint}")
    private String supabaseEndpoint;

    @Value("${app.supabase.access-key}")
    private String accessKey;

    @Value("${app.supabase.secret-key}")
    private String secretKey;

    @Value("${app.supabase.region:us-east-1}")
    private String region;

    /**
     * Crea el bean ITesseract configurado para OCR en español.
     * 
     * WHAT: Configura Tesseract con el datapath por defecto del sistema
     *       y el idioma español (spa).
     * WHY: El datapath contiene los modelos de idioma (.traineddata).
     *      En Docker, se instalan via apk add tesseract-ocr-data-spa.
     *      En desarrollo local, tesseract debe estar instalado.
     * 
     * @return Instancia de ITesseract lista para usar
     */
    @Bean
    public ITesseract tesseract() {
        Tesseract tesseract = new Tesseract();
        // En Alpine/Docker, los modelos están en /usr/share/tessdata/
        // En Windows/Mac con instalación estándar, Tesseract los detecta automáticamente
        tesseract.setDatapath("/usr/share/tessdata/");
        tesseract.setLanguage("spa");
        return tesseract;
    }

    /**
     * Crea el bean S3Client configurado para Supabase Storage.
     * 
     * WHAT: Configura un cliente S3 con endpoint personalizado (Supabase),
     *       credenciales estáticas y path-style access.
     * WHY: Supabase Storage no es AWS S3 nativo; requiere endpoint override
     *      y path-style access (en vez de virtual hosted-style).
     * 
     * @return Instancia de S3Client configurada para Supabase
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .endpointOverride(URI.create(supabaseEndpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true) // Necesario para Supabase Storage (no virtual host)
                .build();
    }
}
