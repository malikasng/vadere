package org.vadere.simulator.control.external.models;


import org.json.JSONObject;
import org.vadere.simulator.control.external.reaction.ReactionModel;
import org.vadere.state.scenario.Pedestrian;
import org.vadere.state.scenario.Topography;
import org.vadere.util.logging.Logger;
import rx.Subscription;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.stream.Collectors;

public abstract class ControlModel implements IControlModel {

    public static Logger logger = Logger.getLogger(Subscription.class);

    public Topography topography;
    public Double simTime;
    private CtlCommand command;
    protected ReactionModel reactionModel;
    protected HashMap<Pedestrian,LinkedList<Integer>> processedAgents;


    public ControlModel(){
        simTime = 0.0;
        this.reactionModel = new ReactionModel();
        processedAgents = new HashMap<>();
    }

    public ControlModel(ReactionModel reactionModel){
        simTime = 0.0;
        this.reactionModel = reactionModel;
        processedAgents = new HashMap<>();
    }


    public abstract boolean isPedReact();
    protected abstract void triggerRedRaction(Pedestrian ped);


    public void setProcessedAgents(Pedestrian ped, LinkedList<Integer> ids){

        if (processedAgents.containsKey(ped)){
            LinkedList<Integer> idsOld  = processedAgents.get(ped);
            ids.addAll(idsOld);
        }
        processedAgents.put(ped, ids);
    }

    public void setProcessedAgents(Pedestrian ped, int id){
        LinkedList<Integer> ids = new LinkedList<>();
        ids.add(id);
        setProcessedAgents(ped,ids);
    }

    public boolean isIdInList(Pedestrian ped, int id){
        if (processedAgents.containsKey(ped)){
            if (processedAgents.get(ped).contains(id)){
                logger.info("Skip command, because agent with id=" + ped.getId() + " has already received commandId " + id);
            }

            return processedAgents.get(ped).contains(id);
        }
        return false;
    }


    public abstract void getControlAction(Pedestrian ped, JSONObject command);

    public void update(Topography topo, Double time, String commandStr, Integer pedId)  {
        topography = topo;
        simTime = time;
        command = new CtlCommand(commandStr);

        for (int i : get_pedIds(pedId)) {
            Pedestrian ped = topography.getPedestrianDynamicElements().getElement(i);
            if (this.isInformationProcessed(ped, getCommandId())){
                if (isInfoInTime() && isPedInDefinedArea(ped)) {
                    this.getControlAction(ped, command.getPedCommand());
                    this.setAction(ped);
                    this.setProcessedAgents(ped,getCommandId());
                }
            }
        }
    }

    public void update(Topography topography, Double time, String command) {
        update(topography,time,command,-1);

    }

    private LinkedList<Integer> get_pedIds(Integer pedId)
    {
        LinkedList<Integer> pedIds = new LinkedList<>();
        if (pedId == -1){
            pedIds.addAll(topography.getPedestrianDynamicElements().getElements().stream().map(Pedestrian::getId).collect(Collectors.toList()));
        }
        else{
            pedIds.add(pedId);
        }
        return  pedIds;
    }

    boolean isPedInDefinedArea(Pedestrian ped) {
        if (command.isSpaceBounded()) {
            return command.getSpace().contains(ped.getPosition());
        }
        else{
            return true;
        }
    }

    boolean isInfoInTime(){
        return command.getExecTime() >= simTime;
    }

    public int getCommandId(){
        return command.getCommandId();
    }


    public void setAction(Pedestrian ped){
        if (isPedReact()){
            triggerRedRaction(ped);
        }
    }

    public void setReactionModel(ReactionModel reactionModel){
        this.reactionModel = reactionModel;
    }

    public boolean isInformationProcessed(Pedestrian ped, int commandId){

        // 1. handle conflicting instructions over time
        if (reactionModel.isReactingToFirstInformationOnly()){
            return isFirstInformation(ped);
        }

        // 2. handle recurring information that is received multiple times.
        if (isIdInList(ped, commandId)){
            return reactionModel.isReactingToRecurringInformation();
        }
        return true;
    }

    public boolean isFirstInformation(Pedestrian ped){

        if (processedAgents.containsKey(ped)) {
            return processedAgents.get(ped).isEmpty();
        }
        return false;
    }



}