package com.mundolimpio.application.backup.repository;

import com.mundolimpio.application.backup.domain.Backup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * WHAT: Repositorio Spring Data JPA para la entidad Backup.
 * WHY: Proporciona CRUD basico para gestion de backups.
 *      El ordenamiento se maneja via @PageableDefault en el controller.
 */
@Repository
public interface BackupRepository extends JpaRepository<Backup, Long> {
}
