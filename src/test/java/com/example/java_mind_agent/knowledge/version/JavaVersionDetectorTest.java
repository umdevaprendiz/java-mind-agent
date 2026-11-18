package com.example.java_mind_agent.knowledge.version;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * O detector de versão: o que a pessoa diz ("Java 8") e o que o código exige ({@code record} pede o 16). A tabela de
 * trechos cobre <b>todas</b> as regras: acrescentar uma regra sem um trecho que a exercite derruba o teste de cobertura.
 */
class JavaVersionDetectorTest {

	private final JavaVersionRegistry registry = new JavaVersionRegistry();

	private final JavaVersionDetector detector = new JavaVersionDetector(registry);

	/** Trecho, a regra que tem de disparar e a versão mínima que o trecho inteiro exige. */
	static Stream<Arguments> trechos() {
		return Stream.of(
				Arguments.of("List<String> nomes;", "generics", 5),
				Arguments.of("for (String s : nomes) { }", "enhanced-for", 5),
				Arguments.of("@Override\npublic String toString() { return \"x\"; }", "annotations", 5),
				Arguments.of("void f(String... xs) { }", "varargs", 5),
				Arguments.of("enum Cor { AZUL, VERDE }", "enum", 5),
				Arguments.of("import static java.lang.Math.max;", "static-import", 5),
				Arguments.of("ExecutorService ex = Executors.newFixedThreadPool(2);", "concurrent", 5),
				Arguments.of("Map<String, Integer> m = new HashMap<>();", "diamond", 7),
				Arguments.of("try (BufferedReader r = new BufferedReader(src)) { }", "try-with-resources", 7),
				Arguments.of("try { } catch (IOException | SQLException e) { }", "multi-catch", 7),
				Arguments.of("boolean ok = Files.exists(p);", "nio-file", 7),
				Arguments.of("int mil = 1_000_000;", "numeric-literals", 7),
				Arguments.of("boolean eq = Objects.equals(a, b);", "objects", 7),
				Arguments.of("Runnable r = () -> run();", "lambda", 8),
				Arguments.of("lista.forEach(System.out::println);", "method-reference", 8),
				Arguments.of("long n = lista.stream().count();", "streams", 8),
				Arguments.of("Optional<String> o = Optional.empty();", "optional", 8),
				Arguments.of("LocalDate hoje = LocalDate.now();", "java-time", 8),
				Arguments.of("interface A { default void f() { } }", "default-method", 8),
				Arguments.of("Function<Integer, Integer> f = null;", "functional", 8),
				Arguments.of("CompletableFuture<Void> f = null;", "completable-future", 8),
				Arguments.of("lista.forEach(x -> print(x));", "foreach-method", 8),
				Arguments.of("List l = List.of(1, 2);", "collection-factories", 9),
				Arguments.of("module app { requires java.sql; }", "modules", 9),
				Arguments.of("Stream s = x.takeWhile(v -> v < 3);", "stream-9", 9),
				Arguments.of("long pid = ProcessHandle.current().pid();", "process-api", 9),
				Arguments.of("try (recurso) { }", "try-existing-var", 9),
				Arguments.of("byte[] b = in.readAllBytes();", "io-9", 9),
				Arguments.of("var nome = \"Ana\";", "var", 10),
				Arguments.of("List c = List.copyOf(l);", "copy-of", 10),
				Arguments.of("BiFunction f = (var a, var b) -> a + b;", "var-lambda", 11),
				Arguments.of("boolean v = s.isBlank();", "string-11", 11),
				Arguments.of("HttpClient c = HttpClient.newHttpClient();", "http-client", 11),
				Arguments.of("Path p = Path.of(\"a.txt\");", "path-of", 11),
				Arguments.of("var p = Predicate.not(String::isBlank);", "predicate-not", 11),
				Arguments.of("var r = s.collect(Collectors.teeing(a, b, f));", "teeing", 12),
				Arguments.of("int d = switch (x) { case 1 -> 10; default -> 0; };", "switch-arrow", 14),
				Arguments.of("int v = switch (x) { case 1: yield 5; default: yield 0; };", "yield", 14),
				Arguments.of("String t = \"\"\"\n  oi\n  \"\"\";", "text-block", 15),
				Arguments.of("String t = \"%s\".formatted(x);", "string-15", 15),
				Arguments.of("record Ponto(int x, int y) { }", "record", 16),
				Arguments.of("if (o instanceof String s) { }", "instanceof-pattern", 16),
				Arguments.of("var l = s.stream().toList();", "to-list", 16),
				Arguments.of("s.mapMulti((x, c) -> c.accept(x));", "map-multi", 16),
				Arguments.of("sealed interface Forma permits Circulo { }", "sealed", 17),
				Arguments.of("RandomGenerator g = RandomGenerator.getDefault();", "random-generator", 17),
				Arguments.of("Thread t = Thread.ofVirtual().start(r);", "virtual-threads", 21),
				Arguments.of("if (o instanceof Ponto(int x, int y)) { }", "record-patterns", 21),
				Arguments.of("switch (o) { case Integer i -> f(i); default -> g(); }", "switch-patterns", 21),
				Arguments.of("SequencedCollection c = lista;", "sequenced", 21),
				Arguments.of("int v = Math.clamp(x, 0, 10);", "math-clamp", 21),
				Arguments.of("try { f(); } catch (Exception _) { }", "unnamed", 22),
				Arguments.of("Arena a = Arena.ofConfined();", "ffm", 22),
				Arguments.of("var r = s.gather(Gatherers.windowFixed(3));", "gatherers", 24),
				Arguments.of("var cf = ClassFile.of();", "classfile", 24),
				Arguments.of("void main() {\n    System.out.println(1);\n}", "instance-main", 25),
				Arguments.of("import module java.base;", "module-import", 25),
				Arguments.of("IO.println(\"oi\");", "io-println", 25),
				Arguments.of("ScopedValue<String> u = ScopedValue.newInstance();", "scoped-value", 25),
				Arguments.of("var v = HttpClient.Version.HTTP_3;", "http3", 26),
				Arguments.of("var s = StructuredTaskScope.open();", "structured-concurrency", 21),
				Arguments.of("StableValue<String> v = null;", "lazy-constants", 25),
				Arguments.of("if (x instanceof int i) { }", "primitive-patterns", 23),
				Arguments.of("VectorSpecies<Integer> sp = null;", "vector-api", 16));
	}

