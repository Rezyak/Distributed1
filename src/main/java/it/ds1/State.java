package it.ds1;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

import akka.actor.ActorRef;

public class State {
    private Map<Integer, ActorRef> groupView = null;
    
    public interface Action{
        public void perform(Integer id, ActorRef nodeRef);
    }

    public State(){
        this.groupView = new HashMap<Integer, ActorRef>();
    }

    public Map<Integer, ActorRef> getGroupView(){
        return this.groupView;
    }

    public void putMember(Integer id, ActorRef nodeRef){
		this.groupView.put(id, nodeRef);        
    }
    public void putAllMembers(Map<Integer, ActorRef> members){
		this.groupView.putAll(members);        
    }

    public void forEach(Action f){
        Iterator<Map.Entry<Integer, ActorRef>> iter = groupView.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Integer, ActorRef> entry = iter.next();
            f.perform(entry.getKey(), entry.getValue());
        }
    }    
}