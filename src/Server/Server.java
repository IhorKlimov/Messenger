package Server;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;

/**
 * Created by Igor Klimov on 8/15/2015.
 */
public class Server {
    static HashMap<Integer, PrintStream> outList = new HashMap<>();
    static volatile Deque<String> messages = new ArrayDeque<>();

    public static void main(String[] args) {
        System.out.println("Starting Server on host : 192.168.1.166 port : 7070 ");
        try (ServerSocket server = new ServerSocket(7070, 50, InetAddress.getByName("192.168.1.166"))) {
            while (true) {
                System.out.println("Waiting for connection");
                Socket socket = server.accept();
                System.out.println("Accepted connection");
                new Thread(() -> {
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(),"windows-1251"));
                         PrintStream out = new PrintStream(socket.getOutputStream(),true, "windows-1251")) {
                        int client = Integer.valueOf(in.readLine());
                        int connectTo = Integer.valueOf(in.readLine());
                        String msg;
                        System.out.println("Recieved connection with user: " + client + " ,who wants to connect to user: " + connectTo);
                        outList.put(client, out);

                        while ((msg = in.readLine()) != null) {
                            if (msg.equals("done")) {
                                messages.add("done");
                                return;
                            }
                            System.out.println("Got message: " + msg);
                            messages.add(msg);
                            System.out.println("added " + msg);
                            while (messages.size() > 0) {
                                System.out.println("sending " + messages.peek());
                                outList.get(connectTo).println(messages.poll());
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
