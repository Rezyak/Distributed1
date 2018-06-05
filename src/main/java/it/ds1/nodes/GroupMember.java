package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.Logging;

import java.io.Serializable;

import java.util.Queue;
import java.util.LinkedList;
import javafx.util.Pair;

import akka.actor.Props;
import akka.actor.ActorRef;
import akka.actor.Cancellable;

public class GroupMember extends Node{

    private Cancellable messageTimeout;

    private GroupMember(int id, String remotePath) {
        super(id, remotePath);   
    }

    @Override 
    protected void init(int id){
        super.init(id);

        this.messageTimeout = null;
    }

    static public Props props(int id, String remotePath) {
		return Props.create(GroupMember.class, () -> new GroupMember(id, remotePath));
	}

    public void preStart() {
		if (this.remotePath != null) {          
			getContext().actorSelection(remotePath).tell(new Join(), getSelf());
            checkMessageTimeout();        
		}
	}
    
    private void onJoinID(JoinID message) {
        if(crashed.get()) return;
        
        this.id = message.id; 
        checkMessageTimeout();
	}

    private void onGroupView(GroupView message) {
        if(crashed.get()) return;
        
        this.state.clearFlush();        
        checkMessageTimeout();
        // Logging.log(this.state.getGroupViewSeqnum(),
        //     "request update group view "+message.groupViewSeqnum);
        cancelTimers();         
        this.groupViewQueue.add(message);

        this.state.putAllMembers(message);
        setFlushTimeout();             
        
        allToAll(message.groupViewSeqnum-1, this.id);       
	}
    

    @Override
    protected void onMessage(ChatMsg msg){
        if(crashed.get()) return;
        
        if (msg.senderID.compareTo(0)==0){
            checkMessageTimeout();
        }
        super.onMessage(msg);
    }

    private void checkMessageTimeout(){
        if (this.messageTimeout!=null) this.messageTimeout.cancel();
        this.messageTimeout = sendSelfAsyncMessage(Network.Td, new MessageTimeout(0));
    }

    @Override
    protected void onFlushTimeout(FlushTimeout msg){
        super.onFlushTimeout(msg);
        // Logging.log(this.state.getGroupViewSeqnum(),
        //     "Flush timeout for "+msg.id);
    }

    protected void onMessageTimeout(MessageTimeout msg){
        Logging.out("onTimeout");
        //stop timers and clear state
        cancelTimers();
        init(-1);
        //Rejoin
        Logging.out("rejoin request");
        preStart();
    }
    @Override    
    protected void onInit(Init msg){
        super.onInit(msg);
        init(-1);        
        preStart();
    }
    @Override
    protected void cancelTimers(){
        super.cancelTimers();
        if (this.messageTimeout!=null){
            this.messageTimeout.cancel();
            this.messageTimeout = null;
        }
    }

    @Override
	public Receive createReceive() {
		return this.getReceive()
            .match(GroupView.class, this::onGroupView)        
            .match(MessageTimeout.class, this::onMessageTimeout)            
            .match(JoinID.class, this::onJoinID)            
            .build();
	}
}