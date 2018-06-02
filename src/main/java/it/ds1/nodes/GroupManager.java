package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.Logging;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import javafx.util.Pair;

import akka.actor.Props;
import akka.actor.ActorRef;
import akka.actor.Cancellable;

public class GroupManager extends Node{

    private Map<Integer,Cancellable> messageTimeout;

    private GroupManager(int id, String remotePath) {
        super(id, remotePath);
       
    }

    @Override 
    protected void init(int id){
        super.init(id);
        this.messageTimeout = new HashMap<>();

        this.state.putMember(id, getSelf());
        GroupView updateView = new GroupView(
            this.state.getGroupView(), 
            0
        );
        this.groupViewQueue.add(updateView);        
        justInstallView();
    }

    private void justInstallView(){
        for(GroupView v: groupViewQueue){
            this.state.setGroupViewSeqnum(v);
            this.state.putAllMembers(v);            
            printInstallView();
        }
        cancelTimers();        
        clearBuffers();            
        this.groupViewQueue = new LinkedList<>();
    }

    static public Props props(int id, String remotePath) {
		return Props.create(GroupManager.class, () -> new GroupManager(id, remotePath));
	}

	private void onJoin(Join message) {

        Logging.log("join request from "+message.id);
        cancelTimers();
        clearBuffers();        
		int id = message.id;
        this.state.putMember(id, getSender());
        updateGroupView();
	}

    private void updateGroupView(){
        Integer nextGroupViewSeqnum = this.state.getGroupViewSeqnum()+1;
        
        try{
            GroupView lastGroup = this.groupViewQueue.getLast();
            nextGroupViewSeqnum = lastGroup.groupViewSeqnum+1;
        }catch(NoSuchElementException e){}

        GroupView updateView = new GroupView(
            this.state.getGroupView(), 
            nextGroupViewSeqnum
        );
        Logging.log("enqueue "+nextGroupViewSeqnum+" "+this.state.commaSeparatedList());
        this.groupViewQueue.add(updateView);
        multicast(updateView);
        setFlushTimeout();                    
        allToAll(nextGroupViewSeqnum-1, this.id);        
    }

    private void onCrashDetected(int id){     
        Logging.log("crash detected "+id);   
        cancelTimers();                                   
        clearBuffers(); 
        this.state.removeMember(id);
        
        Integer groupSize = this.state.getGroupViewSize()-1;
        if (groupSize==0){
            //he is alone
            Integer nextGroupViewSeqnum = this.state.getGroupViewSeqnum()+1;
            
            try{
                GroupView lastGroup = this.groupViewQueue.getLast();
                nextGroupViewSeqnum = lastGroup.groupViewSeqnum+1;
            }catch(NoSuchElementException e){}

            GroupView updateView = new GroupView(
                this.state.getGroupView(), 
                nextGroupViewSeqnum
            );
           
            this.groupViewQueue.add(updateView);
            justInstallView();            
        }       
        else{
            updateGroupView();
        } 
    }

    
    @Override
    protected void onFlushTimeout(FlushTimeout msg){       
        Logging.log("Flush timeout for "+msg.id);
        onCrashDetected(msg.id);
        
    }
    protected void onMessageTimeout(MessageTimeout msg){
        onCrashDetected(msg.id);
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

    @Override
	public Receive createReceive() {
		return this.getReceive()
            .match(Join.class, this::onJoin)
            .match(MessageTimeout.class, this::onMessageTimeout)            
            .build();
	}
}