package tarefas;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** A tela só coordena controles; validações e alterações pertencem ao domínio. */
public class TelaTarefas {
    private GerenciadorDeTarefas gerenciador = new GerenciadorDeTarefas();
    private Supplier<LocalDate> relogio;
    private final BorderPane raiz = new BorderPane();
    private final TableView<Tarefa> tabela = new TableView<>();
    private final TextField titulo = new TextField();
    private final TextArea descricao = new TextArea();
    private final ComboBox<Prioridade> prioridade = new ComboBox<>();
    private final DatePicker prazo = new DatePicker();
    private final TextField busca = new TextField();
    private final ComboBox<String> filtroPrioridade = new ComboBox<>();
    private final ComboBox<String> filtroStatus = new ComboBox<>();
    private final Label mensagem = new Label("Tudo pronto. Crie sua primeira tarefa.");
    private final Label editorTitulo = new Label("Nova tarefa");
    private final Label dataAtual = new Label();
    private final Label total = new Label("0"), pendentes = new Label("0"), concluidas = new Label("0"), atrasadas = new Label("0");
    private final Label resultado = new Label();
    private final Button salvar = new Button("Criar tarefa");
    private final Button alternar = new Button("Concluir"), excluir = new Button("Excluir");
    private final HBox confirmacao = new HBox(8);
    private Long editando;
    private boolean atualizando;
    private String prazoInvalido;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    public TelaTarefas(Supplier<LocalDate> relogio) {
        this.relogio = relogio;
        raiz.getStyleClass().add("app");
        construirLateral(); construirConteudo(); atualizar(); novo();
    }
    public Parent raiz() { return raiz; }

