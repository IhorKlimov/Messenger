package Client;

import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.util.Callback;
import res.Friend;
import res.FriendRequest;
import res.FriendResponse;
import res.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.ResourceBundle;

/**
 * Created by Igor Klimov on 9/22/2015.
 */
public class Controller implements Initializable {
    @FXML
    private ImageView reqImg;
    @FXML
    private Label reqLabel;
    @FXML
    private Label requests;
    @FXML
    private AnchorPane root;
    @FXML
    private ScrollPane scroll;
    @FXML
    private VBox vbox;
    @FXML
    private TextArea textArea;
    @FXML
    private TextField search;
    @FXML
    private ImageView cancel;
    @FXML
    private Label emailField;
    @FXML
    private ListView<Friend> friendsListView;
//
    private Socket socket;
    private Socket requestsSocket;
    private ObjectOutputStream out;
    private ObjectOutputStream requestsOut;
    private volatile SimpleBooleanProperty isConnected = new SimpleBooleanProperty(false);
    private volatile Deque<Message> outputDeque = new ArrayDeque<>();
    private volatile HashMap<Integer, ArrayDeque<Message>> inputDeque = new HashMap<>();
    private volatile HashMap<Integer, ObservableList<Message>> cachedMessages = new HashMap<>();
    static String email;
    static String name;
    static int ID;
    static int toID;
    static ObservableList<Friend> friends = FXCollections.observableArrayList();
    private ObservableList<FriendRequest> friendRequests = FXCollections.observableArrayList();
    private SimpleDoubleProperty doubleProperty = new SimpleDoubleProperty();
    private Service<HBox> resultSetter;
    private Service<Void> outputService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        requests.visibleProperty().bind(new BooleanBinding() {
            {
                bind(friendRequests);
            }

            @Override
            protected boolean computeValue() {
                return friendRequests.size() > 0;
            }
        });
        reqImg.visibleProperty().bind(new BooleanBinding() {
            {
                bind(friendRequests);
            }

            @Override
            protected boolean computeValue() {
                return friendRequests.size() > 0;
            }
        });
        requests.setOnMouseClicked(event -> {
            friendsListView.getSelectionModel().select(null);
            vbox.getChildren().clear();
            textArea.setDisable(true);
            ListView<FriendRequest> e = new ListView<>();
            e.setItems(friendRequests);
            e.setMaxHeight(300);
            e.setCellFactory(param -> new FriendRequestCell(e));
            vbox.getChildren().add(e);
            reqLabel.setVisible(true);
        });
        cancel.visibleProperty().bind(new BooleanBinding() {
            {
                bind(search.textProperty());
            }

            @Override
            protected boolean computeValue() {
                return search.getText().length() > 0;
            }
        });
        doubleProperty.bind(vbox.heightProperty());
        doubleProperty.addListener((observable, oldValue, newValue) -> scroll.setVvalue(scroll.getVmax()));
        textArea.setDisable(true);
        emailField.setText(name);
        friendsListView.setItems(friends);
        friendsListView.setCellFactory(new FriendListCell());
        EventHandler<MouseEvent> mouseEventEventHandler = e -> {
            reqLabel.setVisible(false);
            Integer selectedItem = friendsListView.getSelectionModel().getSelectedItem().getID();
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
                        Tooltip info = new Tooltip(message.getLocalDT().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, u hh:mm a")));
                        tim.setTooltip(info);
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
        };
        cancel.setOnMouseClicked(event -> {
            friendsListView.setItems(friends);
            friendsListView.setCellFactory(null);
            search.clear();
            scroll.setVisible(true);
            textArea.setDisable(false);
            friendsListView.setOnMouseClicked(mouseEventEventHandler);
        });
        friendsListView.setOnMouseClicked(mouseEventEventHandler);
//        Filter friend list and add friends
        search.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() > 0) {
                friendsListView.setItems(friends.filtered(friend -> friend.getName().toLowerCase().contains(newValue.toLowerCase())));
            } else {
                friendsListView.setItems(friends);
                friendsListView.setCellFactory(new FriendListCell());
            }
        });
        search.setOnKeyPressed(event -> {
            if (search.getText().length() > 0 && event.getCode().equals(KeyCode.ENTER)) {
                try {
                    ObservableList<Friend> searchResult = FXCollections.observableArrayList();
                    Connection conn = DriverManager.getConnection("jdbc:mysql://192.168.1.166:3306/user1", "IgorKlimov", "pass");
                    String sql = "SELECT first_name, user_id FROM user WHERE first_name LIKE ? AND user_id != ?";
                    PreparedStatement statement = conn.prepareStatement(sql);
                    String like = "%" + search.getText() + "%";
                    statement.setString(1, like);
                    statement.setInt(2, ID);
                    ResultSet resultSet = statement.executeQuery();
                    while (resultSet.next()) {
                        String name = resultSet.getString(1);
                        int id = resultSet.getInt(2);
                        searchResult.add(new Friend(name, id));
                    }
                    friendsListView.setItems(searchResult);
                    friendsListView.setCellFactory(new SearchCell());
                    vbox.getChildren().clear();
                    textArea.setDisable(true);
                    friendsListView.setOnMouseClicked(null);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        });


        for (Friend friend : friends) {
            inputDeque.put(friend.getID(), new ArrayDeque<>());
            cachedMessages.put(friend.getID(), FXCollections.observableArrayList());
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
                            if (outputDeque.size() > 0) {
                                out.writeObject(outputDeque.poll());
                            }
                            return null;
                        }
                    };
                }
            };
            inputService.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            requestsSocket = new Socket(InetAddress.getByName("192.168.1.166"), 7071);
            requestsOut = new ObjectOutputStream(requestsSocket.getOutputStream());
            requestsOut.writeObject(ID);
            Service<Void> reqIn = new Service<Void>() {
                @Override
                protected Task<Void> createTask() {
                    return new Task<Void>() {
                        @Override
                        protected Void call() throws Exception {
                            try (ObjectInputStream requestIn = new ObjectInputStream(requestsSocket.getInputStream())) {
                                while (isConnected.get()) {
                                    Object res = requestIn.readObject();
                                    if (res.getClass().equals(FriendRequest.class)) {
                                        FriendRequest req = (FriendRequest) res;
                                        friendRequests.add(req);
                                        System.out.println(req.getUserFromId() + "->" + req.getUserToId());
                                    }
                                    else if (res.getClass().equals(Friend.class)) {
                                        Friend f = (Friend) res;
                                        Platform.runLater(()-> friends.add(f));
                                        inputDeque.put(f.getID(), new ArrayDeque<>());
                                        cachedMessages.put(f.getID(), FXCollections.observableArrayList());
                                    }
                                    else if (res.getClass().equals(HashMap.class)) {
                                        HashMap<Integer, Boolean> map = (HashMap<Integer, Boolean>) res;
                                        for (Integer id : map.keySet()) {
                                            System.err.println("Friend " + id + " is online " + map.get(id));
                                            Friend friend = friends.filtered(p -> p.getID() == id).get(0);
                                            friend.setIsOnline(map.get(id));
                                            friendsListView.refresh();
                                        }
                                    }
                                }
                            }
                            return null;
                        }
                    };
                }
            };
            reqIn.start();
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
        requestsOut.writeObject("done");
        socket.close();
        requestsSocket.close();
        out.close();
        requestsOut.close();

    }

    class SearchCell implements Callback<ListView<Friend>, ListCell<Friend>> {

        @Override
        public ListCell<Friend> call(ListView<Friend> param) {
            HBox hBox = new HBox();
            Label name = new Label();
            ImageView addImg = new ImageView("res/plus_add_green.png");
            name.setTranslateX(7);
            addImg.setTranslateY(4);
            ListCell<Friend> cell = new ListCell<>();

            Tooltip t = new Tooltip("add friend");
            t.setStyle("-fx-background-color: rgba(250, 235, 215, 0.67); -fx-text-fill: black; -fx-padding: 5");
            hBox.getChildren().addAll(addImg, name);
            cell.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null) {
                    name.setText(newItem.getName());
                    FilteredList<Friend> filtered = friends.filtered(p -> p.getID() == newItem.getID());
                    if ((filtered.size() == 0)) {
                        addImg.setImage(new Image("res/plus_add_green.png"));
                        Tooltip.install(addImg, t);
                        addImg.setCursor(Cursor.HAND);
                        addImg.setOnMouseClicked(event -> {
                            Friend person = friendsListView.getSelectionModel().getSelectedItem();
                            try {
                                requestsOut.writeObject(new FriendRequest(person.getID(), ID, Controller.name));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                    } else {
                        addImg.setImage(new Image(filtered.get(0).isOnline() ? "res/on.png" : "res/offline1.png"));
                    }
                }
            });
            cell.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> {
                if (isEmpty) {
                    cell.setGraphic(null);
                } else {
                    cell.setGraphic(hBox);
                }
            });
            cell.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            return cell;
        }
    }


    class FriendRequestCell extends ListCell<FriendRequest> {
        HBox hbox = new HBox();
        Label label = new Label();
        Pane pane = new Pane();
        ImageView accept = new ImageView("res/Accept.png");
        ImageView decline = new ImageView("res/decline.png");
        FriendRequest lastItem;
        ListView<FriendRequest> e;

        public FriendRequestCell(ListView<FriendRequest> e) {
            this.e = e;
            accept.setTranslateX(-5);
            accept.setCursor(Cursor.HAND);
            Tooltip acc = new Tooltip("accept");
            acc.setStyle("-fx-background-color: rgba(250, 235, 215, 0.67); -fx-text-fill: black; -fx-padding: 5");
            Tooltip.install(this.accept, acc);
            Tooltip dec = new Tooltip("decline");
            dec.setStyle("-fx-background-color: rgba(250, 235, 215, 0.67); -fx-text-fill: black; -fx-padding: 5");
            Tooltip.install(this.decline, dec);
            this.decline.setCursor(Cursor.HAND);
            this.accept.setOnMouseClicked(event -> {
                FriendRequest selectedItem = e.getSelectionModel().getSelectedItem();
                System.out.println(selectedItem.getName() + ": " + selectedItem.getUserFromId());
                try {
                    requestsOut.writeObject(new FriendResponse(name, ID, selectedItem.getUserFromId(), true));
                    try {
                        Account account = new Account(DriverManager.getConnection("jdbc:mysql://192.168.1.166:3306/user1", "IgorKlimov", "pass"));
                        account.addFriend(ID, selectedItem.getUserFromId());
                        friends.add(new Friend(selectedItem.getName(), selectedItem.getUserFromId()));
                        inputDeque.put(selectedItem.getUserFromId(), new ArrayDeque<>());
                        cachedMessages.put(selectedItem.getUserFromId(), FXCollections.observableArrayList());
                        friendRequests.remove(selectedItem);
                    } catch (SQLException e1) {
                        e1.printStackTrace();
                    }
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            });
            this.decline.setOnMouseClicked(event -> {
                FriendRequest selectedItem = e.getSelectionModel().getSelectedItem();
                System.out.println("Decline" + selectedItem.getName() + ": " + selectedItem.getUserFromId());
                try {
                    requestsOut.writeObject(new FriendResponse(name, ID, selectedItem.getUserFromId(), false));
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            });
            hbox.getChildren().addAll(label, pane, this.accept, this.decline);
            HBox.setHgrow(pane, Priority.ALWAYS);
        }

        @Override
        protected void updateItem(FriendRequest item, boolean empty) {
            super.updateItem(item, empty);
            setText(null);  // No text in label of super class
            if (empty) {
                lastItem = null;
                setGraphic(null);
            } else {
                lastItem = item;
                label.setText(item != null ? item.getName() : "<null>");
                setGraphic(hbox);
            }
        }
    }

    class FriendListCell implements Callback<ListView<Friend>, ListCell<Friend>> {

        @Override
        public ListCell<Friend> call(ListView<Friend> param) {
            HBox hBox = new HBox();
            Label name = new Label();
            ImageView statusImg = new ImageView();
            name.setTranslateX(7);
            statusImg.setTranslateY(4);
            hBox.getChildren().addAll(statusImg, name);
            ListCell<Friend> cell = new ListCell<>();
            cell.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem != null) {
                    name.setText(newItem.getName());
                    statusImg.setImage(new Image(newItem.isOnline() ? "res/on.png" : "res/offline1.png"));
                }
            });
            cell.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> {
                if (isEmpty) {
                    cell.setGraphic(null);
                } else {
                    cell.setGraphic(hBox);
                }
            });
            cell.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            return cell;
        }
    }
}

