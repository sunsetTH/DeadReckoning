package com.astar.i2r.ins.map;

import com.astar.i2r.ins.gui.Weighted;

public class SimpleWeightedEdge extends SimpleEdge implements WeightedEdge {
	public final double weight;
	private static final double DEFAULTWEIGHT = 1.0;

	public SimpleWeightedEdge(Edge edge, double w) {
		super(edge);
		weight = w;
	}

	public SimpleWeightedEdge(Edge edge) {
		this(edge, DEFAULTWEIGHT);
	}

	@Override
	public double getWeight() {
		return weight;
	}

	@Override
	public Weighted setWeight(double w) {
		return new SimpleWeightedEdge(this, w);
	}

}
