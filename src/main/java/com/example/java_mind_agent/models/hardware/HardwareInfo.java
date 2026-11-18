package com.example.java_mind_agent.models.hardware;

import org.jspecify.annotations.Nullable;

/**
 * Hardware desta máquina (schema {@code HardwareInfo} do contrato).
 *
 * @param totalRamGb RAM física total, em GiB
 * @param freeRamGb  RAM disponível agora, em GiB; oscila bastante com o que está aberto
 * @param gpu        nome da GPU, quando se conseguiu descobrir
 * @param cpuOnly    {@code true} quando não há GPU com aceleração para o Ollama e a inferência roda na CPU
 */
public record HardwareInfo(double totalRamGb, double freeRamGb, @Nullable String gpu, boolean cpuOnly) {
}
