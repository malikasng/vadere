package org.vadere.simulator.projects.dataprocessing.processor;

import org.mockito.Mockito;
import org.vadere.simulator.projects.dataprocessing.datakey.TimestepKey;
import org.vadere.simulator.projects.dataprocessing.writer.VadereWriterFactory;
import org.vadere.state.attributes.processor.AttributesAreaSpeedProcessor;
import org.vadere.state.attributes.scenario.AttributesMeasurementArea;
import org.vadere.state.scenario.MeasurementArea;
import org.vadere.state.scenario.Pedestrian;
import org.vadere.util.geometry.shapes.VPoint;
import org.vadere.util.geometry.shapes.VRectangle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static org.mockito.Mockito.when;

public class AreaSpeedProcessorTestEnv extends ProcessorTestEnv<TimestepKey, Double> {

	private PedestrianListBuilder b = new PedestrianListBuilder();

	@SuppressWarnings("unchecked")
	AreaSpeedProcessorTestEnv() {
		try {
			testedProcessor = processorFactory.createDataProcessor(AreaSpeedProcessor.class);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		testedProcessor.setId(nextProcessorId());

		int pedPosProcId = nextProcessorId();
		int pedVelProcId = nextProcessorId();
		AttributesAreaSpeedProcessor attr =
				(AttributesAreaSpeedProcessor) testedProcessor.getAttributes();
		attr.setPedestrianPositionProcessorId(pedPosProcId);
		attr.setPedestrianVelocityProcessorId(pedVelProcId);

		PedestrianVelocityProcessorTestEnv pedVelProcEnv = new PedestrianVelocityProcessorTestEnv(pedVelProcId);
		DataProcessor pedVelProc = pedVelProcEnv.getTestedProcessor();
		addRequiredProcessors(pedVelProcEnv);
		Mockito.when(manager.getProcessor(pedVelProcId)).thenReturn(pedVelProc);

		PedestrianPositionProcessorTestEnv pedPosProcEnv = new PedestrianPositionProcessorTestEnv(pedPosProcId);
		DataProcessor pedPosProc = pedPosProcEnv.getTestedProcessor();
		addRequiredProcessors(pedPosProcEnv);
		Mockito.when(manager.getProcessor(pedPosProcId)).thenReturn(pedPosProc);

		attr.setMeasurementAreaId(99);
		MeasurementArea measurementArea = new MeasurementArea(
				new AttributesMeasurementArea(99, new VRectangle(0, 0, 4, 5)));
		Mockito.when(manager.getMeasurementArea(99)).thenReturn(measurementArea);

		try {
			outputFile = outputFileFactory.createDefaultOutputfileByDataKey(
					TimestepKey.class,
					testedProcessor.getId()
			);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		outputFile.setVadereWriterFactory(VadereWriterFactory.getStringWriterFactory());
	}

	@Override
	public void loadDefaultSimulationStateMocks() {

		addSimState(new SimulationStateMock(1) {
			@Override
			public void mockIt() {
				b.clear().add(1, new VPoint(1.0, 0.0))
						.add(2, new VPoint(0.0, 0.0))
						.add(3, new VPoint(7.0, 4.0));    //not in area

				when(state.getTopography().getElements(Pedestrian.class)).thenReturn(b.getList());
				when(state.getSimTimeInSec()).thenReturn(0.0);

				int step = state.getStep();
				addToExpectedOutput(new TimestepKey(step), 0.0);
			}
		});

		addSimState(new SimulationStateMock(2) {
			@Override
			public void mockIt() {
				b.clear().add(1, new VPoint(1.0, 1.0)) //dist = 1.0
						.add(2, new VPoint(3.0, 4.0))    //dist = 5.0
						.add(3, new VPoint(8.0, 4.0));    //not in area

				when(state.getTopography().getElements(Pedestrian.class)).thenReturn(b.getList());
				double simTime = 1.0;
				when(state.getSimTimeInSec()).thenReturn(simTime);

				int step = state.getStep();
				double time = simTime - 0.0;
				double areaSpeed = (1.0 / time + 5.0 / time) / 2; // 2 = noOfPeds
				addToExpectedOutput(new TimestepKey(step), areaSpeed);
			}
		});

		addSimState(new SimulationStateMock(3) {
			@Override
			public void mockIt() {
				b.clear().add(1, new VPoint(3.0, 1.0)) //dist = 2.0
						.add(2, new VPoint(5.0, 4.0))    //not in area
						.add(3, new VPoint(8.0, 8.0));    //not in area

				when(state.getTopography().getElements(Pedestrian.class)).thenReturn(b.getList());
				double simTime = 2.0;
				when(state.getSimTimeInSec()).thenReturn(simTime);

				int step = state.getStep();
				double time = simTime - 1.0; // time in this step
				double areaSpeed = (2.0 / time) / 1; // 1 = noOfPeds
				addToExpectedOutput(new TimestepKey(step), areaSpeed);
			}
		});

		addSimState(new SimulationStateMock(4) {
			@Override
			public void mockIt() {
				b.clear().add(1, new VPoint(6.0, 1.0)) // not in area
						.add(2, new VPoint(7.0, 4.0))    // not in area
						.add(3, new VPoint(9.0, 8.0));    // not in area

				when(state.getTopography().getElements(Pedestrian.class)).thenReturn(b.getList());
				double simTime = 3.0;
				when(state.getSimTimeInSec()).thenReturn(simTime);

				int step = state.getStep();
				addToExpectedOutput(new TimestepKey(step), 0.0);
			}
		});
	}

	@Override
	List<String> getExpectedOutputAsList() {
		List<String> outputList = new ArrayList<>();
		expectedOutput.entrySet()
				.stream()
				.sorted(Comparator.comparing(Map.Entry::getKey))
				.forEach(e -> {
					StringJoiner sj = new StringJoiner(getDelimiter());
					sj.add(Integer.toString(e.getKey().getTimestep()))
							.add(Double.toString(e.getValue()));
					outputList.add(sj.toString());
				});
		return outputList;
	}
}
