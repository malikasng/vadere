package org.vadere.simulator.projects.dataprocessing.processor;

import org.vadere.annotation.factories.dataprocessors.DataProcessorClass;
import org.vadere.simulator.control.simulation.SimulationState;
import org.vadere.simulator.projects.dataprocessing.ProcessorManager;
import org.vadere.simulator.projects.dataprocessing.datakey.TimestepIdDataKey;
import org.vadere.state.attributes.processor.AttributesAerosolCloudShapeProcessor;
import org.vadere.state.scenario.AerosolCloud;

import java.util.Collection;

/**
 * <p>This processor writes out the shape and the pathogen load of all {@link AerosolCloud}s. Each row corresponds to
 * one aerosol cloud at a given simulation time. The shape of an aerosol cloud is described by the vertices
 * ([x, y]-coordinates) and the area.
 * </p>
 *
 * <p>To reduce the size of the output file, one can reduce the sample frequency, i.e. the processor writes out only
 * every {@param sampleEveryNthSimStep}.
 * </p>
 *
 * @author Simon Rahn
 */

@DataProcessorClass()
public class AerosolCloudShapeProcessor extends DataProcessor<TimestepIdDataKey, String> {

    private int sampleEveryNthSimStep;

    public AerosolCloudShapeProcessor() {
        super("pathogenLoad", "radius", "centerX", "centerY");
        setAttributes(new AttributesAerosolCloudShapeProcessor());
    }

    @Override
    protected void doUpdate(final SimulationState state) {

        int timeStep = state.getStep();
        if (timeStep % sampleEveryNthSimStep == 0) {
            Collection<AerosolCloud> clouds = state.getTopography().getAerosolClouds();
            for (AerosolCloud cloud : clouds) {
                putValue(new TimestepIdDataKey(timeStep, cloud.getId()), dataToString(cloud));
            }
        }
    }

    @Override
    public void init(ProcessorManager manager) {
        super.init(manager);
        AttributesAerosolCloudShapeProcessor attr = (AttributesAerosolCloudShapeProcessor) this.getAttributes();
        sampleEveryNthSimStep = attr.getSampleEveryNthSimStep();
    }

    private String dataToString(AerosolCloud aerosolCloud) {
        String dataAsString = String.format("%f %f %f %f",
                aerosolCloud.getCurrentPathogenLoad(),
                aerosolCloud.getRadius(),
                aerosolCloud.getCenter().x,
                aerosolCloud.getCenter().y
        );

        return dataAsString;
    }
}