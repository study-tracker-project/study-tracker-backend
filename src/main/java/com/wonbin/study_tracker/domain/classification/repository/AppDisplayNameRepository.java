package com.wonbin.study_tracker.domain.classification.repository;

import com.wonbin.study_tracker.domain.classification.entity.AppDisplayName;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppDisplayNameRepository extends JpaRepository<AppDisplayName, String> {
}
