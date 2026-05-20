package com.mundolimpio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Punto de entrada principal de MundoLimpio Backend.
 * <p>
 * WHAT: @EnableAsync activa el soporte para metodos @Async en servicios
 * como AuditLogService, permitiendo escritura de auditoria no bloqueante.
 * WHY: Necesario para que logAsync() se ejecute en un thread separado
 * del pool de Spring TaskExecutor, sin bloquear al caller.
 */
@SpringBootApplication
@EnableAsync
public class MundoLimpioApplication {

	public static void main(String[] args) {
		SpringApplication.run(MundoLimpioApplication.class, args);
	}

}
