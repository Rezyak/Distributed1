package it.ds1;
import static it.ds1.Messages.*;
import it.ds1.Logging;
import it.ds1.GroupViewMap;
import it.ds1.MessageMap;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import akka.actor.ActorRef;

public class State {
    protected Integer nodeID;

    // current state
    protected GroupViewMap groupView;

    /**
    *   In order to maintain the state of GroupViews and Messages/Flushs
    *   - build a map: GroupView seq. num -> GroupView class
    *   -build a map: GroupView seq. num -> MessageMap class
    */
    protected Map<Integer, GroupView> groupViewMap;
    protected Map<Integer, MessageMap> messagesMap;

    public State(Integer id){
        this.nodeID = id;
        this.groupView = new GroupViewMap(null);
        this.groupViewMap = new HashMap<>();  
        this.messagesMap = new HashMap<>();
    }

    /**
    *   Add to the map the GroupView change message received
    */
    public void groupViewChange(GroupView message){
        this.groupViewMap.put(message.groupViewSeqnum, message);

        //update members in current state
        putAllMembers(message);
    }

    /**
    *   In order to determine whether deliver or not a message
    *   - get the Message map of the MESSAGE VIEW
    *   - check if the message is for the CURRENT View
    *       * if yes, try to insert the message
    *       * if not, add the message to the buffer 
    *       (once the view is installed the buffered messages will be delivered)
    */
    public Boolean shouldDeliver(ChatMsg msg){
        Boolean selfMessage = msg.senderID.intValue() == this.nodeID.intValue(); 
        if(selfMessage) return false;

        Integer msgGroupView = msg.groupViewSeqnum;
        // Logging.out("check message buffer from "+msg.senderID+" in "+msgGroupView+" seq "+msg.msgSeqnum);        
        
        MessageMap mmap = messagesMap.get(msgGroupView);
        if (mmap == null){
            messagesMap.put(msgGroupView, new MessageMap());
            mmap = messagesMap.get(msgGroupView);
        }
        
        Boolean noGroupView = getGroupViewSeqnum()==null;
        if (noGroupView){
            mmap.addBuffer(msg);
            return false;
        }

        Boolean messageInView = msgGroupView.intValue() == getGroupViewSeqnum().intValue();
        if (messageInView) return insertNewMessage(msg);
        else{
            mmap.addBuffer(msg);
            return false;
        }
    }

    /**
    *   Given a message 
    *   - if the message do not already exists, insert and return true
    *   - if  the seq. number is grater than the old one, return true
    *       * it is inserted as the new unstable message (old one can be deleted)
    *   - return false otherwise
    */
    private Boolean insertNewMessage(ChatMsg msg){
        Integer msgGroupView = msg.groupViewSeqnum;
        MessageMap mmap = messagesMap.get(msgGroupView);
        
        ChatMsg oldMsg = mmap.exists(msg.senderID);
        if (oldMsg==null){
            mmap.putMessage(msg.senderID, msg);
            return true;
        }

        if(oldMsg.msgSeqnum < msg.msgSeqnum){
            mmap.putMessage(msg.senderID, msg);                
            return true;
        }

        return false;
    }

    /**
    *   Given a Flush message
    *   - get the GroupView map of the FLUSH MESSAGE
    *   - put the flush in the Set
    */
    public void addFlush(Flush msg){     
        Boolean selfMessage = msg.senderID.intValue() == this.nodeID; 
        if (selfMessage) return;

        Integer msgGroupView = msg.groupViewSeqnum;
        MessageMap mmap = messagesMap.get(msgGroupView);
        if (mmap == null){
            messagesMap.put(msgGroupView, new MessageMap());
            mmap = messagesMap.get(msgGroupView);
        }
        // Logging.out("receive flush "+msgGroupView+" from "+msg.senderID);
        mmap.setFlush(msg);
    }

