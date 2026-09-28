// Painel do Ponto Morto (RF08): recortes Dia, Mês e Período.
// Um único filtro acima de tudo; indicadores, gráfico, tabela e ranking mostram sempre o mesmo recorte.
(function () {
  'use strict';

  var base = (document.currentScript && document.currentScript.getAttribute('data-base')) || '/';
  var numero = new Intl.NumberFormat('pt-BR');
  var dinheiro = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
  var decimal1 = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

  var estado = { aba: 'dia' };
  var grafico = null;
  var ultimo = null; // última resposta, para redesenhar ao trocar o tema

  var el = function (id) { return document.getElementById(id); };

  function minutos(m) {
    m = Math.round(m || 0);
    if (m < 60) return numero.format(m) + ' min';
    var h = Math.floor(m / 60), r = m % 60;
    return numero.format(h) + ' h ' + (r < 10 ? '0' : '') + r + ' min';
  }

  function cor(nome) {
    return getComputedStyle(document.documentElement).getPropertyValue(nome).trim();
  }

  // ---------- Filtros ----------

  function parametros() {
    var p = new URLSearchParams();
    var motorista = el('f-motorista').value;
    if (motorista) p.set('motoristaId', motorista);
    if (estado.aba === 'dia') p.set('data', el('f-data').value);
    if (estado.aba === 'mes') p.set('mes', el('f-mes').value);
    if (estado.aba === 'periodo') { p.set('inicio', el('f-inicio').value); p.set('fim', el('f-fim').value); }
    return p;
  }

  function trocarAba(aba) {
    estado.aba = aba;
    document.querySelectorAll('[role="tab"]').forEach(function (t) {
      var ativa = t.getAttribute('data-aba') === aba;
      t.setAttribute('aria-selected', ativa ? 'true' : 'false');
      t.tabIndex = ativa ? 0 : -1;
      if (ativa) el('painel-conteudo').setAttribute('aria-labelledby', t.id);
    });
    document.querySelectorAll('[data-filtro]').forEach(function (c) { c.hidden = c.getAttribute('data-filtro') !== aba; });
    document.querySelectorAll('[data-so]').forEach(function (c) { c.hidden = c.getAttribute('data-so') !== aba; });
    carregar();
  }

  // ---------- Carga ----------

  function carregar() {
    var caixa = el('painel-conteudo');
    caixa.classList.add('carregando'); // mantém o desenho anterior, sem "piscar"
    el('painel-erro').hidden = true;
    var aba = estado.aba;
    fetch(base + 'api/painel/' + aba + '?' + parametros().toString(), { headers: { 'Accept': 'application/json' } })
      .then(function (r) {
        return r.json().then(function (corpo) {
          if (!r.ok) throw new Error(corpo.erro || 'Não foi possível carregar o painel.');
          return corpo;
        });
      })
      .then(function (dados) {
        if (aba !== estado.aba) return;
        ultimo = { aba: aba, dados: dados };
        desenhar(aba, dados);
        if (aba === 'dia') carregarEstradas();
      })
      .catch(function (e) {
        el('painel-erro').textContent = e.message;
        el('painel-erro').hidden = false;
      })
      .finally(function () { caixa.classList.remove('carregando'); });
  }

  function carregarEstradas() {
    var p = new URLSearchParams({ data: el('f-data').value });
    if (el('f-motorista').value) p.set('motoristaId', el('f-motorista').value);
    fetch(base + 'painel/estradas?' + p.toString())
      .then(function (r) { return r.text(); })
      .then(function (html) { el('estradas').innerHTML = html; }); // HTML gerado pelo servidor (Thymeleaf, com escape)
  }

  // ---------- Desenho ----------

  function desenhar(aba, dados) {
    var ind = dados.indicadores;
    el('recorte-titulo').textContent = dados.titulo;
    texto('[data-kpi="total"]', minutos(ind.totalParadoMin));
    texto('[data-kpi="roteiros"]', numero.format(ind.roteiros) + (ind.roteiros === 1 ? ' roteiro' : ' roteiros') + ' · ' + dados.titulo);
    texto('[data-kpi="media"]', minutos(ind.mediaPorRoteiroMin));
    texto('[data-kpi="percentual"]', decimal1.format(ind.percentualJornada) + '%');
    texto('[data-kpi="jornada"]', 'de ' + numero.format(ind.roteiros) + ' × ' + decimal1.format(ind.jornadaHoras) + ' h de jornada');
    texto('[data-kpi="custo"]', dinheiro.format(ind.custoTotal));
    texto('[data-kpi="distancia"]', decimal1.format(ind.distanciaRealKm) + ' km');
    var dif = Number(ind.diferencaKm);
    texto('[data-kpi="diferenca"]', 'estimada ' + decimal1.format(ind.distanciaEstimadaKm) + ' km · diferença '
      + (dif > 0 ? '+' : '') + decimal1.format(dif) + ' km');
    el('tempo-consulta').textContent = 'consulta em ' + numero.format(dados.tempoConsultaMs) + ' ms';

    ranking(dados.ranking);
    if (aba === 'dia') { graficoDia(dados); paradas(dados.paradas); }
    if (aba === 'mes') graficoSerie(dados.dias, 'Tempo parado por dia · ' + dados.titulo, 'Dia');
    if (aba === 'periodo') { graficoSerie(dados.meses, 'Tempo parado por mês · ' + dados.titulo, 'Mês'); motoristas(dados.motoristas); }
  }

  function texto(seletor, valor) { document.querySelector(seletor).textContent = valor; }

  function linha(celulas, classes) {
    var tr = document.createElement('tr');
    celulas.forEach(function (c, i) {
      var td = document.createElement('td');
      td.textContent = c;
      if (classes && classes[i]) td.className = classes[i];
      tr.appendChild(td);
    });
    return tr;
  }

  function preencher(tbody, linhas, vazio, colunas) {
    tbody.replaceChildren();
    if (!linhas.length) {
      var tr = document.createElement('tr'), td = document.createElement('td');
      td.colSpan = colunas; td.className = 'vazio'; td.textContent = vazio;
      tr.appendChild(td); tbody.appendChild(tr);
      return;
    }
    linhas.forEach(function (l) { tbody.appendChild(l); });
  }

  function ranking(itens) {
    var num = [null, null, 'numero', 'numero', 'numero'];
    preencher(document.querySelector('#tabela-ranking tbody'), itens.map(function (i) {
      return linha([i.posicao + 'º', i.endereco, minutos(i.minutos), numero.format(i.paradas), minutos(i.maiorParadaMin)], num);
    }), 'Nenhuma parada registrada neste recorte.', 5);
  }

  function paradas(itens) {
    preencher(document.querySelector('#tabela-paradas tbody'), itens.map(function (p) {
      var tr = linha([p.motorista, p.ordem + 'º', p.endereco, p.chegada, p.saida, minutos(p.minutos) + (p.acimaDoMaximo ? ' ⚠' : '')],
        [null, null, null, 'numero', 'numero', 'numero']);
      return tr;
    }), 'Nenhuma parada registrada neste dia.', 6);
  }

  function motoristas(itens) {
    preencher(document.querySelector('#tabela-motoristas tbody'), itens.map(function (m) {
      return linha([m.nome, numero.format(m.roteiros), minutos(m.minutosParados), minutos(m.mediaMin),
        decimal1.format(m.percentualJornada) + '%', dinheiro.format(m.custo)], [null, 'numero', 'numero', 'numero', 'numero', 'numero']);
    }), 'Sem roteiros no período.', 6);
  }

  function tabelaSerie(cabecalho, linhas) {
    var thead = document.querySelector('#tabela-serie thead');
    thead.replaceChildren();
    var tr = document.createElement('tr');
    cabecalho.forEach(function (c, i) {
      var th = document.createElement('th'); th.textContent = c; if (i > 0) th.className = 'numero'; tr.appendChild(th);
    });
    thead.appendChild(tr);
    preencher(document.querySelector('#tabela-serie tbody'), linhas, 'Sem dados.', cabecalho.length);
  }

  // ---------- Gráficos (uma série: sem legenda; o título diz o que está plotado) ----------

  // Rótulo direto só na barra de maior valor (nunca um número em cada barra).
  var rotuloMaximo = {
    id: 'rotuloMaximo',
    afterDatasetsDraw: function (chart) {
      var valores = chart.data.datasets[0].data;
      if (!valores.length) return;
      var max = Math.max.apply(null, valores);
      if (max <= 0) return;
      var i = valores.indexOf(max);
      var barra = chart.getDatasetMeta(0).data[i];
      var ctx = chart.ctx;
      ctx.save();
      ctx.fillStyle = cor('--texto');
      ctx.font = '600 12px Barlow, system-ui, sans-serif';
      var t = minutos(max);
      if (chart.options.indexAxis === 'y') {
        ctx.textBaseline = 'middle'; ctx.textAlign = 'left';
        ctx.fillText(t, barra.x + 6, barra.y);
      } else {
        ctx.textBaseline = 'bottom'; ctx.textAlign = 'center';
        ctx.fillText(t, barra.x, barra.y - 4);
      }
      ctx.restore();
    }
  };

  function opcoesBase(horizontal, tooltips) {
    var grade = cor('--grafico-grade'), tinta = cor('--texto-3');
    var eixoValor = {
      beginAtZero: true, grace: '8%',
      grid: { color: grade, lineWidth: 1 }, border: { display: false },
      ticks: { color: tinta, precision: 0, callback: function (v) { return numero.format(v); } },
      title: { display: true, text: 'minutos parados', color: tinta }
    };
    var eixoCategoria = { grid: { display: false }, border: { color: grade }, ticks: { color: tinta, autoSkip: true, maxRotation: 0 } };
    return {
      responsive: true, maintainAspectRatio: false, animation: { duration: 250 },
      indexAxis: horizontal ? 'y' : 'x',
      layout: { padding: { top: 18, right: horizontal ? 64 : 8 } },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: cor('--superficie'), titleColor: cor('--texto-2'), bodyColor: cor('--texto'),
          borderColor: cor('--linha-forte'), borderWidth: 1, padding: 10, displayColors: false,
          bodyFont: { weight: '600' }, callbacks: tooltips
        }
      },
      scales: horizontal ? { x: eixoValor, y: eixoCategoria } : { x: eixoCategoria, y: eixoValor }
    };
  }

  function desenharGrafico(tipoHorizontal, rotulos, valores, tooltips, altura) {
    el('grafico-caixa').style.height = altura + 'px';
    var dataset = {
      data: valores, backgroundColor: cor('--grafico-parado'), hoverBackgroundColor: cor('--amarelo'),
      borderRadius: 4, borderSkipped: 'start', maxBarThickness: 24, categoryPercentage: 0.8, barPercentage: 0.9
    };
    if (grafico) grafico.destroy();
    grafico = new Chart(el('grafico'), {
      type: 'bar',
      data: { labels: rotulos, datasets: [dataset] },
      options: opcoesBase(tipoHorizontal, tooltips),
      plugins: [rotuloMaximo]
    });
  }

  function graficoDia(dados) {
    el('grafico-titulo').textContent = 'Tempo parado em cada ponto · ' + dados.titulo;
    el('grafico-subtitulo').textContent = 'Cada barra é uma parada, ligada ao endereço e aos horários registrados.';
    var itens = dados.paradas;
    var rotulos = itens.map(function (p) { return p.motorista.split(' ')[0] + ' · ' + p.endereco.split(' - ')[0]; });
    desenharGrafico(true, rotulos, itens.map(function (p) { return p.minutos; }), {
      title: function (c) { var p = itens[c[0].dataIndex]; return p.motorista + ' · ' + p.ordem + 'º ponto'; },
      label: function (c) { return minutos(c.raw) + ' parado'; },
      afterLabel: function (c) { var p = itens[c.dataIndex]; return p.endereco + '\nChegada ' + p.chegada + ' · saída ' + p.saida; }
    }, Math.max(240, 60 + itens.length * 30));
    el('grafico').setAttribute('aria-label', 'Barras horizontais: tempo parado em ' + itens.length + ' paradas. Os números estão na tabela.');
    tabelaSerie(['Parada', 'Minutos', 'Chegada', 'Saída'], itens.map(function (p) {
      return linha([p.motorista + ' · ' + p.endereco, numero.format(p.minutos), p.chegada, p.saida], [null, 'numero', 'numero', 'numero']);
    }));
  }

  function graficoSerie(itens, titulo, nomeColuna) {
    el('grafico-titulo').textContent = titulo;
    el('grafico-subtitulo').textContent = 'Soma do tempo parado de todos os roteiros com coleta.';
    desenharGrafico(false, itens.map(function (i) { return i.rotulo; }), itens.map(function (i) { return i.minutosParados; }), {
      title: function (c) { return itens[c[0].dataIndex].rotulo; },
      label: function (c) { return minutos(c.raw) + ' parados'; },
      afterLabel: function (c) {
        var i = itens[c.dataIndex];
        return numero.format(i.roteiros) + ' roteiro(s) · ' + decimal1.format(i.percentualJornada) + '% da jornada · ' + dinheiro.format(i.custo);
      }
    }, 320);
    el('grafico').setAttribute('aria-label', 'Colunas: ' + titulo + '. Os números estão na tabela.');
    tabelaSerie([nomeColuna, 'Roteiros', 'Parado', '% jornada', 'Custo'], itens.map(function (i) {
      return linha([i.rotulo, numero.format(i.roteiros), minutos(i.minutosParados), decimal1.format(i.percentualJornada) + '%',
        dinheiro.format(i.custo)], [null, 'numero', 'numero', 'numero', 'numero']);
    }));
  }

  // ---------- Eventos ----------

  document.querySelectorAll('[role="tab"]').forEach(function (t) {
    t.addEventListener('click', function () { trocarAba(t.getAttribute('data-aba')); });
    t.addEventListener('keydown', function (e) {
      var abas = Array.prototype.slice.call(document.querySelectorAll('[role="tab"]'));
      var i = abas.indexOf(t);
      if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
        var prox = abas[(i + (e.key === 'ArrowRight' ? 1 : abas.length - 1)) % abas.length];
        prox.focus(); trocarAba(prox.getAttribute('data-aba'));
      }
    });
  });
  ['f-data', 'f-mes', 'f-inicio', 'f-fim', 'f-motorista'].forEach(function (id) { el(id).addEventListener('change', carregar); });
  document.addEventListener('pm:tema', function () { if (ultimo) desenhar(ultimo.aba, ultimo.dados); });
  if (window.matchMedia) {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function () { if (ultimo) desenhar(ultimo.aba, ultimo.dados); });
  }

  carregar();
})();
