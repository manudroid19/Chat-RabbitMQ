/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messages;

import com.rabbitmq.client.AMQP.Queue.DeclareOk;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

/**
 *
 * @author mprad
 */
public class Main extends Application implements Initializable {

    private String user;
    private Channel canal;
    private Scene scene;
    private HashMap<String, ObservableList<String>> listasMensajes;
    private String currentDestino;
    private ObservableList<String> listaAmigos;

    @FXML
    private TextArea mensaje;
    @FXML
    private ListView lista;
    @FXML
    private ListView listaUsuarios;
    @FXML
    private TextField nuevoUsuario;
    @FXML
    private Label miUser;
    @FXML
    private Button botonEnviar;

    public static void main(String[] args) {
        Application.launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader f = new FXMLLoader(getClass().getResource("Main.fxml"));
        f.setController(this);
        scene = new Scene(f.load(), 800, 400);
        primaryStage.setTitle("Chat");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        lista.setDisable(true);
        mensaje.setDisable(true);
        botonEnviar.setDisable(true);
        listasMensajes = new HashMap<>();
        listaAmigos = FXCollections.observableArrayList();
        user = FxDialogs.showTextInput("Introduce tu nombre de usuario", "Nombre con el que accederás al chat", "user1");
        if (user == null) {
            Platform.exit();
            return;
        }
        miUser.setText("Usuario " + user + " conectado.");
        try {
            canal = new ConnectionFactory().newConnection().createChannel();
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("x-single-active-consumer", true);
            DeclareOk cola = canal.queueDeclare(user, true, false, false, arguments); //durable
            if (cola.getConsumerCount() != 0) {
                FxDialogs.showError("Error", "Ya hay un usuario conectado con este nombre.", user);
                Platform.exit();
                return;
            }
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String recibido = new String(delivery.getBody(), "UTF-8");
                System.out.println("Recibido '" + recibido + "'");
                String emisor = recibido.split("##")[0];
                if (!listasMensajes.containsKey(emisor)) {
                    Platform.runLater(() -> listaAmigos.add(emisor));
                    listasMensajes.put(emisor, FXCollections.observableArrayList());
                }
                Platform.runLater(() -> listasMensajes.get(emisor).add(recibido.split("##")[1]));
            };
            canal.basicConsume(user, true, deliverCallback, (c) -> {
            });
        } catch (IOException | TimeoutException ex) {
            try {
                canal.queueDelete(user);
            } catch (IOException ex1) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex1);
            }
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

        mensaje.setOnKeyPressed((KeyEvent keyEvent) -> {
            if (keyEvent.getCode() == KeyCode.ENTER) {
                enviarMensaje(mensaje.getText());
                mensaje.setText("");
            }
        });
        listaUsuarios.setItems(listaAmigos);
        listaUsuarios.setOnMouseClicked((MouseEvent event) -> {
            if (listaUsuarios.getSelectionModel().getSelectedItem() != null) {
                setDestino(listaUsuarios.getSelectionModel().getSelectedItem().toString());
            }
        });
        lista.setCellFactory(param -> new ListCell<String>() {
            @Override
            public void updateItem(String name, boolean empty) {
                Platform.runLater(() -> {
                    super.updateItem(name, empty);
                    if (empty) {
                        setText(null);
                    } else {
                        if (name.startsWith("&!&")) {
                            this.setAlignment(Pos.CENTER_RIGHT);
                            setText(name.substring(3));
                        } else {
                            this.setAlignment(Pos.CENTER_LEFT);
                            setText(name);
                        }
                    }
                });
            }
        }
        );
    }

    @Override
    public void stop() {
        if (canal != null) {
            try {
                canal.close();
                canal.getConnection().close();
            } catch (IOException | TimeoutException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @FXML
    private void enviarMensaje(ActionEvent event) {
        event.consume();
        enviarMensaje(mensaje.getText().replace("\n", ""));
        mensaje.setText("");
    }

    @FXML
    private void anhadirUsuario(ActionEvent event) {
        event.consume();
        setDestino(nuevoUsuario.getText());
        if (!listasMensajes.containsKey(currentDestino)) {
            listasMensajes.put(currentDestino, FXCollections.observableArrayList());
            listaAmigos.add(currentDestino);
        }
        nuevoUsuario.setText(null);
    }

    public void enviarMensaje(String broadcastMessage) {
        try (Connection conexion = new ConnectionFactory().newConnection();
                Channel canalEnvio = conexion.createChannel()) {
            canalEnvio.basicPublish("", currentDestino, null, (this.user + "##" + broadcastMessage).getBytes(StandardCharsets.UTF_8));
            System.out.println("Enviado '" + broadcastMessage + "'");
            listasMensajes.get(currentDestino).add("&!&" + broadcastMessage);
        } catch (IOException | TimeoutException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void setDestino(String text) {
        this.currentDestino = text;
        lista.setDisable(false);
        mensaje.setDisable(false);
        botonEnviar.setDisable(false);
        lista.setItems(listasMensajes.get(text));
    }
}
