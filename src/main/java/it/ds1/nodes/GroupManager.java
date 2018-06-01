package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.Logging;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.LinkedList;
import javafx.util.Pair;

import akka.actor.Props;
import akka.actor.ActorRef;
import akka.actor.Cancellable;

public class GroupManager extends Node{

    private Queue<Pair<ActorRef, Join>> joinQueue;

    private Map<Integer,Cancellable> messageTimeout;

    private GroupManager(int id, String remotePath) {
        super(id, remotePath);
       
    }

    @Override 
    protected void init(int id){
        super.init(id);
        this.joinQueue = new LinkedList<>();
        this.messageTimeout = new HashMap<>();

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

    private void onCrashDetected(int id){
        this.onGroupViewUpdate = true;        
        Logging.log("crash detected "+id);   

        this.state.removeMember(id);

        Integer groupSize = this.state.getGroupViewSize()-1;
        if (groupSize==0){
            //he is alone
            this.state.setGroupViewSeqnum(this.state.getGroupViewSeqnum()+1);
            cancelTimers();                                   
            init(this.id);
        }       
        else{
            updateGroupView();
        } 
    }

    private void updateGroupView(){
        // this.state.printState();
        multicast(new GroupView(
            this.state.getGroupView(), 
            this.state.getGroupViewSeqnum()
        ));
        setFlushTimeout();                    
        allToAll(this.state.getGroupViewSeqnum(), this.id);        
    }
    @Override
    protected void onFlushTimeout(FlushTimeout msg){
        Logging.log("Flush timeout for "+msg.id);
        onCrashDetected(msg.id);
        
    }

    @Override
    protected void onViewInstalled(){
        super.onViewInstalled();
        
        if (this.joinQueue.size()>0){
            Pair<ActorRef, Join> item = this.joinQueue.remove();
            Logging.log("dequeue");
            getSelf().tell(item.getValue(), item.getKey());        
            return;
        }
    }

    @Override
    protected void onMessage(ChatMsg msg){
        Boolean selfMessage = msg.senderID.compareTo(this.id)==0; 
        Boolean inGroup = this.state.isMember(msg.senderID);
        if (selfMessage==false && inGroup){
            Cancellable timer = this.messageTimeout.get(msg.senderID);
            if (timer!=null) timer.cancel();
            this.messageTimeout.put(msg.senderID, sendSelfAsyncMessage(Network.Td, new MessageTimeout(msg.senderID)));
        }
        
        super.onMessage(msg);
    }
   
    @Override
    protected void cancelTimers(){
        super.cancelTimers();
        List<Integer> memberList = this.state.getMemberList();
        for (Integer member: memberList){
            Cancellable timer = this.messageTimeout.get(member);
            if (timer!=null){
                timer.cancel();
                this.messageTimeout.put(member, null);        
            }
        }
    }
    private void setMessageTimeout(){
        List<Integer> memberList = this.state.getMemberList();
        for (Integer member: memberList){
            this.messageTimeout.put(member, sendSelfAsyncMessage(Network.Td, new MessageTimeout(member)));                    
        }
    }

    protected void onMessageTimeout(MessageTimeout msg){
        if (onGroupViewUpdate) return;
        onCrashDetected(msg.id);
    }    

    @Override
	public Receive createReceive() {
		return this.getReceive()
            .match(Join.class, this::onJoin)
            .match(MessageTimeout.class, this::onMessageTimeout)            
            .build();
	}
}