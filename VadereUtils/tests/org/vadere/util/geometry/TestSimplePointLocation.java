package org.vadere.util.geometry;

import org.junit.Before;
import org.junit.Test;
import org.vadere.util.geometry.mesh.impl.PFace;
import org.vadere.util.geometry.mesh.impl.PHalfEdge;
import org.vadere.util.geometry.mesh.impl.PMesh;
import org.vadere.util.geometry.mesh.inter.IMesh;
import org.vadere.util.triangulation.PointLocation;
import org.vadere.util.geometry.shapes.VPoint;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * Created by bzoennchen on 15.11.16.
 */
public class TestSimplePointLocation {

	private static PFace face1;
	private static PFace face2;
	private static double EPSILON = 1.0e-10;
	private IMesh<VPoint, PHalfEdge<VPoint>, PFace<VPoint>> mesh;

	@Before
	public void setUp() throws Exception {
		mesh = new PMesh<>((x, y) -> new VPoint(x, y));
		face1 = mesh.createFace();
		face2 = mesh.createFace();
		PHalfEdge halfEdge1 = mesh.createEdge(mesh.createVertex(0,0), face1);
		PHalfEdge halfEdge2 = mesh.createEdge(mesh.createVertex(3,0), face1);
		PHalfEdge halfEdge3 = mesh.createEdge(mesh.createVertex(1.5,3.0), face1);

		PHalfEdge halfEdge4 = mesh.createEdge(mesh.createVertex(3.0,0), face2);
		mesh.setTwin(halfEdge4, halfEdge3);
		PHalfEdge halfEdge5 = mesh.createEdge(mesh.createVertex(4.5,3.0), face2);
		PHalfEdge halfEdge6 = mesh.createEdge(mesh.createVertex(1.5,3.0), face2);

		mesh.setNext(halfEdge4, halfEdge5);
		mesh.setNext(halfEdge5, halfEdge6);
		mesh.setNext(halfEdge6, halfEdge4);

		mesh.setEdge(face2, halfEdge4);

		mesh.setNext(halfEdge1, halfEdge2);
		mesh.setNext(halfEdge2, halfEdge3);
		mesh.setNext(halfEdge3, halfEdge1);

		mesh.setEdge(face1, halfEdge1);
	}

	@Test
	public void testFaceIterator() {
		PointLocation<VPoint> pointLocation = new PointLocation<>(Arrays.asList(face1, face2), mesh);

		assertEquals(face1, pointLocation.getFace(new VPoint(0,0)).get());

		assertEquals(face1, pointLocation.getFace(new VPoint(1.4,1.5)).get());

		assertEquals(face1, pointLocation.getFace(new VPoint(1.4,1.5)).get());

		assertEquals(Optional.empty(), pointLocation.getFace(new VPoint(1.4,3.5)));

		assertEquals(Optional.empty(), pointLocation.getFace(new VPoint(-1.5,1.4)));

		assertEquals(face2, pointLocation.getFace(new VPoint(3.5,1.4)).get());

		assertEquals(Optional.empty(), pointLocation.getFace(new VPoint(3.5,0.2)));

		assertEquals(face2, pointLocation.getFace(new VPoint(3.0,1.5)).get());

		// edges
		assertEquals(face2, pointLocation.getFace(new VPoint(3.0, EPSILON)).get());
		assertEquals(face1, pointLocation.getFace(new VPoint(1.5,3.0 - EPSILON)).get());
		assertEquals(Optional.empty(), pointLocation.getFace(new VPoint(1.5 - EPSILON,3.0 + EPSILON)));
	}
}