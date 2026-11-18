package tarefas;

import javafx.application.Application;

/** Launcher separado evita a resolução especial de Application no classpath. */
public final class Main {
    public static void main(String[] args) { Application.launch(TarefasApp.class, args); }
}
