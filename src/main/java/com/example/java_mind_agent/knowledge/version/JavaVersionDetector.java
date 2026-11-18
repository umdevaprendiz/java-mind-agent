package com.example.java_mind_agent.knowledge.version;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Descobre a versão do Java num texto de duas maneiras: pelo que a pessoa <b>diz</b> ("Java 8", "JDK 17", "versão 11
 * do Java", "versão antiga") e pelo que o código <b>exige</b> (um {@code record} pede Java 16 ou mais). Não decide
 * qual versão usar, só relata os sinais; a escolha é da {@link JavaVersionService}.
 */
@Component
public class JavaVersionDetector {

	private static final int REGEX_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

	/**
	 * "Java 8", "JDK 17", "Java SE 11", "OpenJDK 21", "J2SE 5.0", "Java 1.8", "jdk1.4", "Java versão 21", "JDK 8u291" e
	 * "Java 17.0.2". O primeiro número vem no grupo 1 e a casa depois do ponto no 2 ({@code 1.x} vale {@code x}).
	 */
	private static final Pattern MENTION = Pattern.compile(
			"(?<![\\p{L}\\d])(?:java(?:[\\s_-]*se)?|jdk|j2se|openjdk|jre)(?:[\\s_-]*(?:vers[ãa]o|version|v\\.?))?[\\s_-]*"
					+ "(\\d{1,2})(?:\\.(\\d{1,2}))?(?:u\\d+)?(?![\\p{L}\\d])",
			REGEX_FLAGS);

	/** "versão 17 do Java", "version 17 of Java". */
	private static final Pattern MENTION_INVERTED = Pattern.compile(
			"(?<![\\p{L}\\d])(?:vers[ãa]o|version)\\s+(\\d{1,2})(?:\\.(\\d{1,2}))?\\s+(?:d[oae]s?|of)\\s+(?:o\\s+)?(?:java|jdk)\\b",
			REGEX_FLAGS);

	/** Outro número logo depois de uma citação: "Java 8 e 17", "do Java 8 para o 17", "Java 8, 11 e 17". */
	private static final Pattern FOLLOWING = Pattern.compile(
			"\\s*(?:,|/|\\be\\b|\\band\\b|\\bvs\\.?|\\bversus\\b|\\bou\\b|\\bor\\b|\\bpara\\b|\\bto\\b|\\bat[ée]\\b|->|→)\\s*"
					+ "(?:(?:o|a|the)\\s+)?(?:(?:java|jdk)\\s*)?(\\d{1,2})(?:\\.(\\d{1,2}))?(?![\\p{L}\\d])",
			REGEX_FLAGS);

	private static final Pattern LEGACY_HINT = Pattern.compile(
			"(?<!\\p{L})(?:vers(?:ão|ao|ões|oes)\\s+(?:muito\\s+)?(?:antig\\p{L}*|legad\\p{L}*|velh\\p{L}*)"
					+ "|java\\s+(?:muito\\s+)?(?:antigo|legado|velho)|(?:old|older|legacy)\\s+(?:java|jdk|versions?)"
					+ "|java\\s+(?:old|legacy))(?!\\p{L})",
			REGEX_FLAGS);

	private static final Pattern LATEST_HINT = Pattern.compile(
			"(?<!\\p{L})(?:vers(?:ão|ao)\\s+mais\\s+(?:recente|nova)|[úu]ltima\\s+vers(?:ão|ao)"
					+ "|(?:latest|newest)\\s+(?:java|jdk|version)|java\\s+mais\\s+(?:recente|novo))(?!\\p{L})",
			REGEX_FLAGS);

	private static final Pattern FENCED = Pattern.compile("```[^\\n`]*\\n?(.*?)(?:```|\\z)", Pattern.DOTALL);

	private static final Pattern INLINE = Pattern.compile("`([^`\\n]+)`");

	/** Texto sem marcação de código só é lido como código se parecer com código. */
	private static final Pattern LOOKS_LIKE_CODE = Pattern
		.compile("[;{}]|=\\s*\\S|\\b(?:var|record|enum|sealed|import|class|interface|new|void|final)\\s+[A-Za-z_]");

	private static final Pattern TEXT_BLOCK = Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"");

	private static final Pattern STRING = Pattern.compile("\"(?:\\\\.|[^\"\\\\\\n])*\"");

	private static final Pattern CHAR = Pattern.compile("'(?:\\\\.|[^'\\\\\\n])'");

	private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*[\\s\\S]*?\\*/");

	private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

	private static final int SAMPLE_MAX = 60;

	private final JavaVersionRegistry registry;

	public JavaVersionDetector(JavaVersionRegistry registry) {
		this.registry = registry;
	}

	public VersionDetection detect(String text) {
		if (text == null || text.isBlank()) {
			return VersionDetection.empty();
		}
		Set<Integer> mentioned = new LinkedHashSet<>();
		Set<Integer> unknown = new LinkedHashSet<>();
		collectMentions(text, mentioned, unknown);
		boolean legacy = LEGACY_HINT.matcher(text).find();
		boolean latest = LATEST_HINT.matcher(text).find();
		return new VersionDetection(List.copyOf(mentioned), List.copyOf(unknown), legacy, latest, evidence(codeOf(text)));
	}

	// ---- o que a pessoa diz

	private void collectMentions(String text, Set<Integer> mentioned, Set<Integer> unknown) {
		for (Pattern pattern : List.of(MENTION, MENTION_INVERTED)) {
			Matcher m = pattern.matcher(text);
			while (m.find()) {
				add(toVersion(m.group(1), m.group(2)), mentioned, unknown);
				int end = m.end();
				Matcher next = FOLLOWING.matcher(text);
				while (next.region(end, text.length()).lookingAt()) {
					add(toVersion(next.group(1), next.group(2)), mentioned, unknown);
					end = next.end();
				}
			}
		}
	}

	/** {@code 1.8} vale 8; {@code 1.0} e {@code 1.1} valem 1; {@code 17.0} vale 17. {@code 0} = número sem sentido. */
	static int toVersion(String first, String afterDot) {
		int major = Integer.parseInt(first);
		if (major == 1 && afterDot != null) {
			return Math.max(Integer.parseInt(afterDot), 1);
		}
		return major;
	}

	private void add(int version, Set<Integer> mentioned, Set<Integer> unknown) {
		if (version < 1) {
			return;
		}
		(registry.contains(version) ? mentioned : unknown).add(version);
	}

	// ---- o que o código exige

	private List<VersionEvidence> evidence(String code) {
		if (code.isBlank()) {
			return List.of();
		}
		String clean = clean(code);
		String noSwitchArrow = JavaSyntaxRules.SWITCH_ARROW.matcher(clean).replaceAll(" ");
		List<VersionEvidence> found = new ArrayList<>();
		for (JavaSyntaxRule rule : JavaSyntaxRules.ALL) {
			String view = switch (rule.view()) {
				case RAW -> code;
				case CODE -> clean;
				case NO_SWITCH_ARROW -> noSwitchArrow;
			};
			Matcher m = rule.pattern().matcher(view);
			if (m.find()) {
				found.add(new VersionEvidence(rule.id(), rule.label(), rule.since(), rule.previewSince(), rule.preview(),
						sample(m.group())));
			}
		}
		found.sort(Comparator.comparingInt(VersionEvidence::minVersion).reversed().thenComparing(VersionEvidence::rule));
		return found;
	}

	/** Sem blocos de texto, strings e comentários: {@code "record Ponto("} dentro de uma string não é um record. */
	private static String clean(String code) {
		String semBlocos = TEXT_BLOCK.matcher(code).replaceAll("\"\"");
		String semStrings = CHAR.matcher(STRING.matcher(semBlocos).replaceAll("\"\"")).replaceAll("''");
		return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(semStrings).replaceAll(" ")).replaceAll(" ");
	}

	private static String sample(String match) {
		String flat = match.replaceAll("\\s+", " ").strip();
		return flat.length() <= SAMPLE_MAX ? flat : flat.substring(0, SAMPLE_MAX - 1) + "…";
	}

	/**
	 * Só os trechos que são código: blocos com três crases e trechos com uma crase; sem marcação, o texto inteiro, se
	 * parecer código. Evita que uma seta numa frase ("A -> B") passe por lambda.
	 */
	static String codeOf(String text) {
		StringBuilder code = new StringBuilder();
		StringBuilder prose = new StringBuilder();
		Matcher fenced = FENCED.matcher(text);
		int last = 0;
		while (fenced.find()) {
			prose.append(text, last, fenced.start()).append(' ');
			code.append(fenced.group(1)).append('\n');
			last = fenced.end();
		}
		prose.append(text, last, text.length());
		Matcher inline = INLINE.matcher(prose);
		while (inline.find()) {
			code.append(inline.group(1)).append('\n');
		}
		if (!code.isEmpty()) {
			return code.toString();
		}
		return LOOKS_LIKE_CODE.matcher(text).find() ? text : "";
	}
}
