package com.syncari.core.model.misc;

import java.util.HashSet;
import java.util.Set;

public class StateMachine<T extends Enum<T>> {
    private Set<Transition<T>> validTransitions = new HashSet<Transition<T>>();
    
    public StateMachine(Set<Transition<T>> validTransitions) {
        this.validTransitions = validTransitions;
    }
    
    public boolean isValidTransition(Transition<T> transition) {
        return validTransitions.contains(transition) || transition.from == transition.to;
    }

    public boolean isValidTransition(T from, T to) {
        return validTransitions.contains(new Transition<T>(from, to)) || from == to;
    }
}
