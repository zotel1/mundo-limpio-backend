package com.mundolimpio.application.audit.repository;

import com.mundolimpio.application.audit.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para AuditLog.
 * <p>
 * WHAT: Provee operaciones CRUD basicas sobre la tabla audit_log
 * sin necesidad de implementar queries manualmente.
 * <p>
 * WHY: Spring Data JPA genera automaticamente findById, findAll, save, delete
 * y queries derivadas por nombre de metodo. Para el caso de auditoria solo
 * necesitamos save() (escritura) y posiblemente find por actor/entidad
 * (consultas futuras).
 * <p>
 * DIFFERENCES: Sigue el mismo patron que los otros repositorios del proyecto
 * (UserRepository, ProductRepository, etc.) — interfaz que extiende JpaRepository.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
