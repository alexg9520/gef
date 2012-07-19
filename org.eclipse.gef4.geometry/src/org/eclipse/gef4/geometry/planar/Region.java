/*******************************************************************************
 * Copyright (c) 2011, 2012 itemis AG and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Alexander Nyßen (itemis AG) - initial API and implementation
 *     Matthias Wienand (itemis AG) - contribution for Bugzilla #355997
 *     
 *******************************************************************************/
package org.eclipse.gef4.geometry.planar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Stack;

import org.eclipse.gef4.geometry.euclidean.Angle;

/**
 * A combination of {@link Rectangle}s. The {@link Rectangle}s that build up a
 * {@link Region} do not have to be touching. The area covered by the
 * {@link Region} is exactly the area that all of its corresponding
 * {@link Rectangle}s are covering.
 * 
 * A {@link Region} differentiates between the internal {@link Rectangle}s and
 * the external {@link Rectangle}s. The external {@link Rectangle}s are those
 * that you feed it, in order to construct the {@link Region}. The internal
 * {@link Rectangle}s are those used for computations of the {@link Region}.
 * They are defined to not share any area, so that only their borders can be
 * overlapping.
 * 
 * @author anyssen
 * @author mwienand
 * 
 */
public class Region extends AbstractPolyShape implements ITranslatable<Region>,
		IScalable<Region>, IRotatable<Ring> {

	private static final long serialVersionUID = 1L;

	/**
	 * Cuts the given {@link Rectangle}s along the given parallel to the x-axis.
	 * 
	 * @param y
	 *            the distance of the cut-line to the x-axis
	 * @param parts
	 *            the {@link Rectangle}s to cut along that line
	 */
	private static void cutH(double y, ArrayList<Rectangle> parts) {
		for (Rectangle r : new ArrayList<Rectangle>(parts)) {
			if (r.y < y && y < r.y + r.height) {
				parts.remove(r);
				parts.add(new Rectangle(r.x, r.y, r.width, y - r.y));
				parts.add(new Rectangle(r.x, y, r.width, r.y + r.height - y));
			}
		}
	}

	/**
	 * Cuts the given {@link Rectangle}s along the given parallel to the y-axis.
	 * 
	 * @param x
	 *            the distance of the cut-line to the y-axis
	 * @param parts
	 *            the {@link Rectangle}s to cut along that line
	 */
	private static void cutV(double x, ArrayList<Rectangle> parts) {
		for (Rectangle r : new ArrayList<Rectangle>(parts)) {
			if (r.x < x && x < r.x + r.width) {
				parts.remove(r);
				parts.add(new Rectangle(r.x, r.y, x - r.x, r.height));
				parts.add(new Rectangle(x, r.y, r.x + r.width - x, r.height));
			}
		}
	}

	private ArrayList<Rectangle> rects;

	/**
	 * Constructs a new {@link Region} not covering any area.
	 */
	public Region() {
		rects = new ArrayList<Rectangle>();
	}

	/**
	 * Constructs a new {@link Region} from the given list of {@link Rectangle}
	 * s.
	 * 
	 * The given {@link Rectangle}s are {@link #add(Rectangle)}ed to the
	 * {@link Region} one after the other.
	 * 
	 * @param rectangles
	 */
	public Region(Rectangle... rectangles) {
		this();
		rects.add(rectangles[0].getCopy());

		for (int i = 1; i < rectangles.length; i++) {
			add(rectangles[i].getCopy());
		}
	}

	/**
	 * Constructs a new {@link Region} from the given other {@link Region}. In
	 * other words, it copies the given other {@link Region}.
	 * 
	 * @param other
	 */
	public Region(Region other) {
		rects = new ArrayList<Rectangle>(other.rects.size());

		for (Rectangle or : other.rects) {
			rects.add(or.getCopy());
		}
	}

	/**
	 * Adds the given {@link Rectangle} to this {@link Region}.
	 * 
	 * To assure the required conditions for internal {@link Rectangle}s, the
	 * given {@link Rectangle} is cut into several sub-{@link Rectangle}s so
	 * that no internal {@link Rectangle}s share any area.
	 * 
	 * @param rectangle
	 *            the {@link Rectangle} to add to this {@link Region}
	 * @return <code>this</code> for convenience
	 */
	public Region add(Rectangle rectangle) {
		ArrayList<Rectangle> toAdd = new ArrayList<Rectangle>(1);

		toAdd.add(rectangle.getCopy());

		for (Rectangle retain : rects) {
			for (Rectangle addend : new ArrayList<Rectangle>(toAdd)) {
				ArrayList<Rectangle> parts = new ArrayList<Rectangle>(8);
				parts.add(addend);

				if (addend.x <= retain.x && retain.x <= addend.x + addend.width
						|| retain.x <= addend.x
						&& addend.x <= retain.x + retain.width) {
					cutH(retain.y, parts);
					cutH(retain.y + retain.height, parts);
				}

				if (addend.y <= retain.y
						&& retain.y <= addend.y + addend.height
						|| retain.y <= addend.y
						&& addend.y <= retain.y + retain.height) {
					cutV(retain.x, parts);
					cutV(retain.x + retain.width, parts);
				}

				// filter inner parts:
				for (Iterator<Rectangle> p = parts.iterator(); p.hasNext();) {
					if (retain.contains(p.next())) {
						p.remove();
					}
				}

				toAdd.remove(addend);
				toAdd.addAll(parts);
			}
		}

		rects.addAll(toAdd);

		return this;
	}

	public boolean contains(IGeometry g) {
		return ShapeUtils.contains(this, g);
	}

	/**
	 * Collects all outline segments of the internal {@link Rectangle}s.
	 * 
	 * @return all the outline segments of the internal {@link Rectangle}s
	 */
	@Override
	protected Line[] getAllEdges() {
		Stack<Line> edges = new Stack<Line>();

		for (Rectangle r : rects) {
			for (Line e : r.getOutlineSegments()) {
				edges.push(e);
			}
		}
		return edges.toArray(new Line[] {});
	}

	public Rectangle getBounds() {
		if (rects.size() == 0) {
			return null;
		}

		Rectangle bounds = rects.get(0).getBounds();
		for (int i = 1; i < rects.size(); i++) {
			bounds.union(rects.get(i).getBounds());
		}

		return bounds;
	}

	public Region getCopy() {
		return new Region(this);
	}

	/**
	 * Computes the {@link Point}s of intersection of this {@link Region} with
	 * the given {@link ICurve}.
	 * 
	 * @param c
	 * @return the intersection {@link Point}s
	 */
	public Point[] getOutlineIntersections(ICurve c) {
		Set<Point> intersections = new HashSet<Point>(0);

		for (Line seg : getOutlineSegments()) {
			intersections.addAll(Arrays.asList(seg.getIntersections(c)));
		}

		return intersections.toArray(new Point[] {});
	}

	public Ring getRotatedCCW(Angle angle) {
		Point centroid = getBounds().getCenter();
		return getRotatedCCW(angle, centroid.x, centroid.y);
	}

	public Ring getRotatedCCW(Angle angle, double cx, double cy) {
		Polygon[] polys = new Polygon[rects.size()];
		for (int i = 0; i < polys.length; i++) {
			polys[i] = rects.get(i).getRotatedCCW(angle, cx, cy);
		}
		return new Ring(polys);
	}

	public Ring getRotatedCCW(Angle angle, Point center) {
		return getRotatedCCW(angle, center.x, center.y);
	}

	public Ring getRotatedCW(Angle angle) {
		Point centroid = getBounds().getCenter();
		return getRotatedCW(angle, centroid.x, centroid.y);
	}

	public Ring getRotatedCW(Angle angle, double cx, double cy) {
		Polygon[] polys = new Polygon[rects.size()];
		for (int i = 0; i < polys.length; i++) {
			polys[i] = rects.get(i).getRotatedCW(angle, cx, cy);
		}
		return new Ring(polys);
	}

	public Ring getRotatedCW(Angle angle, Point center) {
		return getRotatedCW(angle, center.x, center.y);
	}

	public Region getScaled(double factor) {
		return getCopy().scale(factor);
	}

	public Region getScaled(double fx, double fy) {
		return getCopy().scale(fx, fy);
	}

	public Region getScaled(double factor, double cx, double cy) {
		return getCopy().scale(factor, cx, cy);
	}

	public Region getScaled(double fx, double fy, double cx, double cy) {
		return getCopy().scale(fx, fy, cx, cy);
	}

	public Region getScaled(double fx, double fy, Point center) {
		return getCopy().scale(fx, fy, center);
	}

	public Region getScaled(double factor, Point center) {
		return getCopy().scale(factor, center);
	}

	public Rectangle[] getShapes() {
		return rects.toArray(new Rectangle[] {});
	}

	public Region getTranslated(double dx, double dy) {
		return getCopy().translate(dx, dy);
	}

	public Region getTranslated(Point d) {
		return getCopy().translate(d.x, d.y);
	}

	public Region scale(double factor) {
		return scale(factor, factor);
	}

	public Region scale(double fx, double fy) {
		Point centroid = getBounds().getCenter();
		return scale(fx, fy, centroid.x, centroid.y);
	}

	public Region scale(double factor, double cx, double cy) {
		return scale(factor, factor, cx, cy);
	}

	public Region scale(double fx, double fy, double cx, double cy) {
		for (Rectangle r : rects) {
			r.scale(fx, fy, cx, cy);
		}
		return this;
	}

	public Region scale(double fx, double fy, Point center) {
		return scale(fx, fy, center.x, center.y);
	}

	public Region scale(double factor, Point center) {
		return scale(factor, factor, center.x, center.y);
	}

	public Path toPath() {
		return CurveUtils.toPath(getOutlineSegments());
	}

	/**
	 * Constructs a new {@link Ring} that covers the same area as this
	 * {@link Region}.
	 * 
	 * @return a new {@link Ring} that covers the same area as this
	 *         {@link Region}
	 */
	public Ring toRing() {
		Polygon[] polys = new Polygon[rects.size()];
		Iterator<Rectangle> i = rects.iterator();
		for (int j = 0; j < rects.size(); j++) {
			polys[j] = i.next().toPolygon();
		}
		return new Ring(polys);
	}

	/**
	 * <p>
	 * Constructs a new {@link org.eclipse.swt.graphics.Region} that covers the
	 * same area as this {@link Region}. This is to ease the use of a
	 * {@link Region} for clipping:
	 * </p>
	 * 
	 * <p>
	 * <code>gc.setClipping(region.toSWTRegion());</code>
	 * </p>
	 * 
	 * @return a new {@link org.eclipse.swt.graphics.Region} that covers the
	 *         same area as this {@link Region}
	 */
	public org.eclipse.swt.graphics.Region toSWTRegion() {
		org.eclipse.swt.graphics.Region swtRegion = new org.eclipse.swt.graphics.Region();

		for (Rectangle r : rects) {
			swtRegion.add(r.toSWTRectangle());
		}

		return swtRegion;
	}

	public Region translate(double dx, double dy) {
		for (Rectangle r : rects) {
			r.translate(dx, dy);
		}
		return this;
	}

	public Region translate(Point d) {
		return translate(d.x, d.y);
	}

}
