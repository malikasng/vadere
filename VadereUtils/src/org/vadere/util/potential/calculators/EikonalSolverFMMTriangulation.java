package org.vadere.util.potential.calculators;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.vadere.util.geometry.Geometry;
import org.vadere.util.geometry.GeometryUtils;
import org.vadere.util.geometry.LineIterator;
import org.vadere.util.geometry.data.Face;
import org.vadere.util.geometry.data.HalfEdge;
import org.vadere.util.geometry.data.Triangulation;
import org.vadere.util.geometry.shapes.IPoint;
import org.vadere.util.geometry.shapes.VCone;
import org.vadere.util.geometry.shapes.VLine;
import org.vadere.util.geometry.shapes.VPoint;
import org.vadere.util.geometry.shapes.VRectangle;
import org.vadere.util.geometry.shapes.VShape;
import org.vadere.util.geometry.shapes.VTriangle;
import org.vadere.util.math.InterpolationUtil;
import org.vadere.util.math.MathUtil;
import org.vadere.util.geometry.shapes.VShape;
import org.vadere.util.potential.CellGrid;
import org.vadere.util.potential.PathFindingTag;
import org.vadere.util.potential.timecost.ITimeCostFunction;
import org.vadere.util.triangulation.PointConstructor;
import org.vadere.util.triangulation.adaptive.MeshPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;


public class EikonalSolverFMMTriangulation<P extends PotentialPoint> implements EikonalSolver  {

	private static Logger logger = LogManager.getLogger(EikonalSolverFMMTriangulation.class);

	private ITimeCostFunction timeCostFunction;
	private Triangulation<P> triangulation;
	private boolean calculationFinished;
	private PriorityQueue<FFMHalfEdge> narrowBand;
	private final Collection<VRectangle> targetAreas;
	private final PointConstructor<P> pointConstructor;

	private Comparator<FFMHalfEdge> pointComparator = (he1, he2) -> {
		if (he1.halfEdge.getEnd().getPotential() < he2.halfEdge.getEnd().getPotential()) {
			return -1;
		} else if(he1.halfEdge.getEnd().getPotential() > he2.halfEdge.getEnd().getPotential()) {
			return 1;
		}
		else {
			return 0;
		}
	};

	public EikonalSolverFMMTriangulation(final Collection<VRectangle> targetAreas,
	                                     final ITimeCostFunction timeCostFunction,
	                                     final Triangulation<P> triangulation,
	                                     final PointConstructor<P> pointConstructor
	                                     ) {
		this.triangulation = triangulation;
		this.calculationFinished = false;
		this.timeCostFunction = timeCostFunction;
		this.targetAreas = targetAreas;
		this.pointConstructor = pointConstructor;
	}


	private void initializeTargetAreas() {
		for(VRectangle rectangle : targetAreas) {
			VPoint topLeft = new VPoint(rectangle.getX(), rectangle.getY());
			VPoint bottomLeft = new VPoint(rectangle.getX(), rectangle.getMaxY());
			VPoint bottomRight = new VPoint(rectangle.getMaxX(), rectangle.getMaxY());
			VPoint topRight = new VPoint(rectangle.getMaxX(), rectangle.getY());
			LineIterator lineIterator1 = new LineIterator(new VLine(topLeft, topRight), 1.0);
			LineIterator lineIterator2 = new LineIterator(new VLine(topLeft, bottomLeft), 1.0);
			LineIterator lineIterator3 = new LineIterator(new VLine(bottomLeft, bottomRight), 1.0);
			LineIterator lineIterator4 = new LineIterator(new VLine(topRight, bottomRight), 1.0);

			List<LineIterator> lineIterators = Arrays.asList(lineIterator1, lineIterator2, lineIterator3, lineIterator4);

			for(LineIterator lineIterator : lineIterators) {
				while (lineIterator.hasNext()) {
					IPoint next = lineIterator.next();
					P potentialPoint = pointConstructor.create(next.getX(), next.getY());
					potentialPoint.setPathFindingTag(PathFindingTag.Reached);
					potentialPoint.setPotential(0.0);
					HalfEdge<P> halfEdge = triangulation.insert(potentialPoint);

					if(halfEdge != null && halfEdge.getEnd().equals(potentialPoint)) {
						narrowBand.add(new FFMHalfEdge(halfEdge));
					}
					else {
						logger.warn("did not found inserted edge!");
					}
				}
			}

			for(Face<P> face : triangulation) {
				for(HalfEdge<P> potentialPoint : face) {
					for(VRectangle targetRect : targetAreas) {
						if(targetRect.contains(potentialPoint.getEnd())) {
							potentialPoint.getEnd().setPotential(0.0);
							potentialPoint.getEnd().setPathFindingTag(PathFindingTag.Reached);
							narrowBand.add(new FFMHalfEdge(potentialPoint));
						}
					}
				}
			}
		}
	}

