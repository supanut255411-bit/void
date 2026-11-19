package dev.voidreaver.chain;

/**
 * Pure math (no Minecraft types) for the chain pull, so it can be unit-tested on its own.
 * Boxes are passed as {minX, minY, minZ, maxX, maxY, maxZ}.
 */
public final class PullMath {
	private PullMath() { }

	/** Distance along one axis between two intervals; 0 if they overlap. */
	public static double axisGap(double aMin, double aMax, double bMin, double bMax) {
		return Math.max(0.0, Math.max(aMin - bMax, bMin - aMax));
	}

	/** Shortest distance between two axis-aligned boxes; 0 if they touch or overlap. */
	public static double boxGap(double aMinX, double aMinY, double aMinZ, double aMaxX, double aMaxY, double aMaxZ,
			double bMinX, double bMinY, double bMinZ, double bMaxX, double bMaxY, double bMaxZ) {
		double dx = axisGap(aMinX, aMaxX, bMinX, bMaxX);
		double dy = axisGap(aMinY, aMaxY, bMinY, bMaxY);
		double dz = axisGap(aMinZ, aMaxZ, bMinZ, bMaxZ);
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/**
	 * Swept box test: how much of {@code motion} (0..1) the moving box can travel before its hitbox touches the target's.
	 * 1 means the path is free this tick, 0 means the boxes already touch. The player keeps full speed right up to contact
	 * and the last step ends exactly on the target's surface (within {@code tolerance}), so there is no overshoot or clipping.
	 */
	public static double travelFraction(double[] mover, double[] target, double mx, double my, double mz, double tolerance) {
		double cx = (mover[0] + mover[3]) * 0.5;
		double cy = (mover[1] + mover[4]) * 0.5;
		double cz = (mover[2] + mover[5]) * 0.5;
		double hx = (mover[3] - mover[0]) * 0.5 + tolerance;
		double hy = (mover[4] - mover[1]) * 0.5 + tolerance;
		double hz = (mover[5] - mover[2]) * 0.5 + tolerance;

		// Minkowski sum: the mover's centre must stay outside the target box grown by the mover's half-size.
		double[] min = {target[0] - hx, target[1] - hy, target[2] - hz};
		double[] max = {target[3] + hx, target[4] + hy, target[5] + hz};
		double[] origin = {cx, cy, cz};
		double[] dir = {mx, my, mz};

		boolean inside = true;
		double tNear = Double.NEGATIVE_INFINITY;
		double tFar = Double.POSITIVE_INFINITY;

		for (int axis = 0; axis < 3; axis++) {
			if (origin[axis] < min[axis] || origin[axis] > max[axis]) {
				inside = false;
			}

			if (Math.abs(dir[axis]) < 1.0E-9) {
				if (origin[axis] < min[axis] || origin[axis] > max[axis]) {
					return 1.0; // moving parallel to this slab and outside it: never touches
				}

				continue;
			}

			double t1 = (min[axis] - origin[axis]) / dir[axis];
			double t2 = (max[axis] - origin[axis]) / dir[axis];
			tNear = Math.max(tNear, Math.min(t1, t2));
			tFar = Math.min(tFar, Math.max(t1, t2));
		}

		if (inside) {
			return 0.0;
		}

		if (tNear > tFar || tFar < 0.0 || tNear > 1.0) {
			return 1.0;
		}

		return Math.max(0.0, Math.min(1.0, tNear));
	}
}
