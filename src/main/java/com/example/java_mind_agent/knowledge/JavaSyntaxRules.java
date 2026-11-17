package com.example.java_mind_agent.knowledge;

import java.util.List;
import java.util.regex.Pattern;

import com.example.java_mind_agent.knowledge.JavaSyntaxRule.View;

/**
 * As regras que inferem a versão <b>mínima</b> do Java pela sintaxe e pelas APIs que o código usa. Uma regra só existe
 * quando o recurso é próprio de uma versão: palavras comuns ("record" numa frase) não bastam, é preciso a forma do
 * código ({@code record Ponto(}). Quando uma regra pode errar para mais (uma API que existe desde antes mas ficou mais
 * comum depois), ela fica fora: errar para menos é aceitável, a versão inferida é um piso.
 */
final class JavaSyntaxRules {

	/** {@code case X ->} e {@code default ->}: a seta do switch moderno, que não é lambda. */
	static final Pattern SWITCH_ARROW = Pattern.compile("\\b(?:case\\b[^:;{}\\n]*?|default)\\s*->");

	static final List<JavaSyntaxRule> ALL = List.of(
			// ---- Java 5
			rule("generics", "generics (List<String>)", 5,
					"\\b(?:List|ArrayList|LinkedList|Map|HashMap|TreeMap|LinkedHashMap|Set|HashSet|TreeSet|LinkedHashSet|Collection|Iterator|Iterable|Comparable|Comparator|Class|Queue|Deque)\\s*<\\s*[A-Z?]"),
			rule("enhanced-for", "for-each", 5,
					"\\bfor\\s*\\(\\s*(?:final\\s+)?[\\w.<>\\[\\]?,\\s]+?\\s+\\w+\\s*:\\s*[^;)]"),
			rule("annotations", "anotações (@Override, @Deprecated)", 5, "@(?:Override|Deprecated|SuppressWarnings)\\b"),
			rule("varargs", "varargs (Tipo... nome)", 5, "[\\w>\\]]\\s*\\.\\.\\.\\s*\\w+\\s*[,)]"),
			rule("enum", "enum", 5, "\\benum\\s+[A-Z]\\w*"),
			rule("static-import", "import static", 5, "\\bimport\\s+static\\b"),
			rule("concurrent", "java.util.concurrent (ExecutorService...)", 5,
					"\\bjava\\.util\\.concurrent\\b|\\b(?:ExecutorService|Executors|ConcurrentHashMap|CountDownLatch|AtomicInteger|AtomicLong)\\b"),

			// ---- Java 7
			rule("diamond", "operador diamante (<>)", 7, "\\bnew\\s+[A-Z][\\w.]*\\s*<\\s*>"),
			rule("try-with-resources", "try-with-resources", 7,
					"\\btry\\s*\\(\\s*(?:final\\s+)?[\\w.<>\\[\\]?,\\s]+?\\s+\\w+\\s*="),
			rule("multi-catch", "multi-catch (A | B)", 7, "\\bcatch\\s*\\(\\s*(?:final\\s+)?[\\w.]+\\s*\\|\\s*[\\w.]+"),
			rule("nio-file", "java.nio.file (Files, Paths)", 7, "\\bjava\\.nio\\.file\\b|\\bPaths\\.get\\(|\\bFiles\\.\\w+\\("),
			rule("numeric-literals", "literais binários e com sublinhado", 7, "\\b0[bB][01][01_]*\\b|\\b\\d+(?:_\\d+)+\\b"),
			rule("objects", "java.util.Objects", 7,
					"\\bObjects\\.(?:equals|hash|hashCode|requireNonNull|toString|isNull|nonNull)\\("),

			// ---- Java 8
			rule("lambda", "expressão lambda (->)", 8, 0, false, View.NO_SWITCH_ARROW, "(?:\\)|\\b[A-Za-z_]\\w*)\\s*->"),
			rule("method-reference", "referência a método (::)", 8, "[\\w\\])>]\\s*::\\s*(?:new|[A-Za-z_]\\w*)"),
			rule("streams", "Stream API", 8,
					"\\.stream\\(\\)|\\b(?:Stream|IntStream|LongStream|DoubleStream)\\.(?:of|iterate|generate|range|rangeClosed|empty)\\(|\\bjava\\.util\\.stream\\b|\\bCollectors\\.\\w+\\(|\\bStream\\s*<"),
			rule("optional", "Optional", 8, "\\bOptional\\s*<|\\bOptional\\.(?:of|empty|ofNullable)\\("),
			rule("java-time", "java.time (LocalDate...)", 8,
					"\\bjava\\.time\\b|\\b(?:LocalDate|LocalDateTime|LocalTime|ZonedDateTime|OffsetDateTime|DateTimeFormatter|ChronoUnit)\\b"),
			rule("default-method", "método default em interface", 8,
					"\\bdefault\\s+(?:<[^>]+>\\s+)?[\\w.<>\\[\\]?,]+\\s+\\w+\\s*\\("),
			rule("functional", "interfaces funcionais (Function, Supplier...)", 8,
					"@FunctionalInterface\\b|\\b(?:Function|BiFunction|Supplier|Consumer|BiConsumer|Predicate|BiPredicate|UnaryOperator|BinaryOperator)\\s*<"),
			rule("completable-future", "CompletableFuture", 8, "\\bCompletableFuture\\b"),
			rule("foreach-method", "forEach(...)", 8, "\\.forEach\\("),

			// ---- Java 9
			rule("collection-factories", "List.of, Set.of e Map.of", 9,
					"\\b(?:List|Set|Map)\\.(?:of|ofEntries)\\(|\\bMap\\.entry\\("),
			rule("modules", "módulos (module-info)", 9,
					"\\bmodule-info\\b|\\bmodule\\s+[a-z][\\w.]*\\s*\\{|\\bopens\\s+[a-z][\\w.]*\\s*;"),
			rule("stream-9", "takeWhile, dropWhile e ifPresentOrElse", 9,
					"\\.(?:takeWhile|dropWhile|ifPresentOrElse)\\(|\\bStream\\.ofNullable\\("),
			rule("process-api", "ProcessHandle, StackWalker e Flow", 9,
					"\\b(?:ProcessHandle|StackWalker|VarHandle)\\b|\\bFlow\\.(?:Publisher|Subscriber|Subscription|Processor)\\b"),
			rule("try-existing-var", "try com variável já declarada", 9, "\\btry\\s*\\(\\s*[A-Za-z_]\\w*\\s*[);]"),
			rule("io-9", "transferTo e readAllBytes", 9, "\\.(?:transferTo|readAllBytes)\\("),

			// ---- Java 10 e 11
			rule("var", "var em variável local", 10, "\\bvar\\s+[A-Za-z_]\\w*\\s*[=:]"),
			rule("copy-of", "List.copyOf e coletores não modificáveis", 10,
					"\\b(?:List|Set|Map)\\.copyOf\\(|\\bCollectors\\.toUnmodifiable(?:List|Set|Map)\\(|\\.orElseThrow\\(\\s*\\)"),
			rule("var-lambda", "var em parâmetro de lambda", 11, "\\(\\s*var\\s+\\w+\\s*(?:,\\s*var\\s+\\w+\\s*)*\\)\\s*->"),
			rule("string-11", "String.isBlank, strip e repeat", 11,
					"\\.(?:isBlank|strip|stripLeading|stripTrailing|repeat)\\("),
			rule("http-client", "java.net.http.HttpClient", 11,
					"\\bjava\\.net\\.http\\b|\\bHttpClient\\.newHttpClient\\(|\\bHttpRequest\\.newBuilder\\("),
			rule("path-of", "Path.of e Files.readString", 11, "\\bPath\\.of\\(|\\bFiles\\.(?:readString|writeString)\\("),
			rule("predicate-not", "Predicate.not", 11, "\\bPredicate\\.not\\("),
			rule("teeing", "Collectors.teeing", 12, "\\bCollectors\\.teeing\\("),

			// ---- Java 14 a 17
			rule("switch-arrow", "switch com seta (case x ->)", 14, 12, false, View.CODE, SWITCH_ARROW.pattern()),
			rule("yield", "yield em switch", 14, 12, false, View.CODE, "\\byield\\s+(?=[\\w\"'(!+\\-])[^;]*;"),
			rule("text-block", "text block (\"\"\")", 15, 13, false, View.RAW, "\"\"\""),
			rule("string-15", "String.formatted e stripIndent", 15, "\\.(?:formatted|stripIndent|translateEscapes)\\("),
			rule("record", "record", 16, 14, false, View.CODE, "\\brecord\\s+[A-Z]\\w*\\s*(?:<[^>{}]*>\\s*)?\\("),
			rule("instanceof-pattern", "instanceof com padrão (x instanceof Tipo t)", 16, 14, false, View.CODE,
					"\\binstanceof\\s+(?:final\\s+)?[\\w.]+(?:<[^>()]*>)?(?:\\[\\])*\\s+[a-z_]\\w*\\b"),
			rule("to-list", "Stream.toList()", 16, "(?<!Collectors)\\.toList\\(\\)"),
			rule("map-multi", "Stream.mapMulti", 16, "\\.mapMulti\\("),
			rule("sealed", "sealed classes (sealed, permits)", 17, 15, false, View.CODE,
					"\\bsealed\\s+(?:(?:abstract|public|protected|private|static|strictfp)\\s+)*(?:class|interface)\\b|\\bnon-sealed\\b|\\bpermits\\s+[A-Z]\\w*"),
			rule("random-generator", "RandomGenerator e HexFormat", 17,
					"\\b(?:RandomGenerator|RandomGeneratorFactory|HexFormat)\\b"),

			// ---- Java 21 e seguintes
			rule("virtual-threads", "virtual threads", 21, 19, false, View.CODE,
					"\\bThread\\.(?:ofVirtual|ofPlatform|startVirtualThread)\\(|\\bnewVirtualThreadPerTaskExecutor\\("),
			rule("record-patterns", "record patterns (case Ponto(int x, int y))", 21, 19, false, View.CODE,
					"\\bcase\\s+[A-Z][\\w.]*\\s*\\([^)]*\\)|\\binstanceof\\s+[A-Z][\\w.]*\\s*\\([^()]*\\)"),
			rule("switch-patterns", "pattern matching em switch (case Tipo t, case null)", 21, 17, false, View.CODE,
					"\\bcase\\s+null\\b|\\bcase\\s+[A-Z][\\w.]*(?:<[^>]*>)?\\s+[a-z_]\\w*\\s*(?:->|:|,|\\bwhen\\b)"),
			rule("sequenced", "SequencedCollection", 21, "\\bSequenced(?:Collection|Map|Set)\\b"),
			rule("math-clamp", "Math.clamp", 21, "\\bMath\\.clamp\\("),
			rule("unnamed", "variáveis e padrões sem nome (_)", 22, 21, false, View.CODE,
					"\\bcatch\\s*\\([^)]*\\s_\\s*\\)|[(,]\\s*_\\s*[,)]|\\b_\\s*->|(?:\\bint|\\bvar|\\b[A-Z]\\w*)\\s+_\\s*[=:;,)]"),
			rule("ffm", "Foreign Function & Memory API", 22, 19, false, View.CODE,
					"\\bjava\\.lang\\.foreign\\b|\\bArena\\.of(?:Confined|Auto|Shared|Global)\\(|\\bLinker\\.nativeLinker\\(|\\bMemorySegment\\b"),
			rule("gatherers", "Stream Gatherers", 24, 22, false, View.CODE, "\\.gather\\(|\\bGatherers?\\b"),
			rule("classfile", "Class-File API", 24, 22, false, View.CODE, "\\bjava\\.lang\\.classfile\\b|\\bClassFile\\.of\\("),
			rule("instance-main", "main de instância (void main() sem static)", 25, 21, false, View.CODE,
					"(?m)^(?![^\\n]*\\bstatic\\b)[ \\t]*(?:public\\s+)?void\\s+main\\s*\\(\\s*(?:String\\s*(?:\\[\\s*\\]|\\.\\.\\.)\\s*\\w+)?\\s*\\)"),
			rule("module-import", "import module", 25, 23, false, View.CODE, "\\bimport\\s+module\\s+[\\w.]+\\s*;"),
			rule("io-println", "IO.println", 25, 23, false, View.CODE, "\\bIO\\.(?:println|print|readln)\\("),
			rule("scoped-value", "ScopedValue", 25, 20, false, View.CODE, "\\bScopedValue\\b"),
			rule("http3", "HTTP/3 no HttpClient", 26, "\\bHTTP_3\\b"),

			// ---- ainda em preview ou incubadora: exigem flag, e a API muda de uma versão para outra
			rule("structured-concurrency", "StructuredTaskScope (preview)", 21, 21, true, View.CODE, "\\bStructuredTaskScope\\b"),
			rule("lazy-constants", "LazyConstant e StableValue (preview)", 25, 25, true, View.CODE,
					"\\b(?:LazyConstant|StableValue)\\b"),
			rule("primitive-patterns", "tipos primitivos em padrões (preview)", 23, 23, true, View.CODE,
					"\\binstanceof\\s+(?:int|long|double|float|short|byte|char|boolean)\\b|\\bcase\\s+(?:int|long|double|float|short|byte|char|boolean)\\s+\\w+"),
			rule("vector-api", "Vector API (incubadora)", 16, 16, true, View.CODE,
					"\\bjdk\\.incubator\\.vector\\b|\\bVectorSpecies\\b"));

	private JavaSyntaxRules() {
	}

	private static JavaSyntaxRule rule(String id, String label, int since, String regex) {
		return rule(id, label, since, 0, false, View.CODE, regex);
	}

	private static JavaSyntaxRule rule(String id, String label, int since, int previewSince, boolean preview,
			View view, String regex) {
		return new JavaSyntaxRule(id, label, since, previewSince, preview, view, Pattern.compile(regex));
	}
}
