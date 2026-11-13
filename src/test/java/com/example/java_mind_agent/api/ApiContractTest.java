package com.example.java_mind_agent.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.example.java_mind_agent.chat.MessageRole;
import com.example.java_mind_agent.knowledge.CorpusSourceKind;
import com.example.java_mind_agent.knowledge.CorpusSourceStatus;
import com.example.java_mind_agent.pdf.AnswerStatus;
import com.example.java_mind_agent.pdf.JobStatus;
import com.example.java_mind_agent.pdf.PdfStatus;
import com.example.java_mind_agent.pdf.QuestionType;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Protege o contrato da API (F04): {@code api/openapi.yaml} é a fonte da verdade para o front e para os controllers,
 * então ele não pode ter referência quebrada, evento fora da união, exemplo inválido nem enum diferente do código.
 * Não sobe Spring nem Docker; roda em milissegundos.
 */
class ApiContractTest {

	private static final Path CONTRATO = Path.of("api", "openapi.yaml");
	private static final String SCHEMAS = "#/components/schemas/";
	private static final List<String> METODOS = List.of("get", "post", "put", "patch", "delete");
	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final Set<String> CAMPOS_DE_OPERACAO = Set.of("tags", "summary", "description", "operationId",
			"parameters", "requestBody", "responses", "security", "deprecated");

	private static final Set<String> PALAVRAS_CHAVE = Set.of("type", "format", "enum", "const", "title",
			"description", "default", "examples", "required", "properties", "items", "additionalProperties", "allOf",
			"oneOf", "anyOf", "not", "$ref", "discriminator", "minimum", "maximum", "exclusiveMinimum",
			"exclusiveMaximum", "minLength", "maxLength", "pattern", "minItems", "maxItems", "uniqueItems",
			"minProperties", "maxProperties", "deprecated", "readOnly", "writeOnly", "contentMediaType",
			"contentEncoding");

	/** Formatos (uuid, date-time, uri...) passam a valer como regra, não só como anotação. */
	private static final SchemaRegistry VALIDADORES = SchemaRegistry.withDefaultDialect(
			SpecificationVersion.DRAFT_2020_12,
			builder -> builder.schemaRegistryConfig(
					SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build()));

	private static JsonNode contrato;

	@BeforeAll
	static void carregaOContrato() throws IOException {
		LoaderOptions opcoes = new LoaderOptions();
		opcoes.setAllowDuplicateKeys(false);
		try (Reader leitor = Files.newBufferedReader(CONTRATO, StandardCharsets.UTF_8)) {
			contrato = MAPPER.valueToTree(new Yaml(new SafeConstructor(opcoes)).load(leitor));
		}
	}

	// ------------------------------------------------------------------ estrutura

	@Test
	void eUmContratoOpenApi31SemAutenticacao() {
		assertThat(contrato.path("openapi").asString("")).startsWith("3.1");
		assertThat(contrato.path("info").path("title").asString("")).isNotBlank();
		assertThat(contrato.path("info").path("version").asString("")).isNotBlank();
		assertThat(contrato.path("servers").size()).isPositive();
		assertThat(contrato.path("security").isArray()).isTrue();
		assertThat(contrato.path("security").size()).as("app local, sem login").isZero();
		assertThat(contrato.at("/components/securitySchemes").isMissingNode()).isTrue();
	}

	@Test
	void todasAsReferenciasInternasResolvem() {
		List<String> quebradas = new ArrayList<>();
		percorre(contrato, no -> {
			JsonNode ref = no.get("$ref");
			if (no.isObject() && ref != null && ref.isString()) {
				String alvo = ref.stringValue();
				if (!alvo.startsWith("#/") || contrato.at(alvo.substring(1)).isMissingNode()) {
					quebradas.add(alvo);
				}
			}
		});

		assertThat(quebradas).isEmpty();
	}