	@ParameterizedTest(name = "[{index}] {1} -> Java {2}")
	@MethodSource("trechos")
	void cadaRecursoDisparaASuaRegraEPedeAVersaoCerta(String codigo, String regra, int minimo) {
		VersionDetection detection = detector.detect(codigo);

		assertThat(detection.evidence()).extracting(VersionEvidence::rule).contains(regra);
		assertThat(detection.inferredMin()).contains(minimo);
	}

	@Test
	void aTabelaDeTrechosCobreTodasAsRegras() {
		Set<String> cobertas = new HashSet<>();
		trechos().forEach(args -> cobertas.add((String) args.get()[1]));
		Set<String> todas = new HashSet<>();
		JavaSyntaxRules.ALL.forEach(r -> todas.add(r.id()));

		assertThat(cobertas).containsExactlyInAnyOrderElementsOf(todas);
	}

	@Test
	void asRegrasSaoCoerentesComORegistro() {
		Set<String> ids = new HashSet<>();
		for (JavaSyntaxRule regra : JavaSyntaxRules.ALL) {
			assertThat(ids.add(regra.id())).as("id repetido: " + regra.id()).isTrue();
			assertThat(registry.contains(regra.since())).as(regra.id() + " aponta para uma versão fora do registro").isTrue();
			assertThat(regra.previewSince()).as(regra.id()).isLessThanOrEqualTo(regra.since());
			assertThat(regra.label()).as(regra.id()).isNotBlank();
		}
	}

	@Test
	void umRecordPedeNoMinimoOJava16() {
		// o critério de pronto da F10
		assertThat(detector.detect("record Point(int x){}").inferredMin()).hasValueSatisfying(v -> assertThat(v).isGreaterThanOrEqualTo(16));
	}

