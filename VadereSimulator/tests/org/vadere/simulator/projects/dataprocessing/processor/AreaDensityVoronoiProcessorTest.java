package org.vadere.simulator.projects.dataprocessing.processor;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class AreaDensityVoronoiProcessorTest extends ProcessorTest {

	@Before
	public void setup(){
		processorTestEnv = new AreaDensityVoronoiProcessorTestEnv();
		super.setup();
	}

	@Test
	public void doUpdate() throws Exception {
		super.doUpdate();
	}

	@Test
	public void init() throws Exception {
		super.init();
	}

}