	@Test
	void todaOperacaoTemIdUnicoTagResumoERespostaDeSucesso() {
		Set<String> tagsDeclaradas = new HashSet<>();
		contrato.path("tags").forEach(tag -> tagsDeclaradas.add(tag.path("name").asString("")));
		Set<String> ids = new HashSet<>();
		List<String> problemas = new ArrayList<>();

		operacoes((rota, metodo, op) -> {
			String onde = metodo.toUpperCase() + " " + rota;
			String id = op.path("operationId").asString("");
			if (!id.matches("[a-z][A-Za-z0-9]*")) {
				problemas.add(onde + ": operationId inválido '" + id + "'");
			} else if (!ids.add(id)) {
				problemas.add(onde + ": operationId repetido '" + id + "'");
			}
			if (op.path("summary").asString("").isBlank()) {
				problemas.add(onde + ": sem summary");
			}
			if (op.path("tags").isEmpty()) {
				problemas.add(onde + ": sem tag");
			}
			op.path("tags").forEach(tag -> {
				if (!tagsDeclaradas.contains(tag.asString(""))) {
					problemas.add(onde + ": tag não declarada '" + tag.asString("") + "'");
				}
			});
			boolean temSucesso = op.path("responses").propertyNames().stream().anyMatch(codigo -> codigo.startsWith("2"));
			if (!temSucesso) {
				problemas.add(onde + ": sem resposta 2xx");
			}
		});

		assertThat(problemas).isEmpty();
	}

	@Test
	void cobreOsEndpointsDasFeaturesPlanejadas() {
		Set<String> existentes = new LinkedHashSet<>();
		operacoes((rota, metodo, op) -> existentes.add(metodo.toUpperCase() + " " + rota));

		assertThat(existentes).contains(
				"POST /api/chat",
				"GET /api/conversations",
				"POST /api/conversations",
				"PATCH /api/conversations/{conversationId}",
				"DELETE /api/conversations/{conversationId}/messages",
				"POST /api/conversations/{conversationId}/attachments",
				"GET /api/models",
				"POST /api/models/{modelId}/pull",
				"GET /api/settings",
				"POST /api/pdfs",
				"PATCH /api/pdfs/{pdfId}/questions/{questionId}",
				"GET /api/pdfs/{pdfId}/events",
				"POST /api/pdfs/{pdfId}/jobs",
				"GET /api/pdfs/{pdfId}/export",
				"GET /api/jobs/{jobId}/events",
				"POST /api/jobs/{jobId}/cancel",
				"GET /api/knowledge/versions",
				"GET /api/knowledge/search",
				"POST /api/sandbox/run",
				"GET /api/system/status");
	}

	@Test
	void parametrosDeCaminhoDeclaradosBatemComOTemplate() {
		Pattern placeholder = Pattern.compile("\\{(\\w+)}");
		List<String> problemas = new ArrayList<>();

		contrato.path("paths").properties().forEach(entrada -> {
			String rota = entrada.getKey();
			Set<String> noTemplate = new LinkedHashSet<>();
			Matcher m = placeholder.matcher(rota);
			while (m.find()) {
				noTemplate.add(m.group(1));
			}
			for (String metodo : METODOS) {
				JsonNode op = entrada.getValue().get(metodo);
				if (op == null) {
					continue;
				}
				Set<String> declarados = new LinkedHashSet<>();
				declarados.addAll(parametrosDeCaminho(entrada.getValue().path("parameters")));
				declarados.addAll(parametrosDeCaminho(op.path("parameters")));
				if (!declarados.equals(noTemplate)) {
					problemas.add(metodo.toUpperCase() + " " + rota + ": template " + noTemplate + " x declarados "
							+ declarados);
				}
			}
		});

		assertThat(problemas).isEmpty();
	}

