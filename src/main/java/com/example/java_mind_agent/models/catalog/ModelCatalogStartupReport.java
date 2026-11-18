package com.example.java_mind_agent.models.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.java_mind_agent.models.hardware.HardwareInfo;
import com.example.java_mind_agent.models.ModelsProperties;

/**
 * Ao subir, escreve no log o hardware e quais tiers não podem ser usados nesta máquina e por quê, para o problema
 * aparecer na hora e não só quando alguém escolher o tier. Roda depois de o app estar no ar e nunca derruba a subida.
 */
@Component
@ConditionalOnProperty(prefix = "app.models", name = "startup-report", havingValue = "true", matchIfMissing = true)
class ModelCatalogStartupReport {

	private static final Logger log = LoggerFactory.getLogger(ModelCatalogStartupReport.class);

	private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

	private final ModelCatalogService catalog;

	private final ModelsProperties properties;

	ModelCatalogStartupReport(ModelCatalogService catalog, ModelsProperties properties) {
		this.catalog = catalog;
		this.properties = properties;
	}

	@EventListener(ApplicationReadyEvent.class)
	void report() {
		try {
			for (Line line : describe(catalog.catalog(), properties.capacity().reservedRamGb())) {
				if (line.warning()) {
					log.warn(line.text());
				}
				else {
					log.info(line.text());
				}
			}
		}
		catch (RuntimeException e) {
			log.warn("Não consegui montar o relatório de modelos: {}", e.getMessage());
		}
	}

	record Line(boolean warning, String text) {
	}

	static List<Line> describe(ModelCatalog catalog, double reservedRamGb) {
		List<Line> lines = new ArrayList<>();
		HardwareInfo hw = catalog.hardware();
		lines.add(new Line(false, "Hardware: %s GB de RAM (%s GB livres agora), GPU: %s — %s".formatted(
				gb(hw.totalRamGb()), gb(hw.freeRamGb()), hw.gpu() != null ? hw.gpu() : "não identificada",
				hw.cpuOnly() ? "sem aceleração, a inferência roda na CPU" : "com aceleração")));
		double usable = Math.max(0, hw.totalRamGb() - reservedRamGb);
		List<ModelInfo> all = new ArrayList<>(catalog.tiers());
		all.add(catalog.embedding());
		for (ModelInfo model : all) {
			String name = "%s (%s)".formatted(model.label(), model.ollamaModel());
			ModelInfo.UnavailableReason reason = model.unavailableReason();
			// Não caber é um fato da máquina, vale mesmo com o Ollama desligado (que é como o app costuma subir).
			if (!model.fits() || reason == ModelInfo.UnavailableReason.DOES_NOT_FIT) {
				lines.add(new Line(true, ("%s: INVIÁVEL nesta máquina — pede ~%s GB e há %s GB utilizáveis "
						+ "(RAM total %s GB, reserva %s GB). Aponte base-url para outro Ollama para usá-lo.")
					.formatted(name, gb(model.minRamGb()), gb(usable), gb(hw.totalRamGb()), gb(reservedRamGb))));
			}
			else if (reason == null) {
				lines.add(new Line(false, name + ": disponível" + (model.remoteHost() != null
						? ", no Ollama " + model.remoteHost() : "")));
			}
			else if (reason == ModelInfo.UnavailableReason.HOST_UNREACHABLE) {
				lines.add(new Line(true, name + ": Ollama fora do ar ("
						+ (model.remoteHost() != null ? model.remoteHost() : "host padrão") + ")"));
			}
			else {
				lines.add(new Line(false, name + ": ainda não instalado (ollama pull " + model.ollamaModel() + ")"));
			}
		}
		return lines;
	}

	private static String gb(double value) {
		return String.format(PT_BR, "%.1f", value);
	}
}
