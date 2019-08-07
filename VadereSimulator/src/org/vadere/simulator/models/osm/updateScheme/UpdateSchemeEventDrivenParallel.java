package org.vadere.simulator.models.osm.updateScheme;

import org.jetbrains.annotations.NotNull;
import org.vadere.meshing.mesh.gen.IncrementalTriangulation;
import org.vadere.meshing.mesh.gen.MeshPanel;
import org.vadere.meshing.mesh.gen.PFace;
import org.vadere.meshing.mesh.gen.PHalfEdge;
import org.vadere.meshing.mesh.gen.PMesh;
import org.vadere.meshing.mesh.gen.PVertex;
import org.vadere.meshing.mesh.inter.IIncrementalTriangulation;
import org.vadere.simulator.models.osm.PedestrianOSM;
import org.vadere.state.scenario.Topography;
import org.vadere.util.geometry.LinkedCellsGrid;
import org.vadere.util.geometry.shapes.IPoint;
import org.vadere.util.geometry.shapes.VPoint;
import org.vadere.util.geometry.shapes.VRectangle;
import org.vadere.util.logging.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UpdateSchemeEventDrivenParallel extends UpdateSchemeEventDriven {

	private final static Logger logger = Logger.getLogger(UpdateSchemeEventDrivenParallel.class);

	static {
		logger.setDebug();
	}

	private final Topography topography;
	private LinkedCellsGrid<PedestrianOSM> linkedCellsGrid;
	private boolean[][] locked;
	private double pedestrianPotentialWidth;
	//private PMesh mesh;
	//private MeshPanel<PVertex, PHalfEdge, PFace> panel;
	private Map<PedestrianOSM, PVertex> map;
	//private IIncrementalTriangulation<PVertex, PHalfEdge, PFace> triangulation;


	public UpdateSchemeEventDrivenParallel(@NotNull final Topography topography, @NotNull final double pedestrianPotentialWidth) {
		super(topography);
		this.topography = topography;
		this.pedestrianPotentialWidth = pedestrianPotentialWidth;
		this.map = new HashMap<>();
	}

	@Override
	public void update(final double timeStepInSec, final double currentTimeInSec) {
		int count = 0;
		// construct delaunay triangulation




		/*for(PedestrianOSM pedestrianOSM : topography.getElements(PedestrianOSM.class)) {
			pedestrianOSM.clearStrides();
		}

		if(!pedestrianEventsQueue.isEmpty()) {
			// event driven update ignores time credits!
			while (pedestrianEventsQueue.peek().getTimeOfNextStep() < currentTimeInSec) {
				PedestrianOSM ped = pedestrianEventsQueue.poll();
				update(ped, currentTimeInSec);
				pedestrianEventsQueue.add(ped);
			}
		}*/

		double maxStepSize = 0;
		double maxDesiredSpeed = 0;
		for(PedestrianOSM pedestrianOSM : topography.getElements(PedestrianOSM.class)) {
			pedestrianOSM.clearStrides();
			maxStepSize = Math.max(pedestrianOSM.getDesiredStepSize(), maxStepSize);
			maxDesiredSpeed = Math.max(pedestrianOSM.getDesiredSpeed(), maxDesiredSpeed);
		}


		int counter = 1;
		// event driven update ignores time credits
		do {
			double stepSize = Math.max(maxStepSize, maxDesiredSpeed * timeStepInSec);
			linkedCellsGrid = new LinkedCellsGrid<>(new VRectangle(topography.getBounds()), (pedestrianPotentialWidth));
			locked = new boolean[linkedCellsGrid.getGridWidth()][linkedCellsGrid.getGridHeight()];

			List<PedestrianOSM> parallelUpdatablePeds = new ArrayList<>();
			List<PedestrianOSM> unUpdatablePedsd = new ArrayList<>();

			while (!pedestrianEventsQueue.isEmpty() && pedestrianEventsQueue.peek().getTimeOfNextStep() < currentTimeInSec) {
				PedestrianOSM ped = pedestrianEventsQueue.poll();
				int[] gridPos = linkedCellsGrid.gridPos(ped.getPosition());

				//boolean requiresUpdate = requireUpdate(ped);
				boolean requiresUpdate = true;

				if(!locked[gridPos[0]][gridPos[1]] && requiresUpdate) {
					parallelUpdatablePeds.add(ped);
				}
				else if(requiresUpdate) {
					unUpdatablePedsd.add(ped);
				}

				if(requiresUpdate) {
					for(int y = -1; y <= 1; y++) {
						for(int x = -1; x <= 1; x++) {
							int col = Math.min(locked.length-1, Math.max(0, gridPos[0]+x));
							int row = Math.min(locked[0].length-1, Math.max(0, gridPos[1]+y));
							locked[col][row] = true;
						}
					}
				}

				if(!requiresUpdate) {
					double stepDuration = ped.getDurationNextStep();
					ped.setTimeOfNextStep(ped.getTimeOfNextStep() + stepDuration);
					pedestrianEventsQueue.add(ped);
					count++;
				}
			}
			logger.debug("update " + parallelUpdatablePeds.size() + " in parallel in round " + counter + ".");
			parallelUpdatablePeds.stream().forEach(ped -> {
				//logger.info(ped.getTimeOfNextStep());
				//System.out.println(ped.getId());
				update(ped, timeStepInSec, currentTimeInSec);
			});


			pedestrianEventsQueue.addAll(unUpdatablePedsd);
			pedestrianEventsQueue.addAll(parallelUpdatablePeds);
			counter++;
		} while (!pedestrianEventsQueue.isEmpty() && pedestrianEventsQueue.peek().getTimeOfNextStep() < currentTimeInSec);
		logger.info("avoided updates: " + count);
	}

	/*private boolean requireUpdate(@NotNull final PedestrianOSM pedestrianOSM) {

		PVertex vertex = map.get(pedestrianOSM);
		if(hasChanged(pedestrianOSM)) {
			return true;
		}

		for(PVertex v : mesh.getAdjacentVertexIt(vertex)) {
			PedestrianOSM ped = triangulation.getMesh().getData(v, "pedestrian", PedestrianOSM.class).get();
			if(hasChanged(ped)) {
				return true;
			}
		}
		return false;
	}*/

	private boolean hasChanged(@NotNull final PedestrianOSM pedestrianOSM) {
		return pedestrianOSM.getLastPosition().equals(pedestrianOSM.getPosition());
	}
}
