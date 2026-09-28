package br.pucminas.pontomorto.servico;

import br.pucminas.pontomorto.dominio.Parametro;
import br.pucminas.pontomorto.dominio.Ponto;
import br.pucminas.pontomorto.dominio.Roteiro;
import br.pucminas.pontomorto.dominio.Veiculo;
import br.pucminas.pontomorto.regras.CalculadoraCusto;
import br.pucminas.pontomorto.regras.CalculadoraTempoParado;
import br.pucminas.pontomorto.regras.ParametrosTempo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Recalcula os números de um roteiro a partir dos pontos e dos parâmetros (RF06, RF11):
 * tempo parado de cada ponto, total do roteiro e custo estimado.
 */
@Service
public class CalculoRoteiroService {

    private final ParametroService parametros;

    public CalculoRoteiroService(ParametroService parametros) {
        this.parametros = parametros;
    }

    public void recalcular(Roteiro roteiro) {
        recalcular(roteiro, parametros.obter());
    }

    public void recalcular(Roteiro roteiro, Parametro parametro) {
        ParametrosTempo regras = parametro.paraCalculoDeTempo();
        long total = 0;
        for (Ponto ponto : roteiro.getPontos()) {
            CalculadoraTempoParado.ResultadoParada resultado = CalculadoraTempoParado.calcularParada(ponto.comoParada(), regras);
            ponto.aplicar(resultado);
            if (!resultado.partida() && resultado.segundosConsiderados() != null) {
                total += resultado.segundosConsiderados();
            }
        }
        roteiro.setTempoTotalParadoSeg(total);

        BigDecimal kmLitro = kmPorLitro(roteiro, parametro);
        BigDecimal custo = CalculadoraCusto.custoDoTrajeto(roteiro.getDistanciaParaCusto(), kmLitro,
                parametro.getPrecoCombustivel(), parametro.getCustoPorKm());
        roteiro.definirCusto(custo, parametro.getPrecoCombustivel(), kmLitro, parametro.getCustoPorKm());
    }

    /** km/l do veículo do motorista; se não houver veículo, usa o valor padrão dos parâmetros. */
    static BigDecimal kmPorLitro(Roteiro roteiro, Parametro parametro) {
        Veiculo veiculo = roteiro.getMotorista().getVeiculo();
        if (veiculo != null && veiculo.getRendimentoKmLitro() != null && veiculo.getRendimentoKmLitro().signum() > 0) {
            return veiculo.getRendimentoKmLitro();
        }
        return parametro.getKmLitroPadrao();
    }
}
