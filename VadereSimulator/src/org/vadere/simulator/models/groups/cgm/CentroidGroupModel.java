package org.vadere.simulator.models.groups.cgm;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.vadere.annotation.factories.models.ModelClass;
import org.vadere.simulator.models.Model;
import org.vadere.simulator.models.groups.*;
import org.vadere.simulator.models.potential.fields.IPotentialFieldTarget;
import org.vadere.state.attributes.Attributes;
import org.vadere.state.attributes.models.AttributesCGM;
import org.vadere.state.attributes.scenario.AttributesAgent;
import org.vadere.state.scenario.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *	Implementation of group behavior model described in
 *	'Pedestrian Group Behavior in a Cellular Automaton' (bib-key: seitz-2014)
 */
@ModelClass
public class CentroidGroupModel implements GroupModel<CentroidGroup> {

	private static Logger logger = LogManager.getLogger(CentroidGroupModel.class);

	private Random random;
	private Map<Integer, CentroidGroupFactory> groupFactories;		// for each source a separate group factory.
	private Map<ScenarioElement, CentroidGroup> pedestrianGroupMap;

	private Topography topography;
	private IPotentialFieldTarget potentialFieldTarget;
	private AttributesCGM attributesCGM;

	private AtomicInteger nextFreeGroupId;

	public CentroidGroupModel() {
		this.groupFactories = new HashMap<>();
		this.pedestrianGroupMap = new HashMap<>();
		this.nextFreeGroupId = new AtomicInteger(0);
	}

	@Override
	public void initialize(List<Attributes> attributesList, Topography topography,
						   AttributesAgent attributesPedestrian, Random random) {
		this.attributesCGM = Model.findAttributes(attributesList, AttributesCGM.class);
		this.topography = topography;
		this.random = random;

	}

	@Override
	public void setPotentialFieldTarget(IPotentialFieldTarget potentialFieldTarget) {
		this.potentialFieldTarget = potentialFieldTarget;
		// update all existing groups
        for(CentroidGroup group : pedestrianGroupMap.values()) {
            group.setPotentialFieldTarget(potentialFieldTarget);
        }
	}

	@Override
	public IPotentialFieldTarget getPotentialFieldTarget() {
		return potentialFieldTarget;
	}

	protected int getFreeGroupId() {
		return nextFreeGroupId.getAndIncrement();
	}

	@Override
	public GroupFactory getGroupFactory(final int sourceId) {

		CentroidGroupFactory result = groupFactories.get(sourceId);

		if (result == null) {
			throw new IllegalArgumentException("For SourceID: " + sourceId + " no GroupFactory exists. " +
					"Is this really a valid source?");
		}

		return result;
	}

	@Override
	public void initializeGroupFactory(int sourceId, List<Double> groupSizeDistribution) {
		GroupSizeDeterminator gsD = new GroupSizeDeterminatorRandom(groupSizeDistribution, random);
		CentroidGroupFactory result =
				new CentroidGroupFactory(this, gsD);
		groupFactories.put(sourceId, result);
	}

	@Override
	public CentroidGroup getGroup(final ScenarioElement pedestrian) {
        //logger.debug(String.format("Get Group for Pedestrian %s", ped));
        CentroidGroup group = pedestrianGroupMap.get(pedestrian);
        assert group != null: "No group found for pedestrian";
		return group;
	}

	@Override
	public void registerMember(final ScenarioElement ped, final CentroidGroup group) {
	    //logger.debug(String.format("Register Pedestrian %s, Group %s", ped, group));
		pedestrianGroupMap.put(ped, (CentroidGroup) group);
	}

	@Override
	public Map<ScenarioElement, CentroidGroup> getPedestrianGroupMap() {
		return pedestrianGroupMap;
	}

	@Override
	public CentroidGroup getNewGroup(final int size) {
		return getNewGroup(getFreeGroupId(), size);
	}

	private CentroidGroup getNewGroup(final int id, final int size) {
		return new CentroidGroup(id, size, this.potentialFieldTarget);
	}

	private void initializeGroupsOfInitialPedestrians(){
		// get all pedestrians already in topography
		DynamicElementContainer<Pedestrian> c = topography.getPedestrianDynamicElements();

		if (c.getElements().size() > 0) {
			Map<Integer, List<Pedestrian>> groups = new HashMap<>();

			// aggregate group data
			c.getElements().stream().forEach(p -> {
				for (Integer id : p.getGroupIds()) {
					List<Pedestrian> peds = groups.get(id);
					if (peds == null) {
						peds = new ArrayList<>();
						groups.put(id, peds);
					}
					// empty group id and size values, will be set later on
					p.setGroupIds(new LinkedList<>());
					p.setGroupSizes(new LinkedList<>());
					peds.add(p);
				}
			});


			// build groups depending on group ids and register pedestrian
			for (Integer id : groups.keySet()) {
				logger.debug("GroupId: " + id);
				List<Pedestrian> peds = groups.get(id);
				CentroidGroup group = getNewGroup(id, peds.size());
				peds.stream().forEach(p -> {
					// update group id / size info on ped
					p.getGroupIds().add(id);
					p.getGroupSizes().add(peds.size());
					group.addMember(p);
					registerMember(p, group);
				});
			}

			// set latest groupid to max id + 1
			Integer max = groups.keySet().stream().max(Integer::compareTo).get();
			nextFreeGroupId = new AtomicInteger(max+1);
		}
	}



	public AttributesCGM getAttributesCGM() {
		return attributesCGM;
	}



	/* DynamicElement Listeners */

	@Override
	public void elementAdded(Pedestrian pedestrian) {
		// call GroupFactory for selected Source
		getGroupFactory(pedestrian.getSource().getId()).elementAdded(pedestrian);
	}

	@Override
	public void elementRemoved(Pedestrian pedestrian) {
		Group group = pedestrianGroupMap.remove(pedestrian);
		if (group != null){
			group.removeMember(pedestrian);
		}
	}

	/* Model Interface */

	@Override
	public void preLoop(final double simTimeInSec) {
		initializeGroupsOfInitialPedestrians();
		topography.addElementAddedListener(Pedestrian.class, this);
		topography.addElementRemovedListener(Pedestrian.class, this);
	}

	@Override
	public void postLoop(final double simTimeInSec) {
	}

	@Override
	public void update(final double simTimeInSec) {
	}
}