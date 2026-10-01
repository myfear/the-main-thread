----------------------------- MODULE InventoryFixed -----------------------------
EXTENDS Integers

CONSTANT Workers
VARIABLES stock, pc
vars == <<stock, pc>>

Init ==
    /\ stock = 1
    /\ pc = [w \in Workers |-> "reserve"]

Reserve(w) ==
    /\ pc[w] = "reserve"
    /\ stock' = IF stock > 0 THEN stock - 1 ELSE stock
    /\ pc' = [pc EXCEPT ![w] = "done"]

Terminated ==
    /\ \A w \in Workers : pc[w] = "done"
    /\ UNCHANGED vars

Next == (\E w \in Workers : Reserve(w)) \/ Terminated

TypeOK == stock \in Int /\ pc \in [Workers -> {"reserve", "done"}]
NoOversell == stock >= 0
================================================================================
