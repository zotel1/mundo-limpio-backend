package com.mundolimpio.application.receipt.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Instant;
import java.util.UUID;

/**
 * WHAT: Implementación concreta de ReceiptStorageService usando AWS S3 SDK
 *       contra Supabase Storage (API S3-compatible).
 * WHY: Supabase Storage expone una API compatible con S3, lo que permite
 *      usar el SDK de AWS sin dependencias adicionales. Si migramos a AWS S3
 *      en el futuro, solo cambiamos las credenciales (endpoint, access key, secret).
 * 
 * DIFFERENCES con Google Cloud Storage / Azure Blob:
 * - S3 SDK es el estándar de facto para object storage.
 * - Supabase Storage es gratuito en el free tier (1GB, 50MB/file).
 * - Misma infraestructura que PostgreSQL (todo en Supabase).
 */
@Service
public class SupabaseStorageService implements ReceiptStorageService {

    private final S3Client s3Client;

    @Value("${app.supabase.endpoint}")
    private String endpoint;

    @Value("${app.supabase.bucket}")
    private String bucket;

    @Value("${app.supabase.public-base-url}")
    private String publicBaseUrl;

    /**
     * Inyección por constructor del S3Client.
     * WHY: Constructor injection permite mockear S3Client en tests unitarios
     *      sin necesidad de cargar el contexto de Spring.
     */
    public SupabaseStorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Sube una imagen de ticket a Supabase Storage y retorna la URL pública.
     *
     * FLUJO:
     * 1. Validar que el archivo no esté vacío
     * 2. Validar MIME type (solo JPEG/PNG)
     * 3. Validar tamaño máximo (10MB)
     * 4. Generar nombre único (timestamp + UUID + extensión original)
     * 5. Construir PutObjectRequest con bucket, key y contentType
     * 6. Llamar a s3Client.putObject() con los bytes del archivo
     * 7. Construir y retornar la URL pública
     *
     * @param file Archivo multipart (JPEG/PNG) subido por el admin
     * @return URL pública de la imagen en Supabase Storage
     * @throws IllegalArgumentException si el archivo es null, vacío, formato no soportado, o excede 10MB
     * @throws RuntimeException si falla la conexión con Supabase
     */
    @Override
    public String upload(MultipartFile file) {
        // Validación de nulidad
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }

        // Validación de archivo vacío
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        // WHAT: Validación de MIME type — solo JPEG y PNG permitidos.
        // WHY: La spec REC-002 requiere filtrar formatos no soportados.
        //      Tesseract solo procesa imágenes; PDFs y otros formatos deben rechazarse.
        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.equals(org.springframework.http.MediaType.IMAGE_JPEG_VALUE) &&
                 !contentType.equals(org.springframework.http.MediaType.IMAGE_PNG_VALUE))) {
            throw new IllegalArgumentException(
                    "Unsupported file type: " + contentType + ". Only JPEG and PNG are accepted.");
        }

        // WHAT: Validación de tamaño máximo — 10MB.
        // WHY: La spec REC-002 define 10MB como límite máximo para imágenes de tickets.
        //      Imágenes más grandes probablemente no son fotos de tickets (ej: RAW de cámara).
        final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB en bytes
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File size exceeds maximum of 10MB. Actual size: " + file.getSize() + " bytes");
        }

        try {
            // Generar nombre único para evitar colisiones
            String filename = generateUniqueFilename(file.getOriginalFilename());

            // Construir request S3
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(filename)
                    .contentType(file.getContentType())
                    .build();

            // Subir a Supabase Storage
            s3Client.putObject(putRequest,
                    RequestBody.fromBytes(file.getBytes()));

            // Construir URL pública
            return publicBaseUrl + "/" + bucket + "/" + filename;

        } catch (Exception e) {
            throw new RuntimeException("Failed to upload image: " + e.getMessage(), e);
        }
    }

    /**
     * Genera un nombre de archivo único basado en timestamp + UUID.
     * WHY: Dos admins podrían subir tickets con el mismo nombre (ej: "ticket.jpg").
     *      El prefijo con timestamp y UUID garantiza unicidad sin colisiones.
     *
     * @param originalFilename Nombre original del archivo subido (puede ser null)
     * @return Nombre único con formato: {timestamp}-{uuid}.{ext}
     */
    private String generateUniqueFilename(String originalFilename) {
        String extension = "jpg"; // default
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        }

        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String uuid = UUID.randomUUID().toString().substring(0, 8);

        return "ticket-" + timestamp + "-" + uuid + "." + extension;
    }
}
