package org.vadere.simulator.control.scenarioelements;

import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.distribution.RealDistribution;
import org.vadere.simulator.control.scenarioelements.listener.ControllerEventListener;
import org.vadere.simulator.control.scenarioelements.listener.ControllerEventProvider;
import org.vadere.simulator.models.DynamicElementFactory;
import org.vadere.state.attributes.scenario.AttributesAgent;
import org.vadere.state.attributes.scenario.AttributesDynamicElement;
import org.vadere.state.attributes.scenario.AttributesSource;
import org.vadere.state.scenario.*;
import org.vadere.state.scenario.distribution.DistributionFactory;
import org.vadere.state.scenario.distribution.VadereDistribution;
import org.vadere.state.scenario.distribution.impl.MixedDistribution;
import org.vadere.util.geometry.LinkedCellsGrid;
import org.vadere.util.geometry.shapes.VCircle;
import org.vadere.util.geometry.shapes.VPoint;
import org.vadere.util.geometry.shapes.VShape;
import org.vadere.util.logging.Logger;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public abstract class SourceController extends ScenarioElementController implements ControllerEventProvider<Agent, SourceController> {

    protected final double BUFFER_CA = 0.01; // amount of overlap between spawned agents that is allowed in order to allow touching agents in CA
    protected final double NO_EVENT = Double.MAX_VALUE;
	protected static Logger logger = Logger.getLogger(SourceController.class);

    protected final Source source;
    protected final DynamicElementFactory dynamicElementFactory;
    protected final Random random;
    protected final AttributesSource sourceAttributes;
    protected final AttributesDynamicElement attributesDynamicElement;
    private final Topography topography;

	protected ArrayList<ControllerEventListener<Agent, SourceController>> eventListener;

	/**
	 * <code>null</code>, if there is no next event.
	 */
	protected Double timeOfNextEvent;
	protected VadereDistribution distribution;
	protected int dynamicElementsCreatedTotal;

    public SourceController(Topography scenario, Source source,
                            DynamicElementFactory dynamicElementFactory,
                            AttributesDynamicElement attributesDynamicElement,
                            Random random) {
        this.source = source;
        this.sourceAttributes = source.getAttributes();
        this.attributesDynamicElement = attributesDynamicElement;
        this.dynamicElementFactory = dynamicElementFactory;
        this.topography = scenario;
        this.random = random;
        this.eventListener = new ArrayList<>();

        timeOfNextEvent = sourceAttributes.getStartTime();
        try {
            distribution = DistributionFactory.create(
                    sourceAttributes.getInterSpawnTimeDistribution(),
                    sourceAttributes.getDistributionParameters(),
                    sourceAttributes.getSpawnNumber(),
                    new JDKRandomGenerator(random.nextInt())
            );

        } catch (Exception e) {
            throw new IllegalArgumentException("Problem with scenario parameters for source: "
                    + "interSpawnTimeDistribution and/or distributionParameters. See causing Excepion herefafter.", e);
        }
    }


    /**
     * @return List of DynamicElements within a circle, which surrounds the source shape completely
     */
    protected List<DynamicElement> getDynElementsAtSource() {
        return getDynElementsAtPosition(source.getShape().getCircumCircle());
    }

    protected List<DynamicElement> getDynElementsAtPosition(VCircle circumCircle) {
        LinkedCellsGrid<DynamicElement> dynElements = topography.getSpatialMap(DynamicElement.class);
        return dynElements.getObjects(circumCircle.getCenter(), circumCircle.getRadius());
    }

    abstract public void update(double simTimeInSec);

    public boolean isSourceFinished(double simTimeInSec) {
        if (isMaximumNumberOfSpawnedElementsReached()) {
            return true;
        }
        if (isSourceWithOneSingleSpawnEvent()) {
            return dynamicElementsCreatedTotal == sourceAttributes.getSpawnNumber();
        }
        return isAfterSourceEndTime(simTimeInSec) && isQueueEmpty();
    }

    protected boolean isSourceWithOneSingleSpawnEvent() {
        return sourceAttributes.getStartTime() == sourceAttributes.getEndTime();
    }

    protected boolean isAfterSourceEndTime(double simTimeInSec) {
        return simTimeInSec > sourceAttributes.getEndTime();
    }

    protected boolean isMaximumNumberOfSpawnedElementsReached() {
        final int maxNumber = sourceAttributes.getMaxSpawnNumberTotal();
        return maxNumber != AttributesSource.NO_MAX_SPAWN_NUMBER_TOTAL
                && dynamicElementsCreatedTotal >= maxNumber;
    }

    protected boolean testFreeSpace(final VShape freeSpace, final List<VShape> blockPedestrianShapes) {
        VShape freeSpaceCA = new VCircle(((VCircle) freeSpace).getCenter(), ((VCircle) freeSpace).getRadius() - BUFFER_CA);
        final VShape freeSpaceModelSpecific = sourceAttributes.isSpawnAtGridPositionsCA() ? freeSpaceCA : freeSpace;

        boolean pedOverlap = blockPedestrianShapes.stream().noneMatch(shape -> shape.intersects(freeSpaceModelSpecific));
        boolean obstOverlap = this.getTopography().getObstacles().stream()
                .map(Obstacle::getShape).noneMatch(shape -> shape.intersects(freeSpaceModelSpecific));

        return pedOverlap && obstOverlap;
    }

    abstract protected boolean isQueueEmpty();

    abstract protected void determineNumberOfSpawnsAndNextEvent(double simTimeInSec);

    protected Topography getTopography() {
        return topography;
    }

    protected void createNextEvent() {
        if (isSourceWithOneSingleSpawnEvent()) {
            timeOfNextEvent = NO_EVENT;
            return;
        }

        // Read: timeOfNextEvent == timeOfCurrentEvent for this call
        double newTimeOfNextEvent = distribution.getNextSpawnTime(timeOfNextEvent);

        if (newTimeOfNextEvent < timeOfNextEvent) { //TODO mit Herr Lehmberg reden wg. < vs <=
            String distributionName;
            if (distribution instanceof MixedDistribution) {
                distributionName = ((MixedDistribution) distribution).
                        getCurrentDistribution().getClass().getSimpleName();
            } else {
                distributionName = distribution.getClass().getSimpleName();
            }
            throw new RuntimeException("Bug: newTimeOfNextEvent is smaller or equal to timeOfNextEvent. The selected " +
                    "distribution " + distributionName + " has a bug. " +
                    "Please report error (with message) at gitlab issue.");
        } else {
            timeOfNextEvent = newTimeOfNextEvent;
        }

        if (isAfterSourceEndTime(timeOfNextEvent)) {
            timeOfNextEvent = NO_EVENT;
        }

    }

    /**
     * note that most models create their own pedestrians and ignore the attributes given here. the
     * source is mostly used to set the position and target ids, not the attributes.
     */

	protected void addNewAgentToScenario(final List<VPoint> position, double simTimeInSec) {
		position.forEach(p -> addNewAgentToScenario(p, simTimeInSec));
	}

	protected void addNewAgentToScenario(final VPoint position, double simTimeInSec) {
		Agent newElement = (Agent) createDynamicElement(position);

        // TODO [priority=high] [task=refactoring] this is bad programming. Why is the target list added later?
        // What if Pedestrian does something with the target list before it is stored?

        // if the pedestrian itself has no targets, add the targets from the source
        // TODO [priority=high] [task=refactoring] why only if he has no targets? because the createElement method
        // might add some.
        if (newElement.getTargets().isEmpty()) {
            newElement.setTargets(new LinkedList<>(sourceAttributes.getTargetIds()));
        }

        topography.addElement(newElement);
		notifyListeners(simTimeInSec, newElement);
	}


    private DynamicElement createDynamicElement(final VPoint position) {
        Agent result;
        switch (sourceAttributes.getDynamicElementType()) {
            case PEDESTRIAN:
                if (source.getAttributes().getAttributesPedestrian() == null) {
                    result = (Agent) dynamicElementFactory.createElement(position, AttributesAgent.ID_NOT_SET, Pedestrian.class);
                } else {
                    result = (Agent) dynamicElementFactory.createElement(position, AttributesAgent.ID_NOT_SET, source.getAttributes().getAttributesPedestrian(), Pedestrian.class);
                }
                break;
            case CAR:
                result = (Agent) dynamicElementFactory.createElement(position, AttributesAgent.ID_NOT_SET, Car.class);
                break;
            default:
                throw new IllegalArgumentException("The controller's source has an unsupported element type: "
                        + sourceAttributes.getDynamicElementType());
        }
        result.setSource(source);
        return result;
    }




	@Override
	public void register(ControllerEventListener<Agent, SourceController> listener) {
		if (! eventListener.contains(listener)){
			eventListener.add(listener);
		}
	}

	@Override
	public void unregister(ControllerEventListener<Agent, SourceController> listener) {
		eventListener.remove(listener);
	}

	protected void notifyListeners(double simTimeInSec, Agent pedestrian){
		for (var listener: eventListener){
			listener.notify(this, simTimeInSec, pedestrian);
		}
	}

	public int getSourceId() { return source.getId(); }
}