	@Override
	public void initialize() {
		narrowBand = new PriorityQueue<>(pointComparator);
		initializeTargetAreas();
		/*for(IPoint point : targetPoints) {
			Face<? extends PotentialPoint> face = triangulation.locate(point);

			for(HalfEdge<? extends PotentialPoint> halfEdge : face) {
				PotentialPoint potentialPoint = halfEdge.getEnd();
				double distance = point.distance(potentialPoint);

				if(potentialPoint.getPathFindingTag() != PathFindingTag.Undefined) {
					narrowBand.remove(new FFMHalfEdge(halfEdge));
				}

				potentialPoint.setPotential(Math.min(potentialPoint.getPotential(), distance / timeCostFunction.costAt(potentialPoint)));
				potentialPoint.setPathFindingTag(PathFindingTag.Reached);
				narrowBand.add(new FFMHalfEdge(halfEdge));
			}
		}*/

		calculate();
	}

    @Override
    public CellGrid getPotentialField() {
        return null;
    }

    @Override
	public double getValue(double x, double y) {
		Face<? extends PotentialPoint> triangle = triangulation.locate(new VPoint(x, y));

		if(triangle == null) {
			logger.warn("no triangle found for coordinates (" + x + "," + y + ")");
		}
		else {
			return InterpolationUtil.barycentricInterpolation(triangle, x, y);
		}
		return Double.MAX_VALUE;
	}


	/**
	 * Calculate the fast marching solution. This is called only once,
	 * subsequent calls only return the result of the first.
	 */
	private void calculate() {
		if (!calculationFinished) {
			while (this.narrowBand.size() > 0) {
				//System.out.println(narrowBand.size());
				// poll the point with lowest data value
				FFMHalfEdge ffmHalfEdge = this.narrowBand.poll();
				// add it to the frozen points
				ffmHalfEdge.halfEdge.getEnd().setPathFindingTag(PathFindingTag.Reached);
				// recalculate the value based on the adjacent triangles
				//double potential = recalculatePoint(ffmHalfEdge.halfEdge);
				//ffmHalfEdge.halfEdge.getEnd().setPotential(Math.min(ffmHalfEdge.halfEdge.getEnd().getPotential(), potential));
				// add narrow points
				setNeighborDistances(ffmHalfEdge.halfEdge);
			}

			this.calculationFinished = true;
		}
	}

	/**
	 * Gets points in the narrow band around p.
	 *
	 * @param halfEdge
	 * @return a set of points in the narrow band that are close to p.
	 */
	private void setNeighborDistances(final HalfEdge<? extends PotentialPoint> halfEdge) {
		// remove frozen points
		Iterator<? extends HalfEdge<? extends PotentialPoint>> it = halfEdge.incidentVertexIterator();

		while (it.hasNext()) {
			HalfEdge<? extends PotentialPoint> neighbour = it.next();
			if(neighbour.getEnd().getPathFindingTag() == PathFindingTag.Undefined) {
				double potential = recalculatePoint(neighbour);

				// if not, it was not possible to compute a valid potential. TODO?
				if(potential < neighbour.getEnd().getPotential()) {
					neighbour.getEnd().setPotential(potential);
					neighbour.getEnd().setPathFindingTag(PathFindingTag.Reachable);
					narrowBand.add(new FFMHalfEdge(neighbour));
				}
				else {
					logger.warn("could not set neighbour vertex" + neighbour + "," + neighbour.getFace().isBorder());
					potential = recalculatePoint(neighbour);
					potential = recalculatePoint(neighbour);
				}
			}
			else if(neighbour.getEnd().getPathFindingTag() == PathFindingTag.Reachable) {
				//double potential = neighbour.getEnd().getPotential();
				double potential = recalculatePoint(neighbour);

				// neighbour might be already in the narrowBand => update it
				if (potential < neighbour.getEnd().getPotential()) {
					FFMHalfEdge ffmHalfEdge = new FFMHalfEdge(neighbour);
					narrowBand.remove(new FFMHalfEdge(neighbour));
					neighbour.getEnd().setPotential(potential);
					narrowBand.add(ffmHalfEdge);
				}
			}
		}
	}

