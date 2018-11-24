import java.nio.*;
import java.nio.charset.*;
    
public class User {
    private State state;
    private String name;
    private String room;
    private String buffer;

    static enum State {
        INIT,
        OUTSIDE,
        INSIDE
    }
    
    User() {
        state = State.INIT;
        name = null;
        room = null;
        buffer = "";
    }

    State getState() {
        return state;
    }

    void setState(State state) {
        this.state = state;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    String getRoom() {
        return room;
    }

    void setRoom(String room) {
        this.room = room;
    }

    void cacheMessage(String msg) {
        buffer += msg;
    }

    String getFullMessage() {
        String r = buffer;
        buffer = "";
        return r;
    }
}
