package br.pucminas.pontomorto.servico;

import br.pucminas.pontomorto.dominio.Pedido;
import br.pucminas.pontomorto.dominio.Ponto;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/** RF12: exporta o histórico do período consultado em CSV (abre no Excel) e PDF. */
@Service
public class RelatorioService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color ASFALTO = new Color(0x2B, 0x2F, 0x33);
    private static final Color AMARELO = new Color(0xF5, 0xC2, 0x00);
    private static final Color CINZA_CLARO = new Color(0xF1, 0xF2, 0xF3);

    private final Clock relogio;

    public RelatorioService(Clock relogio) {
        this.relogio = relogio;
    }

    /** CSV com ";" e BOM UTF-8, para abrir direto no Excel em português. */
    public byte[] csv(List<Ponto> pontos) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("Data;Motorista;Ordem;Endereço;Latitude;Longitude;Chegada;Saída;Tempo parado (min);Situação;Pedidos\n");
        for (Ponto p : pontos) {
            sb.append(p.getRoteiro().getData().format(DATA)).append(';')
                    .append(celula(p.getRoteiro().getMotorista().getNome())).append(';')
                    .append(p.getOrdem()).append(';')
                    .append(celula(p.getEndereco())).append(';')
                    .append(p.getLatitude() == null ? "" : p.getLatitude().toPlainString().replace('.', ',')).append(';')
                    .append(p.getLongitude() == null ? "" : p.getLongitude().toPlainString().replace('.', ',')).append(';')
                    .append(p.getChegada() == null ? "" : p.getChegada().format(DATA_HORA)).append(';')
                    .append(p.getSaida() == null ? "" : p.getSaida().format(DATA_HORA)).append(';')
                    .append(p.isPartida() || p.getTempoParadoMin() == null ? "" : p.getTempoParadoMin()).append(';')
                    .append(situacao(p)).append(';')
                    .append(celula(pedidos(p))).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] pdf(List<Ponto> pontos, String periodo, String motorista) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
        PdfWriter.getInstance(documento, saida);
        documento.open();

        Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, ASFALTO);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9, ASFALTO);
        Font negrito = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, ASFALTO);
        Font cabecalho = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);

        Paragraph marca = new Paragraph();
        marca.add(new Chunk("PONTO MORTO", titulo));
        marca.add(new Chunk("   Histórico de pontos e tempos parados", FontFactory.getFont(FontFactory.HELVETICA, 12, ASFALTO)));
        documento.add(marca);
        documento.add(new Paragraph("Período: " + periodo + "   ·   Motorista: " + motorista
                + "   ·   Gerado em " + LocalDateTime.now(relogio).format(DATA_HORA), normal));

        long totalMin = pontos.stream().filter(p -> !p.isPartida() && p.getTempoParadoMin() != null)
                .mapToLong(Ponto::getTempoParadoMin).sum();
        long paradas = pontos.stream().filter(p -> !p.isPartida() && p.getTempoParadoMin() != null).count();
        Paragraph resumo = new Paragraph("Paradas: " + paradas + "   ·   Tempo parado total: " + formatarMinutos(totalMin)
                + "   (o ponto de partida não conta tempo parado)", negrito);
        resumo.setSpacingAfter(10);
        documento.add(resumo);

        float[] larguras = {7, 13, 4, 26, 11, 11, 7, 9, 12};
        PdfPTable tabela = new PdfPTable(larguras);
        tabela.setWidthPercentage(100);
        tabela.setHeaderRows(1);
        for (String coluna : List.of("Data", "Motorista", "Ordem", "Endereço", "Chegada", "Saída", "Parado", "Situação", "Pedidos")) {
            PdfPCell celula = new PdfPCell(new Phrase(coluna, cabecalho));
            celula.setBackgroundColor(ASFALTO);
            celula.setPadding(5);
            tabela.addCell(celula);
        }
        boolean zebra = false;
        for (Ponto p : pontos) {
            Color fundo = zebra ? CINZA_CLARO : Color.WHITE;
            zebra = !zebra;
            adicionar(tabela, p.getRoteiro().getData().format(DATA), normal, fundo);
            adicionar(tabela, p.getRoteiro().getMotorista().getNome(), normal, fundo);
            adicionar(tabela, String.valueOf(p.getOrdem()), normal, fundo);
            adicionar(tabela, p.getEndereco(), normal, fundo);
            adicionar(tabela, p.getChegada() == null ? "—" : p.getChegada().format(DATA_HORA), normal, fundo);
            adicionar(tabela, p.getSaida() == null ? "—" : p.getSaida().format(DATA_HORA), normal, fundo);
            PdfPCell parado = new PdfPCell(new Phrase(p.isPartida() || p.getTempoParadoMin() == null ? "—"
                    : p.getTempoParadoMin() + " min", negrito));
            parado.setBackgroundColor(p.isPartida() || p.getTempoParadoMin() == null ? fundo : AMARELO);
            parado.setHorizontalAlignment(Element.ALIGN_RIGHT);
            parado.setPadding(4);
            tabela.addCell(parado);
            adicionar(tabela, situacao(p), normal, fundo);
            adicionar(tabela, pedidos(p), normal, fundo);
        }
        documento.add(tabela);
        documento.close();
        return saida.toByteArray();
    }

    private static void adicionar(PdfPTable tabela, String texto, Font fonte, Color fundo) {
        PdfPCell celula = new PdfPCell(new Phrase(texto == null ? "" : texto, fonte));
        celula.setBackgroundColor(fundo);
        celula.setPadding(4);
        tabela.addCell(celula);
    }

    static String situacao(Ponto p) {
        if (p.isPartida()) {
            return "Partida (não conta)";
        }
        if (p.getSaida() == null) {
            return p.getChegada() == null ? "Não visitado" : "No local";
        }
        if (p.isIgnorada()) {
            return "Parada curta (ignorada)";
        }
        if (p.isAcimaDoMaximo()) {
            return "Acima do máximo (" + p.getTempoBrutoMin() + " min)";
        }
        return "Registrado";
    }

    private static String pedidos(Ponto p) {
        return p.getPedidos().stream().map(Pedido::getNumero).collect(Collectors.joining(", "));
    }

    private static String celula(String texto) {
        if (texto == null) {
            return "";
        }
        String limpo = texto.replace("\"", "\"\"");
        return limpo.contains(";") || limpo.contains("\n") ? "\"" + limpo + "\"" : limpo;
    }

    public static String formatarMinutos(long minutos) {
        if (minutos < 60) {
            return minutos + " min";
        }
        return (minutos / 60) + " h " + String.format("%02d", minutos % 60) + " min";
    }
}
