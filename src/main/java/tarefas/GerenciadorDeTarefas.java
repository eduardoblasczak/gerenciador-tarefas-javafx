package tarefas;

import java.time.LocalDate;
import java.util.*;

/** Regras de negócio sem dependência de JavaFX, relógio global ou banco de dados. */
public class GerenciadorDeTarefas {
    private final Map<Long, Tarefa> tarefas = new LinkedHashMap<>();
    private long proximoId = 1;

    public Tarefa criar(String titulo, String descricao, Prioridade prioridade, LocalDate prazo, LocalDate hoje) {
        String valido = validarTitulo(titulo);
        validarPrazo(prazo, hoje);
        Tarefa tarefa = new Tarefa(proximoId++, valido, normalizarDescricao(descricao),
                Status.PENDENTE, prioridade == null ? Prioridade.MEDIA : prioridade, prazo);
        tarefas.put(tarefa.id(), tarefa);
        return tarefa;
    }

    public Tarefa editar(long id, String titulo, String descricao, Prioridade prioridade, LocalDate prazo, LocalDate hoje) {
        Tarefa anterior = obter(id);
        String valido = validarTitulo(titulo);
        // Um prazo que venceu desde a criação não impede editar o título.
        validarHoje(hoje);
        if (!Objects.equals(prazo, anterior.prazo())) validarPrazo(prazo, hoje);
        Tarefa nova = new Tarefa(id, valido, normalizarDescricao(descricao), anterior.status(),
                prioridade == null ? Prioridade.MEDIA : prioridade, prazo);
        tarefas.put(id, nova);
        return nova;
    }

    public Tarefa obter(long id) {
        Tarefa tarefa = tarefas.get(id);
        if (tarefa == null) throw new IllegalArgumentException("Tarefa não encontrada");
        return tarefa;
    }
    public void concluir(long id) {
        Tarefa t = obter(id);
        if (t.status() == Status.CONCLUIDA) throw new IllegalArgumentException("A tarefa já está concluída");
        atualizar(t, Status.CONCLUIDA, t.prioridade(), t.prazo());
    }
    public void reabrir(long id) {
        Tarefa t = obter(id);
        if (t.status() == Status.PENDENTE) throw new IllegalArgumentException("A tarefa já está pendente");
        atualizar(t, Status.PENDENTE, t.prioridade(), t.prazo());
    }
    public void excluir(long id) { obter(id); tarefas.remove(id); }
    public void definirPrioridade(long id, Prioridade prioridade) {
        Tarefa t = obter(id);
        if (prioridade == null) throw new IllegalArgumentException("A prioridade é obrigatória");
        atualizar(t, t.status(), prioridade, t.prazo());
    }
    public void definirPrazo(long id, LocalDate prazo, LocalDate hoje) {
        Tarefa t = obter(id); validarPrazo(prazo, hoje);
        atualizar(t, t.status(), t.prioridade(), prazo);
    }
    public List<Tarefa> listar() { return List.copyOf(tarefas.values()); }
    public List<Tarefa> consultar(Prioridade prioridade, Status status, String busca) {
        String termo = busca == null ? "" : busca.strip().toLowerCase(Locale.ROOT);
        return tarefas.values().stream()
                .filter(t -> prioridade == null || t.prioridade() == prioridade)
                .filter(t -> status == null || t.status() == status)
                .filter(t -> t.titulo().toLowerCase(Locale.ROOT).contains(termo))
                .sorted(Comparator.comparing(Tarefa::prioridade).thenComparingLong(Tarefa::id))
                .toList();
    }
    private void atualizar(Tarefa t, Status status, Prioridade prioridade, LocalDate prazo) {
        tarefas.put(t.id(), new Tarefa(t.id(), t.titulo(), t.descricao(), status, prioridade, prazo));
    }
    private String validarTitulo(String titulo) {
        if (titulo == null || titulo.isBlank()) throw new IllegalArgumentException("O título é obrigatório");
        return titulo.strip();
    }
    private String normalizarDescricao(String descricao) { return descricao == null ? "" : descricao.strip(); }
    private void validarHoje(LocalDate hoje) {
        if (hoje == null) throw new IllegalArgumentException("A data de hoje é obrigatória");
    }
    private void validarPrazo(LocalDate prazo, LocalDate hoje) {
        validarHoje(hoje);
        if (prazo != null && prazo.isBefore(hoje)) throw new IllegalArgumentException("O prazo não pode ser no passado");
    }
}
