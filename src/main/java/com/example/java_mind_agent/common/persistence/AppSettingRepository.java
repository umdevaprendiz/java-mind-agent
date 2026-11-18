package com.example.java_mind_agent.common.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
