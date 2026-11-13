package com.example.java_mind_agent.models;

import org.jspecify.annotations.Nullable;

/**
 * Decide se um modelo cabe nesta máquina: a RAM que ele pede tem de caber na RAM total menos uma reserva
 * (ver {@link ModelsProperties.Capacity}).
 *
 * @param overheadFactor fator sobre o tamanho em disco para estimar a RAM necessária (pesos + cache de contexto)
 * @param reservedRamGb  RAM em GiB deixada para o sistema e os outros programas
 */
record TierFitPolicy(double overheadFactor, double reservedRamGb) {

	TierFitPolicy(ModelsProperties.Capacity capacity) {
		this(capacity.overheadFactor(), capacity.reservedRamGb());
	}

	/**
	 * RAM de que o modelo precisa: o maior entre o mínimo configurado e o tamanho real em disco vezes o fator
	 * (o real só é conhecido depois de instalado).
	 */
	double requiredRamGb(double minRamGb, @Nullable Double sizeOnDiskGb) {
		return sizeOnDiskGb == null ? minRamGb : Math.max(minRamGb, sizeOnDiskGb * overheadFactor);
	}

	/** RAM que sobra para o modelo depois da reserva. */
	double usableRamGb(HardwareInfo hardware) {
		return Math.max(0, hardware.totalRamGb() - reservedRamGb);
	}

	boolean fits(double requiredRamGb, HardwareInfo hardware) {
		return requiredRamGb <= usableRamGb(hardware);
	}
}
