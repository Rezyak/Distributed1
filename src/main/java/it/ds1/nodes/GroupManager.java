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

        atomicMap.put(Commands.McrashJoin, new AtomicBoolean());
        atomicMap.put(Commands.McrashMessage, new AtomicBoolean());
        atomicMap.put(Commands.McrashViewI, new AtomicBoolean());
        atomicMap.put(Commands.joinOnJoin, new AtomicBoolean());        
        atomicMap.put(Commands.joinOnMulticast, new AtomicBoolean());
        atomicMap.put(Commands.joinOnMessage, new AtomicBoolean());
        atomicMap.put(Commands.joinOnViewI, new AtomicBoolean());
    }

    @Override 
    protected void init(int id){
        super.init(id);
        this.messageTimeout = new HashMap<>();

        this.state.putMember(id, getSelf());
        GroupView updateView = new GroupView(
            this.state.getGroupView(), 
            mView
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
        if(atomicMap.get(Commands.crash).get()) return;
        if(atomicMap.get(Commands.isolate).get()) return;
        
        if(atomicMap.get(Commands.joinOnJoin).compareAndSet(true, false)) createLocalNode();        

        Logging.out(this.id+" join request from "+nodesID);
        cancelTimers();
        this.state.clearFlush();

        this.state.putMember(nodesID, getSender());
        getSender().tell(new JoinID(nodesID), getSelf());

        if(atomicMap.get(Commands.McrashJoin).compareAndSet(true, false)){
            sendRandom(new Crash());
        }

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

    @Override
    protected void multicast(Serializable m){
        if(atomicMap.get(Commands.crash).get()) return;   
        if(atomicMap.get(Commands.isolate).get()) return;      
        if(atomicMap.get(Commands.joinOnMulticast).compareAndSet(true, false)) createLocalNode();        
        super.multicast(m);        
    }

    @Override
    protected void onMessage(ChatMsg msg){
        super.onMessage(msg);

        if(atomicMap.get(Commands.crash).get()) return;   
        if(atomicMap.get(Commands.isolate).get()) return;
        if(atomicMap.get(Commands.McrashMessage).compareAndSet(true, false)) sendRandom(new Crash());
        if(atomicMap.get(Commands.joinOnMessage).compareAndSet(true, false)) createLocalNode();        
        
        Boolean selfMessage = msg.senderID.compareTo(this.id)==0; 
        Boolean inGroup = this.state.isMember(msg.senderID);
        if (selfMessage==false && inGroup){
            setMessageTimeout(msg.senderID);
        }
        
    }

    private void onCrashDetected(int id){     
        // Logging.log(this.state.getGroupViewSeqnum(),
        //     "crash detected "+id);   
        cancelTimers();                                   
        this.state.clearFlush();

        if(atomicMap.get(Commands.crash).get()) return;
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
        if(atomicMap.get(Commands.crash).get()) return;

        super.onFlushTimeout(msg);   
        onCrashDetected(msg.id);        
    }

    protected void onMessageTimeout(MessageTimeout msg){
        if(atomicMap.get(Commands.crash).get()) return;
        
        Logging.out(this.id+" Message timeout for "+msg.id);
        onCrashDetected(msg.id);
    }

    @Override
    protected void onViewInstalled(){
        super.onViewInstalled();
        if(atomicMap.get(Commands.crash).get()) return;
        
        if(atomicMap.get(Commands.McrashViewI).compareAndSet(true, false)) sendRandom(new Crash());        
        if(atomicMap.get(Commands.joinOnViewI).compareAndSet(true, false)) createLocalNode();        
        
        List<Integer> memberList = this.state.getMemberList();
        for (Integer member: memberList){
            if (member.compareTo(0)!=0){
                setMessageTimeout(member);
            }
        }
        
    }

   @Override    
    protected void onInit(Init msg){
        Integer nextGroupViewSeqnum = this.state.getGroupViewSeqnum()+1;
            
        try{
            GroupView lastGroup = this.groupViewQueue.getLast();
            nextGroupViewSeqnum = lastGroup.groupViewSeqnum+1;
        }catch(NoSuchElementException e){}

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
        if (members<2){
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