package res;

import java.io.Serializable;

/**
 * Created by Igor Klimov on 10/4/2015.
 */
public class FriendResponse implements Serializable {
    private String name;
    private int userToId;
    private int userFromId;
    private boolean accepted;

    public FriendResponse(String name, int userToId, int userFromId, boolean accepted) {
        this.name = name;
        this.userToId = userToId;
        this.userFromId = userFromId;
        this.accepted = accepted;
    }

    public String getName() {
        return name;
    }

    public int getUserToId() {
        return userToId;
    }

    public int getUserFromId() {
        return userFromId;
    }

    public boolean isAccepted() {
        return accepted;
    }
}
