/**
 * Modelos locais: cliente Ollama, catálogo de tiers (baixo, médio, forte, extra),
 * detecção de hardware e gerenciador de download (F05 a F09).
 * Lane: Modelos.
 *
 * <p>Cada assunto tem o seu subpacote: {@code ollama} (conversa com o Ollama), {@code catalog} (os tiers),
 * {@code hardware} (RAM e GPU), {@code manager} (baixar, remover, carregar) e {@code routing} (o chat pelo tier
 * escolhido). Na raiz ficam só os tipos que todos usam: {@link com.example.java_mind_agent.models.Tier},
 * {@link com.example.java_mind_agent.models.Speed}, {@link com.example.java_mind_agent.models.ModelException} e
 * {@link com.example.java_mind_agent.models.ModelsProperties}.
 */
package com.example.java_mind_agent.models;
