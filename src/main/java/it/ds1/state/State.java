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

import akka.actor.ActorRef;

public class State {
    protected Integer nodeID;

    protected GroupViewMap groupView;
    protected MessageMap messages;

    public State(Integer id){
        this.groupView = new GroupViewMap(0);
        this.messages = new MessageMap();
        this.nodeID = id;
    }

    public void putAllMembers(GroupView message){
		this.groupView.putAllMember(message.groupView);
        this.groupView.setSeqnum(message.groupViewSeqnum);
    }

    public void putMember(Integer id, ActorRef nodeRef){
		this.groupView.putMember(id, nodeRef);        
    }

    public void putSelf(Integer id, ActorRef nodeRef){
		this.groupView.putMember(id, nodeRef);               
    }

    public Integer getID(){return this.nodeID;}
    public GroupViewMap getGroupViewInstance(){return this.groupView;}
    public MessageMap getMessagesInstance(){return this.messages;}

    public Map<Integer, ActorRef> getGroupView(){return this.groupView.getMap();}
    public Integer getGroupViewSeqnum(){return this.groupView.getSeqnum();}

    public List<Integer> getMemberList(){return new ArrayList(getGroupView().keySet());}

    public Boolean insertNewMessage(ChatMsg msg, Integer id){
        ChatMsg oldMsg = this.messages.exists(msg.senderID);
        if (oldMsg==null){
            this.messages.putMessage(id, msg);
            return true;
        }

        if(oldMsg.msgSeqnum < msg.msgSeqnum){
            this.messages.putMessage(id, msg);                
            return true;
        }

        return false;
    }

    public void insertFlush(Flush msg){this.messages.setFlush(msg);}
    public Integer getFlushSize(){return this.messages.getFlushSize();}
    public void clearFlush(){this.messages.clearFlush();}
    
    public Integer getGroupViewSize(){return getMemberList().size();}
    public void setGroupViewSeqnum(Integer seqnum){this.groupView.setSeqnum(seqnum);}

    public void addBuffer(ChatMsg msg){this.messages.addBuffer(msg);}
    public List<ChatMsg> getBufferMessages(){return this.messages.getBuffer();}
    public void clearBuffer(){this.messages.clearBuffer();}

    public void printState(){
        Logging.log("**************************************");
        Logging.log("Node "+ this.nodeID +" State:");
        Logging.log("groupView seqnum "+ getGroupViewSeqnum());
        this.groupView.forEach(new GroupViewMap.Action<ActorRef>(){
            @Override
            public void perform(Integer id, ActorRef nodeRef){
                Logging.log(id +": "+nodeRef);                
            }
        });
        Logging.log("messages:");
        this.messages.forEach(new MessageMap.Action<ChatMsg>(){
            @Override
            public void perform(Integer id, ChatMsg msg){
                Logging.log(id +": ");
                Logging.log("msg seqnum: "+msg.msgSeqnum);
                Logging.log("msg sender id: "+msg.senderID);
                Logging.log("msg V"+msg.groupViewSeqnum);                
            }
        });
        Logging.log("**************************************");        
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