	// ---- o que NÃO deve contar

	@Test
	void codigoAntigoNaoExigeNada() {
		VersionDetection detection = detector.detect("""
				public static void main(String[] args) {
				    int total = 0;
				    for (int i = 0; i < 5; i++) {
				        if (i % 2 == 0) continue;
				        total += i;
				    }
				    System.out.println(total);
				}
				""");

		assertThat(detection.evidence()).isEmpty();
		assertThat(detection.inferredMin()).isEmpty();
	}

	@Test
	void palavrasDentroDeStringsEComentariosNaoContam() {
		assertThat(detector.detect("System.out.println(\"record Ponto(int x) { }\");").evidence()).isEmpty();
		assertThat(detector.detect("// record Ponto(int x) { }\nint a = 1;").evidence()).isEmpty();
		assertThat(detector.detect("/* var x = 1; */ int a = 1;").evidence()).isEmpty();
		assertThat(detector.detect("char c = '\"'; int a = 1;").evidence()).isEmpty();
	}

	@Test
	void aSetaDoSwitchNaoEUmLambda() {
		VersionDetection detection = detector.detect("int d = switch (x) { case 1 -> 10; default -> 0; };");

		assertThat(detection.evidence()).extracting(VersionEvidence::rule).contains("switch-arrow").doesNotContain("lambda");
		assertThat(detection.inferredMin()).contains(14);
	}

	@Test
	void collectorsToListEDoJava8ENaoDoJava16() {
		VersionDetection detection = detector.detect("List l = s.collect(Collectors.toList());");

		assertThat(detection.evidence()).extracting(VersionEvidence::rule).doesNotContain("to-list");
		assertThat(detection.inferredMin()).contains(8);
	}

	@Test
	void threadYieldNaoEOYieldDoSwitch() {
		assertThat(detector.detect("Thread.yield();").evidence()).isEmpty();
		assertThat(detector.detect("int yield = 3;").evidence()).isEmpty();
	}

	@Test
	void frasesSemCodigoNaoDisparamRegraNenhuma() {
		assertThat(detector.detect("O que é um record em Java?").evidence()).isEmpty();
		assertThat(detector.detect("Em UML, A -> B significa dependência").evidence()).isEmpty();
		assertThat(detector.detect("Qual a diferença entre var e final?").evidence()).isEmpty();
	}

	@Test
	void emProsaComCrasesSoOTrechoMarcadoViraCodigo() {
		VersionDetection fenced = detector.detect("Explique:\n```java\nrecord P(int x) { }\n```\nA -> B, obrigado");
		assertThat(fenced.inferredMin()).contains(16);
		assertThat(fenced.evidence()).extracting(VersionEvidence::rule).doesNotContain("lambda");

		VersionDetection inline = detector.detect("O que faz `var x = 10` aqui?");
		assertThat(inline.inferredMin()).contains(10);
	}

	@Test
	void umBlocoSemFechamentoAindaELido() {
		assertThat(detector.detect("```java\nrecord P(int x) { }").inferredMin()).contains(16);
	}

	@Test
	void recursosEmPreviewSaoMarcados() {
		VersionDetection detection = detector.detect("var s = StructuredTaskScope.open();");

		assertThat(detection.previewFeatures()).extracting(VersionEvidence::rule).containsExactly("structured-concurrency");
		assertThat(detector.detect("record P(int x) { }").previewFeatures()).isEmpty();
	}

	@Test
	void aEvidenciaVemDoMaisNovoParaOMaisAntigoEGuardaOTrecho() {
		VersionDetection detection = detector.detect("var l = s.stream().toList();");

		assertThat(detection.evidence()).extracting(VersionEvidence::minVersion).isSortedAccordingTo(java.util.Comparator.reverseOrder());
		assertThat(detection.evidence().get(0).sample()).isEqualTo(".toList()");
		assertThat(detection.evidence().get(0).rule()).isEqualTo("to-list");
	}

