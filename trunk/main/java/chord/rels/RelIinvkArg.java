/*
 * Copyright (c) 2008-2009, Intel Corporation.
 * Copyright (c) 2006-2007, The Trustees of Stanford University.
 * All rights reserved.
 */
package chord.rels;

import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.RegisterFactory.Register;

import chord.doms.DomI;
import chord.doms.DomV;
import chord.project.Chord;
import chord.project.ProgramRel;

/**
 * Relation containing each tuple (i,z,v) such that local variable v
 * is the zth argument variable of method invocation statement i.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
	name = "IinvkArg",
	sign = "I0,Z0,V1:I0_V1_Z0"
)
public class RelIinvkArg extends ProgramRel {
	public void fill() {
		DomI domI = (DomI) doms[0];
		DomV domV = (DomV) doms[2];
		int numI = domI.size();
		for (int iIdx = 0; iIdx < numI; iIdx++) {
			Quad q = domI.get(iIdx);
			ParamListOperand l = Invoke.getParamList(q);
			int numArgs = l.length();
			for (int zIdx = 0; zIdx < numArgs; zIdx++) {
				RegisterOperand vo = l.get(zIdx);
				Register v = vo.getRegister();
				if (v.getType().isReferenceType()) {
					int vIdx = domV.indexOf(v);
					add(iIdx, zIdx, vIdx);
				}
			}
		}
	}
}
