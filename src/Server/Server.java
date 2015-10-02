package Server;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.HashMap;

public class Server {
    static HashMap<Integer, ObjectOutputStream> outputMap = new HashMap<>();
    static HashMap<Integer, ObservableList<Message>> messageQue = new HashMap<>();
    static volatile ArrayDeque<Message> dbQue = new ArrayDeque<>();
    static ListChangeListener<Message> ch;

    public static void main(String[] args) {
//        database saver every 5 min or till somebody logs out
        Thread dbThread = new Thread(() -> {
            try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/user", "root", "I0tN9N0R")) {
                while (true) {
                    try {
                        Thread.sleep(300_000);
                    } catch (InterruptedException e) {
//                        NOP
                    }
                    saveToDB(connection);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
        dbThread.start();

        System.out.println("Starting Server on host : 192.168.1.166 port : 7070 ");
        try (ServerSocket server = new ServerSocket(7070, 50, InetAddress.getByName("192.168.1.166"))) {
            while (true) {
                System.out.println("Waiting for connection");
                Socket socket = server.accept();
                System.out.println("Accepted connection");
                new Thread(() -> {
                    try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                         ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                        int fromID = (int) in.readObject();
                        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/user", "root", "I0tN9N0R")) {
                            String sql = "SELECT * FROM link WHERE user_id = ?";
                            PreparedStatement statement = conn.prepareStatement(sql);
                            statement.setInt(1, fromID);
                            ResultSet resultSet = statement.executeQuery();
                            while (resultSet.next()) {
                                int friendId = resultSet.getInt(2);
                                System.out.println("user: " + fromID + "has a friend with ID:" + friendId);
                                createMessageHistory(fromID, friendId);
                            }
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                        Message msg;
                        if (!outputMap.containsKey(fromID)) {
                            outputMap.put(fromID, out);
                        } else {
                            outputMap.remove(fromID);
                            outputMap.put(fromID, out);
                        }

//                        read my messages
                        Thread t = new Thread(() -> {
//                            read database message history
                            ArrayDeque<Message> localQue = new ArrayDeque<>();
                            try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/user", "root", "I0tN9N0R")) {
                                String sql = "SELECT * FROM message WHERE fromID = ? || toID = ?";
                                PreparedStatement statement = connection.prepareStatement(sql);
                                statement.setInt(1, fromID);
                                statement.setInt(2, fromID);
                                ResultSet resultSet = statement.executeQuery();
                                while (resultSet.next()) {
                                    String string = resultSet.getString(1);
                                    LocalDateTime date = resultSet.getTimestamp(2).toLocalDateTime();
                                    int from = resultSet.getInt(3);
                                    int to = resultSet.getInt(4);
                                    Message e = new Message(string, date, from, to);
                                    localQue.add(e);
                                }
                                while (localQue.size() > 0) {
                                    outputMap.get(fromID).writeObject(localQue.poll());
                                }
//                                while (true) {
//                                    try {
//                                        Thread.sleep(300_000);
//                                    } catch (InterruptedException e) {
////                                        NOP
//                                    }
//                                    if (messageQue.get(fromID).size() > 0) {
//
//                                        outputMap.get(fromID).writeObject(messageQue.get(fromID).poll());
//                                    }
//
//                                }
                                for (Message message : messageQue.get(fromID)) {
                                    outputMap.get(fromID).writeObject(message);
                                }
                                ch = c -> {
                                    while (c.next()) {
                                        if (messageQue.get(fromID).size() > 0 && messageQue.get(fromID).size() > c.getFrom()
                                                && messageQue.get(fromID).get(c.getFrom()) != null
                                                && outputMap.containsKey(fromID) ) {
                                            try {
                                                Message message = messageQue.get(fromID).get(c.getFrom());
                                                System.out.println("sending " + message.getMsg());
                                                messageQue.get(fromID).remove(c.getFrom());
                                                outputMap.get(fromID).writeObject(message);
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                };
                                messageQue.get(fromID).addListener(ch);
                            } catch (SQLException | IOException e) {
                                e.printStackTrace();
                            }
                        });
                        t.start();
//                        send messages to que
                        while ((msg = (Message) in.readObject()) != null) {
                            if (msg.getMsg().equals("done")) {
                                dbThread.interrupt();
                                t.stop();
                                outputMap.remove(fromID);
                                messageQue.get(fromID).removeListener(ch);
                                return;
                            } else {
                                messageQue.get(fromID).add(msg);
                                messageQue.get(msg.getToID()).add(msg);
                                dbQue.add(msg);
                                System.out.println("added " + msg.getMsg());
                            }
                        }

                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveToDB(Connection connection) throws SQLException {
        System.out.println("DB saver");
        LocalTime now = LocalTime.now();
        while (dbQue.size() > 0) {
            Message poll = dbQue.poll();
            String sql = "CALL save_message(?,?,?,?)";
            CallableStatement statement = connection.prepareCall(sql);
            statement.setString(1, poll.getMsg());
            statement.setString(2, poll.getLocalDT().toString());
            statement.setInt(3, poll.getFromID());
            statement.setInt(4, poll.getToID());
            statement.execute();
        }
        for (Integer id : messageQue.keySet()) {
            messageQue.get(id).clear();
            System.out.println(messageQue.get(id).size() +  " sieze");
        }
    }

    private static void createMessageHistory(int fromID, int sendToID) {
        if (!messageQue.containsKey(fromID)) {
            System.out.println("Creating a que");
            ObservableList<Message> localDeque = FXCollections.observableArrayList();
            messageQue.put(fromID, localDeque);
        }
        if (!messageQue.containsKey(sendToID)) {
            System.out.println("Creating a que");
            ObservableList<Message> localDeque = FXCollections.observableArrayList();
            messageQue.put(sendToID, localDeque);
        }
    }
}
