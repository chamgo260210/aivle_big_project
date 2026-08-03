package com.aivle.backend.taskrun.repository;

import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRunRepository extends JpaRepository<TaskRun, String> {
    @Query("select r from TaskRun r join fetch r.project p where r.id=:id and p.id=:projectId and p.owner.id=:ownerId and p.deletedAt is null")
    Optional<TaskRun> findOwned(@Param("ownerId") Long ownerId, @Param("projectId") Long projectId, @Param("id") String id);

    Optional<TaskRun> findByProjectIdAndIdempotencyScopeAndIdempotencyKey(Long projectId, String idempotencyScope, String idempotencyKey);
    Optional<TaskRun> findFirstByProjectIdAndTaskTypeAndSubjectTypeAndSubjectIdAndInputHashAndStateIn(
        Long projectId, com.aivle.backend.taskrun.domain.TaskType taskType, String subjectType,
        String subjectId, String inputHash, List<TaskRunState> states);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TaskRun r join fetch r.project where r.id=:id")
    Optional<TaskRun> findLocked(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TaskRun r join fetch r.project where r.state in :states order by r.createdAt, r.id")
    List<TaskRun> findClaimable(@Param("states") List<TaskRunState> states, Pageable pageable);

    @Query("select r from TaskRun r where r.state=:state and r.updatedAt<:cutoff")
    List<TaskRun> findStale(@Param("state") TaskRunState state, @Param("cutoff") LocalDateTime cutoff);

    @Query("select r from TaskRun r join fetch r.project p where r.deletedAt is null order by r.updatedAt desc, r.id desc")
    List<TaskRun> findRecentForAdmin(Pageable pageable);

    long countByStateAndDeletedAtIsNull(TaskRunState state);
}
