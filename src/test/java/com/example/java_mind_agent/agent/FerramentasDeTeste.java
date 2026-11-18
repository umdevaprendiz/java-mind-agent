package com.example.java_mind_agent.agent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.example.java_mind_agent.agent.AgentEvent.Phase;
import com.example.java_mind_agent.agent.tool.AgentToolSet;

/** Ferramentas falsas para os testes do agente: cada uma exercita um jeito de uma ferramenta de verdade se comportar. */
public class FerramentasDeTeste implements AgentToolSet {

	public final AtomicInteger execucoes = new AtomicInteger();

	public final List<String> textosRecebidos = new CopyOnWriteArrayList<>();

	public final AtomicBoolean lentaIniciou = new AtomicBoolean();

	public final AtomicBoolean lentaFoiInterrompida = new AtomicBoolean();

	@Tool(description = "Repete o texto recebido, em maiúsculas")
	public String eco(@ToolParam(description = "o texto a repetir") String texto) {
		execucoes.incrementAndGet();
		textosRecebidos.add(texto);
		return texto.toUpperCase();
	}

	@Tool(description = "Falha sempre")
	public String quebra() {
		execucoes.incrementAndGet();
		throw new IllegalStateException("explodiu de propósito");
	}

	@Tool(description = "Demora o tempo pedido")
	public String lenta(@ToolParam(description = "milissegundos a esperar") long ms) {
		execucoes.incrementAndGet();
		lentaIniciou.set(true);
		try {
			Thread.sleep(ms);
		}
		catch (InterruptedException e) {
			lentaFoiInterrompida.set(true);
			Thread.currentThread().interrupt();
		}
		return "acordei";
	}

	@Tool(description = "Devolve uma saída longa")
	public String grande() {
		execucoes.incrementAndGet();
		return "x".repeat(500);
	}

	@Tool(description = "Devolve texto vazio")
	public String vazia() {
		return "";
	}

	@Override
	public Phase phaseOf(String tool) {
		return tool.equals("eco") ? Phase.EXECUTING_CODE : Phase.GENERATING;
	}
}
