/*
 * Copyright (c) 2008-2009, Intel Corporation.
 * Copyright (c) 2006-2007, The Trustees of Stanford University.
 * All rights reserved.
 */
package chord.visitors;

import joeq.Compiler.Quad.Quad;

/**
 * Visitor over all return statements in all methods in the program.
 * 
 * @author Mayur Naik (mayur.naik@intel.com)
 */
public interface IReturnInstVisitor extends IMethodVisitor {
	/**
	 * Visits all return statements in all methods in the program.
	 * 
	 * @param	q	A return statement.
	 */
	public void visitReturnInst(Quad q);
}
