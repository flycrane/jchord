/*
 * Copyright (c) 2008-2010, Intel Corporation.
 * Copyright (c) 2006-2007, The Trustees of Stanford University.
 * All rights reserved.
 */
package chord.doms;

import java.util.Set;

import joeq.Class.jq_Reference;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.CheckCast;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Util.Templates.ListIterator;
import chord.project.Chord;
import chord.project.Project;
import chord.project.Properties;
import chord.project.analyses.ProgramDom;
import chord.program.Program;
import chord.util.IndexSet;
import chord.util.tuple.object.Pair;

/**
 * Domain of object allocation statements.
 * <p>		
 * The 0th element of this domain (null) is a distinguished		
 * hypothetical object allocation statement that may be used		
 * for various purposes.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 * @author omertripp (omertrip@post.tau.ac.il)
 */
@Chord(
	name = "H",
	consumedNames = { "M", "T" }
)
public class DomH extends ProgramDom<Object> {
	protected DomM domM;
	protected DomT domT;
	protected int lastRealHidx;
	public int getLastRealHidx() {
		return lastRealHidx;
	}
	public void init() {		
		domM = (DomM) Project.getTrgt("M");
		domT = (DomT) Project.getTrgt("T");
		getOrAdd(null);	
	}
	public void fill() {
		Program program = Program.getProgram();
		int numM = domM.size();
		for (int mIdx = 0; mIdx < numM; mIdx++) {
			jq_Method m = domM.get(mIdx);
			if (m.isAbstract())
				continue;
			ControlFlowGraph cfg = m.getCFG();
			for (ListIterator.BasicBlock it = cfg.reversePostOrderIterator();
					it.hasNext();) {
				BasicBlock bb = it.nextBasicBlock();
				for (ListIterator.Quad it2 = bb.iterator(); it2.hasNext();) {
					Quad q = it2.nextQuad();
					Operator op = q.getOperator();
					if (op instanceof New || op instanceof NewArray) {
						program.mapInstToMethod(q, m);
						getOrAdd(q);
					}
				}
			}
		}
		lastRealHidx = size() - 1;
		for (jq_Reference c : program.getReflectClasses())
			getOrAdd(c);
	}
	public String toUniqueString(Object o) {
		if (o instanceof Quad) {
			Quad q = (Quad) o;
			return Program.getProgram().toBytePosStr(q);
		}
		if (o instanceof jq_Reference) {
			jq_Reference r = (jq_Reference) o;
			return r.getName();
		}
		assert (o == null);
		return "null";
	}
	public String toXMLAttrsString(Object o) {
		if (o instanceof Quad) {
			Quad q = (Quad) o;
			Operator op = q.getOperator();
			String type = (op instanceof New) ? New.getType(q).getType().getName() :
				(op instanceof NewArray) ? NewArray.getType(q).getType().getName() : "unknown";
			jq_Method m = Program.getProgram().getMethod(q);
			String file = Program.getSourceFileName(m.getDeclaringClass());
			int line = Program.getLineNumber(q, m);
			int mIdx = domM.indexOf(m);
			return "file=\"" + file + "\" " + "line=\"" + line + "\" " +
				"Mid=\"M" + mIdx + "\"" + " type=\"" + type + "\"";
		}
		return "";
	}
}