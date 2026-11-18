package com.example.java_mind_agent.models.hardware;

import java.lang.management.ManagementFactory;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Lê a RAM pelo JDK e a GPU pelo sistema operacional. A RAM é lida a cada chamada (a livre muda o tempo todo); a GPU
 * não muda enquanto o app roda, então é descoberta uma vez só, na primeira chamada.
 */
@Component
class SystemHardwareDetector implements HardwareDetector {

	private static final double GIB = 1024.0 * 1024.0 * 1024.0;

	private volatile @Nullable Gpu gpu;

	@Override
	public HardwareInfo detect() {
		double total = 0;
		double free = 0;
		if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
			total = os.getTotalMemorySize() / GIB;
			free = os.getFreeMemorySize() / GIB;
		}
		Gpu found = gpu();
		return new HardwareInfo(round(total), round(free), found.name(), !found.accelerated());
	}

	private Gpu gpu() {
		Gpu cached = gpu;
		if (cached == null) {
			String name = GpuProbe.primary(GpuProbe.detectNames());
			cached = new Gpu(name, GpuProbe.isAccelerated(name));
			gpu = cached;
		}
		return cached;
	}

	private static double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private record Gpu(@Nullable String name, boolean accelerated) {
	}
}
