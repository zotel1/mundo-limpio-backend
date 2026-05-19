package com.mundolimpio.application.receipt.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * WHAT: Interfaz que define el contrato para el almacenamiento de imágenes de tickets.
 * WHY: Abstracción sobre Supabase Storage (S3-compatible) permite cambiar el backend
 *      de almacenamiento (ej: AWS S3, Google Cloud Storage) sin tocar el código que lo usa.
 * 
 * IMPLEMENTACIÓN: SupabaseStorageService (PR 2) — usa AWS S3 SDK contra Supabase Storage.
 */
public interface ReceiptStorageService {

    /**
     * Sube una imagen de ticket al storage y retorna la URL pública.
     * 
     * @param file Archivo multipart (JPEG/PNG) subido por el admin
     * @return URL pública de la imagen (ej: https://<project>.supabase.co/storage/v1/object/public/receipts/<filename>)
     * @throws RuntimeException si falla la conexión con Supabase o el bucket no existe
     */
    String upload(MultipartFile file);
}
