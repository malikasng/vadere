package org.vadere.simulator.control;

import org.vadere.simulator.context.VadereContext;
import org.vadere.simulator.models.potential.fields.PotentialFieldDistancesBruteForce;
import org.vadere.simulator.utils.cache.ScenarioCache;
import org.vadere.state.attributes.models.AttributesFloorField;
import org.vadere.state.scenario.Car;
import org.vadere.state.scenario.Obstacle;
import org.vadere.state.scenario.Pedestrian;
import org.vadere.state.scenario.Topography;
import org.vadere.util.data.cellgrid.CellGridReachablePointProvider;
import org.vadere.util.geometry.shapes.VRectangle;

import java.util.Random;
import java.util.stream.Collectors;

public class OfflineTopographyController {

	private final Topography topography;
	protected final Random random;

	public OfflineTopographyController(final Topography topography, final Random random) {
		this.topography = topography;
		this.random = random;
	}

	protected void update(double simTimeInSec) {
		recomputeCells();
	}

	public Topography getTopography() {
		return topography;
	}

	// add bounding box
	protected void prepareTopography() {
		// add boundaries
		if (this.topography.isBounded() && !this.topography.hasBoundary()) {
			for(Obstacle obstacle : Topography.createObstacleBoundary(topography)) {
				this.topography.addBoundary(obstacle);
			}
		}

		// add distance function
		ScenarioCache cache = (ScenarioCache) VadereContext.get(topography).getOrDefault("cache", ScenarioCache.empty());
		PotentialFieldDistancesBruteForce distanceField = new PotentialFieldDistancesBruteForce(
				topography.getObstacles().stream().map(obs -> obs.getShape()).collect(Collectors.toList()),
				new VRectangle(topography.getBounds()),
				new AttributesFloorField(), cache);
		this.topography.setObstacleDistanceFunction(distanceField);

		this.topography.setReachablePointProvider(CellGridReachablePointProvider.createUniform(
				distanceField.getCellGrid(), random));
	}

	/**
	 * Recomputes the {@link org.vadere.util.geometry.LinkedCellsGrid} for fast access to pedestrian
	 * and car
	 * neighbors.
	 */
	protected void recomputeCells() {
		this.topography.getSpatialMap(Pedestrian.class).clear();
		for (Pedestrian pedestrian : this.topography.getElements(Pedestrian.class)) {
			this.topography.getSpatialMap(Pedestrian.class).addObject(pedestrian);
		}
		this.topography.getSpatialMap(Car.class).clear();
		for (Car car : this.topography.getElements(Car.class)) {
			this.topography.getSpatialMap(Car.class).addObject(car);
		}
		this.topography.setRecomputeCells(false);
	}
}
