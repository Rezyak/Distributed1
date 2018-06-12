package it.ds1;
import static it.ds1.Messages.*;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

public class MessageMap extends Iterable<ChatMsg>{

    protected Set<Flush> flushMap;
    protected List<ChatMsg> buffer;    

    public MessageMap(){
        this.flushMap = new HashSet<>();
        this.buffer = new ArrayList<>();
    }

    // unstable messages
    public void putMessage(Integer id, ChatMsg msg){
        this.map.put(id, msg);
    }

    //Flush set methods
    public Integer getFlushSize(){
        return this.flushMap.size();
    }
    public void setFlush(Flush msg){
        this.flushMap.add(msg);
    }
    public void clearFlush(){
        this.flushMap = new HashSet<>();
    }

    //buffer list methods
    public void addBuffer(ChatMsg msg){
        this.buffer.add(msg);
    }
    public List<ChatMsg> getBuffer(){
        return this.buffer;
    }
    public void clearBuffer(){
        this.buffer = new ArrayList<>();
    }
}