package com.example.java_mind_agent.support;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code api/openapi.yaml} carregado para os testes: o documento inteiro como árvore JSON e validadores de schema
 * (Draft 2020-12, o mesmo dialeto do OpenAPI 3.1). Serve para provar que o que um controller devolve cumpre o contrato
 * que o front vai ler.
 */
public final class OpenApiContract {

	private static final Path CONTRATO = Path.of("api", "openapi.yaml");

	private static final String SCHEMAS = "#/components/schemas/";

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	/** Formatos (uuid, date-time, uri...) passam a valer como regra, não só como anotação. */
	private static final SchemaRegistry VALIDADORES = SchemaRegistry.withDefaultDialect(
			SpecificationVersion.DRAFT_2020_12,
			builder -> builder.schemaRegistryConfig(
					SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build()));

	private static final JsonNode DOCUMENTO = carrega();

	private OpenApiContract() {
	}

	/** O contrato inteiro; chaves duplicadas no YAML são erro, não "vale a última". */
	public static JsonNode documento() {
		return DOCUMENTO;
	}

	/**
	 * Monta um schema que aponta para um componente e carrega {@code components} junto, para os {@code $ref} internos
	 * resolverem.
	 */
	public static Schema schema(String componente) {
		ObjectNode raiz = MAPPER.createObjectNode();
		raiz.put("$ref", SCHEMAS + componente);
		raiz.set("components", DOCUMENTO.get("components"));
		return VALIDADORES.getSchema(raiz);
	}

	/** Valida um JSON (texto) contra um componente do contrato; lista vazia = cumpre. */
	public static List<Error> valida(String componente, String json) {
		return schema(componente).validate(MAPPER.readTree(json));
	}

	public static JsonNode le(String json) {
		return MAPPER.readTree(json);
	}

	private static JsonNode carrega() {
		LoaderOptions opcoes = new LoaderOptions();
		opcoes.setAllowDuplicateKeys(false);
		try (Reader leitor = Files.newBufferedReader(CONTRATO, StandardCharsets.UTF_8)) {
			return MAPPER.valueToTree(new Yaml(new SafeConstructor(opcoes)).load(leitor));
		}
		catch (IOException e) {
			throw new UncheckedIOException("Não consegui ler " + CONTRATO, e);
		}
	}
}
