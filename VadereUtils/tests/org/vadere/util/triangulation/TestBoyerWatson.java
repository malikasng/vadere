package org.vadere.util.triangulation;

import org.junit.Before;
import org.junit.Test;
import org.vadere.util.geometry.mesh.impl.PFace;
import org.vadere.util.geometry.mesh.inter.IMesh;
import org.vadere.util.geometry.mesh.impl.PHalfEdge;
import org.vadere.util.geometry.mesh.impl.PMesh;
import org.vadere.util.geometry.mesh.triangulations.IncrementalTriangulation;
import org.vadere.util.geometry.shapes.VPoint;
import org.vadere.util.geometry.shapes.VTriangle;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestBoyerWatson {

	private IMesh<VPoint, PHalfEdge<VPoint>, PFace<VPoint>> mesh;

	@Before
	public void setUp() throws Exception {
		mesh = new PMesh<>((x, y) -> new VPoint(x, y));
	}

	@Test
	public void testFaceIterator() {
		VPoint p1 = mesh.createVertex(0, 0);
		VPoint p2 = mesh.createVertex(50, 0);
		VPoint p3 = mesh.createVertex(50, 50);
		VPoint p4 = mesh.createVertex(0, 50);

		VPoint p6 = mesh.createVertex(50, 50);
		VPoint p5 = mesh.createVertex(25, 25);

		Set<VPoint> points = new HashSet<>();
		points.add(p1);
		points.add(p2);
		points.add(p3);
		points.add(p4);
		points.add(p6);
		points.add(p5);

		IncrementalTriangulation<VPoint, PHalfEdge<VPoint>, PFace<VPoint>> delaunayTriangulation = new IncrementalTriangulation<>(mesh, points, (x, y) -> new VPoint(x, y));
		delaunayTriangulation.compute();
		//delaunayTriangulation.finalize();

		Set<VTriangle> triangulation = new HashSet<>(delaunayTriangulation.getTriangles());

		Set<VPoint> triangle1 = new HashSet<>();
		triangle1.add(p1);
		triangle1.add(p5);
		triangle1.add(p4);

		Set<VPoint> triangle2 = new HashSet<>();
		triangle2.add(p1);
		triangle2.add(p2);
		triangle2.add(p5);

		Set<VPoint> triangle3 = new HashSet<>();
		triangle3.add(p2);
		triangle3.add(p3);
		triangle3.add(p5);

		Set<VPoint> triangle4 = new HashSet<>();
		triangle4.add(p4);
		triangle4.add(p5);
		triangle4.add(p3);

		Set<Set<VPoint>> pointSets = triangulation.stream().map(t -> new HashSet<>(t.getPoints())).collect(Collectors.toSet());

		Set<Set<VPoint>> expextedPointSets = new HashSet<>();
		expextedPointSets.add(triangle1);
		expextedPointSets.add(triangle2);
		expextedPointSets.add(triangle3);
		expextedPointSets.add(triangle4);

		assertTrue(expextedPointSets.equals(pointSets));

		triangulation.forEach(System.out::println);
	}

	@Test
	public void testSplitTriangle() {

		VPoint p1 = mesh.createVertex(0, 0);
		VPoint p2 = mesh.createVertex(50, 0);
		VPoint p3 = mesh.createVertex(25, 25);
		VPoint centerPoint = mesh.createVertex(25, 10);

		Set<VPoint> points = new HashSet<>();
		points.add(p1);
		points.add(p2);
		points.add(p3);

		IncrementalTriangulation<VPoint, PHalfEdge<VPoint>, PFace<VPoint>> delaunayTriangulation = new IncrementalTriangulation<>(mesh, points, (x, y) -> new VPoint(x, y));
		delaunayTriangulation.compute();
		PFace<VPoint> face = delaunayTriangulation.locate(centerPoint).get();

		delaunayTriangulation.splitTriangle(face, centerPoint);
		delaunayTriangulation.finalize();

		Set<VTriangle> triangles = new HashSet<>(delaunayTriangulation.getTriangles());
		Set<VTriangle> expectedResult = new HashSet<>(Arrays.asList(new VTriangle(p1, p2, centerPoint), new VTriangle(p2, p3, centerPoint), new VTriangle(p1, p3, centerPoint)));
		assertTrue(testTriangulationEquality(triangles, expectedResult));
	}

	@Test
	public void testPerformance() {
		Set<VPoint> points = new HashSet<>();
		int width = 300;
		int height = 300;
		Random r = new Random();

		int numberOfPoints = 1000;

		for(int i=0; i< numberOfPoints; i++) {
			VPoint point = mesh.createVertex(width*r.nextDouble(), height*r.nextDouble());
			points.add(point);
		}

		long ms = System.currentTimeMillis();
		IncrementalTriangulation<VPoint, PHalfEdge<VPoint>, PFace<VPoint>> delaunayTriangulation = new IncrementalTriangulation<>(mesh, points, (x, y) -> new VPoint(x, y));

		delaunayTriangulation.compute();
		System.out.println("runtime of the BowyerWatson for " + numberOfPoints + " vertices =" + (System.currentTimeMillis() - ms));
	}

	private static boolean testTriangulationEquality(final Set<VTriangle> triangulation1, final Set<VTriangle> triangulation2) {
		if(triangulation1.size() != triangulation2.size())
			return false;

		for (VTriangle triangle1 : triangulation1) {
			boolean found = false;
			for (VTriangle triangle2 : triangulation2) {
				if(TestBoyerWatson.testTriangleEquality(triangle1, triangle2)){
					found = true;
				}
			}
			if(!found)
				return false;
		}

		return true;
	}

	private static boolean testTriangleEquality(final VTriangle triangle1, final VTriangle triangle2) {
		return	(triangle2.p1.equals(triangle1.p1) && triangle2.p2.equals(triangle1.p2) && triangle2.p3.equals(triangle1.p3)) ||
				(triangle2.p1.equals(triangle1.p1) && triangle2.p2.equals(triangle1.p3) && triangle2.p3.equals(triangle1.p2)) ||
				(triangle2.p1.equals(triangle1.p2) && triangle2.p2.equals(triangle1.p1) && triangle2.p3.equals(triangle1.p3)) ||
				(triangle2.p1.equals(triangle1.p2) && triangle2.p2.equals(triangle1.p3) && triangle2.p3.equals(triangle1.p1)) ||
				(triangle2.p1.equals(triangle1.p3) && triangle2.p2.equals(triangle1.p2) && triangle2.p3.equals(triangle1.p1)) ||
				(triangle2.p1.equals(triangle1.p3) && triangle2.p2.equals(triangle1.p1) && triangle2.p3.equals(triangle1.p2));
	}
}