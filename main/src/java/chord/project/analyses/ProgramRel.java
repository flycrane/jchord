/*
 * Copyright (c) 2008-2010, Intel Corporation.
 * Copyright (c) 2006-2007, The Trustees of Stanford University.
 * All rights reserved.
 * Licensed under the terms of the New BSD License.
 */
package chord.project.analyses;

import chord.bddbddb.Rel;
import chord.program.visitors.IClassVisitor;
import chord.project.Project;
import chord.project.ChordProperties;
import chord.project.VisitorHandler;
import chord.util.ChordRuntimeException;
import chord.project.Messages;

/**
 * Generic implementation of a program relation (a specialized kind
 * of Java task).
 * <p>
 * A program relation is a relation over one or more program domains.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class ProgramRel extends Rel implements ITask {
	private static final String SKIP_TUPLE =
		"WARN: Skipping a tuple from relation '%s' as element '%s' was not found in domain '%s'.";
	public void run() {
		zero();
		init();
		fill();
		save();
	}
	public void init() { }
	public void save() {
		System.out.println("SAVING rel " + name + " size: " + size());
		super.save(ChordProperties.bddbddbWorkDirName);
		Project.setTrgtDone(this);
	}
	public void load() {
		super.load(ChordProperties.bddbddbWorkDirName);
	}
	public void fill() {
		if (this instanceof IClassVisitor) {
			VisitorHandler vh = new VisitorHandler(this);
			vh.visitProgram();
		} else {
			throw new ChordRuntimeException("Relation '" + name +
				"' must override method fill().");
		}
	}
	public void print() {
		super.print(ChordProperties.outDirName);
	}
	public String toString() {
		return name;
	}
	public void skip(Object elem, ProgramDom dom) {
		Messages.log(SKIP_TUPLE, getClass().getName(), elem, dom.getClass().getName());
	}
}