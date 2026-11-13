package com.example.java_mind_agent.models;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Descobre o nome das placas de vídeo perguntando ao sistema operacional (comandos só de leitura) e diz se alguma
 * serve para acelerar o Ollama. As funções de interpretação são puras para poderem ser testadas sem a máquina.
 */
final class GpuProbe {

	/**
	 * Placas que o Ollama usa para acelerar: NVIDIA, Radeon discretas (RX/PRO/Instinct) e Apple Silicon. Intel (UHD,
	 * Iris, Arc) e os gráficos integrados da AMD ("Radeon Graphics", Vega) ficam de fora: na prática a inferência
	 * continua na CPU.
	 */
	private static final Pattern ACCELERATED = Pattern.compile(
			"nvidia|geforce|\\brtx\\b|\\bgtx\\b|quadro|tesla|radeon\\s+(rx|pro|instinct)|\\bapple\\s+m\\d", Pattern.CASE_INSENSITIVE);

	private static final Pattern VIRTUAL = Pattern.compile("microsoft basic|remote display|virtual|vmware|parsec",
			Pattern.CASE_INSENSITIVE);

	private static final long TIMEOUT_SECONDS = 5;

	private GpuProbe() {
	}

	static boolean isAccelerated(@Nullable String gpuName) {
		return gpuName != null && ACCELERATED.matcher(gpuName).find();
	}

	/** A GPU que melhor representa a máquina: a primeira com aceleração, senão a primeira que seja real. */
	static @Nullable String primary(List<String> names) {
		List<String> real = names.stream().map(String::strip).filter(n -> !n.isEmpty())
			.filter(n -> !VIRTUAL.matcher(n).find()).toList();
		return real.stream().filter(GpuProbe::isAccelerated).findFirst().or(() -> real.stream().findFirst())
			.orElse(null);
	}

	/** Linhas "Chipset Model: Apple M2" da saída do {@code system_profiler} do macOS. */
	static List<String> parseMacProfiler(String output) {
		List<String> names = new ArrayList<>();
		for (String line : output.split("\\R")) {
			String trimmed = line.strip();
			if (trimmed.startsWith("Chipset Model:")) {
				names.add(trimmed.substring("Chipset Model:".length()).strip());
			}
		}
		return names;
	}

	/**
	 * Linhas do {@code lspci} que descrevem placa de vídeo ({@code 00:02.0 VGA compatible controller: Intel ... (rev
	 * 07)}); devolve só o nome, sem o endereço, a classe e o "(rev xx)" do fim.
	 */
	static List<String> parseLspci(String output) {
		List<String> names = new ArrayList<>();
		for (String line : output.split("\\R")) {
			String lower = line.toLowerCase(Locale.ROOT);
			if (lower.contains("vga compatible") || lower.contains("3d controller") || lower.contains("display controller")) {
				int cut = line.indexOf(": ");
				String name = (cut >= 0 ? line.substring(cut + 2) : line).strip();
				names.add(name.replaceAll("\\s*\\(rev [0-9a-fA-F]+\\)$", ""));
			}
		}
		return names;
	}

	/** Nomes das placas de vídeo desta máquina; lista vazia se não deu para descobrir. */
	static List<String> detectNames() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("win")) {
			return lines(run(List.of("powershell", "-NoProfile", "-NonInteractive", "-Command",
					"Get-CimInstance Win32_VideoController | Select-Object -ExpandProperty Name")));
		}
		if (os.contains("mac")) {
			return parseMacProfiler(run(List.of("system_profiler", "SPDisplaysDataType")));
		}
		List<String> nvidia = lines(run(List.of("nvidia-smi", "--query-gpu=name", "--format=csv,noheader")));
		return nvidia.isEmpty() ? parseLspci(run(List.of("lspci"))) : nvidia;
	}

	private static List<String> lines(String output) {
		return output.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
	}

	/** Roda o comando com limite de tempo; qualquer falha (comando inexistente, demora) vira saída vazia. */
	private static String run(List<String> command) {
		Process process = null;
		try {
			process = new ProcessBuilder(command).redirectErrorStream(true).start();
			process.getOutputStream().close();
			if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return "";
			}
			return process.exitValue() == 0 ? new String(process.getInputStream().readAllBytes(), outputCharset()) : "";
		}
		catch (IOException e) {
			return "";
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return "";
		}
		finally {
			if (process != null && process.isAlive()) {
				process.destroyForcibly();
			}
		}
	}

	private static Charset outputCharset() {
		String name = System.getProperty("native.encoding");
		try {
			return name != null ? Charset.forName(name) : StandardCharsets.UTF_8;
		}
		catch (RuntimeException e) {
			return StandardCharsets.UTF_8;
		}
	}
}
