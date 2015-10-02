package Client;

import Server.Message;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Created by Igor Klimov on 9/22/2015.
 */
public class Controller implements Initializable {

    public AnchorPane root;
    public ScrollPane scroll;
    public VBox vbox;
    public TextArea textArea;
    @FXML
    private Label emailField;
    @FXML
    private ListView<String> friendsList;
    private Socket socket;
    private ObjectOutputStream out;
    private SimpleBooleanProperty isConnected = new SimpleBooleanProperty(false);
    private volatile Deque<Message> outputDeque = new ArrayDeque<>();
    private volatile HashMap<Integer, ArrayDeque<Message>> inputDeque = new HashMap<>();
    private volatile HashMap<Integer, ObservableList<Message>> cachedMessages = new HashMap<>();
    static String email;
    static int ID;
    static int toID;
    static ObservableMap<Integer, String> friends = FXCollections.observableHashMap();
    SimpleDoubleProperty doubleProperty = new SimpleDoubleProperty();
    Service<HBox> resultSetter;
    Service<Void> outputService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        doubleProperty.bind(vbox.heightProperty());
        doubleProperty.addListener((observable, oldValue, newValue) -> scroll.setVvalue(scroll.getVmax()));
        ObservableList<String> list = FXCollections.observableArrayList();
        HashMap<String, Integer> map = new HashMap<>();
        for (Integer id : friends.keySet()) {
            map.put(friends.get(id), id);
        }

        list.addAll(map.keySet().stream().collect(Collectors.toList()));
        textArea.setDisable(true);
        emailField.setText(email);
        friendsList.setItems(list);
        friendsList.setOnMouseClicked(e -> {

            Integer selectedItem = map.get(friendsList.getSelectionModel().getSelectedItem());
            if (selectedItem != null) {
                textArea.setDisable(false);
                toID = selectedItem;
                vbox.getChildren().clear();

                if (cachedMessages.containsKey(toID) && cachedMessages.get(toID).size() > 0) {
                    cachedMessages.get(toID).sort((o1, o2) -> o1.getLocalDT().compareTo(o2.getLocalDT()));
                    for (Message message : cachedMessages.get(toID)) {
                        HBox msgLine = createMessageLine(message);
                        String s = message.getLocalDT().format(DateTimeFormatter.ofPattern("hh:mm a"));
                        int size = vbox.getChildren().size();
                        if (size > 0) {
                            HBox line = (HBox) vbox.getChildren().get(size - 1);
                            VBox txbox = (VBox) line.getChildren().get(0);
                            VBox timebox = (VBox) line.getChildren().get(1);
                            Label l = (Label) txbox.getChildren().get(0);
                            Label tim = (Label) timebox.getChildren().get(0);
                            double top = msgLine.getPadding().getTop();
                            if (top == 5 && s.equals(tim.getText())) {
                                l.setText(l.getText() + "\n" + message.getMsg());
                            } else {
                                vbox.getChildren().addAll(msgLine);
                            }
                        } else {
                            vbox.getChildren().addAll(msgLine);
                        }
                    }
                }

                inputDeque.get(toID).clear();
                cachedMessages.get(toID).addListener((ListChangeListener<Message>) c -> {
                    resultSetter = new Service<HBox>() {
                        @Override
                        protected Task<HBox> createTask() {
                            return new Task<HBox>() {
                                @Override
                                protected HBox call() throws Exception {
                                    if (inputDeque.containsKey(toID) && inputDeque.get(toID).size() > 0) {
                                        Message poll;
                                        if ((poll = inputDeque.get(toID).poll()) != null) {
                                            HBox msgLine = createMessageLine(poll);
                                            updateValue(msgLine);
                                        }
                                    }
                                    return null;
                                }
                            };
                        }
                    };
                    resultSetter.valueProperty().addListener((observable, oldValue, newValue) -> {
                        if (newValue != null) {
                            VBox ttbox = (VBox) newValue.getChildren().get(0);
                            VBox timmbox = (VBox) newValue.getChildren().get(1);
                            Label la = (Label) ttbox.getChildren().get(0);
                            Label ti = (Label) timmbox.getChildren().get(0);
                            String s = ti.getText();
                            int size = vbox.getChildren().size();
                            if (size > 0) {
                                HBox line = (HBox) vbox.getChildren().get(size - 1);
                                VBox txbox = (VBox) line.getChildren().get(0);
                                VBox timebox = (VBox) line.getChildren().get(1);
                                Label l = (Label) txbox.getChildren().get(0);
                                Label tim = (Label) timebox.getChildren().get(0);
                                double top = newValue.getPadding().getTop();
                                if (top == 5 && s.equals(tim.getText())) {
                                    l.setText(l.getText() + "\n" + la.getText());
                                } else {
                                    vbox.getChildren().addAll(newValue);
                                }
                            } else {
                                vbox.getChildren().addAll(newValue);
                            }
                        }
                    });
                    resultSetter.start();
                });
            }
        });

