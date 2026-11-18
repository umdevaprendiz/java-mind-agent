package com.example.java_mind_agent.knowledge.version;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * A tabela de versões do Java, da 1.0 à mais nova. Os dados ficam em {@code knowledge/java-versions.json} (data, não
 * código): quem acrescenta o próximo JDK edita o arquivo, e a F14 passa a fazê-lo sozinha. A estrutura é validada ao
 * subir, para um arquivo estragado falhar na hora e não numa resposta.
 */
@Component
public class JavaVersionRegistry {

	static final String RECURSO = "knowledge/java-versions.json";

	private final List<JavaRelease> releases;

	private final Map<Integer, JavaRelease> porVersao;

	@Autowired
	public JavaVersionRegistry() {
		this(carrega());
	}

	/** Para testes e para a F14: monta o registro a partir de uma lista, com as mesmas validações. */
	JavaVersionRegistry(List<JavaRelease> releases) {
		valida(releases);
		this.releases = List.copyOf(releases);
		this.porVersao = this.releases.stream().collect(Collectors.toUnmodifiableMap(JavaRelease::version, Function.identity()));
	}

	/** Todas as versões, da mais antiga para a mais nova. */
	public List<JavaRelease> all() {
		return releases;
	}

	/** As mesmas versões no formato da API. */
	public List<JavaVersionInfo> infos() {
		return releases.stream().map(JavaRelease::info).toList();
	}

	public Optional<JavaRelease> find(int version) {
		return Optional.ofNullable(porVersao.get(version));
	}

	public boolean contains(int version) {
		return porVersao.containsKey(version);
	}

	/** A versão mais nova do registro (LTS ou não). */
	public JavaRelease latest() {
		return releases.get(releases.size() - 1);
	}

	/** A LTS mais nova: a versão padrão quando nada indica outra. */
	public JavaRelease latestLts() {
		for (int i = releases.size() - 1; i >= 0; i--) {
			if (releases.get(i).lts()) {
				return releases.get(i);
			}
		}
		throw new IllegalStateException("O registro não tem nenhuma versão LTS");
	}

	private static void valida(List<JavaRelease> releases) {
		if (releases == null || releases.isEmpty()) {
			throw new IllegalArgumentException("O registro de versões do Java está vazio");
		}
		for (int i = 1; i < releases.size(); i++) {
			JavaRelease anterior = releases.get(i - 1);
			JavaRelease atual = releases.get(i);
			if (atual.version() <= anterior.version()) {
				throw new IllegalArgumentException("As versões precisam estar em ordem crescente e sem repetição: "
						+ anterior.version() + " seguida de " + atual.version());
			}
			if (atual.releaseYear() < anterior.releaseYear()) {
				throw new IllegalArgumentException("O Java " + atual.version() + " (" + atual.releaseYear()
						+ ") não pode ser mais antigo que o " + anterior.version() + " (" + anterior.releaseYear() + ")");
			}
		}
		if (releases.stream().noneMatch(JavaRelease::lts)) {
			throw new IllegalArgumentException("O registro precisa de pelo menos uma versão LTS");
		}
	}

	private static List<JavaRelease> carrega() {
		try (InputStream in = new ClassPathResource(RECURSO).getInputStream()) {
			Arquivo arquivo = JsonMapper.builder().build().readValue(in, Arquivo.class);
			return new ArrayList<>(arquivo.releases());
		}
		catch (IOException e) {
			throw new UncheckedIOException("Não consegui ler " + RECURSO, e);
		}
	}

	/** O formato do arquivo; {@code fonte} só documenta de onde vieram os dados. */
	private record Arquivo(String fonte, List<JavaRelease> releases) {
	}
}
