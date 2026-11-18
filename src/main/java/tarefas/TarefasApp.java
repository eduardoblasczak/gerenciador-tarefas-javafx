package tarefas;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.time.LocalDate;
import java.util.Locale;

public class TarefasApp extends Application {
    @Override public void start(Stage stage) {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"));
        TelaTarefas tela = new TelaTarefas(LocalDate::now);
        Scene scene = new Scene(tela.raiz(), 1240, 840);
        scene.getStylesheets().add(getClass().getResource("/tarefas/estilo.css").toExternalForm());
        stage.setTitle("Em dia | Gerenciador de tarefas");
        stage.setScene(scene); stage.setMinWidth(1080); stage.setMinHeight(700); stage.show();
        if (getParameters().getRaw().contains("--robot")) PonteRobot.iniciar(tela, stage);
    }
}