	@Test
	void errosHttpSaoSempreProblemJson() {
		List<String> problemas = new ArrayList<>();

		operacoes((rota, metodo, op) -> op.path("responses").properties().forEach(resposta -> {
			String codigo = resposta.getKey();
			if (!codigo.startsWith("4") && !codigo.startsWith("5")) {
				return;
			}
			JsonNode corpo = resolve(resposta.getValue());
			JsonNode schema = corpo.at("/content/application~1problem+json/schema");
			if (schema.isMissingNode() || !(SCHEMAS + "ProblemDetail").equals(schema.path("$ref").asString(""))) {
				problemas.add(metodo.toUpperCase() + " " + rota + " " + codigo + ": não usa ProblemDetail");
			}
		}));

		assertThat(problemas).isEmpty();
	}

	/**
	 * Pega erro de YAML que ainda é YAML válido: uma vírgula ou {@code : } em texto sem aspas dentro de {@code { }}
	 * vira uma chave falsa (ex.: {@code description: Saída completa, truncada.} cria a chave "truncada.").
	 */
	@Test
	void schemasEOperacoesSoUsamPalavrasChaveConhecidas() {
		List<String> problemas = new ArrayList<>();

		contrato.at("/components/schemas").properties()
				.forEach(entrada -> verificaSchema(entrada.getKey(), entrada.getValue(), problemas));
		for (String raiz : List.of("/paths", "/components/parameters", "/components/responses")) {
			procuraSchemasEmLinha(raiz, contrato.at(raiz), problemas);
		}
		operacoes((rota, metodo, op) -> op.propertyNames().forEach(chave -> {
			if (!CAMPOS_DE_OPERACAO.contains(chave)) {
				problemas.add(metodo.toUpperCase() + " " + rota + ": campo desconhecido '" + chave + "'");
			}
		}));

		assertThat(problemas).isEmpty();
	}

	@Test
	void propriedadesObrigatoriasExistemNoSchema() {
		List<String> problemas = new ArrayList<>();

		percorre(contrato.at("/components/schemas"), no -> {
			if (no.isObject() && no.path("required").isArray() && no.path("properties").isObject()) {
				no.path("required").forEach(nome -> {
					if (!no.path("properties").has(nome.asString(""))) {
						problemas.add("required '" + nome.asString("") + "' não existe em properties");
					}
				});
			}
		});

		assertThat(problemas).isEmpty();
	}

	// ------------------------------------------------------------- eventos de SSE

	@Test
	void cadaUniaoDeEventosTemDiscriminadorConsistenteComSeusMembros() {
		Map<String, JsonNode> unioes = unioesDeEventos();
		assertThat(unioes.keySet()).containsExactlyInAnyOrder(
				"ChatStreamEvent", "ModelPullStreamEvent", "PdfStreamEvent", "JobStreamEvent");
		List<String> problemas = new ArrayList<>();

		unioes.forEach((nome, uniao) -> {
			Set<String> membros = new LinkedHashSet<>();
			uniao.path("oneOf").forEach(m -> membros.add(m.path("$ref").asString("")));
			JsonNode discriminador = uniao.path("discriminator");
			if (!"type".equals(discriminador.path("propertyName").asString(""))) {
				problemas.add(nome + ": discriminador deve ser 'type'");
			}
			Set<String> mapeados = new LinkedHashSet<>();
			discriminador.path("mapping").properties().forEach(par -> {
				mapeados.add(par.getValue().asString(""));
				JsonNode membro = contrato.at("/components/schemas/" + par.getValue().asString("").substring(SCHEMAS.length()));
				String constante = membro.at("/properties/type/const").asString("");
				if (!par.getKey().equals(constante)) {
					problemas.add(nome + ": mapping '" + par.getKey() + "' aponta para evento com type '" + constante + "'");
				}
			});
			if (!membros.equals(mapeados)) {
				problemas.add(nome + ": oneOf " + membros + " diferente do mapping " + mapeados);
			}
			if (!membros.contains(SCHEMAS + "ErrorEvent")) {
				problemas.add(nome + ": todo stream pode terminar em erro, falta ErrorEvent");
			}
		});

		assertThat(problemas).isEmpty();
	}

