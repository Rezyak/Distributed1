package it.ds1;
import it.ds1.Logging;
import static it.ds1.Messages.*;
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
    protected MessageMap oldMessages;

    public State(Integer id){
        this.groupView = new GroupViewMap(null);
        this.oldMessages = new MessageMap();
        this.nodeID = id;
    }

    public Integer getID(){return this.nodeID;}
    public GroupViewMap getGroupViewInstance(){return this.groupView;}
    public MessageMap getOldMessagesInstance(){return this.oldMessages;}

    public Map<Integer, ActorRef> getGroupView(){return this.groupView.getMap();}
    public Integer getGroupViewSeqnum(){return this.groupView.getSeqnum();}

    public List<Integer> getMemberList(){return new ArrayList(getGroupView().keySet());}

    public Boolean insertNewMessage(ChatMsg msg, Integer id){
        ChatMsg oldMsg = this.oldMessages.exists(msg.senderID);
        if (oldMsg==null){
            this.oldMessages.putMessage(id, msg);
            return true;
        }

        if(oldMsg.msgSeqnum < msg.msgSeqnum){
            this.oldMessages.putMessage(id, msg);                
            return true;
        }

        return false;
    }

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
        Logging.log("messages");
        this.oldMessages.forEach(new MessageMap.Action<ChatMsg>(){
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
    // protected void printGroupView(){
    //     this.state.forEach(new State.Action(){
    //         @Override
    //         public void perform(Integer id, ActorRef nodeRef){
    //             System.out.println(id+" "+nodeRef);                
    //         }
    //     });
    // }
    // protected String commaSeparatedList(){
    //     List<Integer> members = this.state.getMemberList();
    //     StringBuilder sb = new StringBuilder();
    //     String delim = "";
    //     for (Integer m : members) {
    //         sb.append(delim).append(m);
    //         delim = ",";
    //     }
    //     return sb.toString();
    // }
}