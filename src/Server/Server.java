package Server;

import Client.Account;
import javafx.collections.*;
import res.Friend;
import res.FriendRequest;
import res.FriendResponse;
import res.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;

public class Server {
    private static HashMap<Integer, ObjectOutputStream> outputMap = new HashMap<>();
    private static HashMap<Integer, ObjectOutputStream> outRequest = new HashMap<>();
    private static HashMap<Integer, ObservableList<Message>> messageQue = new HashMap<>();
    private static HashMap<Integer, ObservableList<FriendRequest>> friendRequests = new HashMap<>();
    private static volatile ArrayDeque<Message> dbQue = new ArrayDeque<>();
    private static ListChangeListener<Message> ch;
    private static ObservableMap<Integer, Socket> socketHashMap = FXCollections.observableHashMap();
    private static HashMap<Integer, ArrayList<Integer>> friendList = new HashMap<>();

    public static void main(String[] args) {

        socketHashMap.addListener((MapChangeListener<Integer, Socket>) change -> {
            if (socketHashMap.containsKey(change.getKey())) {
                System.err.println("User " + change.getKey() + " connected");
                friendList.get(change.getKey()).forEach(s -> {
                    if (socketHashMap.containsKey(s)) {
                        try {
                            HashMap<Integer, Boolean> map = new HashMap<>();
                            map.put(change.getKey(), true);
                            outRequest.get(s).writeObject(map);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            } else {
                System.err.println("User " + change.getKey() + " disconnected");
                friendList.get(change.getKey()).forEach(s -> {
                    if (socketHashMap.containsKey(s)) {
                        try {
                            HashMap<Integer, Boolean> map = new HashMap<>();
                            map.put(change.getKey(), false);
                            outRequest.get(s).writeObject(map);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
//        database saver every 5 min or till somebody logs out
        Thread dbThread = new Thread(() -> {
            try (Connection connection = DriverManager.getConnection("jdbc:mysql://192.168.1.166:3306/user1", "IgorKlimov", "pass")) {
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

        Thread friendRequestHandler = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(7071, 50, InetAddress.getByName("192.168.1.166"))) {
                while (true) {
                    Socket socket = server.accept();
                    new Thread(() -> {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                            int fromID = (int) in.readObject();
                            outRequest.put(fromID, out);
                            HashMap<Integer, Boolean> isOnlineMap = new HashMap<>();
                            friendList.get(fromID).forEach(f -> {
                                isOnlineMap.put(f, socketHashMap.containsKey(f));
                            });
                            outRequest.get(fromID).writeObject(isOnlineMap);
//                            read Friends Requests from DB
                            try (Connection conn = DriverManager.getConnection("jdbc:mysql://192.168.1.166:3306/user1", "IgorKlimov", "pass")) {
                                String sql = "SELECT user_from_id FROM friend_request WHERE user_to_id = ?";
                                PreparedStatement statement = conn.prepareStatement(sql);
                                statement.setInt(1, fromID);
                                ResultSet resultSet = statement.executeQuery();
                                ArrayDeque<FriendRequest> localFr = new ArrayDeque<>();
                                while (resultSet.next()) {
                                    int requestFromId = resultSet.getInt(1);
                                    String name = new Account(conn).getName(requestFromId);
                                    localFr.add(new FriendRequest(fromID, requestFromId, name));
                                }
                                for (FriendRequest fr : localFr) {
                                    System.out.println("Found friend request from DB: " + fr.getUserFromId() + "->" + fr.getUserToId());
                                    out.writeObject(fr);
                                }
                            } catch (SQLException e) {
                                e.printStackTrace();
                            }
                            for (FriendRequest req : friendRequests.get(fromID)) {
                                System.out.println("have request in memory: " + req.getUserFromId() + "->" + req.getUserToId());
                                out.writeObject(req);
                            }
                            ListChangeListener<FriendRequest> friendRequestListChangeListener = c -> {
                                while (c.next()) {
                                    if (friendRequests.get(fromID).size() > 0) {
                                        System.out.println("From listener: got friend request " +
                                                friendRequests.get(fromID).get(c.getFrom()).getUserFromId() + "->"
                                                + friendRequests.get(fromID).get(c.getFrom()).getUserToId());
                                        try {
                                            out.writeObject(friendRequests.get(fromID).get(c.getFrom()));
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            };
                            friendRequests.get(fromID).addListener(friendRequestListChangeListener);
                            Object res;
//                            input
                            while (true) {
                                res = in.readObject();
                                if (res.getClass().equals(String.class) && res.equals("done")) {
                                    friendRequests.get(fromID).removeListener(friendRequestListChangeListener);
                                    return;
                                }
                                if (res.getClass().equals(FriendRequest.class)) {
                                    FriendRequest fr = (FriendRequest) res;
                                    int userToId = fr.getUserToId();
                                    if (!friendRequests.containsKey(userToId)) {
                                        System.out.println("Creating friend Requests memory");
                                        friendRequests.put(userToId, FXCollections.observableArrayList());
                                    }
                                    friendRequests.get(userToId).add(fr);
                                    System.out.println("got friend request from " + fr.getUserFromId() + " to user " + userToId);
                                } else if (res.getClass().equals(FriendResponse.class)) {
                                    System.out.println("GOT FRIEND RESPONSE");
                                    try (Connection con = DriverManager.getConnection("jdbc:mysql://192.168.1.166:3306/user1", "IgorKlimov", "pass")) {
                                        FriendResponse response = (FriendResponse) res;
                                        String sql = "DELETE FROM friend_request WHERE user_from_id = ?";
                                        CallableStatement statement = con.prepareCall(sql);
                                        int userFromId = response.getUserFromId();
                                        int myID = response.getUserToId();
                                        statement.setInt(1, userFromId);
                                        statement.execute();
                                        System.out.println(myID);
                                        int size = friendRequests.get(myID).size();
                                        System.out.println(size);
                                        if (size > 0) {
                                            FriendRequest friendRequest = friendRequests.get(myID)
                                                    .filtered(fr -> fr.getUserFromId() == userFromId && fr.getUserToId() == myID).get(0);
                                            if (friendRequests.get(myID).contains(friendRequest)) {
                                                friendRequests.get(myID).remove(friendRequest);
                                            }
                                        }
                                        if (response.isAccepted()) {
                                            try {
                                                System.out.println("Sending");
                                                outRequest.get(userFromId).writeObject(new Friend(response.getName(), myID));
                                                Thread.sleep(300);
                                                friendList.get(myID).add(userFromId);
                                                friendList.get(userFromId).add(myID);
                                                HashMap<Integer, Boolean> map1 = new HashMap<>();
                                                HashMap<Integer, Boolean> map2 = new HashMap<>();
                                                map1.put(myID, socketHashMap.containsKey(userFromId));
                                                map2.put(userFromId, socketHashMap.containsKey(myID));
                                                outRequest.get(userFromId).writeObject(map1);
                                                outRequest.get(myID).writeObject(map2);
                                            } catch (IOException | InterruptedException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    } catch (SQLException e) {
                                        e.printStackTrace();
                                    }
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
        });
        friendRequestHandler.start();

        System.out.println("Starting Server on host : 192.168.1.166 port : 7070 ");
        try (ServerSocket server = new ServerSocket(7070, 50, InetAddress.getByName("192.168.1.166"))) {
            while (true) {
                System.out.println("Waiting for connection");
                Socket socket = server.accept();
                System.out.println("Accepted connection");
                new Thread(() -> {
                    try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                         ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                        int myId = (int) in.readObject();
                        friendList.put(myId, new ArrayList<>());
                        createMessageHistory(myId);
                        try (Connection conn = DriverManager.getConnection("jdbc:mysql://192.168.1.166:3306/user1", "IgorKlimov", "pass")) {
                            String sql = "SELECT * FROM link WHERE user_id = ?";
                            PreparedStatement statement = conn.prepareStatement(sql);
                            statement.setInt(1, myId);
                            ResultSet resultSet = statement.executeQuery();
                            while (resultSet.next()) {
                                int friendId = resultSet.getInt(2);
                                friendList.get(myId).add(friendId);
                                System.out.println("user: " + myId + "has a friend with ID:" + friendId);
                                createMessageHistory(friendId);
                            }
                            socketHashMap.put(myId, socket);

                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                        Message msg;
                        if (!outputMap.containsKey(myId)) {
                            outputMap.put(myId, out);
                        } else {
                            outputMap.remove(myId);
                            outputMap.put(myId, out);
                        }

//                        read my messages
                        Thread t = new Thread(() -> {
//                            read database message history
                            ArrayDeque<Message> localQue = new ArrayDeque<>();
                            try (Connection connection = DriverManager.getConnection("jdbc:mysql://192.168.1.166:3306/user1", "IgorKlimov", "pass")) {
                                String sql = "SELECT * FROM message WHERE fromID = ? || toID = ?";
                                PreparedStatement statement = connection.prepareStatement(sql);
                                statement.setInt(1, myId);
                                statement.setInt(2, myId);
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
                                    outputMap.get(myId).writeObject(localQue.poll());
                                }
                                for (Message message : messageQue.get(myId)) {
                                    outputMap.get(myId).writeObject(message);
                                }
                                ch = c -> {
                                    while (c.next()) {
                                        if (messageQue.get(myId).size() > 0 && messageQue.get(myId).size() > c.getFrom()
                                                && messageQue.get(myId).get(c.getFrom()) != null
                                                && outputMap.containsKey(myId)) {
                                            try {
                                                Message message = messageQue.get(myId).get(c.getFrom());
                                                System.out.println("sending " + message.getMsg());
                                                messageQue.get(myId).remove(c.getFrom());
                                                outputMap.get(myId).writeObject(message);
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                };
                                messageQue.get(myId).addListener(ch);
                            } catch (SQLException | IOException e) {
                                e.printStackTrace();
                            }
                        });
                        t.start();
//                        send messages to que
                        while ((msg = (Message) in.readObject()) != null) {
                            if (msg.getMsg().equals("done")) {
                                Thread.sleep(500);
                                dbThread.interrupt();
                                t.stop();
                                outputMap.remove(myId);
                                messageQue.get(myId).removeListener(ch);
                                socketHashMap.remove(myId);
                                return;
                            } else {
                                messageQue.get(myId).add(msg);
                                messageQue.get(msg.getToID()).add(msg);
                                dbQue.add(msg);
                                System.out.println("added " + msg.getMsg());
                            }
                        }

                    } catch (IOException | ClassNotFoundException | InterruptedException e) {
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
        }
        for (Integer id : friendRequests.keySet()) {
            for (FriendRequest friendRequest : friendRequests.get(id)) {
                String sql = "INSERT INTO friend_request VALUES (?,?)";
                PreparedStatement statement = connection.prepareStatement(sql);
                statement.setInt(1, friendRequest.getUserToId());
                statement.setInt(2, friendRequest.getUserFromId());
                statement.execute();
            }
            friendRequests.get(id).clear();
        }

    }

    private static void createMessageHistory(int userId) {
        if (!messageQue.containsKey(userId)) {
            System.out.println("Creating a que");
            messageQue.put(userId, FXCollections.observableArrayList());
        }
        if (!friendRequests.containsKey(userId)) {
            System.out.println("Creating friend Requests memory");
            friendRequests.put(userId, FXCollections.observableArrayList());
        }
    }
}
