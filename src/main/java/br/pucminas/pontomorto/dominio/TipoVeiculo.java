package br.pucminas.pontomorto.dominio;

public enum TipoVeiculo {
    MOTO("Moto"),
    CARRO("Carro"),
    VAN("Van / utilitário"),
    CAMINHAO("Caminhão");

    private final String rotulo;

    TipoVeiculo(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