	/**
	 * Recalculates the vertex given by the formulas in Sethian-1999.
	 *
	 * @param point
	 * @return the same point, with a (possibly) changed data value.
	 */
	private double recalculatePoint(final HalfEdge<? extends PotentialPoint> point) {
		// loop over all, check whether the point is contained and update its
		// value accordingly
		double potential = Double.MAX_VALUE;
		Iterator<? extends Face<? extends PotentialPoint>> it = point.incidentFaceIterator();
		while (it.hasNext()) {
			Face<? extends PotentialPoint> face = it.next();
			if(!face.isBorder()) {
				potential = Math.min(updatePoint(point.getEnd(), face), potential);
			}
		}
		return potential;
	}

	/**
	 * Updates a point given a triangle. The point can only be updated if the
	 * triangle contains it and the other two points are in the frozen band.
	 *
	 * @param point
	 * @param face
	 */
	private double updatePoint(final PotentialPoint point, final Face<? extends PotentialPoint> face) {
		// check whether the triangle does contain useful data
		List<? extends PotentialPoint> points = face.getPoints();
		HalfEdge<? extends PotentialPoint> halfEdge = face.stream().filter(p -> p.getEnd().equals(point)).findAny().get();
		points.removeIf(p -> p.equals(point));

		assert points.size() == 2;
		PotentialPoint p1 = points.get(0);
		PotentialPoint p2 = points.get(1);

		/*if(!p1.getPathFindingTag().frozen && !p2.getPathFindingTag().frozen) {
			return Double.MAX_VALUE;
		}*/

		// search another vertex in the acute cone
		//if(GeometryUtils.angle(p1, point, p2) > Math.PI / 2) {
		if(!p1.getPathFindingTag().frozen || !p2.getPathFindingTag().frozen) {
			// find support vertex

			Optional<HalfEdge<? extends PotentialPoint>> optHe = findPointInCone(halfEdge, p1, p2);

			if(optHe.isPresent()) {
				double pot1 = updatePoint(point, optHe.get().getEnd(), p1);
				double pot2 = updatePoint(point, optHe.get().getEnd(), p2);
				return Math.min(pot1, pot2);
			}
			else {
				return point.getPotential();
			}
		}
		else {
			return updatePoint(point, p1, p2);
		}
	}

	private Optional<HalfEdge<? extends PotentialPoint>> findPointInCone(final HalfEdge<? extends PotentialPoint> halfEdge, final PotentialPoint p1, final PotentialPoint p2) {
		PotentialPoint point = halfEdge.getEnd();
		VTriangle triangle = new VTriangle(new VPoint(point), new VPoint(p1), new VPoint(p2));

		// 1. construct the acute cone
		VPoint direction = triangle.getIncenter().subtract(point);
		double angle = Math.PI - GeometryUtils.angle(p1, point, p2);
		VPoint origin = new VPoint(point);
		VCone cone = new VCone(origin, direction, angle);

		// 2. search for the nearest point inside the cone
		HalfEdge<? extends PotentialPoint> he = halfEdge.getPrevious().getTwin();

		while((!cone.contains(he.getNext().getEnd()) && !cone.contains(he.getPrevious().getEnd()))) {

			if(he.isBoundary()) {
				return Optional.empty();
			}

			VLine line1 = new VLine(new VPoint(he.getEnd()), new VPoint(he.getNext().getEnd()));
			VLine line2 = new VLine(new VPoint(he.getNext().getEnd()), new VPoint(he.getNext().getNext().getEnd()));
			// the line segment from a to b intersect the cone?
			if(cone.overlapLineSegment(line1)) {
				he = he.getNext().getTwin();
			}
			else if(cone.overlapLineSegment(line2)) {
				he = he.getNext().getNext().getTwin();
			}
			else {
				logger.warn("could not recompute potential for " + point);
				//findPointInCone(halfEdge, p1, p2);
				return Optional.empty();
				//findPointInCone(halfEdge, p1, p2);
				//throw new IllegalStateException("no line overlap the acute cone!");
			}
		}

		return Optional.of(he);
	}

