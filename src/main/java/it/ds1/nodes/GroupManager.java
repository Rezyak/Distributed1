package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.Commands;
import it.ds1.Logging;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import javafx.util.Pair;
import java.util.concurrent.atomic.AtomicBoolean;

import java.io.Serializable;
import java.io.IOException;

import java.lang.ProcessBuilder;
import java.lang.Process;
import java.lang.Runtime;

import akka.actor.Props;
import akka.actor.ActorRef;
import akka.actor.Cancellable;

public class GroupManager extends Node{
    private static Integer mView = 0;
    private static Integer nodesID = 1;

    private Map<Integer,Cancellable> messageTimeout;

    private GroupManager(int id, String remotePath) {
        super(id, remotePath);
    }

    @Override 
    protected void init(int id){
        atomicMap.put(Commands.McrashJoin, new AtomicBoolean());
        atomicMap.put(Commands.McrashMessage, new AtomicBoolean());
        atomicMap.put(Commands.McrashViewI, new AtomicBoolean());
        atomicMap.put(Commands.joinOnJoin, new AtomicBoolean());        
        atomicMap.put(Commands.joinOnMulticast, new AtomicBoolean());
        atomicMap.put(Commands.joinOnMessage, new AtomicBoolean());
        atomicMap.put(Commands.joinOnViewI, new AtomicBoolean());

        super.init(id);

        this.messageTimeout = new HashMap<>();

        // put himself into the member list
        // tell myself to install the view
        this.state.putMember(id, getSelf());

        GroupView updateView = new GroupView(
            this.state.getGroupView(), 
            mView
        );
        this.state.groupViewChange(updateView);   
        getSelf().tell(new InstallView(), getSelf());
    }

    static public Props props(int id, String remotePath) {
		return Props.create(GroupManager.class, () -> new GroupManager(id, remotePath));
	}

    /**
    *   on receiving a join request from a node
    *   - if should be crashed or isolated do nothing
    *   - if should receive a join during a join, create a local node
    *   - add the member, send him his new ID, update the Group View
    */
	private void onJoin(Join message) {
        if(atomicMap.get(Commands.crash).get()){
            // Logging.out(this.id+" is crashed ");
            return;
        }
        if(atomicMap.get(Commands.isolate).get()){
            // Logging.out(this.id+" is isolated ");
            return;
        }
        Logging.out(this.id+" join request from "+nodesID);        
        
        if(atomicMap.get(Commands.joinOnJoin).compareAndSet(true, false)) createLocalNode();
        cancelTimers();

        this.state.putMember(nodesID, getSender());        
        getSender().tell(new JoinID(nodesID), getSelf());
        nodesID++;

        if(atomicMap.get(Commands.McrashJoin).compareAndSet(true, false)){
            sendRandom(new Crash());
        }

        updateGroupView();
	}

    /**
    *   update the Group View seq. number
    *   - send a multicast message for Group View Change
    *   - all-to-all
    */
    private void updateGroupView(){
        Integer nextGroupViewSeqnum = this.state.getMaxView()+1;

        GroupView updateView = new GroupView(
            this.state.getGroupView(), 
            nextGroupViewSeqnum
        );
        this.state.groupViewChange(updateView);        
        
        updateViewMulticast(updateView);
        setFlushTimeout();                    
        allToAll(nextGroupViewSeqnum-1, this.id);        
    }

    private void updateViewMulticast(GroupView m){
        this.generalMulticast(m);
    }

    /**
    *   Check testing commands before calling super
    */
    @Override
    protected void multicast(ChatMsg m){
        if(atomicMap.get(Commands.crash).get()){
            // Logging.out(this.id+" is crashed ");
            return;
        }
        if(atomicMap.get(Commands.isolate).get()){
            // Logging.out(this.id+" is isolated ");
            return;
        }   
        if(atomicMap.get(Commands.joinOnMulticast).compareAndSet(true, false)) createLocalNode();        
        super.multicast(m);        
    }

    /**
    *   After handling the message reception
    *   - set a timeout for crash detection
    */
    @Override
    protected void onMessage(ChatMsg msg){
        super.onMessage(msg);

        if(atomicMap.get(Commands.crash).get()){
            return;
        }
        if(atomicMap.get(Commands.isolate).get()){
            return;
        }
        if(atomicMap.get(Commands.McrashMessage).compareAndSet(true, false)) sendRandom(new Crash());
        if(atomicMap.get(Commands.joinOnMessage).compareAndSet(true, false)) createLocalNode();        
        
        Boolean selfMessage = msg.senderID.intValue() == this.id; 
        Boolean inGroup = this.state.isMember(msg.senderID);
        if (selfMessage==false && inGroup){
            setMessageTimeout(msg.senderID);
        }
    }

