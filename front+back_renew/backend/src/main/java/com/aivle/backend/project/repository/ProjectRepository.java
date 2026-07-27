package com.aivle.backend.project.repository;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findAllByOwnerIdAndDeletedAtIsNull(Long ownerId);
    List<Project> findAllByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(Long ownerId);
    Optional<Project> findByIdAndDeletedAtIsNull(Long id);
    Optional<Project> findByIdAndOwnerIdAndDeletedAtIsNull(Long id, Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select p
        from Project p
        join fetch p.owner
        where p.id = :projectId
          and p.deletedAt is null
        """)
    Optional<Project> findByIdForUpdate(@Param("projectId") Long projectId);
}