	private double updatePoint(final PotentialPoint point, final PotentialPoint p1, final PotentialPoint p2) {

		/*if ((Double.isInfinite(p1.getPotential()) && Double.isInfinite((p2.getPotential())))
				|| (Double.isInfinite(p1.getPotential()) && Double.isInfinite(point.getPotential()))
				|| (Double.isInfinite(p2.getPotential()) && Double.isInfinite(point.getPotential()))) {
			return point.getPotential();
		}*/

		// check whether they are in the frozen set. only if they are, we can
		// continue.
		// if(this.frozenPoints.contains(points.first()) &&
		// this.frozenPoints.contains(points.last()))

		/*if(
				(p1.getPathFindingTag() == PathFindingTag.Reached || p1.getPathFindingTag() == PathFindingTag.Reachable)
						&& (p2.getPathFindingTag() == PathFindingTag.Reached || p2.getPathFindingTag() == PathFindingTag.Reachable))
		{*/
		if(p1.getPathFindingTag().frozen && p2.getPathFindingTag().frozen) {
			// see: Sethian, Level Set Methods and Fast Marching Methods, page
			// 124.
			double u = p2.getPotential() - p1.getX();
			double a = p2.distance(point);
			double b = p1.distance(point);
			double c = p1.distance(p2);
			double TA = p1.getPotential();
			double TB = p2.getPotential();

			double phi = GeometryUtils.angle(p1, point, p2);
			double cosphi = Math.cos(phi);

			double F = 1.0 / this.timeCostFunction.costAt(point);

			// solve x2 t^2 + x1 t + x0 == 0
			double x2 = a * a + b * b - 2 * a * b * cosphi;
			double x1 = 2 * b * u * (a * cosphi - b);
			double x0 = b * b
					* (u * u - F * F * a * a * Math.sin(phi) * Math.sin(phi));
			double t = solveQuadratic(x2, x1, x0);

			double inTriangle = (b * (t - u) / t);
			if (u < t && a * cosphi < inTriangle && inTriangle < a / cosphi) {
				return t + TA;
			} else {
				return Math.min(b * F + TA, c * F + TB);
			}
		}
		else {
			return point.getPotential();
		}
	}

	/**
	 * Solves the quadratic equation given by a x^2+bx+c=0.
	 *
	 * @param a
	 * @param b
	 * @param c
	 * @return the maximum of both solutions, if any. If det=b^2-4ac < 0, it
	 *         returns Double.MIN_VALUE
	 */
	private double solveQuadratic(double a, double b, double c) {
		List<Double> solutions = MathUtil.solveQuadratic(a, b, c);
		double result = Double.MIN_VALUE;
		if (solutions.size() == 2) {
			result =  Math.max(solutions.get(0), solutions.get(1));
		} else if (solutions.size() == 1) {
			result = solutions.get(0);
		}

		return result;

		/*double det = b * b - 4 * a * c;
		if (det < 0) {
			return Double.MIN_VALUE;
		}

		return Math.max((-b + Math.sqrt(det)) / (2 * a), (-b - Math.sqrt(det))
				/ (2 * a));*/
	}

	/**
	 * We require a half-edge that has an equals which only depends on the end-vertex.
	 */
	private class FFMHalfEdge {
		private HalfEdge<? extends PotentialPoint> halfEdge;

		public FFMHalfEdge(final HalfEdge<? extends PotentialPoint> halfEdge){
			this.halfEdge = halfEdge;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			FFMHalfEdge that = (FFMHalfEdge) o;

			return halfEdge.getEnd().equals(that.halfEdge.getEnd());
		}

		@Override
		public int hashCode() {
			return halfEdge.getEnd().hashCode();
		}

		@Override
		public String toString() {
			return halfEdge.toString();
		}
	}
}
