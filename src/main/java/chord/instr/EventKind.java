/*
 * Copyright (c) 2008-2009, Intel Corporation.
 * Copyright (c) 2006-2007, The Trustees of Stanford University.
 * All rights reserved.
 */
package chord.instr;

/**
 * The kind of an event generated during an instrumented
 * program's execution.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class EventKind {
	public static final byte ENTER_METHOD = 0;
	public static final byte LEAVE_METHOD = 1;
	public static final byte ENTER_LOOP = 2;
	public static final byte LEAVE_LOOP = 3;

	public static final byte BEF_NEW = 4;
	public static final byte AFT_NEW = 5;
	public static final byte NEW = 6;
	public static final byte NEW_ARRAY = 7;

	public static final byte GETSTATIC_PRIMITIVE = 8;
	public static final byte GETSTATIC_REFERENCE = 9;
	public static final byte PUTSTATIC_PRIMITIVE = 10;
	public static final byte PUTSTATIC_REFERENCE = 11;

	public static final byte GETFIELD_PRIMITIVE = 12;
	public static final byte GETFIELD_REFERENCE = 13;
	public static final byte PUTFIELD_PRIMITIVE = 14;
	public static final byte PUTFIELD_REFERENCE = 15;

	public static final byte ALOAD_PRIMITIVE = 16;
	public static final byte ALOAD_REFERENCE = 17;
	public static final byte ASTORE_PRIMITIVE = 18; 
	public static final byte ASTORE_REFERENCE = 19; 

	public static final byte METHOD_CALL_BEF = 20;
	public static final byte METHOD_CALL_AFT = 21;
	public static final byte RETURN_PRIMITIVE = 22;
	public static final byte RETURN_REFERENCE = 23;
	public static final byte EXPLICIT_THROW = 24;
	public static final byte IMPLICIT_THROW = 25;

	public static final byte QUAD = 26;
	public static final byte BASIC_BLOCK = 27;

	public static final byte THREAD_START = 28;
	public static final byte THREAD_JOIN = 29;
	public static final byte ACQUIRE_LOCK = 30;
	public static final byte RELEASE_LOCK = 31;
	public static final byte WAIT = 32;
	public static final byte NOTIFY = 33;
	public static final byte NOTIFY_ALL = 34;
}
