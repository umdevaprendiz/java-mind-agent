package com.example.java_mind_agent.models;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * A regra de "cabe nesta máquina". Os números são os da máquina de desenvolvimento (15,76 GiB de RAM total): ali o
 * Forte (12 GiB) cabe raspando e o Extra (26 GiB) não cabe de jeito nenhum.
 */
class TierFitPolicyTest {

	private final TierFitPolicy policy = new TierFitPolicy(1.3, 3);

	private static HardwareInfo maquina(double totalGb) {
		return new HardwareInfo(totalGb, 2.5, "Intel(R) UHD Graphics", true);
	}

	@Test
	void semTamanhoRealUsaOMinimoConfigurado() {
		assertThat(policy.requiredRamGb(6, null)).isEqualTo(6);
	}

	@Test
	void comTamanhoRealPedeOMaiorEntreOMinimoEOTamanhoComSobrecarga() {
		assertThat(policy.requiredRamGb(6, 4.36)).as("4,36 × 1,3 = 5,67 < 6").isEqualTo(6);
		assertThat(policy.requiredRamGb(2.5, 20.0)).as("modelo bem maior que o mínimo prometido").isEqualTo(26);
	}

	@Test
	void usavelEATotalMenosAReservaENuncaNegativa() {
		assertThat(policy.usableRamGb(maquina(15.76))).isCloseTo(12.76, org.assertj.core.data.Offset.offset(0.001));
		assertThat(policy.usableRamGb(maquina(2))).isZero();
	}

	@Test
	void usaARamTotalEntaoOQueEstaLivreAgoraNaoMudaOResultado() {
		HardwareInfo poucaLivre = new HardwareInfo(15.76, 0.4, null, true);
		HardwareInfo muitaLivre = new HardwareInfo(15.76, 12.0, null, true);

		assertThat(policy.fits(6, poucaLivre)).isTrue();
		assertThat(policy.fits(6, muitaLivre)).isTrue();
	}

	@Test
	void nosQuatroTiersDestaMaquinaCabemBaixoMedioEForteMasNaoOExtra() {
		HardwareInfo desenvolvimento = maquina(15.76);

		assertThat(policy.fits(2.5, desenvolvimento)).as("Baixo").isTrue();
		assertThat(policy.fits(6, desenvolvimento)).as("Médio").isTrue();
		assertThat(policy.fits(12, desenvolvimento)).as("Forte").isTrue();
		assertThat(policy.fits(26, desenvolvimento)).as("Extra").isFalse();
	}

	@Test
	void exatamenteIgualAoUsavelAindaCabe() {
		assertThat(policy.fits(13, maquina(16))).isTrue();
		assertThat(policy.fits(13.01, maquina(16))).isFalse();
	}

	@Test
	void construidoAPartirDaConfiguracao() {
		TierFitPolicy daConfig = new TierFitPolicy(new ModelsProperties.Capacity(2.0, 1));

		assertThat(daConfig.overheadFactor()).isEqualTo(2.0);
		assertThat(daConfig.reservedRamGb()).isEqualTo(1);
	}
}
