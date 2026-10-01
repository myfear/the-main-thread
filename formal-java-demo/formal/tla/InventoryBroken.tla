---------------------------- MODULE InventoryBroken ----------------------------
EXTENDS Integers

CONSTANT Workers
VARIABLES stock, pc
vars == <<stock, pc>>

Init ==
    /\ stock = 1
    /\ pc = [w \in Workers |-> "check"]

Check(w) ==
    /\ pc[w] = "check"
    /\ pc' = [pc EXCEPT ![w] = IF stock > 0 THEN "decrement" ELSE "done"]
    /\ UNCHANGED stock

Decrement(w) ==
    /\ pc[w] = "decrement"
    /\ stock' = stock - 1
    /\ pc' = [pc EXCEPT ![w] = "done"]

Terminated ==
    /\ \A w \in Workers : pc[w] = "done"
    /\ UNCHANGED vars

Next == (\E w \in Workers : Check(w) \/ Decrement(w)) \/ Terminated

TypeOK == stock \in Int /\ pc \in [Workers -> {"check", "decrement", "done"}]
NoOversell == stock >= 0
================================================================================
