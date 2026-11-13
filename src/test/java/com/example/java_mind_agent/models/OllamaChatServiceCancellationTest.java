package com.example.java_mind_agent.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import com.sun.net.httpserver.HttpServer;

/**
 * Cancelar a assinatura tem de fechar a conexão com o Ollama: é assim que ele percebe que ninguém mais quer a resposta
 * e para de gastar CPU gerando tokens. Um servidor HTTP de verdade (o do JDK) conta quantos pedaços chegou a enviar e
 * se a escrita falhou depois do cancelamento.
 */
class OllamaChatServiceCancellationTest {

	private static final int PEDACOS = 200;

	@Test
	void cancelarOStreamFechaAConexaoEOServidorParaDeEnviar() throws IOException {
		AtomicInteger enviados = new AtomicInteger();
		AtomicBoolean clienteSaiu = new AtomicBoolean();
		HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		servidor.createContext("/api/chat", troca -> {
			troca.getResponseHeaders().add("Content-Type", "application/x-ndjson");
			troca.sendResponseHeaders(200, 0);
			try (OutputStream saida = troca.getResponseBody()) {
				for (int i = 0; i < PEDACOS; i++) {
					saida.write((OllamaChatServiceTest.token("t" + i) + "\n").getBytes(StandardCharsets.UTF_8));
					saida.flush();
					enviados.incrementAndGet();
					Thread.sleep(50);
				}
			}
			catch (IOException | InterruptedException e) {
				clienteSaiu.set(true);
			}
		});
		servidor.start();
		try {
			OllamaChatService service = OllamaChatServiceTest
				.servico("http://127.0.0.1:" + servidor.getAddress().getPort());
			List<Message> pergunta = List.of(new UserMessage("conte até 200"));

			List<ModelStreamEvent> recebidos = service.stream("m", pergunta, GenerationOptions.defaults())
				.take(3)
				.collectList()
				.block(Duration.ofSeconds(15));

			assertThat(recebidos).hasSize(3);
			await().atMost(Duration.ofSeconds(8))
				.untilAsserted(() -> assertThat(clienteSaiu).as("o servidor percebeu que o cliente saiu").isTrue());
			assertThat(enviados.get()).as("parou antes de enviar tudo").isLessThan(PEDACOS);
		}
		finally {
			servidor.stop(0);
		}
	}
}
