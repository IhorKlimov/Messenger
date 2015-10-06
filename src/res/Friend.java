package res;

import java.io.Serializable;

/**
 * Created by Igor Klimov on 10/5/2015.
 */
public class Friend implements Serializable {
    private String name;
    private int ID;
    private boolean isOnline;

    public Friend(String name, int ID) {
        this.name = name;
        this.ID = ID;
    }

    public String getName() {
        return name;
    }

    public int getID() {
        return ID;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setIsOnline(boolean isOnline) {
        this.isOnline = isOnline;
    }
}
