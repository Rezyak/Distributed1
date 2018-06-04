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

    private Integer nodesID;
    private Map<Integer,Cancellable> messageTimeout;

    private GroupManager(int id, String remotePath) {
        super(id, remotePath);
       
    }

    @Override 
    protected void init(int id){
        super.init(id);
        
        this.nodesID = 1;
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
        this.state.clearFlush();            
        this.groupViewQueue = new LinkedList<>();
    }

    static public Props props(int id, String remotePath) {
		return Props.create(GroupManager.class, () -> new GroupManager(id, remotePath));
	}

	private void onJoin(Join message) {

        Logging.log(this.state.getGroupViewSeqnum(),
            "join request from "+nodesID);
        cancelTimers();
        this.state.clearFlush();
        this.state.putMember(nodesID, getSender());
        getSender().tell(new JoinID(nodesID), getSelf());
        nodesID++;
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
        // Logging.log(this.state.getGroupViewSeqnum(),
        //     "enqueue "+nextGroupViewSeqnum+" "+this.state.commaSeparatedList());
        this.groupViewQueue.add(updateView);
        multicast(updateView);
        setFlushTimeout();                    
        allToAll(nextGroupViewSeqnum-1, this.id);        
    }

    private void onCrashDetected(int id){     
        // Logging.log(this.state.getGroupViewSeqnum(),
        //     "crash detected "+id);   
        cancelTimers();                                   
        this.state.clearFlush();
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
        // Logging.log(this.state.getGroupViewSeqnum(),
        //     "Flush timeout for "+msg.id);
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