package org.vadere.simulator.models.osm.optimization;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.vadere.simulator.models.osm.PedestrianOSM;
import org.vadere.state.attributes.models.AttributesOSM;
import org.vadere.state.scenario.Pedestrian;
import org.vadere.state.types.MovementType;
import org.vadere.util.geometry.GeometryUtils;
import org.vadere.util.geometry.Vector2D;
import org.vadere.util.geometry.shapes.ICircleSector;
import org.vadere.util.geometry.shapes.VCircle;
import org.vadere.util.geometry.shapes.VCircleSector;
import org.vadere.util.geometry.shapes.VPoint;
import org.vadere.util.math.pso.PSO;

import java.awt.Shape;
import java.util.*;
import java.util.List;

/**
 * @author Benedikt Zoennchen
 */
public class ParticleSwarmOptimizer implements StepCircleOptimizer {

	private final double movementThreshold;
	private final Random random;

	public ParticleSwarmOptimizer(final double movementThreshold, @NotNull final Random random) {
		this.movementThreshold = movementThreshold;
		this.random = random;
	}

	@Override
	public VPoint getNextPosition(@NotNull final PedestrianOSM pedestrian, @NotNull final Shape reachableArea) {
		assert reachableArea instanceof VCircle;
		VCircle circle = ((VCircle) reachableArea);
		double stepSize = circle.getRadius();

		final PotentialEvaluationFunction potentialEvaluationFunction = new PotentialEvaluationFunction(pedestrian);
		potentialEvaluationFunction.setStepSize(stepSize);


		List<VPoint> positions = StepCircleOptimizerDiscrete.getReachablePositions(pedestrian, random);
		// maximum possible angle of movement relative to ankerAngle
		double angle;

		// smallest possible angle of movement
		double anchorAngle;

		ICircleSector circleSector;
		// TODO: dupclated code see StepCircleOptimizerDiscrete!
		if (pedestrian.getAttributesOSM().getMovementType() == MovementType.DIRECTIONAL) {
			angle = StepCircleOptimizerDiscrete.getMovementAngle(pedestrian);
			Vector2D velocity = pedestrian.getVelocity();
			anchorAngle = velocity.angleToZero() - angle;
			angle = 2 * angle;
			circleSector = new VCircleSector(circle.getCenter(), circle.getRadius(), anchorAngle, anchorAngle + 2 * angle);
		} else {
			angle = 2 * Math.PI;
			anchorAngle = 0;
			circleSector = circle;
		}

		PSO pso = new PSO(p -> getValue(p, pedestrian, stepSize), circleSector, anchorAngle, anchorAngle + 2 * angle, random, stepSize / 5.0, positions);

		VPoint nextPos = pso.getOptimumArg();
		VPoint curPos = pedestrian.getPosition();
		double curPosPotential = pedestrian.getPotential(curPos);
		double potential = pso.getOptimum();

		if (curPosPotential - potential < movementThreshold) {
			nextPos = curPos;
		}

		return nextPos;
	}

	private double getValue(@NotNull final VPoint newPos, @NotNull final PedestrianOSM ped, final double stepSize) {
		VPoint pedPos = ped.getPosition();
		double result = 100000;

		// step is not too small nor too large?
		if (Math.pow(newPos.x - pedPos.x, 2) + Math.pow(newPos.y - pedPos.y, 2) <= Math.pow(stepSize, 2) + 0.00001
				&& Math.pow(newPos.x - pedPos.x, 2) + Math.pow(newPos.y - pedPos.y, 2) >= Math.pow(ped.getMinStepLength(), 2)
				- 0.00001) {
			result = ped.getPotential(newPos);
		}

		return result;
	}

	@Override
	public StepCircleOptimizer clone() {
		return new ParticleSwarmOptimizer(movementThreshold, random);
	}
}