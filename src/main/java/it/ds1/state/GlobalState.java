package it.ds1;
import it.ds1.State;

import akka.actor.ActorRef;

public class GlobalState extends State {
    
    public GlobalState(Integer id){
        super(id);
    }

    public void putMember(Integer id, ActorRef nodeRef){
		this.groupView.putMember(id, nodeRef);  
        if (getGroupViewSeqnum() == null){
            this.groupView.setSeqnum(0);
        }
    }

    public void putSelf(Integer id, ActorRef nodeRef){
		this.groupView.putMember(id, nodeRef);               
    }

}