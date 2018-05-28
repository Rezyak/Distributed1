package it.ds1;
import static it.ds1.Messages.*;

public class MessageMap extends Iterable<ChatMsg>{

    public MessageMap(){

    }

    public void putMessage(Integer id, ChatMsg msg){
        this.map.put(id, msg);  
    }
}