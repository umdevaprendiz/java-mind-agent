package com.example.java_mind_agent.models.routing;

import org.jspecify.annotations.Nullable;

import com.example.java_mind_agent.models.Tier;

/**
 * Para onde uma requisição vai: o nível escolhido (pelo usuário ou o padrão), o modelo e o servidor Ollama que o
 * atendem, já conferidos como prontos para uso.
 *
 * @param tier        o nível que vai responder; nunca é trocado por outro sem o usuário pedir
 * @param label       nome do nível na tela (Baixo, Médio, Forte, Extra)
 * @param model       nome do modelo no Ollama
 * @param host        URL do Ollama que o guarda (o padrão ou o {@code base-url} do nível)
 * @param remote      o Ollama não é o padrão (outro computador)
 * @param loaded      o modelo já está na memória; se não, a primeira resposta espera o carregamento
 * @param fromDefault a requisição não escolheu nível e valeu o padrão
 * @param supportsTools o modelo sabe chamar ferramentas ({@code true}), não sabe ({@code false}) ou o Ollama não
 *                    informou ({@code null}); o agente só oferece ferramentas quando é {@code true}
 */
public record Route(Tier tier, String label, String model, String host, boolean remote, boolean loaded,
		boolean fromDefault, @Nullable Boolean supportsTools) {

	/** Rota sem informação sobre ferramentas. */
	public Route(Tier tier, String label, String model, String host, boolean remote, boolean loaded,
			boolean fromDefault) {
		this(tier, label, model, host, remote, loaded, fromDefault, null);
	}
}
