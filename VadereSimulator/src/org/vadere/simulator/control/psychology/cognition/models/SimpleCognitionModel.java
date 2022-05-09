package org.vadere.simulator.control.psychology.cognition.models;

import org.vadere.state.attributes.Attributes;
import org.vadere.state.attributes.models.psychology.AttributesSimpleCognitionModel;
import org.vadere.state.psychology.cognition.SelfCategory;
import org.vadere.state.psychology.perception.types.*;
import org.vadere.state.scenario.Pedestrian;
import org.vadere.state.scenario.Topography;

import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * The {@link SimpleCognitionModel} just passes the perceived stimulus to the
 * behavioral/locomotion layer without further processing.
 */
public class SimpleCognitionModel implements ICognitionModel {

    private Topography topography;
    private AttributesSimpleCognitionModel attributes;

    @Override
    public void initialize(Topography topography, List<Attributes> attributes, Random random) {
        this.topography = topography;
        this.attributes = new AttributesSimpleCognitionModel();
    }

    @Override
    public void update(Collection<Pedestrian> pedestrians) {
        for (Pedestrian pedestrian : pedestrians) {

            Stimulus stimulus = pedestrian.getMostImportantStimulus();
            SelfCategory nextSelfCategory;

            if (stimulus instanceof ChangeTarget) {
                nextSelfCategory = SelfCategory.CHANGE_TARGET;
            } else if (stimulus instanceof Threat) {
                nextSelfCategory = SelfCategory.THREATENED;
            } else if (stimulus instanceof Wait || stimulus instanceof WaitInArea) {
                nextSelfCategory = SelfCategory.WAIT;
            } else if (stimulus instanceof ElapsedTime) {
                nextSelfCategory = SelfCategory.TARGET_ORIENTED;
            } else {
                throw new IllegalArgumentException(String.format("Stimulus \"%s\" not supported by \"%s\"",
                        stimulus.getClass().getSimpleName(),
                        this.getClass().getSimpleName()));
            }

            pedestrian.setSelfCategory(nextSelfCategory);
        }
    }

}
