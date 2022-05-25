package org.vadere.state.attributes.models;

import org.vadere.annotation.factories.attributes.ModelAttributeClass;
import org.vadere.state.attributes.Attributes;

@ModelAttributeClass
public class AttributesSIRG extends Attributes {

	private int infectionsAtStart = 0;
	private double infectionRate = 0.01;
	private double infectionMaxDistance = 1;

	// Controls the number of recovered pedestrians in the beginning of the simulation.This value can also we controlled using the GUI
	private int recoveredAtStart = 0;

	// Controls the Recovery rate of the pedestrians. This value can also we controlled using the GUI
	private double recoveryRate = 0.01;

	public int getInfectionsAtStart() { return infectionsAtStart; }

	public double getInfectionRate() {
		return infectionRate;
	}

	public double getInfectionMaxDistance() {
		return infectionMaxDistance;
	}

	public int getRecoveredAtStart() { return recoveredAtStart; }

	public double getRecoveryRate() {
		return recoveryRate;
	}

}
