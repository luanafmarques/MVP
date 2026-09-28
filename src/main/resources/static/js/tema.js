// Modo claro/escuro: segue o sistema; o botão "Tema" fixa a escolha neste navegador.
(function () {
  var CHAVE = 'pm-tema';
  function lerEscolha() {
    try { return localStorage.getItem(CHAVE); } catch (e) { return null; }
  }
  function aplicar(tema) {
    if (tema === 'light' || tema === 'dark') {
      document.documentElement.setAttribute('data-theme', tema);
    } else {
      document.documentElement.removeAttribute('data-theme');
    }
  }
  aplicar(lerEscolha());

  function temaEfetivo() {
    var fixo = document.documentElement.getAttribute('data-theme');
    if (fixo) return fixo;
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  window.pontoMortoTema = { efetivo: temaEfetivo };

  document.addEventListener('DOMContentLoaded', function () {
    var botao = document.querySelector('[data-alternar-tema]');
    if (botao) {
      var atualizarRotulo = function () {
        var escuro = temaEfetivo() === 'dark';
        botao.setAttribute('aria-pressed', escuro ? 'true' : 'false');
        botao.querySelector('span').textContent = escuro ? 'Modo claro' : 'Modo escuro';
      };
      atualizarRotulo();
      botao.addEventListener('click', function () {
        var novo = temaEfetivo() === 'dark' ? 'light' : 'dark';
        try { localStorage.setItem(CHAVE, novo); } catch (e) { /* navegador sem armazenamento */ }
        aplicar(novo);
        atualizarRotulo();
        document.dispatchEvent(new CustomEvent('pm:tema'));
      });
    }

    var menu = document.querySelector('[data-menu-botao]');
    if (menu) {
      menu.addEventListener('click', function () {
        var aberto = menu.getAttribute('aria-expanded') === 'true';
        menu.setAttribute('aria-expanded', aberto ? 'false' : 'true');
        document.getElementById(menu.getAttribute('aria-controls')).classList.toggle('aberto', !aberto);
      });
    }

    // Confirmação antes de ações que não têm volta (excluir, anonimizar...).
    document.querySelectorAll('form[data-confirmar]').forEach(function (form) {
      form.addEventListener('submit', function (e) {
        if (!window.confirm(form.getAttribute('data-confirmar'))) e.preventDefault();
      });
    });
  });
})();
