package br.pucminas.pontomorto.dominio;

/** Perfis de acesso (RNF04). */
public enum Perfil {
    ADMIN("Administrador"),
    GERENTE("Gerente / coordenador"),
    MOTORISTA("Motorista / motoboy");

    private final String rotulo;

    Perfil(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
