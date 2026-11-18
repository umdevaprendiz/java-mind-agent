package com.example.java_mind_agent.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.example.java_mind_agent.chat.domain.Attachment;
import com.example.java_mind_agent.chat.domain.Conversation;
import com.example.java_mind_agent.chat.domain.Message;
import com.example.java_mind_agent.chat.domain.MessageRole;
import com.example.java_mind_agent.support.RepositoryIntegrationTest;

@RepositoryIntegrationTest
class ChatRepositoriesTest {

	@Autowired
	private TestEntityManager em;

	@Autowired
	private ConversationRepository conversations;

	@Autowired
	private MessageRepository messages;

	@Autowired
	private AttachmentRepository attachments;

	@Test
	void conversaNovaRecebeValoresPadrao() {
		Conversation salva = conversations.saveAndFlush(new Conversation("Dúvida sobre generics"));
		em.clear();

		Conversation lida = conversations.findById(salva.getId()).orElseThrow();

		assertThat(lida.getTitle()).isEqualTo("Dúvida sobre generics");
		assertThat(lida.getLanguage()).isEqualTo("pt-BR");
		assertThat(lida.getJavaVersion()).isNull();
		assertThat(lida.getPreferences()).isEqualTo("{}");
		assertThat(lida.getCreatedAt()).isNotNull();
		assertThat(lida.getUpdatedAt()).isNotNull();
	}

	@Test
	void listaMensagensNaOrdemEmQueForamGravadas() {
		Conversation conversa = conversations.save(new Conversation("Records"));
		Message pergunta = new Message(conversa, MessageRole.USER, "O que é um record?");
		Message resposta = new Message(conversa, MessageRole.ASSISTANT, "Um record é uma classe de dados imutável.");
		resposta.setTier("medium");
		resposta.setModel("qwen2.5:7b");
		resposta.setJavaVersion(21);
		resposta.setTokensPerSecond(6.5);
		resposta.setSources("[{\"title\":\"JEP 395\",\"url\":\"https://openjdk.org/jeps/395\"}]");
		messages.saveAll(List.of(pergunta, resposta));
		em.flush();
		em.clear();

		List<Message> lidas = messages.findByConversationIdOrderByIdAsc(conversa.getId());

		assertThat(lidas).extracting(Message::getRole).containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
		Message respostaLida = lidas.get(1);
		assertThat(respostaLida.getTier()).isEqualTo("medium");
		assertThat(respostaLida.getJavaVersion()).isEqualTo(21);
		assertThat(respostaLida.getTokensPerSecond()).isEqualTo(6.5);
		assertThat(respostaLida.getSources()).contains("JEP 395");
		assertThat(respostaLida.getCreatedAt()).isNotNull();
	}

	@Test
	void apagarAConversaRemoveMensagensEAnexos() {
		Conversation conversa = conversations.save(new Conversation("Stack trace"));
		Message mensagem = messages.save(new Message(conversa, MessageRole.USER, "Segue o erro"));
		Attachment anexo = new Attachment(conversa, "erro.txt", 120, "a".repeat(64), "data/anexos/erro.txt");
		anexo.setMessage(mensagem);
		attachments.save(anexo);
		em.flush();
		em.clear();

		conversations.deleteById(conversa.getId());
		em.flush();
		em.clear();

		assertThat(messages.countByConversationId(conversa.getId())).isZero();
		assertThat(attachments.findByConversationId(conversa.getId())).isEmpty();
	}
}
