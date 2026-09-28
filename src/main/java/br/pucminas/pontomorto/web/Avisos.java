package br.pucminas.pontomorto.web;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Mensagens de retorno mostradas no topo da página seguinte (sucesso ou erro). */
final class Avisos {

    private Avisos() {
    }

    static void sucesso(RedirectAttributes ra, String texto) {
        ra.addFlashAttribute("avisoSucesso", texto);
    }

    static void erro(RedirectAttributes ra, String texto) {
        ra.addFlashAttribute("avisoErro", texto);
    }
}
