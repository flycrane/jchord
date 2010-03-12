package chord.analyses.snapshot;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntProcedure;
import gnu.trove.TIntIterator;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TIntProcedure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

interface AbstractionListener {
  // Called when the abstraction is changed
  void abstractionChanged(int o, Object a);
}

interface AbstractionInitializer {
  // Called when the abstraction is changed
  void initAbstraction(Abstraction abstraction);
}

/**
 * An abstraction is a function from a node (object) to an abstract value,
 * which depends on the graph.
 *
 * @author Percy Liang (pliang@cs.berkeley.edu)
 * @author omert (omertrip@post.tau.ac.il)
 */
public abstract class Abstraction {
  public Execution X;
  public State state;
  public AbstractionListener listener;
  public TIntHashSet separateNodes = new TIntHashSet(); // (Optional): let these values have a distinct value

  // Build these as intermediate data structures
  protected TIntObjectHashMap<Object> o2a = new TIntObjectHashMap<Object>(); // object o -> abstract value a
  protected HashMap<Object,List<Integer>> a2os = new HashMap<Object,List<Integer>>(); // abstraction value a -> nodes o with that abstraction value

  // For incrementally creating the abstraction (if necessary).
  public abstract void nodeCreated(ThreadInfo info, int o);
  public abstract void nodeDeleted(int o);
  public void edgeCreated(int b, int f, int o) { }
  public void edgeDeleted(int b, int f, int o) { }
  public void init(AbstractionInitializer initializer) { initializer.initAbstraction(this); }

  // Called before we start using this abstraction in arbitrary ways, so do whatever is necessary.
  // Try to keep this function empty and incrementally update the abstraction.
  public abstract void ensureComputed();

  // Return the value of the abstraction (called after ensureComputed)
  public Object getValue(int o) { return o2a.get(o); }

  // Helpers
  protected void setValue(int o, Object a) {
    if (!state.o2edges.containsKey(o)) throw new RuntimeException(""+o);
    if (separateNodes.contains(o)) a = "-";
    Object old_a = o2a.get(o);
    if (old_a != null) { // There was an old abstraction there already
      if (old_a.equals(a)) return; // Haven't changed the abstraction
      a2os.get(old_a).remove((Integer)o);
    }
    o2a.put(o, a);
    Utils.add(a2os, a, o);
    listener.abstractionChanged(o, a);
  }
  protected void removeValue(int o) {
    Object a = o2a.remove(o);
    a2os.get(a).remove((Integer)o);
  }
}

abstract class LocalAbstraction extends Abstraction {
  public abstract Object computeValue(ThreadInfo info, int o);
}

////////////////////////////////////////////////////////////

class NoneAbstraction extends Abstraction {
  @Override public String toString() { return "none"; }
  @Override public void nodeCreated(ThreadInfo info, int o) { }
  @Override public void nodeDeleted(int o) { }
  @Override public void ensureComputed() { }
  @Override public Object getValue(int o) { return o; }
}

class AllocAbstraction extends LocalAbstraction {
  int kCFA, kOS;

  public AllocAbstraction(int kCFA, int kOS) {
    this.kCFA = kCFA;
    this.kOS = kOS;
  }

  @Override public String toString() {
    if (kCFA == 0 && kOS == 0) return "alloc";
    return String.format("alloc(kCFA=%d,kOS=%d)", kCFA, kOS);
  }

  @Override public void nodeCreated(ThreadInfo info, int o) {
    setValue(o, computeValue(info, o));
  }
  @Override public void nodeDeleted(int o) {
    removeValue(o);
  }

  @Override public void ensureComputed() { }

  public Object computeValue(ThreadInfo info, int o) {
    if (kCFA == 0 && kOS == 0) return state.o2h.get(o); // No context

    StringBuilder buf = new StringBuilder();
    buf.append(state.o2h.get(o));

    if (kCFA > 0) {
      for (int i = 0; i < kCFA; i++) {
        int j = info.callSites.size() - i - 1;
        if (j < 0) break;
        buf.append('_');
        buf.append(info.callSites.get(j));
      }
    }

    /*if (kOS > 0) {
      for (int i = 0; i < kCFA; i++) {
        int j = info.callAllocs.size() - i - 1;
        if (j < 0) break;
        buf.append('_');
        buf.append(info.callAllocs.get(j));
      }
    }*/

    return buf.toString();
  }
}

abstract class LabelBasedAbstraction extends Abstraction {
	protected static interface Label {
	}
	
	private class Procedure implements TIntIntProcedure {
		private final TIntHashSet worklist;
		private final TIntHashSet visited;
		private final Set<Label> labels;
		private final boolean isPos;

