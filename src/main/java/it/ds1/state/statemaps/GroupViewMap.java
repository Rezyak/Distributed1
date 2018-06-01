package it.ds1;
import static it.ds1.Messages.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;

import akka.actor.ActorRef;

public class GroupViewMap extends Iterable<ActorRef>{
    private Integer groupViewSeqnum;
    
    public GroupViewMap(Integer groupViewSeqnum){
        this.groupViewSeqnum = groupViewSeqnum;
    }

    public Integer getSeqnum(){
        return this.groupViewSeqnum;
    }
    public void setSeqnum(Integer seqnum){
        this.groupViewSeqnum = seqnum;
    }
    
    public void putMember(Integer id, ActorRef nodeRef){
        this.map.put(id, nodeRef);  
    }
    public void removeMember(Integer id){
        this.map.remove(id);  
    }

    public void putAllMember(Map<Integer, ActorRef> members){
        this.map = new HashMap<>();
		this.map.putAll(members); 
    }
}