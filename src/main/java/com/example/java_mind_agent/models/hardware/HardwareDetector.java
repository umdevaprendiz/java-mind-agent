package com.example.java_mind_agent.models.hardware;

/** Descobre a RAM e a GPU desta máquina. Interface para os testes poderem simular outras máquinas. */
public interface HardwareDetector {

	HardwareInfo detect();
}
