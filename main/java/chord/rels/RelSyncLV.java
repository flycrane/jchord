/*
 * Copyright (c) 2008-2009, Intel Corporation.
 * Copyright (c) 2006-2007, The Trustees of Stanford University.
 * All rights reserved.
 */
package chord.rels;

import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.Monitor;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.doms.DomL;
import chord.project.Chord;
import chord.project.ProgramRel;

/**
 * Relation containing each tuple (l,v) such that monitorenter
 * statement l is synchronized on variable v.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
	name = "syncLV",
	sign = "L0,V0:L0_V0"
)
public class RelSyncLV extends ProgramRel {
	public void fill() {
		DomL domL = (DomL) doms[0];
		int numL = domL.size();
		for (int lIdx = 0; lIdx < numL; lIdx++) {
			Inst i = domL.get(lIdx);
			if (i instanceof Quad) {
				Quad q = (Quad) i;
				Operand op = Monitor.getSrc(q);
				if (op instanceof RegisterOperand) {
					RegisterOperand ro = (RegisterOperand) op;
					Register v = ro.getRegister();
					add(q, v);
				}
			}
		}
	}
}