		public Procedure(TIntHashSet worklist, TIntHashSet visited, Set<Label> labels, boolean isPos) {
			this.worklist = worklist;
			this.visited = visited;
			this.labels = labels;
			this.isPos = isPos;
		}
		
		@Override
		public boolean execute(int arg0, int arg1) {
			if (arg1 != 0) {
				for (Label label : labels) {
					if (isPos) 
						posLabel(arg1, label);
					else 
						negLabel(arg1, label);
					if (!visited.contains(arg1)) {
						worklist.add(arg1);
					}
				}
			}
			return true;
		}
	}
	
//	private final static int ARRAY_CONTENT = Integer.MIN_VALUE;
	
	protected final TIntObjectHashMap<TIntIntHashMap> heapGraph = new TIntObjectHashMap<TIntIntHashMap>();
	protected final TIntObjectHashMap<Set<Label>> object2labels = new TIntObjectHashMap<Set<Label>>();

	protected abstract TIntHashSet getRoots(Label l);

	@Override
	public void edgeCreated(int b, int f, int o) {
		if (b != 0 && f >= 0) {
			updateHeapGraph(b, f, o);
		}
	}
	
	@Override
	public void edgeDeleted(int b, int f, int o) {
		if (b != 0 && f >= 0) {
			updateHeapGraph(b, f, 0);
		}
	}
	
	private Set<Label> getLabels(int b) {
		return object2labels.get(b);
	}
	
	private void posLabel(int o, Label l) {
		Set<Label> S = object2labels.get(o);
		boolean hasChanged = false;
		if (S == null) {
			object2labels.put(o, S = new HashSet<Label>(1));
			hasChanged = true;
		}
		hasChanged |= S.add(l);
		if (hasChanged) {
			setValue(o, S);
		}
	}
	
	private void negLabel(int o, Label l) {
		Set<Label> S = object2labels.get(o);
		boolean hasChanged = false;
		if (S != null) {
			hasChanged |= S.remove(l);
		}
		if (hasChanged) {
			setValue(o, S);
		}
	}
	
	private void updateHeapGraph(int b, int f, int o) {
		TIntIntHashMap M = heapGraph.get(b);
		if (M == null) {
			heapGraph.put(b, M = new TIntIntHashMap());
		}
		M.put(f, o);
		Set<Label> labels = collectLabels(b, f, o);
		if (labels != null) {
			if (o == 0) {
				propagateLabels(o, labels, false);
				for (Label l : labels) {
					TIntHashSet roots = getRoots(l);
					for (TIntIterator it=roots.iterator(); it.hasNext(); ) {
						int next = it.next();
						assert (object2labels.get(next).contains(l)); // The root should be associated with the label supposedly originating from it.
						propagateLabels(next, Collections.<Label> singleton(l), true);
					}
				}
			} else {
				propagateLabels(o, labels, true);
			}
		}
	}

	private Set<Label> collectLabels(int b, int f, int o) {
		Set<Label> labels = getLabels(b);
		Collection<Label> L = freshLabels(b, f, o);
		if (!L.isEmpty()) {
			if (labels == null) {
				labels = new HashSet<Label>(L.size());
			}
			labels.addAll(L);
		}
		return labels;
	}
	
	protected Collection<Label> freshLabels(int b, int f, int o) {
		return Collections.emptySet();
	}

	private void propagateLabels(int o, Set<Label> labels, boolean isPos) {
		TIntHashSet worklist = new TIntHashSet();
		TIntHashSet visited = new TIntHashSet();
		for (Label l : labels) {
			if (isPos)
				posLabel(o, l);
			else
				negLabel(o, l);
		}
		worklist.add(o);
		while (!worklist.isEmpty()) {
			TIntIterator it = worklist.iterator();
			worklist = new TIntHashSet();
			Procedure proc = new Procedure(worklist, visited, labels, isPos);
			while (it.hasNext()) {
				final int next = it.next();
				visited.add(next);
				TIntIntHashMap M = heapGraph.get(next);
				if (M != null) {
					M.forEachEntry(proc);
				}
			}
		}
	}
}

class ReachableFromAllocPlusFieldsAbstraction extends LabelBasedAbstraction {
	
	private static class AllocPlusFieldLabel implements Label {
		private static final int SELF = Integer.MIN_VALUE;
		public final int h;
		public final int f;
		
		public AllocPlusFieldLabel(int h, int f) {
			this.h = h;
			this.f = f;
		}
		
		public AllocPlusFieldLabel(int h) {
			this(h, SELF);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + f;
			result = prime * result + h;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			AllocPlusFieldLabel other = (AllocPlusFieldLabel) obj;
			if (f != other.f)
				return false;
			if (h != other.h)
				return false;
			return true;
		}
	}
	
