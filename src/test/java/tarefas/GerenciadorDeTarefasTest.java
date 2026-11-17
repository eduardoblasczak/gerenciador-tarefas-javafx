package tarefas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GerenciadorDeTarefasTest {
    private final LocalDate hoje = LocalDate.of(2026, 11, 12);
    private GerenciadorDeTarefas g;
    @BeforeEach void preparar() { g = new GerenciadorDeTarefas(); }
    private Tarefa criar(String titulo) { return g.criar(titulo, "", null, null, hoje); }
    private void erro(String mensagem, Runnable acao) {
        assertEquals(mensagem, assertThrows(IllegalArgumentException.class, acao::run).getMessage());
    }

    @Test void criaPendenteComPrioridadeMedia() {
        Tarefa t = criar("Estudar BDD");
        assertAll(() -> assertEquals(1, t.id()), () -> assertEquals(Status.PENDENTE, t.status()),
                () -> assertEquals(Prioridade.MEDIA, t.prioridade()), () -> assertNull(t.prazo()),
                () -> assertEquals(1, g.listar().size()));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ", "\t", "\n"})
    void rejeitaTituloVazioSemCriar(String titulo) {
        erro("O título é obrigatório", () -> criar(titulo));
        assertTrue(g.listar().isEmpty());
    }
    @Test void normalizaEspacosEDescricaoNula() {
        Tarefa t = g.criar("  Estudar  ", null, null, null, hoje);
        assertEquals("Estudar", t.titulo()); assertEquals("", t.descricao());
    }
    @Test void idsNaoSeRepetemDepoisDeExcluir() {
        long id = criar("Primeira").id(); g.excluir(id);
        assertTrue(criar("Segunda").id() > id);
    }
    @Test void editaTodosOsCamposSemMudarIdOuStatus() {
        Tarefa t = criar("Antigo"); g.concluir(t.id());
        Tarefa editada = g.editar(t.id(), "Novo", "Descrição", Prioridade.ALTA, hoje.plusDays(2), hoje);
        assertAll(() -> assertEquals(t.id(), editada.id()), () -> assertEquals("Novo", editada.titulo()),
                () -> assertEquals("Descrição", editada.descricao()), () -> assertEquals(Status.CONCLUIDA, editada.status()),
                () -> assertEquals(Prioridade.ALTA, editada.prioridade()), () -> assertEquals(hoje.plusDays(2), editada.prazo()));
    }
    @Test void edicaoInvalidaNaoMudaNenhumCampo() {
        Tarefa t = criar("Original");
        erro("O título é obrigatório", () -> g.editar(t.id(), " ", "Outra", Prioridade.ALTA, hoje, hoje));
        assertEquals(t, g.obter(t.id()));
    }
    @Test void prazoInvalidoNaEdicaoNaoMudaNenhumCampo() {
        Tarefa t = criar("Original");
        erro("O prazo não pode ser no passado", () -> g.editar(t.id(), "Novo", "", Prioridade.ALTA, hoje.minusDays(1), hoje));
        assertEquals(t, g.obter(t.id()));
    }
    @Test void permiteEditarTituloMantendoPrazoQueVenceuDepoisDaCriacao() {
        Tarefa t = g.criar("Original", "", null, hoje, hoje);
        g.editar(t.id(), "Novo", "", null, hoje, hoje.plusDays(1));
        assertEquals("Novo", g.obter(t.id()).titulo());
    }
    @Test void concluiEReabre() {
        long id = criar("Lavar a louça").id(); g.concluir(id);
        assertEquals(Status.CONCLUIDA, g.obter(id).status());
        g.reabrir(id); assertEquals(Status.PENDENTE, g.obter(id).status());
    }
    @Test void naoConcluiDuasVezes() {
        long id = criar("Louça").id(); g.concluir(id);
        erro("A tarefa já está concluída", () -> g.concluir(id));
    }
    @Test void naoReabrePendente() {
        long id = criar("Louça").id();
        erro("A tarefa já está pendente", () -> g.reabrir(id));
    }
    @Test void excluiExistente() {
        long id = criar("Boleto").id(); g.excluir(id); assertTrue(g.listar().isEmpty());
        erro("Tarefa não encontrada", () -> g.obter(id));
    }
    @Test void alteraPrioridade() {
        long id = criar("Ler").id(); g.definirPrioridade(id, Prioridade.ALTA);
        assertEquals(Prioridade.ALTA, g.obter(id).prioridade());
    }
    @Test void rejeitaPrioridadeNulaNaAlteracao() {
        long id = criar("Ler").id();
        erro("A prioridade é obrigatória", () -> g.definirPrioridade(id, null));
        assertEquals(Prioridade.MEDIA, g.obter(id).prioridade());
    }
    @Test void aceitaPrazoHojeEFuturoEPodeRemover() {
        long id = criar("Entregar").id(); g.definirPrazo(id, hoje, hoje);
        assertFalse(g.obter(id).atrasada(hoje));
        g.definirPrazo(id, hoje.plusDays(7), hoje); assertEquals(hoje.plusDays(7), g.obter(id).prazo());
        g.definirPrazo(id, null, hoje); assertNull(g.obter(id).prazo());
    }
    @Test void rejeitaCriacaoComPrazoPassado() {
        erro("O prazo não pode ser no passado", () -> g.criar("Entrega", "", null, hoje.minusDays(1), hoje));
        assertTrue(g.listar().isEmpty()); assertEquals(1, criar("Válida").id());
    }
    @Test void rejeitaPrazoPassadoSemAlterarTarefa() {
        Tarefa t = criar("Entrega");
        erro("O prazo não pode ser no passado", () -> g.definirPrazo(t.id(), hoje.minusDays(1), hoje));
        assertEquals(t, g.obter(t.id()));
    }
    @Test void rejeitaHojeNulo() {
        erro("A data de hoje é obrigatória", () -> g.criar("Entrega", "", null, null, null));
    }
    @Test void atrasoDependeDaDataEStatus() {
        Tarefa t = g.criar("Entrega", "", null, hoje, hoje);
        assertFalse(t.atrasada(hoje)); assertTrue(t.atrasada(hoje.plusDays(1)));
        g.concluir(t.id()); assertFalse(g.obter(t.id()).atrasada(hoje.plusDays(1)));
        g.reabrir(t.id()); assertTrue(g.obter(t.id()).atrasada(hoje.plusDays(1)));
        assertFalse(criar("Sem prazo").atrasada(hoje.plusYears(1)));
    }
    @Test void filtraPrioridadeStatusEBuscaEmConjunto() {
        Tarefa alta = g.criar("Estudar Java", "", Prioridade.ALTA, null, hoje);
        long baixa = g.criar("Ir ao mercado", "", Prioridade.BAIXA, null, hoje).id();
        criar("Fazer slides"); g.concluir(baixa);
        assertEquals(2, g.consultar(null, Status.PENDENTE, "").size());
        assertEquals(List.of(alta), g.consultar(Prioridade.ALTA, Status.PENDENTE, ""));
        assertEquals(3, g.consultar(null, null, "").size());
    }
}
