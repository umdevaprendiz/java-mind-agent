package com.example.java_mind_agent.models;

import java.util.List;

/**
 * Resposta de {@code GET /api/models} (schema {@code ModelCatalog} do contrato): tudo que o seletor Baixo / Médio /
 * Forte / Extra precisa para se montar.
 *
 * @param hardware    RAM e GPU desta máquina
 * @param defaultTier tier usado quando a conversa não escolhe um
 * @param tiers       os quatro tiers de chat, do Baixo ao Extra
 * @param embedding   o modelo de embedding do RAG
 */
public record ModelCatalog(HardwareInfo hardware, Tier defaultTier, List<ModelInfo> tiers, ModelInfo embedding) {
}