    /**
    *   Check whether install the view or not
    *   - if received flushes from all members
    */
    public Boolean shouldInstallView(Integer view){
        Integer viewToInstall = view.intValue()+1;
        // Logging.out("should install view "+viewToInstall);
        
        Integer currentGroupView = getGroupViewSeqnum();
        if (currentGroupView!=null && viewToInstall<=currentGroupView) return false;
        
        GroupView groupView = groupViewMap.get(viewToInstall); //all new members
        MessageMap mmap = messagesMap.get(view); //precedent view flash

        if (groupView==null) return false;
        if (mmap==null) return false;

        Integer groupSize = new ArrayList(groupView.groupView.keySet()).size() -1;
        Integer flushSize = mmap.getFlushSize();
        return groupSize.intValue()==flushSize.intValue();
    }

    public List<ChatMsg> getBufferedMessages(){
        //up to current current
        Integer groupView = getGroupViewSeqnum();
        if (groupView==null) return new ArrayList<ChatMsg>();

        MessageMap mmap = messagesMap.get(groupView);
        if (mmap == null) return new ArrayList<ChatMsg>();
        return mmap.getBuffer();
    }

    public Boolean getNextView(){
        Integer nextView = getMinView()+1;
        GroupView groupView = groupViewMap.get(nextView);
        if (groupView==null) return false;

        setGroupViewSeqnum(groupView); 
        putAllMembers(groupView);
        return true;
    }
    public Integer getMaxView(){
        try{
            return (Integer) Collections.max(new ArrayList(groupViewMap.keySet()));
        }catch(NoSuchElementException e){
            Integer current = getGroupViewSeqnum();
            if (current!=null) return current;
            Logging.out("Max view ERROR: no maxBuffered and no current");
            return 0;
        }
    }
    public Integer getMinView(){
        Integer current = getGroupViewSeqnum();
        if (current!=null) return current;

        try{
            return (Integer) Collections.min(new ArrayList(groupViewMap.keySet()))-1;
        }catch(NoSuchElementException e){
            Logging.out("Min view ERROR: no current and no minBuffered");
            return 0;
        }
    }

    public MessageMap getCurrentMessagesInstance(){
        //up to current current
        Integer groupView = getGroupViewSeqnum();
        if (groupView==null) return new MessageMap();

        MessageMap mmap = messagesMap.get(groupView);
        if (mmap == null) return new MessageMap();
        return mmap;
    }

    // ________________Current View________________

    public Integer getID(){return this.nodeID;}
    public void setID(Integer id){this.nodeID = id;}
    
    // ________________Group View________________
    public GroupViewMap getGroupViewInstance(){
        return this.groupView;
    }
    public Map<Integer, ActorRef> getGroupView(){
        return this.groupView.getMap();
    }
    public Integer getGroupViewSize(){
        return getMemberList().size();
    }

    public Integer getGroupViewSeqnum(){    
        return this.groupView.getSeqnum();
    }
    public void setGroupViewSeqnum(GroupView msg){
        this.groupView.setSeqnum(msg.groupViewSeqnum);
    }

    // ________________Members________________
    public ActorRef getMember(Integer id){
        return this.groupView.getMember(id);
    }
    public List<Integer> getMemberList(){
        return new ArrayList(getGroupView().keySet());
    }
    public void putAllMembers(GroupView message){
		this.groupView.putAllMember(message.groupView);
    }
    public void putMember(Integer id, ActorRef nodeRef){
		this.groupView.putMember(id, nodeRef);        
    }
    public void removeMember(Integer id){
		this.groupView.removeMember(id);        
    }    
    public Boolean isMember(Integer id){ 
        return getMemberList().contains(id);
    }

    
    protected String commaSeparatedList(){
        List<Integer> members = getMemberList();
        StringBuilder sb = new StringBuilder();
        String delim = "";
        for (Integer m : members) {
            sb.append(delim).append(m);
            delim = ",";
        }
        return sb.toString();
    }
}