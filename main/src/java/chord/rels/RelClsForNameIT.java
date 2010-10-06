/*
 * Copyright (c) 2008-2010, Intel Corporation.
 * Copyright (c) 2006-2007, The Trustees of Stanford University.
 * All rights reserved.
 * Licensed under the terms of the New BSD License.
 */
package chord.rels;

import java.util.List;

import joeq.Class.jq_Reference;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.Invoke;

import chord.doms.DomI;
import chord.doms.DomT;
import chord.program.Program;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.object.Pair;

/**
 * Relation containing each tuple (m,t) such that method m contains a
 * call site calling static method java.lang.Class.forName(s), with s
 * possibly evaluating to type t.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
@Chord(
	name = "clsForNameIT",
	sign = "I0,T0:I0_T0"
)
public class RelClsForNameIT extends ProgramRel {
	public void fill() {
		DomI domI = (DomI) doms[0];
		DomT domT = (DomT) doms[1];
		List<Pair<Quad, List<jq_Reference>>> l =
			Program.g().getReflect().getResolvedClsForNameSites();
		for (Pair<Quad, List<jq_Reference>> p : l) {
			Quad q = p.val0;
			int iIdx = domI.indexOf(q);
			assert (iIdx >= 0);
			for (jq_Reference t : p.val1) {
				int tIdx = domT.indexOf(t);
				assert (tIdx >= 0);
				add(iIdx, tIdx);
			}
		}
	}
}
