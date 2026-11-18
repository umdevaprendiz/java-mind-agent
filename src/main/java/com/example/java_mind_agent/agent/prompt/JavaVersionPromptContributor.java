package com.example.java_mind_agent.agent.prompt;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.example.java_mind_agent.knowledge.version.AssumedVersion;
import com.example.java_mind_agent.settings.Language;

/**
 * Diz ao modelo qual versão do Java assumir (política da F10: citada &gt; escolhida &gt; inferida do código &gt;
 * preferência &gt; LTS mais recente). A declaração da versão ({@link AssumedVersion#statement(Language)}) não depende
 * do modelo: o agente a escreve no começo da resposta; aqui o modelo só fica sabendo qual versão usar e que não
 * precisa repetir a frase.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JavaVersionPromptContributor implements PromptContributor {

	@Override
	public String contribute(PromptContext context) {
		AssumedVersion version = context.javaVersion();
		String statement = version.statement(context.language());
		if (context.language() == Language.EN) {
			return """
					## Java version
					Write every explanation and every piece of code for %s. Do not use features from newer versions \
					without saying that they are newer.
					The person has already been shown the line below, right before your answer: do not repeat it. \
					Use it as context (it may warn about code that needs a newer version):
					%s""".formatted(version.label() + (version.orNewer() ? " or newer" : ""), statement);
		}
		return """
				## Versão do Java
				Escreva toda explicação e todo código para %s. Não use recursos de versões mais novas sem avisar que \
				são mais novos.
				A pessoa já viu a linha abaixo, logo antes da sua resposta: não a repita. \
				Use-a como contexto (ela pode avisar de código que exige uma versão mais nova):
				%s""".formatted(version.label() + (version.orNewer() ? " ou mais nova" : ""), statement);
	}
}