        for (Integer id : friends.keySet()) {
            inputDeque.put(id, new ArrayDeque<>());
            cachedMessages.put(id, FXCollections.observableArrayList());
        }
        textArea.setOnKeyPressed(event -> {
            if (event.getCode().equals(KeyCode.ENTER)) {
                cashMessage();
                event.consume();
            }
        });
        doConnect();
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            root.getScene().getWindow().setOnCloseRequest(event -> {
                try {
                    closeConnection();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }).start();
    }

    private HBox createMessageLine(Message message) {
        Label text = new Label(message.getMsg());
        if (message.getFromID() == ID) {
            text.setStyle("-fx-background-color:  rgba(127, 255, 212, 0.44); -fx-background-radius:  5");
        } else {
            text.setStyle("-fx-background-color:  rgba(153, 255, 146, 0.44); -fx-background-radius: 5");
        }
        text.setPadding(new Insets(5, 20, 5, 10));
        text.setWrapText(true);
        text.setPrefWidth(165);
        text.setFont(Font.font(14));
        Label time = new Label(message.getLocalDT().format(DateTimeFormatter.ofPattern("hh:mm a")));
        time.setStyle("-fx-text-fill: cadetblue");
        HBox msgLine = new HBox();
        VBox textBox = new VBox();
        int size = vbox.getChildren().size();
        textBox.setPadding(new Insets(0, 20, 0, message.getFromID() == ID ? 25 : 0));
        if (size > 0) {
            HBox node = (HBox) vbox.getChildren().get(size - 1);
            VBox v = (VBox) node.getChildren().get(0);
            Insets prev = v.getPadding();

            if (textBox.getPadding().getLeft() != prev.getLeft()) {
                msgLine.setPadding(new Insets(10, 0, 0, 0));
            } else {
                msgLine.setPadding(new Insets(5, 0, 0, 0));
            }
        }

        VBox timeBox = new VBox();
        textBox.setPrefWidth(200);
        textBox.getChildren().add(text);
        timeBox.getChildren().add(time);
        msgLine.getChildren().addAll(textBox, timeBox);
        return msgLine;
    }

    public void doConnect() {
        try {
            socket = new Socket(InetAddress.getByName("192.168.1.166"), 7070);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(ID);
            isConnected.set(true);
            Service<Void> inputService = new Service<Void>() {
                @Override
                protected Task<Void> createTask() {
                    return new Task<Void>() {
                        @Override
                        protected Void call() throws Exception {
                            try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                                while (isConnected.get()) {

                                    Message msg = (Message) in.readObject();
                                    if (msg.getFromID() == ID) {
                                        inputDeque.get(msg.getToID()).add(msg);
                                        cachedMessages.get(msg.getToID()).add(msg);
                                    } else {
                                        inputDeque.get(msg.getFromID()).add(msg);
                                        cachedMessages.get(msg.getFromID()).add(msg);

                                    }
                                }
                            }
                            return null;
                        }
                    };
                }
            };
            outputService = new Service<Void>() {
                @Override
                protected Task<Void> createTask() {
                    return new Task<Void>() {
                        @Override
                        protected Void call() throws Exception {
//                            while (true) {
//                                if (!isConnected.get()) {
//                                    return null;
//                                }
                            if (outputDeque.size() > 0) {
                                out.writeObject(outputDeque.poll());
                            }
//                            }
                            return null;
                        }
                    };
                }
            };
            inputService.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void cashMessage() {
        outputDeque.add(new Message(textArea.getText(), LocalDateTime.now(), ID, toID));
        outputService.restart();
        textArea.clear();
    }

    private void closeConnection() throws IOException {
        out.writeObject(new Message("done", LocalDateTime.now(), 0, 0));
        socket.close();
        out.close();
    }
}