	@Test
	void chatCobreTodosOsTiposDeEventoPrevistos() {
		Set<String> tipos = new LinkedHashSet<>();
		unioesDeEventos().get("ChatStreamEvent").at("/discriminator/mapping").propertyNames().forEach(tipos::add);

		assertThat(tipos).containsExactlyInAnyOrder("start", "status", "thinking", "token", "tool_call", "tool_result",
				"source", "usage", "done", "error");
	}

	@Test
	void todoEventoTemExemploQueCasaComExatamenteUmMembroDeCadaUniaoQuePertence() {
		List<String> problemas = new ArrayList<>();

		unioesDeEventos().forEach((nomeUniao, uniao) -> {
			Schema validador = schema(nomeUniao);
			uniao.path("oneOf").forEach(m -> {
				String membro = m.path("$ref").asString("").substring(SCHEMAS.length());
				JsonNode exemplos = contrato.at("/components/schemas/" + membro + "/examples");
				if (!exemplos.isArray() || exemplos.isEmpty()) {
					problemas.add(membro + ": evento sem exemplo");
					return;
				}
				exemplos.forEach(exemplo -> {
					List<Error> erros = validador.validate(exemplo);
					if (!erros.isEmpty()) {
						problemas.add(nomeUniao + " rejeita o exemplo de " + membro + ": " + erros);
					}
				});
			});
		});

		assertThat(problemas).isEmpty();
	}

	// ----------------------------------------------------------- schemas/exemplos

	@Test
	void todoExemploDeSchemaValidaContraEleMesmo() {
		List<String> problemas = new ArrayList<>();
		int validados = 0;

		for (Map.Entry<String, JsonNode> entrada : contrato.at("/components/schemas").properties()) {
			JsonNode exemplos = entrada.getValue().path("examples");
			if (!exemplos.isArray()) {
				continue;
			}
			Schema validador = schema(entrada.getKey());
			for (JsonNode exemplo : exemplos) {
				validados++;
				List<Error> erros = validador.validate(exemplo);
				if (!erros.isEmpty()) {
					problemas.add(entrada.getKey() + ": " + erros);
				}
			}
		}

		assertThat(problemas).isEmpty();
		assertThat(validados).as("exemplos validados").isGreaterThan(50);
	}

	@Test
	void validadorRejeitaExemploQuebrado() {
		Schema inicio = schema("StartEvent");
		ObjectNode quebrado = MAPPER.createObjectNode();
		quebrado.put("type", "start");

		assertThat(inicio.validate(quebrado)).as("faltam conversationId, userMessageId, tier e model").isNotEmpty();
	}

	@Test
	void enumsDoContratoEspelhamOsEnumsDoCodigo() {
		Map<String, Class<? extends Enum<?>>> esperados = new LinkedHashMap<>();
		esperados.put("MessageRole", MessageRole.class);
		esperados.put("PdfStatus", PdfStatus.class);
		esperados.put("QuestionType", QuestionType.class);
		esperados.put("AnswerStatus", AnswerStatus.class);
		esperados.put("JobStatus", JobStatus.class);
		esperados.put("CorpusSourceKind", CorpusSourceKind.class);
		esperados.put("CorpusSourceStatus", CorpusSourceStatus.class);

		esperados.forEach((nome, tipo) -> {
			List<String> doContrato = new ArrayList<>();
			contrato.at("/components/schemas/" + nome + "/enum").forEach(v -> doContrato.add(v.asString("")));
			List<String> doCodigo = Arrays.stream(tipo.getEnumConstants()).map(Enum::name).toList();

			assertThat(doContrato).as(nome).containsExactlyInAnyOrderElementsOf(doCodigo);
		});
	}

	@Test
	void tiersSaoOsQuatroNiveisDoSeletorEmOrdemDeCapacidade() {
		List<String> tiers = new ArrayList<>();
		contrato.at("/components/schemas/Tier/enum").forEach(v -> tiers.add(v.asString("")));

		assertThat(tiers).containsExactly("low", "medium", "strong", "extra");
	}

