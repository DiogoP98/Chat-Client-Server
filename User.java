import java.nio.*;
import java.nio.charset.*;
    
public class User {
    private State state;
    private String nick;
    private String room;
    private String buffer;

    static enum State {
        INIT,
        OUTSIDE,
        INSIDE
    }
    
    User() {
        state = State.INIT;
        nick = null;
        room = null;
        buffer = "";
    }

    State getState() {
        return state;
    }

    void setState(State state) {
        this.state = state;
    }

    String getNick() {
        return nick;
    }

    void setNick(String nick) {
        this.nick = nick;
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
