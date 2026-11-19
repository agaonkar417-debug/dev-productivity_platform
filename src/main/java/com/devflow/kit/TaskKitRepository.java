package com.devflow.kit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskKitRepository extends JpaRepository<TaskKit, Long> {

    Optional<TaskKit> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<TaskKit> findAllByOrderByBuiltInDescNameAsc();
}
