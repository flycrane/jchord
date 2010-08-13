/*
 * Copyright (c) 2008-2010, Intel Corporation.
 * Copyright (c) 2006-2007, The Trustees of Stanford University.
 * All rights reserved.
 */
package chord.program;

import java.util.Iterator;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Arrays;

import java.io.PrintWriter;
import java.io.IOException;

import com.java2html.Java2HTML;

import chord.util.tuple.object.Pair;
import chord.project.Project;
import chord.project.OutDirUtils;
import chord.project.Messages;
import chord.project.Properties;
import chord.util.ArraySet;
import chord.util.IndexSet;
import chord.util.FileUtils;
import chord.util.ChordRuntimeException;
 
import joeq.Class.jq_Reference.jq_NullType;
import joeq.Compiler.Quad.BytecodeToQuad.jq_ReturnAddressType;
import joeq.Util.Templates.ListIterator;
import joeq.Main.HostedVM;
import joeq.UTF.Utf8;
import joeq.Class.jq_Type;
import joeq.Class.jq_Class;
import joeq.Class.jq_Array;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.PrimordialClassLoader;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.CheckCast;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.AStore;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Monitor;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Main.Helper;

/**
 * Representation of a Java program.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public abstract class Program {
	private IndexSet<jq_Method> methods;
	private ReflectInfo reflectInfo;
	private IndexSet<jq_Type> types;
	private IndexSet<jq_Reference> classes;
	private Map<String, jq_Type> nameToTypeMap;
	private Map<String, jq_Reference> nameToClassMap;
	private Map<jq_Class, List<jq_Method>> classToMethodsMap;
	private jq_Method mainMethod;
	private boolean HTMLizedJavaSrcFiles;
	private final boolean reuseScope;
    private ClassHierarchy ch;
	private static Program program;
	protected Program() {
		if (Properties.verbose)
			jq_Method.setVerbose();
		if (Properties.doSSA)
			jq_Method.doSSA();
		jq_Method.exclude(Properties.scopeExcludeAry);
		reuseScope = Properties.reuseScope;
	}
	public final static Program getProgram() {
		if (program == null) {
			String scopeKind = Properties.scopeKind;
			if (scopeKind.equals("rta")) {
				program = new RTAProgram(
					Properties.handleForNameReflection,
					Properties.handleNewInstReflection);
			} else if (scopeKind.equals("dynamic")) {
				program = new DynamicProgram();
			} else if (scopeKind.equals("cha")) {
				program = new CHAProgram();
			} else {
				Messages.fatal("SCOPE.INVALID_SCOPE_KIND", scopeKind);
				program = null;
			}
		}
		return program;
	}
    /**
     * Provides the class hierarchy.
     */
    public final ClassHierarchy getClassHierarchy() {
        if (ch == null)
            ch = new ClassHierarchy();
        return ch;
    }
    /**
     * Provides all methods deemed reachable.
     */
    public final IndexSet<jq_Method> getMethods() {
		if (methods == null) {
			String methodsFileName = Properties.methodsFileName;
			File methodsFile = new File(methodsFileName);
			if (reuseScope && methodsFile.exists()) {
				loadMethodsFile(methodsFile);
			} else {
				methods = computeMethods();
				saveMethodsFile(methodsFile);
			}
		}
		return methods;
	}
	/**
	 * Provides resolved reflection information.
	 */
	public final ReflectInfo getReflectInfo() {
		if (reflectInfo == null) {
			String reflectFileName = Properties.reflectFileName;
			File reflectFile = new File(reflectFileName);
			if (reuseScope && reflectFile.exists()) {
				loadReflectFile(reflectFile);
			} else {
				reflectInfo = computeReflectInfo();
				saveReflectFile(reflectFile);
			}
		}
		return reflectInfo;
	}
	/**
	 * Computes reachable methods.
	 * Subclasses must override.
	 */
	protected IndexSet<jq_Method> computeMethods() {
		throw new ChordRuntimeException();
	}
	/**
	 * Resolves reflection.
	 * Subclasses must override.
	 */
	protected ReflectInfo computeReflectInfo() {
		throw new ChordRuntimeException();
	}
    public final IndexSet<jq_Reference> getClasses() {
        if (classes == null)
            computeClassesAndTypes();
        return classes;
    }
    public final IndexSet<jq_Type> getTypes() {
        if (types == null)
            computeClassesAndTypes();
        return types;
    }
    private void computeClassesAndTypes() {
		getMethods();
        PrimordialClassLoader loader = PrimordialClassLoader.loader;
        jq_Type[] typesAry = loader.getAllTypes();
        int numTypes = loader.getNumTypes();
        Arrays.sort(typesAry, 0, numTypes, comparator);
        types = new IndexSet<jq_Type>(numTypes + 2);
        classes = new IndexSet<jq_Reference>();
        types.add(jq_NullType.NULL_TYPE);
        types.add(jq_ReturnAddressType.INSTANCE);
        for (int i = 0; i < numTypes; i++) {
            jq_Type t = typesAry[i];
            assert (t != null);
            types.add(t);
            if (t instanceof jq_Reference && t.isPrepared()) {
                jq_Reference r = (jq_Reference) t;
                classes.add(r);
            }
        }
    }
	private void loadMethodsFile(File file) {
		List<String> l = FileUtils.readFileToList(file);
		Set<String> excludedClasses = new HashSet<String>();
		methods = new IndexSet<jq_Method>(l.size());
		HostedVM.initialize();
		for (String s : l) {
			MethodSign sign = MethodSign.parse(s);
			String cName = sign.cName;
			if (!excludedClasses.contains(cName)) {
				jq_Class c = (jq_Class) loadClass(cName);
				if (c != null) {
					String mName = sign.mName;
					String mDesc = sign.mDesc;
					jq_Method m =
						(jq_Method) c.getDeclaredMember(mName, mDesc);
					assert (m != null);
					if (!m.isAbstract())
						m.getCFG();
					methods.add(m);
				} else
					excludedClasses.add(cName);
			}
		}
	}
	private void saveMethodsFile(File file) {
		try {
			PrintWriter out = new PrintWriter(file);
			for (jq_Method m : methods)
				out.println(m);
			out.close();
		} catch (IOException ex) {
			throw new ChordRuntimeException(ex);
		}
	}

	private void loadReflectFile(File file) {
		List<String> l = FileUtils.readFileToList(file);
		int n = l.size();
		assert (n >= 3);
		int i = 0;
		assert (l.get(i++).equals("# reflectClasses"));
		Set<jq_Reference> reflectClasses = new ArraySet<jq_Reference>();
		for (; true; i++) {
			String s = l.get(i);
			if (s.startsWith("#"))
				break;
			jq_Reference r = loadClass(s);
			if (r != null)
				reflectClasses.add(r);
		}
		assert (l.get(i++).equals("# resolvedForNameSites"));
		Set<Pair<Quad, Set<jq_Reference>>> resolvedForNameSites =
			new ArraySet<Pair<Quad, Set<jq_Reference>>>();
		for (; true; i++) {
			String s = l.get(i);
			if (s.startsWith("#"))
				break;
			Pair<Quad, Set<jq_Reference>> site = strToSite(s);
			resolvedForNameSites.add(site);
		}
		assert (l.get(i++).equals("# resolvedNewInstSites"));
		Set<Pair<Quad, Set<jq_Reference>>> resolvedNewInstSites =
			new ArraySet<Pair<Quad, Set<jq_Reference>>>();
		for (; i < n; i++) {
			String s = l.get(i);
			Pair<Quad, Set<jq_Reference>> site = strToSite(s);
			resolvedNewInstSites.add(site);
		}
		reflectInfo = new ReflectInfo(reflectClasses, resolvedForNameSites,
			resolvedNewInstSites);
	}
	private Pair<Quad, Set<jq_Reference>> strToSite(String s) {
		String[] a = s.split("->");
		assert (a.length == 2);
		MethodElem e = MethodElem.parse(a[0]);
		Quad q = getQuad(e, Invoke.class);
		assert (q != null);
		String[] rNames = a[1].split(",");
		Set<jq_Reference> rTypes = new ArraySet<jq_Reference>(rNames.length);
		for (String rName : rNames) {
			jq_Reference r = loadClass(rName);
			if (r != null)
				rTypes.add(r);
		}
		return new Pair<Quad, Set<jq_Reference>>(q, rTypes);
	}
	private String siteToStr(Pair<Quad, Set<jq_Reference>> p) {
		Set<jq_Reference> l = p.val1;
		assert (l != null);
		int n = l.size();
		Iterator<jq_Reference> it = l.iterator();
 		assert (n > 0);
		String s = p.val0.toByteLocStr() + "->" + it.next();
		for (int i = 1; i < n; i++)
			s += "," + it.next();
		return s;
	}
	private void saveReflectFile(File file) {
		try {
            PrintWriter out = new PrintWriter(file);
			out.println("# reflectClasses");
			for (jq_Reference r : reflectInfo.getReflectClasses())
           	    out.println(r);
			out.println("# resolvedForNameSites");
			for (Pair<Quad, Set<jq_Reference>> p : reflectInfo.getResolvedForNameSites()) {
				String s = siteToStr(p);
				out.println(s);
			}
			out.println("# resolvedNewInstSites");
			for (Pair<Quad, Set<jq_Reference>> p : reflectInfo.getResolvedNewInstSites()) {
				String s = siteToStr(p);
				out.println(s);
			}
			out.close();
		} catch (IOException ex) {
			throw new ChordRuntimeException(ex);
		}
	}

	public static jq_Reference loadClass(String s) {
		if (Properties.verbose)
			Messages.log("SCOPE.LOADING_CLASS", s);
		try {
			jq_Reference c = (jq_Reference) jq_Type.parseType(s);
			c.prepare();
			return c;
		} catch (Throwable ex) {
			Messages.log("SCOPE.EXCLUDING_CLASS", s);
			ex.printStackTrace();
			return null;
		}
	}
	private void buildNameToTypeMap() {
		assert (nameToTypeMap == null);
		IndexSet<jq_Type> types = getTypes();
		nameToTypeMap = new HashMap<String, jq_Type>();
		for (jq_Type t : types) {
			nameToTypeMap.put(t.getName(), t);
		}
	}

	private void buildNameToClassMap() {
		assert (nameToClassMap == null);
		IndexSet<jq_Reference> cList = getClasses();
		nameToClassMap = new HashMap<String, jq_Reference>();
		for (jq_Reference c : cList) {
			nameToClassMap.put(c.getName(), c);
		}
	}

	private void buildClassToMethodsMap() {
		assert (classToMethodsMap == null);
		classToMethodsMap = new HashMap<jq_Class, List<jq_Method>>();
		IndexSet<jq_Method> mList = getMethods();
		for (jq_Method m : mList) {
			jq_Class c = m.getDeclaringClass();
			List<jq_Method> mList2 = classToMethodsMap.get(c);
			if (mList2 == null) {
				mList2 = new ArrayList<jq_Method>();
				classToMethodsMap.put(c, mList2);
			}
			mList2.add(m);
		}
	}

	public jq_Reference getClass(String name) {
		if (nameToClassMap == null)
			buildNameToClassMap();
		return nameToClassMap.get(name);
	}

	public List<jq_Method> getMethods(jq_Class c) {
		if (classToMethodsMap == null)
			buildClassToMethodsMap();
		List<jq_Method> mList = classToMethodsMap.get(c);
		if (mList == null)
			return Collections.emptyList();
		return mList;
	}

	public jq_Method getMethod(String mName, String mDesc, String cName) {
		jq_Reference r = getClass(cName);
		if (r == null || r instanceof jq_Array)
			return null;
		jq_Class c = (jq_Class) r;
		List<jq_Method> mList = getMethods(c);
		for (jq_Method m : mList) {
			if (m.getName().toString().equals(mName) &&
				m.getDesc().toString().equals(mDesc))
				return m;
		}
		return null;
	}
	
	public jq_Method getMethod(MethodSign sign) {
		return getMethod(sign.mName, sign.mDesc, sign.cName);
	}

	public jq_Method getMethod(String sign) {
		return getMethod(MethodSign.parse(sign));
	}

	public jq_Method getMainMethod() {
		if (mainMethod == null) {
			String mainClassName = Properties.mainClassName;
			if (mainClassName == null)
				Messages.fatal("SCOPE.MAIN_CLASS_NOT_DEFINED");
			mainMethod = getMethod("main", "([Ljava/lang/String;)V", mainClassName);
			if (mainMethod == null)
				Messages.fatal("SCOPE.MAIN_METHOD_NOT_FOUND", mainClassName);
		}
		return mainMethod;
	}

	public jq_Method getThreadStartMethod() {
		return getMethod("start", "()V", "java.lang.Thread");
	}

	public jq_Type getType(String name) {
		if (nameToTypeMap == null)
			buildNameToTypeMap();
		return nameToTypeMap.get(name);
	}

	public Quad getQuad(MethodElem e, Class quadOpClass) {
		int offset = e.offset;
		jq_Method m = getMethod(e.mName, e.mDesc, e.cName);
		assert (m != null);
		return m.getQuad(offset, quadOpClass);
	}

	public static String getSign(jq_Method m) {
		String d = m.getDesc().toString();
		return m.getName().toString() + methodDescToStr(d);
	}
	
	// convert the given method descriptor string to a string
	// denoting the comma-separated list of types of the method's
	// arguments in human-readable form
	// e.g.: convert <tt>([Ljava/lang/String;I)V<tt> to
	// <tt>(java.lang.String[],int)</tt>
	public static String methodDescToStr(String desc) {
		String t = desc.substring(1, desc.indexOf(')'));
		return "(" + typesToStr(t) + ")";
	}
	
	// convert the given bytecode string encoding a (possibly empty)
	// list of types to a string denoting the comma-separated list
	// of those types in human-readable form
	// e.g. convert <tt>[Ljava/lang/String;I</tt> to
	// <tt>java.lang.String[],int</tt>
	public static String typesToStr(String typesStr) {
    	String result = "";
    	boolean needsSep = false;
        while (typesStr.length() != 0) {
            boolean isArray = false;
            int numDim = 0;
            String baseType;
            // Handle array case
            while(typesStr.startsWith("[")) {
            	isArray = true;
            	numDim++;
            	typesStr = typesStr.substring(1);
            }
            // Determine base type
            if (typesStr.startsWith("B")) {
            	baseType = "byte";
            	typesStr = typesStr.substring(1);
            } else if (typesStr.startsWith("C")) {
            	baseType = "char";
            	typesStr = typesStr.substring(1);
            } else if (typesStr.startsWith("D")) {
            	baseType = "double";
            	typesStr = typesStr.substring(1);
            } else if (typesStr.startsWith("F")) {
            	baseType = "float";
            	typesStr = typesStr.substring(1);
            } else if (typesStr.startsWith("I")) {
            	baseType = "int";
            	typesStr = typesStr.substring(1);
            } else if (typesStr.startsWith("J")) {
            	baseType = "long";
            	typesStr = typesStr.substring(1);
            } else if (typesStr.startsWith("L")) {
            	int index = typesStr.indexOf(';');
            	if(index == -1)
            		throw new RuntimeException("Class reference has no ending ;");
            	String className = typesStr.substring(1, index);
            	baseType = className.replace('/', '.');
            	typesStr = typesStr.substring(index + 1);
            } else if (typesStr.startsWith("S")) {
            	baseType = "short";
            	typesStr = typesStr.substring(1);
            } else if (typesStr.startsWith("Z")) {
            	baseType = "boolean";
            	typesStr = typesStr.substring(1);
            } else if (typesStr.startsWith("V")) {
            	baseType = "void";
            	typesStr = typesStr.substring(1);
            } else
            	throw new RuntimeException("Unknown field type!");
            if (needsSep)
            	result += ",";
            result += baseType;
            if (isArray) {
            	for (int i = 0; i < numDim; i++)
            		result += "[]";
            }
            needsSep = true;
        }
        return result;
	}

	public static List<String> getDynamicallyLoadedClasses() {
		String mainClassName = Properties.mainClassName;
		if (mainClassName == null)
			Messages.fatal("SCOPE.MAIN_CLASS_NOT_DEFINED");
		String classPathName = Properties.classPathName;
		if (classPathName == null)
			Messages.fatal("SCOPE.CLASS_PATH_NOT_DEFINED");
        String[] runIDs = Properties.runIDs.split(Properties.LIST_SEPARATOR);
		assert(runIDs.length > 0);
        final String cmd = "java " + Properties.runtimeJvmargs +
            " -cp " + classPathName +
            " -agentpath:" + Properties.instrAgentFileName +
            "=classes_file_name=" + Properties.classesFileName +
            " " + mainClassName + " ";
		List<String> classNames = new ArrayList<String>();
		String fileName = Properties.classesFileName;
        for (String runID : runIDs) {
            String args = System.getProperty("chord.args." + runID, "");
			OutDirUtils.executeWithFailOnError(cmd + args);
			try {
				BufferedReader in = new BufferedReader(new FileReader(fileName));
				String s;
				while ((s = in.readLine()) != null) {
					// convert "Lsun/misc/Signal;" to "sun.misc.Signal"
					String cName = typesToStr(s);
					classNames.add(cName);
				}
				in.close();
			} catch (Exception ex) {
				throw new ChordRuntimeException(ex);
			}
		}
		return classNames;
	}

	/**
	 * Dumps this program's Java source files in HTML form.
	 */
	public void HTMLizeJavaSrcFiles() {
		if (!HTMLizedJavaSrcFiles) {
			String srcPathName = Properties.srcPathName;
			if (srcPathName == null)
				Messages.fatal("SCOPE.SRC_PATH_NOT_DEFINED");
			String[] srcDirNames = srcPathName.split(File.pathSeparator);
			try {
				Java2HTML java2HTML = new Java2HTML();
				java2HTML.setMarginSize(4);
				java2HTML.setTabSize(4);
				java2HTML.setJavaDirectorySource(srcDirNames);
				java2HTML.setDestination(Properties.outDirName);
				java2HTML.buildJava2HTML();
			} catch (Exception ex) {
				throw new ChordRuntimeException(ex);
			}
			HTMLizedJavaSrcFiles = true;
		}
	}
	
	/**************************************************************
	 * Functions for printing methods and classes
	 **************************************************************/

	public void printMethod(String sign) {
		jq_Method m = getMethod(sign);
		if (m == null)
			Messages.fatal("SCOPE.METHOD_NOT_FOUND", sign);
		printMethod(m);
	}
	public void printClass(String className) {
		jq_Reference c = getClass(className);
		if (c == null)
			Messages.fatal("SCOPE.CLASS_NOT_FOUND", className);
		printClass(c);
	}
	private void printClass(jq_Reference r) {
		System.out.println("*** Class: " + r);
		if (r instanceof jq_Array)
			return;
		jq_Class c = (jq_Class) r;
		for (jq_Method m : getMethods(c))
			printMethod(m);
	}
	private void printMethod(jq_Method m) {
		System.out.println("Method: " + m);
		if (!m.isAbstract()) {
			ControlFlowGraph cfg = m.getCFG();
			for (ListIterator.BasicBlock it = cfg.reversePostOrderIterator();
					it.hasNext();) {
				BasicBlock bb = it.nextBasicBlock();
				for (ListIterator.Quad it2 = bb.iterator(); it2.hasNext();) {
					Quad q = it2.nextQuad();                        
					int bci = q.getBCI();
					System.out.println("\t" + bci + "#" + q.getID());
				}
			}
			System.out.println(cfg.fullDump());
		}
	}
	public void printAllClasses() {
		for (jq_Reference c : getClasses())
			printClass(c);
	}
    private static Comparator comparator = new Comparator() {
        public int compare(Object o1, Object o2) {
            jq_Type t1 = (jq_Type) o1;
            jq_Type t2 = (jq_Type) o2;
            String s1 = t1.getName();
            String s2 = t2.getName();
            return s1.compareTo(s2);
        }
    };
}