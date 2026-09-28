package br.pucminas.pontomorto.dominio;

public enum StatusRoteiro {
    PLANEJADO("Planejado"),
    EM_ANDAMENTO("Em andamento"),
    CONCLUIDO("Concluído");

    private final String rotulo;

    StatusRoteiro(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
