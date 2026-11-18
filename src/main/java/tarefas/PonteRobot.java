package tarefas;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Protocolo privado por stdin/stdout. Só existe no processo iniciado com --robot.
 * As ações usam controles reais na thread JavaFX; não simulam mouse ou teclado físico.
 */
final class PonteRobot {
    static void iniciar(TelaTarefas tela, Stage stage) {
        Thread leitor = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                System.out.println("READY"); System.out.flush();
                String linha;
                while ((linha = in.readLine()) != null) {
                    String[] partes = linha.split("\t", -1);
                    String[] args = new String[partes.length - 1];
                    for (int i = 1; i < partes.length; i++) args[i - 1] = new String(Base64.getDecoder().decode(partes[i]), StandardCharsets.UTF_8);
                    String comando = partes[0];
                    if (comando.equals("EXIT")) { Platform.runLater(Platform::exit); return; }
                    CompletableFuture<String> resultado = new CompletableFuture<>();
                    Platform.runLater(() -> {
                        try { resultado.complete(executar(comando, args, tela, stage)); }
                        catch (Throwable e) { resultado.completeExceptionally(e); }
                    });
                    try { responder("OK", resultado.get(15, TimeUnit.SECONDS)); }
                    catch (Exception e) { responder("ERR", e.getCause() == null ? e.toString() : e.getCause().toString()); }
                }
                Platform.runLater(Platform::exit);
            } catch (IOException e) { Platform.runLater(Platform::exit); }
        }, "robot-stdin");
        leitor.setDaemon(true); leitor.start();
    }
    private static void responder(String status, String texto) {
        System.out.println(status + "\t" + Base64.getEncoder().encodeToString(texto.getBytes(StandardCharsets.UTF_8))); System.out.flush();
    }
    private static Node controle(TelaTarefas tela, String id) {
        Node n = tela.raiz().lookup("#" + id);
        if (n == null) throw new IllegalArgumentException("Controle inexistente: " + id);
        return n;
    }
    private static String executar(String comando, String[] a, TelaTarefas tela, Stage stage) throws Exception {
        switch (comando) {
            case "RESET" -> tela.reiniciarParaTeste(LocalDate.parse(a[0]));
            case "TODAY" -> tela.dataParaTeste(LocalDate.parse(a[0]));
            case "SET" -> {
                Node n = controle(tela, a[0]);
                n.requestFocus();
                if (n instanceof TextInputControl t) t.setText(a[1]);
                else if (n instanceof DatePicker d) { d.getEditor().requestFocus(); d.getEditor().setText(a[1]); }
                else if (n instanceof ComboBox<?> c) {
                    Object item = c.getItems().stream().filter(i -> i.toString().equals(a[1]) || i instanceof Enum<?> e && e.name().equals(a[1])).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Opção inexistente: " + a[1]));
                    c.getSelectionModel().select(c.getItems().indexOf(item));
                } else throw new IllegalArgumentException("Controle não editável: " + a[0]);
            }
            case "CLICK" -> {
                ButtonBase b = (ButtonBase) controle(tela, a[0]);
                if (b.isDisabled() || !b.isVisible() || !b.getParent().isVisible()) throw new IllegalArgumentException("Botão indisponível: " + a[0]);
                b.requestFocus(); b.fire();
            }
            case "SELECT" -> tela.selecionarParaTeste(a[0]);
            case "TEXT" -> {
                Node n = controle(tela, a[0]);
                if (n instanceof Labeled l) return l.getText();
                if (n instanceof TextInputControl t) return t.getText();
                throw new IllegalArgumentException("Controle sem texto: " + a[0]);
            }
            case "ROWS" -> {
                StringBuilder resultado = new StringBuilder();
                for (Tarefa t : tela.linhasVisiveis()) {
                    // Cada campo é codificado separadamente para preservar tabs e quebras do usuário.
                    String[] campos = {String.valueOf(t.id()), t.titulo(), t.descricao(), t.status().name(), t.prioridade().name(), t.prazo() == null ? "" : t.prazo().toString()};
                    for (int i = 0; i < campos.length; i++) {
                        if (i > 0) resultado.append('\t');
                        resultado.append(Base64.getEncoder().encodeToString(campos[i].getBytes(StandardCharsets.UTF_8)));
                    }
                    resultado.append('\n');
                }
                return resultado.toString();
            }
            case "SNAPSHOT" -> {
                tela.raiz().applyCss(); tela.raiz().layout();
                javafx.scene.image.WritableImage img = tela.raiz().snapshot(null, null);
                java.awt.image.BufferedImage png = new java.awt.image.BufferedImage((int)img.getWidth(), (int)img.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, img.getPixelReader().getArgb(x,y));
                javax.imageio.ImageIO.write(png, "png", Path.of(a[0]).toFile());
            }
            default -> throw new IllegalArgumentException("Comando desconhecido: " + comando);
        }
        return "";
    }
}
