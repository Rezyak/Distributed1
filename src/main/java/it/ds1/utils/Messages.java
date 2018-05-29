package it.ds1;

import java.io.Serializable;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import akka.actor.ActorRef;

public class Messages {

    public static class Join implements Serializable {
        int id;

        public Join(int id) {
            this.id = id;
        }
    }

    public static class SendMessage implements Serializable{}

    public static class ChatMsg implements Serializable {
        final Integer msgSeqnum;
        final Integer senderID;
        final Integer groupViewSeqnum;

        public ChatMsg (Integer msgSeqnum, Integer senderID, Integer groupViewSeqnum){
            this.msgSeqnum = msgSeqnum;
            this.senderID = senderID;
            this.groupViewSeqnum = groupViewSeqnum;
        }
    }
    
    public static class Flush implements Serializable {
        final Integer groupViewSeqnum;
        final Integer senderID;        

        public Flush(Integer groupViewSeqnum, Integer senderID){
            this.groupViewSeqnum = groupViewSeqnum;
            this.senderID = senderID;            
        }        
    }

    public static class GroupView implements Serializable {
        Map<Integer, ActorRef> groupView;
        final Integer groupViewSeqnum;

        public GroupView(Map<Integer, ActorRef> groupView, Integer groupViewSeqnum) {
            this.groupView = Collections.unmodifiableMap(new HashMap<Integer, ActorRef>(groupView));
            this.groupViewSeqnum = groupViewSeqnum;
        }
    }
}
