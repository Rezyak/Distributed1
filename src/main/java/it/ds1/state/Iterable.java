package it.ds1;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;

import akka.actor.ActorRef;

public class Iterable<T> {
    public interface Action<T>{
        public void perform(Integer id, T value);
    }

    protected Map<Integer, T> map;

    public Iterable(){
        this.map = new HashMap<>();
    }

    public void forEach(Action f){
        Iterator<Map.Entry<Integer, T>> iter = map.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Integer, T> entry = iter.next();
            f.perform(entry.getKey(), entry.getValue());
        }
    } 

    public void shuffledForEach(Action f){
        List<Integer> keys = new ArrayList(map.keySet());
        Collections.shuffle(keys);
        for (Integer key : keys) {
            T aref = map.get(key);
            f.perform(key, aref);            
        }
    }

    public Map<Integer, T> getMap(){
        return this.map;
    }

    public T exists(Integer key){
        return map.get(key);
    }
    
}