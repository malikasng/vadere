package org.vadere.util.geometry.mesh.gen;

import org.jetbrains.annotations.NotNull;
import org.vadere.util.geometry.mesh.inter.IVertex;
import org.vadere.util.geometry.shapes.IPoint;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by bzoennchen on 06.09.17.
 */
public class AVertex<P extends IPoint> implements IVertex<P> {

	private final Lock lock;
	private final P point;
	private int down;
	private int halfEdge;
	private final int id;
	private boolean destroyed;


	public AVertex(@NotNull final int id, @NotNull final P point) {
		this.point = point;
		this.id = id;
		this.lock = new ReentrantLock();
	}

	@Override
	public P getPoint() {
		return point;
	}

	public int getEdge() {
		return halfEdge;
	}

	public void setEdge(final int halfEdge) {
		this.halfEdge = halfEdge;
	}

	public int getDown() {
		return down;
	}

	public void setDown(final int down) {
		this.down = down;
	}

	int getId() {
	    return id;
    }

	@Override
	public boolean equals(Object obj) {
		if(obj == null) {
			return false;
		}

		if(obj.getClass() != this.getClass()) {
			return false;
		}

		return point.equals(((AVertex<P>)obj).getPoint());
	}

	public boolean isDestroyed() {
		return destroyed;
	}

	public void destroy() {
		destroyed = true;
	}

	public Lock getLock() {
		return lock;
	}

	@Override
	public int hashCode() {
		return point.hashCode();
	}

	@Override
	public String toString() {
		return point.toString();
	}
}