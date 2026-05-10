package com.mundolimpio.application.user.exception;

/**
 * Excepción lanzada cuando un refresh token no es válido para renovar el access token.
 * <p>
 * QUÉ: Extiende RuntimeException para que sea unchecked y pueda
 * ser capturada por AuthExceptionHandler.
 * POR QUÉ: Los errores de refresh token tienen causas distintas (expirado, inválido,
 * mal formado, usuario no encontrado). Cada una necesita un mensaje en español claro
 * para que el cliente Flutter pueda mostrar el error correcto al usuario.
 * CÓMO: Incluye un enum RefreshError que clasifica la causa del error.
 * El constructor recibe el mensaje descriptivo y la razón específica.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    private final RefreshError reason;

    /**
     * Crea una excepción con un mensaje descriptivo y la razón del error.
     *
     * @param message Mensaje descriptivo en español (ej: "El refresh token ha expirado")
     * @param reason  Causa específica del error (EXPIRED, INVALID, etc.)
     */
    public InvalidRefreshTokenException(String message, RefreshError reason) {
        super(message);
        this.reason = reason;
    }

    /**
     * Devuelve la razón específica por la que el refresh token fue rechazado.
     *
     * @return RefreshError enum con la causa del error
     */
    public RefreshError getReason() {
        return reason;
    }

    /**
     * Enum que clasifica las posibles causas de error de un refresh token.
     * <p>
     * Cada valor tiene una descripción en español lista para usar
     * como mensaje de error al cliente.
     */
    public enum RefreshError {
        EXPIRED("El refresh token ha expirado"),
        INVALID("El refresh token no es válido"),
        USER_NOT_FOUND("El usuario asociado al refresh token no existe"),
        MALFORMED("El refresh token está mal formado");

        private final String description;

        /**
         * @param description Descripción del error en español
         */
        RefreshError(String description) {
            this.description = description;
        }

        /**
         * Devuelve la descripción del error en español.
         *
         * @return Descripción legible del error
         */
        public String getDescription() {
            return description;
        }
    }
}
