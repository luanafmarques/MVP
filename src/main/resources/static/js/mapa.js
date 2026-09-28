// RF03: busca latitude e longitude pelo endereço (Nominatim, pelo servidor) e permite ajuste manual
// digitando ou arrastando o marcador no mapa (Leaflet + OpenStreetMap).
(function () {
  'use strict';
  var caixa = document.querySelector('[data-geocodificacao]');
  if (!caixa) return;
  var base = caixa.getAttribute('data-base') || '/';
  var campoEndereco = document.getElementById(caixa.getAttribute('data-endereco'));
  var campoLat = document.getElementById(caixa.getAttribute('data-lat'));
  var campoLon = document.getElementById(caixa.getAttribute('data-lon'));
  var mensagem = caixa.querySelector('[data-mensagem]');
  var botao = caixa.querySelector('[data-buscar]');
  var centroPadrao = [-19.9191, -43.9386]; // Belo Horizonte
  var mapa = null, marcador = null;

  function valor(campo) { var v = parseFloat(campo.value); return isNaN(v) ? null : v; }

  function iniciarMapa() {
    if (typeof L === 'undefined') return;
    var lat = valor(campoLat), lon = valor(campoLon);
    var temPonto = lat !== null && lon !== null;
    mapa = L.map(caixa.querySelector('.mapa')).setView(temPonto ? [lat, lon] : centroPadrao, temPonto ? 16 : 12);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19, attribution: '&copy; colaboradores do OpenStreetMap'
    }).addTo(mapa);
    if (temPonto) colocarMarcador(lat, lon);
    mapa.on('click', function (e) { colocarMarcador(e.latlng.lat, e.latlng.lng); preencher(e.latlng.lat, e.latlng.lng); });
  }

  function colocarMarcador(lat, lon) {
    if (!mapa) return;
    if (!marcador) {
      marcador = L.marker([lat, lon], { draggable: true }).addTo(mapa);
      marcador.on('dragend', function () { var p = marcador.getLatLng(); preencher(p.lat, p.lng); });
    } else {
      marcador.setLatLng([lat, lon]);
    }
  }

  function preencher(lat, lon) {
    campoLat.value = Number(lat).toFixed(6);
    campoLon.value = Number(lon).toFixed(6);
    mensagem.textContent = 'Coordenadas ajustadas manualmente.';
  }

  function buscar() {
    var endereco = campoEndereco.value.trim();
    if (!endereco) { mensagem.textContent = 'Digite o endereço primeiro.'; return; }
    botao.disabled = true;
    mensagem.textContent = 'Buscando no mapa...';
    fetch(base + 'api/geocodificar?endereco=' + encodeURIComponent(endereco))
      .then(function (r) { return r.json(); })
      .then(function (d) {
        if (d.encontrado) {
          campoLat.value = d.latitude; campoLon.value = d.longitude;
          mensagem.textContent = 'Encontrado: ' + (d.enderecoEncontrado || 'ok') + '. Arraste o marcador se precisar ajustar.';
          colocarMarcador(d.latitude, d.longitude);
          if (mapa) mapa.setView([d.latitude, d.longitude], 16);
        } else {
          mensagem.textContent = d.mensagem;
        }
      })
      .catch(function () { mensagem.textContent = 'Não foi possível buscar agora. Digite as coordenadas ou clique no mapa.'; })
      .finally(function () { botao.disabled = false; });
  }

  botao.addEventListener('click', buscar);
  [campoLat, campoLon].forEach(function (c) {
    c.addEventListener('change', function () {
      var lat = valor(campoLat), lon = valor(campoLon);
      if (lat !== null && lon !== null) { colocarMarcador(lat, lon); if (mapa) mapa.setView([lat, lon], 16); }
    });
  });
  iniciarMapa();
})();
