package br.pucminas.pontomorto.regras;

/**
 * Violação de uma regra de negócio. A mensagem é mostrada ao usuário, por isso vem em português simples.
 */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
