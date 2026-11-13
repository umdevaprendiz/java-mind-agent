package com.example.java_mind_agent.common.sse;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;

class SseSupportTest {

	private static final Duration INTERVALO = Duration.ofSeconds(15);

	@Test
	void enviaComentarioKeepAliveEnquantoOStreamEstaSilencioso() {
		TestPublisher<ServerSentEvent<String>> origem = TestPublisher.create();

		StepVerifier.withVirtualTime(() -> SseSupport.withKeepAlive(origem.flux(), INTERVALO))
			.expectSubscription()
			.then(() -> origem.next(evento("a")))
			.expectNextMatches(e -> "a".equals(e.data()))
			.expectNoEvent(Duration.ofSeconds(14))
			.thenAwait(Duration.ofSeconds(1))
			.expectNextMatches(SseSupportTest::ehKeepAlive)
			.thenAwait(INTERVALO)
			.expectNextMatches(SseSupportTest::ehKeepAlive)
			.then(origem::complete)
			.verifyComplete();
	}

	@Test
	void paraOsComentariosQuandoOStreamTerminaSemEventos() {
		TestPublisher<ServerSentEvent<String>> origem = TestPublisher.create();

		StepVerifier.withVirtualTime(() -> SseSupport.withKeepAlive(origem.flux(), INTERVALO))
			.expectSubscription()
			.then(origem::complete)
			.expectComplete()
			.verify(Duration.ofSeconds(1));
	}

	@Test
	void propagaOErroDoStreamEPara() {
		TestPublisher<ServerSentEvent<String>> origem = TestPublisher.create();

		StepVerifier.withVirtualTime(() -> SseSupport.withKeepAlive(origem.flux(), INTERVALO))
			.expectSubscription()
			.then(() -> origem.error(new IllegalStateException("falhou")))
			.verifyError(IllegalStateException.class);
	}

	@Test
	void cancelarOStreamCancelaAOrigemETambemOKeepAlive() {
		TestPublisher<ServerSentEvent<String>> origem = TestPublisher.create();

		StepVerifier.withVirtualTime(() -> SseSupport.withKeepAlive(origem.flux(), INTERVALO))
			.expectSubscription()
			.thenAwait(INTERVALO)
			.expectNextMatches(SseSupportTest::ehKeepAlive)
			.thenCancel()
			.verify(Duration.ofSeconds(1));

		origem.assertCancelled();
	}

	private static ServerSentEvent<String> evento(String dado) {
		return ServerSentEvent.<String>builder(dado).build();
	}

	private static boolean ehKeepAlive(ServerSentEvent<String> evento) {
		return "keep-alive".equals(evento.comment()) && evento.data() == null;
	}
}
