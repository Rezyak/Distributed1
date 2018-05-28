package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.NodeState;

import akka.actor.Props;
import akka.actor.ActorRef;

public class GroupMember extends Node{
    private NodeState state = null;

    private GroupMember(int id, String remotePath, NodeState state) {
        super(id, remotePath, state);
        this.state = state;
        this.state.printState();            
    }

    static public Props props(int id, String remotePath, NodeState state) {
		return Props.create(GroupMember.class, () -> new GroupMember(id, remotePath, state));
	}

    public void preStart() {
		if (this.remotePath != null) { 
			getContext().actorSelection(remotePath).tell(new Join(this.id), getSelf());
		}
	}
    
    private void onGroupView(GroupView message) {
        this.state.putAllMembers(message);
        this.state.printState();
        allToAll();       
	}

    @Override
	public Receive createReceive() {
		return this.getReceive()
            .match(GroupView.class, this::onGroupView)        
            .build();
	}
}