package com.mundolimpio.application.receipt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WHAT: Tests unitarios para SupabaseStorageService.
 * WHY: Verifica que la subida de imágenes a Supabase Storage (via S3 SDK)
 *      funciona correctamente, con mock del S3Client para no requerir
 *      credenciales reales ni conexión a Supabase.
 *
 * DIFFERENCES: Usamos MockitoExtension puro + ReflectionTestUtils
 *              para inyectar @Value properties sin cargar Spring context.
 */
@ExtendWith(MockitoExtension.class)
class SupabaseStorageServiceTest {

    @Mock
    private S3Client s3Client;

    private SupabaseStorageService storageService;

    private MultipartFile validImage;

    /**
     * Construye el servicio manualmente e inyecta @Value fields via ReflectionTestUtils.
     * WHY: @InjectMocks no setea campos @Value; necesitamos URLs de Supabase para los tests.
     */
    @BeforeEach
    void setUp() {
        storageService = new SupabaseStorageService(s3Client);
        ReflectionTestUtils.setField(storageService, "endpoint",
                "https://testproject.supabase.co");
        ReflectionTestUtils.setField(storageService, "bucket", "receipts");
        ReflectionTestUtils.setField(storageService, "publicBaseUrl",
                "https://testproject.supabase.co/storage/v1/object/public");

        validImage = new MockMultipartFile(
                "image",
                "ticket.jpg",
                "image/jpeg",
                new byte[]{0x00, 0x01, 0x02, 0x03}
        );
    }

    // ===================== RED: Tests de subida exitosa =====================

    /**
     * RED: Verifica que upload() retorna una URL válida de Supabase Storage.
     */
    @Test
    void shouldReturnSupabaseStorageUrl() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(null);

        String url = storageService.upload(validImage);

        assertThat(url).isNotNull();
        assertThat(url).contains("/storage/v1/object/public/");
        assertThat(url).contains("ticket");
        assertThat(url).startsWith("https://testproject.supabase.co");
    }

    /**
     * RED: Verifica que se llama a s3Client.putObject con los parámetros correctos.
     */
    @Test
    void shouldCallPutObjectWithCorrectParameters() {
        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);

        when(s3Client.putObject(requestCaptor.capture(), any(RequestBody.class)))
                .thenReturn(null);

        storageService.upload(validImage);

        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("receipts");
        assertThat(request.key()).contains("ticket");
        assertThat(request.contentType()).isEqualTo("image/jpeg");
    }

    /**
     * RED: Verifica que upload() lanza excepción cuando S3Client falla.
     */
    @Test
    void shouldThrowExceptionWhenS3Fails() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("S3 connection refused"));

        assertThatThrownBy(() -> storageService.upload(validImage))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to upload image");
    }

    /**
     * RED: Verifica que upload() lanza excepción cuando el archivo está vacío.
     */
    @Test
    void shouldThrowExceptionWhenFileIsEmpty() {
        MultipartFile emptyFile = new MockMultipartFile(
                "image", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> storageService.upload(emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    /**
     * RED: Verifica que upload() lanza excepción cuando el archivo es null.
     */
    @Test
    void shouldThrowExceptionWhenFileIsNull() {
        assertThatThrownBy(() -> storageService.upload(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    /**
     * RED: Verifica que el nombre del archivo generado incluye timestamp
     * para evitar colisiones.
     */
    @Test
    void shouldGenerateUniqueFilename() throws Exception {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(null);

        String url1 = storageService.upload(validImage);
        Thread.sleep(5); // Pequeña pausa para generar timestamp diferente
        String url2 = storageService.upload(validImage);

        assertThat(url1).isNotEqualTo(url2);
    }
}
