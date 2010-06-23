/*
 * Copyright (c) 2008-2010, Intel Corporation.
 * Copyright (c) 2006-2007, The Trustees of Stanford University.
 * All rights reserved.
 */
package chord.program;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Class.PrimordialClassLoader;
import joeq.Class.jq_Array;
import joeq.Class.jq_Class;
import joeq.Class.jq_ClassInitializer;
import joeq.Class.jq_Field;
import joeq.Class.jq_InstanceMethod;
import joeq.Class.jq_Method;
import joeq.Class.jq_NameAndDesc;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.CheckCast;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Phi;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Invoke.InvokeInterface;
import joeq.Compiler.Quad.Operator.Invoke.InvokeStatic;
import joeq.Compiler.Quad.Operator.Invoke.InvokeVirtual;
import joeq.Compiler.Quad.RegisterFactory.Register;
import joeq.Main.HostedVM;
import joeq.Util.Templates.ListIterator;

import chord.project.Messages;
import chord.project.Properties;
import chord.util.IndexSet;
import chord.util.Timer;
import chord.util.ArraySet;

/**
 * Rapid Type Analysis algorithm for computing program scope
 * (reachable classes and methods).
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 * @author Omer Tripp (omertripp@post.tau.ac.il)
 */
public class RTAScope implements IScope {
    public static final boolean DEBUG = false;

	// flag determining whether scope construction must analyze calls
	// to Class.newInstance()
	private final boolean handleReflection;

	/*
	 * data structures used only if handleReflection is true
	 */

	// set of classes instantiated by calls to Class.newInstance()
	private IndexSet<jq_Reference> reflectClasses;

	// set of local variables to which the return value of some call to
	// Class.newInstance() is assigned either directly, or transitively
	// via move/phi statements, until a checkcast statement is found
	private Set<Register> reflectVars;

	// set of methods containing a "return v" statement where v is in
	// set reflectVars
    private Set<jq_Method> reflectRetMeths;

	// map from each method to all its call sites encountered so far
	private Map<jq_Method, Set<Quad>> methToInvks;

	// program class hierarchy built either statically or dynamically
    private ClassHierarchy ch;

	/*
	 * data structures reset after every iteration
	 */

	// set of all classes whose clinits and super class/interface clinits
	// have been processed so far in current interation; this set is kept
	// to avoid repeatedly visiting super classes/interfaces within an
	// iteration (which incurs a huge runtime penalty) only to find that
 	// all their clinits have already been processed in that iteration.
	private Set<jq_Class> classesVisitedForClinit;

	// set of all methods deemed reachable so far in current iteration
	private IndexSet<jq_Method> methods;

	/*
	 * persistent data structures
	 */

	// set of all classes deemed reachable so far
	private IndexSet<jq_Reference> classes;

	// set of all (concrete) classes deemed instantiated so far either
	// by a reachable new/newarray statement or by a call to
	// Class.newInstance(); the latter is only if handleReflection is
	// set to true
    private IndexSet<jq_Reference> reachableAllocClasses;

	// worklist for methods seen so far in current iteration but whose
	// CFGs haven't been processed yet
	private List<jq_Method> methodWorklist;

	// handle to the representation of class java.lang.Object
	private jq_Class javaLangObject;

	// flag indicating that another iteration is needed; it is set if
	// any of the following sets grows in the current iteration:
	// reachableAllocClasses, reflectVars, reflectRetMeths
	// Note: sets reflectVars and reflectRetMeths are maintained only
	// if handleReflection is set to true
	private boolean repeat = true;

