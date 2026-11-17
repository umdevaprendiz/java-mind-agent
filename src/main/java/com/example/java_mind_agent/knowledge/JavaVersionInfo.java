package com.example.java_mind_agent.knowledge;

import java.util.List;

/** Schema {@code JavaVersionInfo} do contrato: uma linha do seletor de versão do Java. */
public record JavaVersionInfo(int version, String label, int releaseYear, boolean lts, List<String> highlights) {
}
