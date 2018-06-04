package it.ds1;

import java.io.Serializable;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import akka.actor.ActorRef;

public class Messages {

    public static class Join implements Serializable {}
    public static class JoinID implements Serializable {
        final Integer id;
        public JoinID(int id){
            this.id = id;
        }
    }

    public static class SendMessage implements Serializable{}

    public static class MessageTimeout implements Serializable{
        final Integer id;
         public MessageTimeout(int id) {
            this.id = id;
        }
    }
    public static class FlushTimeout implements Serializable{
        final Integer id;
         public FlushTimeout(int id) {
            this.id = id;
        }
    }

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
    
    public static class Hello implements Serializable{
        final Integer senderID;

        public Hello(Integer id){
            this.senderID = id;
        }
    }


    public static class Flush implements Serializable {
        final Integer groupViewSeqnum;
        final Integer senderID;        

        @Override
        public boolean equals(Object obj) {
            if (this == obj)    return true;
            if (obj == null)    return false;
            if (getClass() != obj.getClass())   return false;
            
            Flush other = (Flush) obj;
            if (this.groupViewSeqnum != other.groupViewSeqnum 
            ||  this.senderID != other.senderID)    return false;
            
            return true;
        }

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