	@Test
	void requiringMoreThanListaOQueNaoExisteNaVersaoDada() {
		VersionDetection detection = detector.detect("record P(int x) { }\nvar a = 1;");

		assertThat(detection.requiringMoreThan(11)).extracting(VersionEvidence::rule).containsExactly("record");
		assertThat(detection.requiringMoreThan(8)).extracting(VersionEvidence::rule).containsExactly("record", "var");
		assertThat(detection.requiringMoreThan(16)).isEmpty();
	}

	// ---- o que a pessoa diz

	static Stream<Arguments> citacoes() {
		return Stream.of(
				Arguments.of("Como faço isso no Java 8?", List.of(8)),
				Arguments.of("JDK 17", List.of(17)),
				Arguments.of("java8", List.of(8)),
				Arguments.of("Java 1.8", List.of(8)),
				Arguments.of("Java 1.8.0_291", List.of(8)),
				Arguments.of("jdk1.4", List.of(4)),
				Arguments.of("J2SE 5.0", List.of(5)),
				Arguments.of("Java 1.0", List.of(1)),
				Arguments.of("Java SE 11", List.of(11)),
				Arguments.of("OpenJDK 21", List.of(21)),
				Arguments.of("JDK-17", List.of(17)),
				Arguments.of("Java versão 21", List.of(21)),
				Arguments.of("Java version 17", List.of(17)),
				Arguments.of("a versão 17 do Java", List.of(17)),
				Arguments.of("version 11 of Java", List.of(11)),
				Arguments.of("Java 17.0.2", List.of(17)),
				Arguments.of("JDK 8u291", List.of(8)),
				Arguments.of("Java 8 vs 17", List.of(8, 17)),
				Arguments.of("o que mudou do Java 8 para o 17?", List.of(8, 17)),
				Arguments.of("Java 8, 11 e 17", List.of(8, 11, 17)),
				Arguments.of("Java 8 e Java 17", List.of(8, 17)),
				Arguments.of("Java 8 e Java 8", List.of(8)));
	}

	@ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
	@MethodSource("citacoes")
	void reconheceAVersaoCitada(String texto, List<Integer> esperado) {
		assertThat(detector.detect(texto).mentioned()).containsExactlyElementsOf(esperado);
	}

	@ParameterizedTest
	@ValueSource(strings = { "java.util.List", "JavaScript 8", "Java EE 8", "Spring Boot 3 é bom", "Java 100", "Java 0",
			"Java Server 8", "versão 17 do Spring", "" })
	void naoConfundeComOutrasCoisas(String texto) {
		VersionDetection detection = detector.detect(texto);

		assertThat(detection.mentioned()).isEmpty();
	}

	@Test
	void umaVersaoQueNaoExisteNoRegistroNaoEUsada() {
		VersionDetection detection = detector.detect("E no Java 40?");

		assertThat(detection.mentioned()).isEmpty();
		assertThat(detection.unknownMentions()).containsExactly(40);
	}

	@ParameterizedTest
	@ValueSource(strings = { "preciso de uma versão antiga", "isso funciona em versões antigas?", "Java antigo", "Java legado",
			"old version of Java", "legacy Java code", "uma versão muito antiga do Java" })
	void entendeVersaoAntigaSemNumero(String texto) {
		VersionDetection detection = detector.detect(texto);

		assertThat(detection.legacyHint()).isTrue();
		assertThat(detection.mentioned()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "qual a última versão do Java?", "na versão mais recente", "latest Java", "the newest version" })
	void entendeAVersaoMaisRecente(String texto) {
		assertThat(detector.detect(texto).latestHint()).isTrue();
	}

	@Test
	void textoVazioOuNuloNaoDetectaNada() {
		assertThat(detector.detect(null)).isEqualTo(VersionDetection.empty());
		assertThat(detector.detect("   ")).isEqualTo(VersionDetection.empty());
		assertThat(VersionDetection.empty().inferredMin()).isEmpty();
	}

	@Test
	void umTextoGrandeNaoTravaNemEstouraTempo() {
		String lixo = "a".repeat(200_000) + "(((" .repeat(20_000) + "\"" + "x".repeat(100_000);

		assertThat(org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5),
				() -> detector.detect(lixo))).isNotNull();
	}
}
