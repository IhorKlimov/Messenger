package Server;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Created by Igor Klimov on 9/25/2015.
 */
public class Message implements Serializable {
    private String msg;
    private LocalDateTime localDT;
    private int fromID;
    private int toID;

    public Message(String msg, LocalDateTime localDT, int fromID, int toID) {
        this.msg = msg;
        this.localDT = localDT;
        this.fromID = fromID;
        this.toID = toID;
    }

    public int getFromID() {
        return fromID;
    }

    public int getToID() {
        return toID;
    }

    public String getMsg() {
        return msg;
    }

    public LocalDateTime getLocalDT() {
        return localDT;
    }

}
