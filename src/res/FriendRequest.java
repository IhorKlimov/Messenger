package res;

import java.io.Serializable;

/**
 * Created by Igor Klimov on 10/4/2015.
 */
public class FriendRequest implements Serializable {
    private int userToId;
    private int userFromId;
    private String name;

    public FriendRequest(int userToId, int userFromId, String name) {
        this.userToId = userToId;
        this.userFromId = userFromId;
        this.name = name;
    }

    public int getUserToId() {
        return userToId;
    }

    public int getUserFromId() {
        return userFromId;
    }

    public String getName() {
        return name;
    }
}
