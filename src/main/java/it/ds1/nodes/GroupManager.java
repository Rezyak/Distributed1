package it.ds1;
import static it.ds1.Messages.*;
import akka.actor.Props;

public class GroupManager extends Node{

    private GroupManager(int id, String remotePath) {
        super(id, remotePath);
        this.state.putSelf(id, getSelf());        
        printInstallView();
    }

    static public Props props(int id, String remotePath) {
		return Props.create(GroupManager.class, () -> new GroupManager(id, remotePath));
	}

	private void onJoin(Join message) {
        this.onGroupViewUpdate = true;        
		int id = message.id;
        this.state.putMember(id, getSender());
        updateGroupView();
	}

    private void updateGroupView(){
        // this.state.printState();
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