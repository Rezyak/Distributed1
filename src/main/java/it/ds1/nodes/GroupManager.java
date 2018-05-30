package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.Logging;

import java.util.Queue;
import java.util.LinkedList;
import javafx.util.Pair;

import akka.actor.Props;
import akka.actor.ActorRef;

public class GroupManager extends Node{

    private Queue<Pair<ActorRef, Join>> joinQueue;

    private GroupManager(int id, String remotePath) {
        super(id, remotePath);
        this.joinQueue = new LinkedList<>();
        this.state.putSelf(id, getSelf());        
        printInstallView();
    }

    static public Props props(int id, String remotePath) {
		return Props.create(GroupManager.class, () -> new GroupManager(id, remotePath));
	}

	private void onJoin(Join message) {
        if (onGroupViewUpdate==true){
            Logging.log("join request from "+message.id+" enqueued");
            this.joinQueue.add(new Pair(getSender(), message));
            return;
        }     
        
        this.onGroupViewUpdate = true;
        Logging.log("join request from "+message.id);   

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
        allToAll(this.state.getGroupViewSeqnum(), this.id);        
    }

    @Override
    protected void onViewInstalled(){
        super.onViewInstalled();
        if (this.joinQueue.size()>0){
            Pair<ActorRef, Join> item = this.joinQueue.remove();
            Logging.log("dequeue");
            getSelf().tell(item.getValue(), item.getKey());        
        }
    }
   
    @Override
	public Receive createReceive() {
		return this.getReceive()
            .match(Join.class, this::onJoin)
            .build();
	}
}