package org.vadere.simulator.control.psychology.perception.models;

import org.vadere.state.psychology.information.InformationState;
import org.vadere.state.psychology.perception.types.ElapsedTime;
import org.vadere.state.psychology.perception.types.Stimulus;
import org.vadere.state.scenario.Pedestrian;

import java.util.Collection;

public abstract class PerceptionModel implements IPerceptionModel {


    void updateInformationStateSingleAgent(Pedestrian pedestrian) {
        // in deterministic models, all agents react to ..
        Stimulus mostImportantStimulus = pedestrian.getMostImportantStimulus();
        if ((pedestrian.getKnowledgeBase().getInformationState() == InformationState.NO_INFORMATION)
                && (!(mostImportantStimulus instanceof ElapsedTime)) ) {
            pedestrian.getKnowledgeBase().setInformationState(InformationState.INFORMATION_CONVINCING_RECEIVED);
        }
    }

    public void setInformationStateGroupMember(Collection<Pedestrian> pedestrians){
        // Assumption: decisions are met groupwise.
        // The decision is assigned to the the first group member in the update list
        // Group members get the label 'FOLLOW_INFORMED_GROUP_MEMBER'
        for (Pedestrian ped : pedestrians) {
            ped.getKnowledgeBase().setInformationState(InformationState.FOLLOW_INFORMED_GROUP_MEMBER);
        }
    }

    public void updateInformationState(Pedestrian pedestrian){
        // Assume that an agent is informed and reacts to information a..
        updateInformationStateSingleAgent(pedestrian);
        // handle group members separately
        setInformationStateGroupMember(pedestrian.getPedGroupMembers());
    }

}
