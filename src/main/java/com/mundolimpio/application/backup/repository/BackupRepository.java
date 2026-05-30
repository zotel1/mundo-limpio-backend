package com.mundolimpio.application.backup.repository;

import com.mundolimpio.application.backup.domain.Backup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * WHAT: Repositorio Spring Data JPA para la entidad Backup.
 * WHY: Proporciona CRUD basico + metodo findAllByOrderByCreatedAtDesc
 *      para listar backups del mas reciente al mas antiguo (BKP-002).
 */
@Repository
public interface BackupRepository extends JpaRepository<Backup, Long> {

    /**
     * Retorna todos los backups ordenados por createdAt descendente.
     * Equivalente SQL: SELECT * FROM backups ORDER BY created_at DESC
     */
    List<Backup> findAllByOrderByCreatedAtDesc();
}
