package com.example.java_mind_agent.common;

import java.time.Instant;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Configuração chave/valor persistida (tier padrão, versão padrão do Java, idioma...). */
@Entity
@Table(name = "app_setting")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppSetting {

	@Id
	@Column(name = "setting_key", length = 100)
	private String key;

	@Column(name = "setting_value", nullable = false)
	private String value;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	public AppSetting(String key, String value) {
		this.key = key;
		this.value = value;
	}
}