    private void construirLateral() {
        Label marca = rotulo("em dia", "marca");
        Label subtitulo = rotulo("Espaço para fazer acontecer.", "lateral-texto"); subtitulo.setWrapText(true);
        Label secao = rotulo("MEU ESPAÇO", "lateral-secao");
        Button todas = botao("Minhas tarefas", "minhas-tarefas", "nav-ativo", () -> {
            busca.clear(); filtroPrioridade.getSelectionModel().selectFirst(); filtroStatus.getSelectionModel().selectFirst();
        }); todas.setMaxWidth(Double.MAX_VALUE);
        Region espaco = new Region(); VBox.setVgrow(espaco, Priority.ALWAYS);
        Label dica = rotulo("Um passo de cada vez.", "lateral-dica");
        Label memoria = rotulo("Sessão em memória\nAs tarefas são apagadas ao fechar.", "lateral-texto"); memoria.setWrapText(true);
        VBox lateral = new VBox(18, marca, subtitulo, secao, todas, espaco, dica, memoria);
        lateral.setPrefWidth(205); lateral.setMinWidth(190); lateral.getStyleClass().add("lateral");
        raiz.setLeft(lateral);
    }
    private void construirConteudo() {
        Label sobre = rotulo("ORGANIZE SEU DIA", "sobretitulo");
        Label heading = rotulo("Minhas tarefas", "heading");
        VBox cabecalho = new VBox(5, sobre, heading, rotulo("Tire as ideias da cabeça e acompanhe cada entrega.", "texto-suave"));
        dataAtual.getStyleClass().add("data-atual"); dataAtual.setId("data-atual");
        Region espacador = new Region(); HBox.setHgrow(espacador, Priority.ALWAYS);
        HBox topo = new HBox(12, cabecalho, espacador, dataAtual); topo.setAlignment(Pos.CENTER_LEFT);
        HBox indicadores = new HBox(12,
                indicador("TOTAL DE TAREFAS", total, "total", ""),
                indicador("PARA FAZER", pendentes, "pendentes", "numero-azul"),
                indicador("CONCLUÍDAS", concluidas, "concluidas", "numero-verde"),
                indicador("EM ATRASO", atrasadas, "atrasadas", "numero-vermelho"));
        busca.setId("busca"); busca.setPromptText("Buscar pelo título..."); HBox.setHgrow(busca, Priority.ALWAYS);
        filtroPrioridade.setId("filtro-prioridade"); filtroPrioridade.getItems().add("Todas as prioridades");
        for (Prioridade p : Prioridade.values()) filtroPrioridade.getItems().add(p.toString());
        filtroPrioridade.getSelectionModel().selectFirst();
        filtroStatus.setId("filtro-status"); filtroStatus.getItems().setAll("Todos os status", "Pendente", "Concluída"); filtroStatus.getSelectionModel().selectFirst();
        Button limpar = botao("Limpar", "limpar-filtros", "botao-suave", () -> {
            busca.clear(); filtroPrioridade.getSelectionModel().selectFirst(); filtroStatus.getSelectionModel().selectFirst();
        });
        HBox filtros = new HBox(8, busca, filtroPrioridade, filtroStatus, limpar);
        busca.textProperty().addListener((o,a,b) -> atualizar());
        filtroPrioridade.valueProperty().addListener((o,a,b) -> atualizar());
        filtroStatus.valueProperty().addListener((o,a,b) -> atualizar());

        tabela.setId("tabela"); tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        coluna("TAREFA", t -> t.titulo(), 240);
        coluna("PRIORIDADE", t -> t.prioridade().toString(), 95);
        coluna("STATUS", t -> t.status().toString(), 100);
        coluna("PRAZO", t -> t.prazo() == null ? "Sem prazo" : DATA.format(t.prazo()) + (t.atrasada(hoje()) ? " · atrasada" : ""), 150);
        VBox vazio = new VBox(8, rotulo("Tudo livre por aqui", "vazio-titulo"), rotulo("Crie uma tarefa ou ajuste seus filtros.", "texto-suave")); vazio.setAlignment(Pos.CENTER);
        tabela.setPlaceholder(vazio);
        tabela.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Tarefa t, boolean vazia) {
                super.updateItem(t, vazia);
                pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("atrasada"), !vazia && t != null && t.atrasada(hoje()));
                pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("concluida"), !vazia && t != null && t.status() == Status.CONCLUIDA);
            }
        });
        tabela.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> { if (!atualizando) { if (b == null) novo(); else carregar(b); } });
        resultado.setId("resultado"); resultado.getStyleClass().add("texto-suave");
        Button nova = botao("+ Nova tarefa", "nova-tarefa", "botao-primario", this::novo);
        Region folga = new Region(); HBox.setHgrow(folga, Priority.ALWAYS);
        HBox listaTopo = new HBox(10, rotulo("Suas tarefas", "titulo-secao"), folga, nova); listaTopo.setAlignment(Pos.CENTER_LEFT);
        VBox lista = new VBox(14, listaTopo, tabela, resultado); lista.getStyleClass().add("painel"); VBox.setVgrow(tabela, Priority.ALWAYS);
        HBox.setHgrow(lista, Priority.ALWAYS); lista.setMinWidth(460);
        ScrollPane editorRolavel = new ScrollPane(construirEditor());
        editorRolavel.setFitToWidth(true); editorRolavel.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        editorRolavel.setPrefWidth(290); editorRolavel.setMinWidth(270); editorRolavel.getStyleClass().add("editor-rolavel");
        HBox corpo = new HBox(16, lista, editorRolavel); VBox.setVgrow(corpo, Priority.ALWAYS);
        mensagem.setId("mensagem"); mensagem.setWrapText(true); mensagem.getStyleClass().add("mensagem"); mensagem.setMinHeight(34);
        VBox conteudo = new VBox(20, topo, indicadores, filtros, corpo, mensagem); conteudo.setPadding(new Insets(30, 28, 18, 28));
        raiz.setCenter(conteudo);
    }
    private VBox construirEditor() {
        editorTitulo.getStyleClass().add("titulo-secao"); editorTitulo.setId("editor-titulo");
        titulo.setId("titulo"); titulo.setPromptText("O que precisa ser feito?");
        descricao.setId("descricao"); descricao.setPromptText("Adicione os detalhes (opcional)"); descricao.setWrapText(true); descricao.setPrefRowCount(3);
        prioridade.setId("prioridade"); prioridade.getItems().setAll(Prioridade.values()); prioridade.setMaxWidth(Double.MAX_VALUE);
        prazo.setId("prazo"); prazo.setPromptText("dd/mm/aaaa (opcional)"); prazo.setMaxWidth(Double.MAX_VALUE); prazo.setEditable(true);
        prazo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(LocalDate d) { return prazoInvalido != null ? prazoInvalido : d == null ? "" : DATA.format(d); }
            @Override public LocalDate fromString(String s) {
                if (s == null || s.isBlank()) { prazoInvalido = null; return null; }
                try { LocalDate data = LocalDate.parse(s.strip(), DATA); prazoInvalido = null; return data; }
                catch (DateTimeParseException e) { prazoInvalido = s; return prazo.getValue(); }
            }
        });
        prazo.valueProperty().addListener((o,a,b) -> {
            prazoInvalido = null;
            prazo.getEditor().setText(b == null ? "" : DATA.format(b));
        });
        prazo.setDayCellFactory(picker -> new DateCell() {
            @Override public void updateItem(LocalDate dia, boolean vazio) { super.updateItem(dia, vazio); setDisable(vazio || dia.isBefore(hoje())); }
        });
        salvar.setId("salvar"); salvar.getStyleClass().add("botao-primario"); salvar.setMaxWidth(Double.MAX_VALUE); salvar.setOnAction(e -> salvar());
        alternar.setId("alternar-status"); alternar.getStyleClass().add("botao-suave"); alternar.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(alternar, Priority.ALWAYS);
        alternar.setOnAction(e -> executar(() -> {
            long id = selecionada(); Tarefa t = gerenciador.obter(id);
            if (t.status() == Status.PENDENTE) gerenciador.concluir(id); else gerenciador.reabrir(id);
            atualizar(); selecionar(id); informar("Status atualizado.", false);
        }));
        excluir.setId("excluir"); excluir.getStyleClass().add("botao-perigo"); excluir.setOnAction(e -> mostrarConfirmacao(true));
        HBox acoes = new HBox(8, alternar, excluir);
        Button confirmar = botao("Confirmar", "confirmar-exclusao", "botao-perigo", () -> executar(() -> {
            gerenciador.excluir(selecionada()); novo(); atualizar(); informar("Tarefa excluída.", false);
        }));
        Button cancelar = botao("Cancelar", "cancelar-exclusao", "botao-suave", () -> mostrarConfirmacao(false));
        confirmacao.getChildren().addAll(confirmar, cancelar); confirmacao.setId("confirmacao"); mostrarConfirmacao(false);
        Label ajuda = rotulo("Selecione uma tarefa na lista para editar, concluir ou excluir.", "texto-suave"); ajuda.setWrapText(true); ajuda.setMinHeight(Region.USE_PREF_SIZE);
        VBox editor = new VBox(10, editorTitulo, campo("Título *", titulo), campo("Descrição", descricao),
                campo("Prioridade", prioridade), campo("Prazo", prazo), salvar, acoes, confirmacao, ajuda);
        editor.setPrefWidth(290); editor.setMinWidth(260); editor.setMaxWidth(310); editor.getStyleClass().add("painel");
        return editor;
    }
    private void salvar() {
        executar(() -> {
            LocalDate data;
            String texto = prazo.getEditor().getText();
            try { data = texto == null || texto.isBlank() ? null : LocalDate.parse(texto.strip(), DATA); }
            catch (DateTimeParseException e) { throw new IllegalArgumentException("Informe uma data válida no formato dd/mm/aaaa"); }
            boolean criando = editando == null;
            Tarefa t = criando ? gerenciador.criar(titulo.getText(), descricao.getText(), prioridade.getValue(), data, hoje())
                    : gerenciador.editar(editando, titulo.getText(), descricao.getText(), prioridade.getValue(), data, hoje());
            atualizar();
            if (tabela.getItems().stream().anyMatch(item -> item.id() == t.id())) selecionar(t.id()); else novo();
            informar(criando ? "Tarefa criada com sucesso." : "Alterações salvas.", false);
        });
    }
    private void executar(Runnable acao) {
        try { acao.run(); } catch (IllegalArgumentException e) { informar(e.getMessage(), true); }
    }
    private long selecionada() {
        if (editando == null) throw new IllegalArgumentException("Selecione uma tarefa");
        return editando;
    }
    private void informar(String texto, boolean erro) {
        mensagem.setText(texto); mensagem.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("erro"), erro);
    }
    private void carregar(Tarefa t) {
        prazoInvalido = null;
        editando = t.id(); editorTitulo.setText("Editar tarefa #" + t.id()); salvar.setText("Salvar alterações");
        titulo.setText(t.titulo()); descricao.setText(t.descricao()); prioridade.setValue(t.prioridade()); prazo.setValue(t.prazo());
        prazo.getEditor().setText(t.prazo() == null ? "" : DATA.format(t.prazo()));
        alternar.setDisable(false); excluir.setDisable(false);
        alternar.setText(t.status() == Status.PENDENTE ? "Concluir" : "Reabrir"); mostrarConfirmacao(false);
    }
    private void novo() {
        prazoInvalido = null;
        atualizando = true; tabela.getSelectionModel().clearSelection(); atualizando = false;
        editando = null; editorTitulo.setText("Nova tarefa"); salvar.setText("Criar tarefa");
        titulo.clear(); descricao.clear(); prioridade.setValue(Prioridade.MEDIA); prazo.setValue(null); prazo.getEditor().clear();
        alternar.setDisable(true); excluir.setDisable(true); mostrarConfirmacao(false);
    }
    private void mostrarConfirmacao(boolean mostrar) { confirmacao.setVisible(mostrar); confirmacao.setManaged(mostrar); }
    private void selecionar(long id) {
        tabela.getItems().stream().filter(t -> t.id() == id).findFirst().ifPresent(t -> {
            tabela.getSelectionModel().select(t); carregar(t);
        });
    }
    private LocalDate hoje() { return relogio.get(); }
    private void atualizar() {
        Prioridade p = filtroPrioridade.getSelectionModel().getSelectedIndex() <= 0 ? null : Prioridade.values()[filtroPrioridade.getSelectionModel().getSelectedIndex()-1];
        Status s = filtroStatus.getSelectionModel().getSelectedIndex() <= 0 ? null : Status.values()[filtroStatus.getSelectionModel().getSelectedIndex()-1];
        Long anterior = editando;
        atualizando = true;
        tabela.setItems(FXCollections.observableArrayList(gerenciador.consultar(p, s, busca.getText())));
        atualizando = false;
        if (anterior != null) {
            if (tabela.getItems().stream().anyMatch(t -> t.id() == anterior)) selecionar(anterior); else novo();
        }
        List<Tarefa> todas = gerenciador.listar();
        total.setText(String.valueOf(todas.size()));
        pendentes.setText(String.valueOf(todas.stream().filter(t -> t.status() == Status.PENDENTE).count()));
        concluidas.setText(String.valueOf(todas.stream().filter(t -> t.status() == Status.CONCLUIDA).count()));
        atrasadas.setText(String.valueOf(todas.stream().filter(t -> t.atrasada(hoje())).count()));
        resultado.setText(tabela.getItems().size() + " de " + todas.size() + " tarefas · prioridade alta primeiro");
        dataAtual.setText("Hoje, " + DATA.format(hoje())); tabela.refresh();
    }
    private void coluna(String nome, Function<Tarefa,String> texto, double largura) {
        TableColumn<Tarefa,String> c = new TableColumn<>(nome);
        c.setCellValueFactory(d -> new ReadOnlyStringWrapper(texto.apply(d.getValue()))); c.setPrefWidth(largura); c.setSortable(false);
        tabela.getColumns().add(c);
    }
    private VBox indicador(String texto, Label valor, String id, String estilo) {
        valor.setId(id); valor.getStyleClass().addAll("numero", estilo.isBlank() ? "numero-padrao" : estilo);
        VBox card = new VBox(8, rotulo(texto, "indicador-titulo"), valor); card.getStyleClass().add("indicador");
        card.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(card, Priority.ALWAYS); return card;
    }
    private VBox campo(String nome, javafx.scene.Node controle) {
        Label label = rotulo(nome, "campo-label"); label.setLabelFor(controle); return new VBox(6, label, controle);
    }
    private Label rotulo(String texto, String estilo) { Label l = new Label(texto); l.getStyleClass().add(estilo); return l; }
    private Button botao(String texto, String id, String estilo, Runnable acao) {
        Button b = new Button(texto); b.setId(id); b.getStyleClass().add(estilo); b.setOnAction(e -> acao.run()); return b;
    }

    // Acessados exclusivamente pela ponte de automação, habilitada com --robot.
    void reiniciarParaTeste(LocalDate data) {
        gerenciador = new GerenciadorDeTarefas(); relogio = () -> data;
        novo(); busca.clear(); filtroPrioridade.getSelectionModel().selectFirst(); filtroStatus.getSelectionModel().selectFirst();
        atualizar(); informar("Ambiente de teste reiniciado.", false);
    }
    void dataParaTeste(LocalDate data) { relogio = () -> data; atualizar(); }
    List<Tarefa> linhasVisiveis() { return List.copyOf(tabela.getItems()); }
    void selecionarParaTeste(String titulo) {
        Tarefa t = tabela.getItems().stream().filter(item -> item.titulo().equals(titulo)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tarefa não visível: " + titulo));
        tabela.getSelectionModel().select(t); tabela.scrollTo(t);
    }
}