    /**
    *   If a crash is detected
    *   - remove the node from member list
    *   - if the manager is alore, custom install view
    *   - if it is not, update the Group View in the standard way
    */
    private void onCrashDetected(int id){
        if(atomicMap.get(Commands.crash).get()){
            // Logging.out(this.id+" is crashed ");
            return;
        }

        Logging.out(this.id+" crash detected "+id);        
        cancelTimers();

        this.state.removeMember(id);

        Integer groupSize = this.state.getGroupViewSize()-1;
        if (groupSize.intValue()==0){
            //he is alone
            Integer nextGroupViewSeqnum = this.state.getMaxView()+1;    

            GroupView updateView = new GroupView(
                this.state.getGroupView(), 
                nextGroupViewSeqnum
            );
            this.state.groupViewChange(updateView);        
            getSelf().tell(new InstallView(), getSelf());
        }       
        else{
            updateGroupView();
        } 
    }

    
    @Override
    protected void onFlushTimeout(FlushTimeout msg){    
        if(atomicMap.get(Commands.crash).get()) return;

        super.onFlushTimeout(msg);   
        onCrashDetected(msg.id);        
    }

    protected void onMessageTimeout(MessageTimeout msg){
        if(atomicMap.get(Commands.crash).get()) return;
        
        Logging.out(this.id+" Message timeout for "+msg.id);
        onCrashDetected(msg.id);
    }

    /**
    *   After the View is installed
    *   - set timeouts for each node in the member list
    */
    @Override
    protected void onViewInstalled(){
        super.onViewInstalled();
        if(atomicMap.get(Commands.crash).get()) return;
        
        if(atomicMap.get(Commands.McrashViewI).compareAndSet(true, false)) sendRandom(new Crash());        
        if(atomicMap.get(Commands.joinOnViewI).compareAndSet(true, false)) createLocalNode();        
        
        List<Integer> memberList = this.state.getMemberList();
        for (Integer member: memberList){
            if (member.intValue()!=0){
                setMessageTimeout(member);
            }
        }
        
    }

   @Override    
    protected void onInit(Init msg){
        Integer nextGroupViewSeqnum = this.state.getMaxView()+1;
        this.mView = nextGroupViewSeqnum;
        init(0);
    }
    
    private void onMcrashJoin(McrashJoin msg){
        atomicMap.get(Commands.McrashJoin).set(true);
        Logging.out("prepare to kill a process on next join...");
    }
 
    private void onMcrashMessage(McrashMessage msg){
        atomicMap.get(Commands.McrashMessage).set(true);
        Logging.out("prepare to kill a process on next message...");
    }
  
    private void onMcrashViewI(McrashViewI msg){
        atomicMap.get(Commands.McrashViewI).set(true);
        Logging.out("prepare to kill a process on next install view...");
    }

    private void onJoinOnJoin(JoinOnJoin msg){
        atomicMap.get(Commands.joinOnJoin).set(true);
        Logging.out("creating a local node join during next join...");
    }
    private void onJoinOnMulticast(JoinOnMulticast msg){
        atomicMap.get(Commands.joinOnMulticast).set(true);
        Logging.out("creating a local node join during next multicast...");
    }
 
    private void onJoinOnMessage(JoinOnMessage msg){
        atomicMap.get(Commands.joinOnMessage).set(true);
        Logging.out("creating a local node join during next message receive...");
    }
  
    private void onJoinOnViewI(JoinOnViewI msg){
        atomicMap.get(Commands.joinOnViewI).set(true);
        Logging.out("creating a local node join during next install view...");
    }


    private void onCrashRandom(CrashRandom msg){
        sendRandom(new Crash());
    }

    private void createLocalNode(){
        Logging.out("creating node...");
        App.createLocalMember();
    }

    private void sendRandom(Serializable m){
        List<Integer> memberList = this.state.getMemberList();
        Integer members = memberList.size();
        if (members.intValue()<2){
            Logging.out("Not enough members");
            return;
        }
        Integer maxIdx = members-1;
        Integer victimIdx = (int)(System.currentTimeMillis() % maxIdx)+1; //skip index 0

        Integer victim = memberList.get(victimIdx);
        Logging.out("victim "+victim);
        ActorRef victimRef = this.state.getMember(victim);
        victimRef.tell(m, getSelf());
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

    private void setMessageTimeout(Integer id){
        Cancellable timer = this.messageTimeout.get(id);
        if (timer!=null) timer.cancel();
        this.messageTimeout.put(id, sendSelfAsyncMessage(Network.Td, new MessageTimeout(id)));
    }    

    @Override
	public Receive createReceive() {
		return this.getReceive()
            .match(Join.class, this::onJoin)
            .match(MessageTimeout.class, this::onMessageTimeout)            
            
            .match(McrashJoin.class, this::onMcrashJoin)            
            .match(McrashMessage.class, this::onMcrashMessage)            
            .match(McrashViewI.class, this::onMcrashViewI)
            .match(JoinOnJoin.class, this::onJoinOnJoin)                        
            .match(JoinOnMulticast.class, this::onJoinOnMulticast)            
            .match(JoinOnMessage.class, this::onJoinOnMessage)            
            .match(JoinOnViewI.class, this::onJoinOnViewI)                        
            .match(CrashRandom.class, this::onCrashRandom)                        
            .build();
	}
}