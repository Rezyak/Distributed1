package it.ds1;
import static it.ds1.Messages.*;

import akka.actor.Props;
import akka.actor.ActorRef;

public class GroupMember extends Node{

    private GroupMember(int id, String remotePath) {
        super(id, remotePath);           
    }

    static public Props props(int id, String remotePath) {
		return Props.create(GroupMember.class, () -> new GroupMember(id, remotePath));
	}

    public void preStart() {
		if (this.remotePath != null) { 
			getContext().actorSelection(remotePath).tell(new Join(this.id), getSelf());
		}
	}
    
    private void onGroupView(GroupView message) {
        this.onGroupViewUpdate = true;                    
        this.state.putAllMembers(message);
        // this.state.printState();
        allToAll();       
	}

    @Override
	public Receive createReceive() {
		return this.getReceive()
            .match(GroupView.class, this::onGroupView)        
            .build();
	}
}