	private final TIntObjectHashMap<TIntHashSet> alloc2objects = new TIntObjectHashMap<TIntHashSet>();
	
	@Override
	public String toString() {
		return "alloc-x-field-reachability";
	}
	
	@Override
	protected TIntHashSet getRoots(Label l) {
		assert (l instanceof AllocPlusFieldLabel);
		AllocPlusFieldLabel apfl = (AllocPlusFieldLabel) l;
		if (apfl.f == AllocPlusFieldLabel.SELF) {
			return alloc2objects.get(apfl.h);
		} else {
			TIntHashSet alloced = alloc2objects.get(apfl.h);
			TIntHashSet result = new TIntHashSet();
			for (TIntIterator it=alloced.iterator(); it.hasNext(); ) {
				int next = it.next();
				TIntIntHashMap M = heapGraph.get(next);
				assert (M != null);
				if (M.containsKey(apfl.f)) {
					int o = M.get(apfl.f);
					if (o != 0) {
						result.add(o);
					}
				}
			}
			return result;
		}
	}

	@Override
	protected Collection<Label> freshLabels(int b, int f, int o) {
		int h = state.o2h.get(b);
		return Collections.<Label> singleton(new AllocPlusFieldLabel(h, f));
	}
	
	@Override
	public void ensureComputed() {
		// This is a no-op.
	}

	@Override
	public void nodeCreated(ThreadInfo info, int o) {
		int h = state.o2h.get(o);
		if (o != 0 /*&& h >= 0*/) {
			Set<Label> S = new HashSet<Label>(1);
			S.add(new AllocPlusFieldLabel(h));
			object2labels.put(o, S);
			setValue(o, S);
			TIntHashSet T = alloc2objects.get(h);
			if (T == null) {
				T = new TIntHashSet();
				alloc2objects.put(h, T);
			}
			T.add(o);
		}
	}

	@Override
	public void nodeDeleted(int o) {
		throw new RuntimeException("Operation 'nodeDeleted' not currently supported.");
	}
}

class ReachableFromAllocAbstraction extends LabelBasedAbstraction {

	private static class AllocationSiteLabel implements Label {
		public final int h;
		
		public AllocationSiteLabel(int h) {
			this.h = h;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + h;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			AllocationSiteLabel other = (AllocationSiteLabel) obj;
			if (h != other.h)
				return false;
			return true;
		}
	}
	
	private final TIntObjectHashMap<TIntHashSet> alloc2objects = new TIntObjectHashMap<TIntHashSet>();

	@Override
	public String toString() {
		return "alloc-reachability";
	}
	
	@Override
	protected TIntHashSet getRoots(Label l) {
		assert (l instanceof AllocationSiteLabel);
		AllocationSiteLabel allocLabel = (AllocationSiteLabel) l;
		return alloc2objects.get(allocLabel.h);		
	}

	@Override
	public void ensureComputed() {
		// This is a no-op.
	}

	@Override
	public void nodeDeleted(int o) {
		throw new RuntimeException("Operation 'nodeDeleted' not currently supported.");
	}
	
	@Override
	public void nodeCreated(ThreadInfo info, int o) {
		int h = state.o2h.get(o);
		if (o != 0 /*&& h >= 0*/) {
			Set<Label> S = new HashSet<Label>(1);
			S.add(new AllocationSiteLabel(h));
			object2labels.put(o, S);
			setValue(o, S);
			TIntHashSet T = alloc2objects.get(h);
			if (T == null) {
				T = new TIntHashSet();
				alloc2objects.put(h, T);
			}
			T.add(o);
		}
	}
}

// SLOW: don't use this; use Recency2Abstraction instead
/*class RecencyAbstraction extends Abstraction {
  TIntIntHashMap h2count = new TIntIntHashMap(); // heap allocation site h -> number of objects that have been allocated at h
  TIntIntHashMap o2count = new TIntIntHashMap(); // object -> count

  public String toString() { return "recency"; }

  @Override public void nodeCreated(ThreadInfo info, int o) {
    int h = state.o2h.get(o);
    h2count.adjustOrPutValue(h, 1, 1);
    o2count.put(o, h2count.get(h));
    //X.logs("nodeCreated: o=%s, h=%s; count = %s", o, h, o2count.get(o));
  }
  @Override public void nodeDeleted(int o) {
    o2count.remove(o);
  }

  @Override public void ensureComputed() {
    //X.logs("RecencyAbstraction.ensureComputed: %s %s", state.o2h.size(), o2count.size());

    state.o2h.forEachKey(new TIntProcedure() { public boolean execute(int o) { 
      setValue(o, computeValue(o));
      return true;
    } });
  }

  private Object computeValue(int o) {
    int h = state.o2h.get(o);
    boolean mostRecent = o2count.get(o) == h2count.get(h);
    return mostRecent ? h+"R" : h;
  }
}*/

