package Client;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ResourceBundle;

/**
 * Created by Igor Klimov on 9/22/2015.
 */
public class Controller implements Initializable {
    @FXML
    private Button button7;
    @FXML
    private Button button1;
    @FXML
    private VBox root;
    @FXML
    private TextField textArea;
    @FXML
    private TextArea result;
    private Socket socket;
    private PrintStream out;
    private SimpleBooleanProperty isConnected = new SimpleBooleanProperty(false);
    private volatile Deque<String> outputDeque = new ArrayDeque<>();
    private volatile Deque<String> inputDeque = new ArrayDeque<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        textArea.selectPositionCaret(0);
        textArea.disableProperty().bind(new BooleanBinding() {
            {
                bind(isConnected);
            }

            @Override
            protected boolean computeValue() {
                return !isConnected.getValue();
            }
        });
        textArea.setOnKeyPressed(event -> {
            if (event.getCode().equals(KeyCode.ENTER)) {
                cashMessage();
            }
        });
        button1.setOnAction(e -> doConnect(1,7));
        button7.setOnAction(e -> doConnect(7,1));
    }

    public void doConnect(int client, int connectTo) {
        try {
            socket = new Socket(InetAddress.getByName("192.168.1.166"), 7070);
            out = new PrintStream(socket.getOutputStream(), true, "windows-1251");
            out.println(client);
            out.println(connectTo);
            isConnected.set(true);
            root.getScene().getWindow().setOnCloseRequest(event -> {
                try {
                    closeConnection();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            Service<Void> resultSetter = new Service<Void>() {
                @Override
                protected Task<Void> createTask() {
                    return new Task<Void>() {
                        @Override
                        protected Void call() throws Exception {
                            while (isConnected.get()) {
                                if (inputDeque.size() > 0) {
                                    System.out.println("Setting result");
                                    result.appendText(inputDeque.poll() + "\n");
                                }
                            }
                            return null;
                        }
                    };
                }
            };
            Service<Void> inputService = new Service<Void>() {
                @Override
                protected Task<Void> createTask() {
                    return new Task<Void>() {
                        @Override
                        protected Void call() throws Exception {
                            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "windows-1251"))) {
                                while (isConnected.get()) {
                                    String msg = in.readLine();
                                    System.out.println("got ::" + msg);
                                    inputDeque.add(msg);
                                }
                            }
                            return null;
                        }
                    };
                }
            };
            Service<Void> outputService = new Service<Void>() {
                @Override
                protected Task<Void> createTask() {
                    return new Task<Void>() {
                        @Override
                        protected Void call() throws Exception {
                            while (true) {
                                if (!isConnected.get()) {
                                    return null;
                                }
                                if (outputDeque.size()> 0) {
                                    System.out.println("sending " + outputDeque.peek());
                                    out.println(outputDeque.poll());
                                }
                            }
                        }
                    };
                }
            };
            resultSetter.start();
            inputService.start();
            outputService.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void doDisconnect(ActionEvent actionEvent) {
        if (isConnected.getValue()) {
            System.out.println("Disconnect");
            try {
                closeConnection();
                isConnected.set(false);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void cashMessage() {
        outputDeque.add(textArea.getText());
        textArea.clear();
        textArea.selectPositionCaret(0);
    }

    private void closeConnection() throws IOException {
        out.println("done");
        socket.close();
        out.close();
    }

}
