package br.pucminas.pontomorto.dominio;

public enum StatusPedido {
    PENDENTE("Aguardando roteiro"),
    EM_ROTEIRO("No roteiro"),
    ENTREGUE("Entregue");

    private final String rotulo;

    StatusPedido(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