class RecencyAbstraction extends Abstraction {
  TObjectIntHashMap<Object> val2lasto = new TObjectIntHashMap<Object>(); // preliminary value -> latest object
  LocalAbstraction abstraction;

  public RecencyAbstraction(LocalAbstraction abstraction) {
    this.abstraction = abstraction;
  }

  @Override public void init(AbstractionInitializer initializer) {
    super.init(initializer);
    abstraction.init(initializer);
  }

  @Override public String toString() { return "recency("+abstraction+")"; }
  @Override public void ensureComputed() { }

  @Override public void nodeCreated(ThreadInfo info, int o) {
    Object val = abstraction.computeValue(info, o);
    if (val2lasto.containsKey(val)) {
      int old_o = val2lasto.get(val);
      setValue(old_o, val+"~"); // Previously new object (old_o) now gets old version of the the value
    }
    val2lasto.put(val, o);
    setValue(o, val); // Most recent
  }
  @Override public void nodeDeleted(int o) { }
}

// SLOW
class ReachabilityAbstraction extends Abstraction {
  static class Spec {
    public boolean pointedTo, matchRepeatedFields, matchFirstField, matchLastField;
  }
  Spec spec;
  TIntObjectHashMap<List<String>> o2pats = new TIntObjectHashMap<List<String>>(); // o -> list of path patterns that describe o

  public ReachabilityAbstraction(Spec spec) {
    this.spec = spec;
  }

  @Override public String toString() {
    if (spec.pointedTo) return "reach(point)";
    if (spec.matchRepeatedFields) return "reach(f*)";
    if (spec.matchFirstField) return "reach(first_f)";
    if (spec.matchLastField) return "reach(last_f)";
    return "reach";
  }

  @Override public void nodeCreated(ThreadInfo info, int o) { }
  @Override public void nodeDeleted(int o) { }

  @Override public void ensureComputed() {
    o2pats.clear();
    state.o2h.forEachKey(new TIntProcedure() { public boolean execute(int o) {
      o2pats.put(o, new ArrayList<String>());
      return true;
    } });

    // For each node, compute reachability from sources
    state.o2h.forEachKey(new TIntProcedure() { public boolean execute(int o) {
      String source = "H"+state.o2h.get(o);
      //X.logs("--- source=%s, a=%s", source, astr(a));
      search(source, -1, -1, 0, o);
      return true;
    } });

    // Compute the values
    state.o2h.forEachKey(new TIntProcedure() { public boolean execute(int o) { 
      setValue(o, computeValue(o));
      return true;
    } });
  }

  // Source: variable or heap-allocation site.
  // General recipe for predicates is some function of the list of fields from source to the node.
  // Examples:
  //   Reachable-from: path must exist.
  //   Pointed-to-by: length must be one.
  //   Reachable-following-a-single field
  //   Reachable-from-with-first-field
  //   Reachable-from-with-last-field

  private void search(String source, int first_f, int last_f, int len, int o) {
    String pat = null;
    /*if (len > 0)*/ { // Avoid trivial predicates
      if (spec.pointedTo) {
        if (len == 1) pat = source;
      }
      else if (spec.matchRepeatedFields) {
        assert (first_f == last_f);
        pat = source+"."+first_f+"*";
      } 
      else if (spec.matchFirstField)
        pat = source+"."+first_f+".*";
      else if (spec.matchLastField)
        pat = source+".*."+last_f;
      else // Plain reachability
        pat = source;
    }

    List<String> pats = o2pats.get(o);

    if (pat != null && pats.indexOf(pat) != -1) return; // Already have it

    if (pat != null) pats.add(pat);
    //X.logs("source=%s first_f=%s last_f=%s len=%s a=%s: v=%s", source, fstr(first_f), fstr(last_f), len, astr(a), a2ws[a]);

    if (spec.pointedTo && len >= 1) return;

    // Recurse
    List<Edge> edges = state.o2edges.get(o);
    if (edges == null) {
      X.errors("o=%s returned no edges (shouldn't happen!)", o);
    }
    else {
      for (Edge e : edges) {
        if (spec.matchRepeatedFields && first_f != -1 && first_f != e.f) continue; // Must have same field
        search(source, len == 0 ? e.f : first_f, e.f, len+1, e.o);
      }
    }
  }

  private Object computeValue(int o) {
    List<String> pats = o2pats.get(o);
    Collections.sort(pats); // Canonicalize
    return pats.toString();
  }
}