	// ------------------------------------------------------------------- auxiliares

	private interface Visitante {
		void visita(String rota, String metodo, JsonNode operacao);
	}

	private static void operacoes(Visitante visitante) {
		contrato.path("paths").properties().forEach(entrada -> {
			for (String metodo : METODOS) {
				JsonNode op = entrada.getValue().get(metodo);
				if (op != null) {
					visitante.visita(entrada.getKey(), metodo, op);
				}
			}
		});
	}

	private static void verificaSchema(String onde, JsonNode schema, List<String> problemas) {
		schema.propertyNames().forEach(chave -> {
			if (!PALAVRAS_CHAVE.contains(chave)) {
				problemas.add(onde + ": palavra-chave desconhecida '" + chave + "'");
			}
		});
		schema.path("properties").properties()
				.forEach(p -> verificaSchema(onde + "." + p.getKey(), p.getValue(), problemas));
		for (String combinador : List.of("allOf", "oneOf", "anyOf")) {
			int i = 0;
			for (JsonNode parte : schema.path(combinador)) {
				verificaSchema(onde + "/" + combinador + "[" + i++ + "]", parte, problemas);
			}
		}
		for (String filho : List.of("items", "additionalProperties", "not")) {
			JsonNode sub = schema.get(filho);
			if (sub != null && sub.isObject()) {
				verificaSchema(onde + "/" + filho, sub, problemas);
			}
		}
	}

	/** Acha os schemas escritos direto em parâmetros, corpos e respostas (a chave {@code schema}). */
	private static void procuraSchemasEmLinha(String onde, JsonNode no, List<String> problemas) {
		no.properties().forEach(entrada -> {
			if ("schema".equals(entrada.getKey()) && entrada.getValue().isObject()) {
				verificaSchema(onde + "/schema", entrada.getValue(), problemas);
			} else {
				procuraSchemasEmLinha(onde + "/" + entrada.getKey(), entrada.getValue(), problemas);
			}
		});
		if (no.isArray()) {
			no.forEach(filho -> procuraSchemasEmLinha(onde, filho, problemas));
		}
	}

	private static void percorre(JsonNode no, Consumer<JsonNode> acao) {
		acao.accept(no);
		no.values().forEach(filho -> percorre(filho, acao));
	}

	/** Segue um {@code $ref} para {@code #/components/...}; devolve o próprio nó se não for referência. */
	private static JsonNode resolve(JsonNode no) {
		JsonNode ref = no.get("$ref");
		return ref != null && ref.isString() ? contrato.at(ref.stringValue().substring(1)) : no;
	}

	private static Set<String> parametrosDeCaminho(JsonNode parametros) {
		Set<String> nomes = new LinkedHashSet<>();
		parametros.forEach(p -> {
			JsonNode param = resolve(p);
			if ("path".equals(param.path("in").asString(""))) {
				nomes.add(param.path("name").asString(""));
			}
		});
		return nomes;
	}

	private static Map<String, JsonNode> unioesDeEventos() {
		Map<String, JsonNode> unioes = new LinkedHashMap<>();
		contrato.at("/components/schemas").properties().forEach(entrada -> {
			if (entrada.getKey().endsWith("StreamEvent")) {
				unioes.put(entrada.getKey(), entrada.getValue());
			}
		});
		return unioes;
	}

	/**
	 * Monta um schema JSON (Draft 2020-12, o mesmo dialeto do OpenAPI 3.1) que aponta para um componente e carrega
	 * {@code components} junto, para os {@code $ref} internos resolverem.
	 */
	private static Schema schema(String componente) {
		ObjectNode raiz = MAPPER.createObjectNode();
		raiz.put("$ref", SCHEMAS + componente);
		raiz.set("components", contrato.get("components"));
		return VALIDADORES.getSchema(raiz);
	}
}