	public RTAScope(boolean _handleReflection) {
		this.handleReflection = _handleReflection;
	}
	public IndexSet<jq_Method> getMethods() {
		if (methods == null)
			build();
		return methods;
	}
	public IndexSet<jq_Reference> getReflectClasses() {
		if (!handleReflection)
			return new IndexSet<jq_Reference>(0);
		if (reflectClasses == null)
			build();
		return reflectClasses;
	}
	private void build() {
		System.out.println("ENTER: RTA");
		Timer timer = new Timer();
		timer.init();
 		classesVisitedForClinit = new HashSet<jq_Class>();
 		methods = new IndexSet<jq_Method>();
 		classes = new IndexSet<jq_Reference>();
 		reachableAllocClasses = new IndexSet<jq_Reference>();
		methodWorklist = new ArrayList<jq_Method>();
		if (handleReflection) {
			reflectClasses = new IndexSet<jq_Reference>();
			reflectVars = new HashSet<Register>();
            reflectRetMeths = new HashSet<jq_Method>();
			methToInvks = new HashMap<jq_Method, Set<Quad>>();
			ch = Program.getProgram().getClassHierarchy();
		}
        HostedVM.initialize();
        javaLangObject = PrimordialClassLoader.getJavaLangObject();
		String mainClassName = Properties.mainClassName;
		if (mainClassName == null)
            Messages.fatal("SCOPE.MAIN_CLASS_NOT_DEFINED");
       	jq_Class mainClass = (jq_Class) jq_Type.parseType(mainClassName);
		prepareClass(mainClass);
		jq_NameAndDesc nd = new jq_NameAndDesc("main", "([Ljava/lang/String;)V");
        jq_Method mainMethod = (jq_Method) mainClass.getDeclaredMember(nd);
		if (mainMethod == null)
			Messages.fatal("SCOPE.MAIN_METHOD_NOT_FOUND", mainClassName);
		for (int i = 0; repeat; i++) {
			System.out.println("Iteration: " + i);
			repeat = false;
         	classesVisitedForClinit.clear();
        	methods.clear();
			visitClinits(mainClass);
        	visitMethod(mainMethod);
	        while (!methodWorklist.isEmpty()) {
	        	jq_Method m = methodWorklist.remove(methodWorklist.size() - 1);
				if (DEBUG) System.out.println("Processing CFG of method: " + m);
	        	processMethod(m);
	        }
        }
		if (DEBUG && handleReflection) {
			System.out.println("Reflectively instantiated classes:");
			for (jq_Reference ref : reflectClasses) {
				System.out.println("\t" + ref);
			}
		}
		System.out.println("LEAVE: RTA");
		timer.done();
		System.out.println("Time: " + timer.getInclusiveTimeStr());
	}
	private void visitMethod(jq_Method m) {
		if (methods.add(m)) {
			if (DEBUG) System.out.println("\tAdding method: " + m);
			if (!m.isAbstract()) 
				methodWorklist.add(m);
		}
	}
	private void processMethod(jq_Method m) {
		ControlFlowGraph cfg = m.getCFG();
		for (ListIterator.BasicBlock it = cfg.reversePostOrderIterator(); it.hasNext();) {
			BasicBlock bb = it.nextBasicBlock();
			for (ListIterator.Quad it2 = bb.iterator(); it2.hasNext();) {
				Quad q = it2.nextQuad();
				if (DEBUG) System.out.println("Quad: " + q);
				Operator op = q.getOperator();
				if (op instanceof Invoke) {
					if (op instanceof InvokeVirtual || op instanceof InvokeInterface)
						processVirtualInvk(q);
					else
						processStaticInvk(q);
				} else if (op instanceof Getstatic) {
					jq_Field f = Getstatic.getField(q).getField();
					jq_Class c = f.getDeclaringClass();
					visitClass(c);
				} else if (op instanceof Putstatic) {
					jq_Field f = Putstatic.getField(q).getField();
					jq_Class c = f.getDeclaringClass();
					visitClass(c);
				} else if (op instanceof New) {
					jq_Class c = (jq_Class) New.getType(q).getType();
					visitClass(c);
					if (reachableAllocClasses.add(c)) {
						repeat = true;
					}
				} else if (op instanceof NewArray) {
					jq_Array a = (jq_Array) NewArray.getType(q).getType();
					visitClass(a);
					if (reachableAllocClasses.add(a)) {
						repeat = true;
					}
				} else if (handleReflection) {
					if (op instanceof Move) {
						processMove(q);
					} else if (op instanceof CheckCast) {
						processCast(q);
					} else if (op instanceof Phi) {
						processPhi(q);
					} else if (op instanceof Return) {
						processReturn(q, m);
					}
				}
			}
		}
	}
	private void processVirtualInvk(Quad q) {
		jq_Method n = Invoke.getMethod(q).getMethod();
		jq_Class c = n.getDeclaringClass();
		visitClass(c);
		visitMethod(n);
		jq_NameAndDesc nd = n.getNameAndDesc();
		if (c.isInterface()) {
			for (jq_Reference r : reachableAllocClasses) {
				if (r instanceof jq_Array)
					continue;
				jq_Class d = (jq_Class) r;
				assert (!d.isInterface());
				assert (!d.isAbstract());
				if (d.implementsInterface(c)) {
					jq_InstanceMethod m2 = d.getVirtualMethod(nd);
					if (m2 == null) {
						Messages.logAnon("WARNING: Expected instance method " + nd +
							" in class " + d + " implementing interface " + c);
					} else {
						if (handleReflection)
							propagateReflectArgsAndRet(q, m2);
						visitMethod(m2);
					}
				}
			}
		} else {
			if (handleReflection && c.getName().equals("java.lang.Class") &&
					n.getName().toString().equals("newInstance") &&
					n.getDesc().toString().equals("()Ljava/lang/Object;")) {
				RegisterOperand ro = Invoke.getDest(q);
				Register r = ro.getRegister();
				if (reflectVars.add(r)) {
					if (DEBUG) System.out.println("\tAdding var: " + r);
					repeat = true;
				}
			}
			for (jq_Reference r : reachableAllocClasses) {
				if (r instanceof jq_Array)
					continue;
				jq_Class d = (jq_Class) r;
				assert (!d.isInterface());
				assert (!d.isAbstract());
				if (d.extendsClass(c)) {
					jq_InstanceMethod m2 = d.getVirtualMethod(nd);
					if (m2 == null) {
						Messages.logAnon("WARNING: Expected instance method " + nd +
								" in class " + d + " subclassing class " + c);
					} else {
						visitMethod(m2);
						if (handleReflection)
							propagateReflectArgsAndRet(q, m2);
					}
				}
			}
		}
	}
	private void processStaticInvk(Quad q) {
		jq_Method n = Invoke.getMethod(q).getMethod();
		jq_Class c = n.getDeclaringClass();
		visitClass(c);
		visitMethod(n);
		if (handleReflection)
			propagateReflectArgsAndRet(q, n);
	}
	private void propagateReflectArgsAndRet(Quad q, jq_Method m2) {
		Set<Quad> invks = methToInvks.get(m2);
		if (invks == null) {
			invks = new ArraySet<Quad>(1);
			methToInvks.put(m2, invks);
		}
		invks.add(q);
		if (reflectRetMeths.contains(m2)) {
			RegisterOperand lo = Invoke.getDest(q);
			if (lo != null) {
				Register l = lo.getRegister();
				if (reflectVars.add(l)) {
					if (DEBUG) System.out.println("\tAdding var: " + l);
					repeat = true;
				}
			}
		}
		RegisterFactory rf = m2.getCFG().getRegisterFactory();
		ParamListOperand iArgs = Invoke.getParamList(q);
		for (int i = 0; i < iArgs.length(); ++i) {
			Register iArg = iArgs.get(i).getRegister();
			if (reflectVars.contains(iArg)) {
				Register fArg = rf.get(i);
				if (reflectVars.add(fArg)) {
					if (DEBUG) System.out.println("\tAdding var: " + fArg);
					repeat = true;
				}
			}
		}
	}
	private void processCast(Quad q) {
		jq_Type t = CheckCast.getType(q).getType();
		if (t instanceof jq_Reference) {
			Operand ro = CheckCast.getSrc(q);
			if (ro instanceof RegisterOperand) {
				Register r = ((RegisterOperand) ro).getRegister();
				if (reflectVars.contains(r)) {
					String tName = t.getName();
					jq_Reference ref = (jq_Reference) t;
					// Note: ref may not be prepared; don't call any methods on it
					Set<String> concreteImps = ch.getConcreteImplementors(tName);
					Set<String> concreteSubs = ch.getConcreteSubclasses(tName);
					assert (concreteImps == null || concreteSubs == null);
					visitClass(ref);
					processConcreteClasses(concreteImps);
					processConcreteClasses(concreteSubs);
				}
			}
		}
	}
	// set may be null
	private void processConcreteClasses(Set<String> set) {
		if (set != null) {
			for (String s : set) {
				jq_Class d = (jq_Class) jq_Type.parseType(s);
				reflectClasses.add(d);
				visitClass(d);
				if (reachableAllocClasses.add(d)) 
					repeat = true;
			}
		}
	}
	private void processMove(Quad q) {
		Operand ro = Move.getSrc(q);
		if (ro instanceof RegisterOperand) {
			Register r = ((RegisterOperand) ro).getRegister();
			if (reflectVars.contains(r)) {
				Register l = Move.getDest(q).getRegister();
				if (reflectVars.add(l)) {
					if (DEBUG) System.out.println("\tAdding var: " + l);
					repeat = true;
				}
			}
		}
	}
	private void processPhi(Quad q) {
		ParamListOperand roList = Phi.getSrcs(q);
		int n = roList.length();
		for (int i = 0; i < n; i++) {
			RegisterOperand ro = roList.get(i);
			if (ro != null) {
				Register r = ro.getRegister();
				if (reflectVars.contains(r)) {
					Register l = Phi.getDest(q).getRegister();
					if (reflectVars.add(l)) {
						if (DEBUG) System.out.println("\tAdding var: " + l);
						repeat = true;
					}
					break;
				}
			}
		}
	}
	private void processReturn(Quad q, jq_Method m) {
		Operand ro = Return.getSrc(q);
		if (ro instanceof RegisterOperand) {
			Register r = ((RegisterOperand) ro).getRegister();
			if (reflectVars.contains(r) && reflectRetMeths.add(m)) {
				if (DEBUG) System.out.println("\tAdding ret: " + q);
				repeat = true;
			}
		}
	}
	private void prepareClass(jq_Reference r) {
		if (classes.add(r)) {
	        r.prepare();
			if (DEBUG) System.out.println("\tAdding class: " + r);
			if (r instanceof jq_Array)
				return;
			jq_Class c = (jq_Class) r;
			jq_Class d = c.getSuperclass();
			if (d == null)
				assert (c == javaLangObject);
			else
				prepareClass(d);
			for (jq_Class i : c.getDeclaredInterfaces())
				prepareClass(i);
		}
	}
	private void visitClass(jq_Reference r) {
		prepareClass(r);
		if (r instanceof jq_Array)
			return;
		jq_Class c = (jq_Class) r;
		visitClinits(c);
	}
	private void visitClinits(jq_Class c) {
		if (classesVisitedForClinit.add(c)) {
			jq_ClassInitializer m = c.getClassInitializer();
			// m is null for classes without class initializer method
			if (m != null)
				visitMethod(m);
			jq_Class d = c.getSuperclass();
			if (d != null)
				visitClinits(d);
			for (jq_Class i : c.getDeclaredInterfaces())
				visitClinits(i);
		}
	}
}