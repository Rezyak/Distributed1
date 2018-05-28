package it.ds1;
import it.ds1.State;
import static it.ds1.Messages.*;

import java.util.Map;

import akka.actor.ActorRef;

public class NodeState extends State {
    
    public NodeState(Integer id){
        super(id);
    }

    public void putAllMembers(GroupView message){
		this.groupView.putAllMember(message.groupView);
        this.groupView.setSeqnum(message.groupViewSeqnum);
    }
}