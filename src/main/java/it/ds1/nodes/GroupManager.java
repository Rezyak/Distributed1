package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.GlobalState;

import akka.actor.Props;

public class GroupManager extends Node{
    private GlobalState state = null;

    private GroupManager(int id, String remotePath, GlobalState state) {
        super(id, remotePath, state);
        this.state = state;
        this.state.putSelf(id, getSelf());        
        this.state.printState();
    }

    static public Props props(int id, String remotePath,  GlobalState state) {
		return Props.create(GroupManager.class, () -> new GroupManager(id, remotePath, state));
	}

	private void onJoin(Join message) {
		int id = message.id;
        this.state.putMember(id, getSender());
        updateGroupView();
	}

    private void updateGroupView(){
        this.state.printState();
        multicast(new GroupView(
            this.state.getGroupView(), 
            this.state.getGroupViewSeqnum()
        ));
        allToAll();        
    }
    @Override
	public Receive createReceive() {
		return this.getReceive()
            .match(Join.class, this::onJoin)
            .build();
	}
}