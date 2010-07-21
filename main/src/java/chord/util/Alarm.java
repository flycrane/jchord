/*
 * Copyright (c) 2008-2010, Intel Corporation.
 * Copyright (c) 2006-2007, The Trustees of Stanford University.
 * All rights reserved.
 * Licensed under the terms of the New BSD License.
 */
package chord.util;

/**
 * Implementation of an alarm.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class Alarm {
	private final Object lock = new Object();
	private final long sleepTime;
	private volatile boolean passTimedOut;
	private volatile boolean doneAllPasses;
	private volatile boolean donePass;
	private Thread alarmThread = new Thread() {
		public void run() {
			while (true) {
				if (doneAllPasses)
					break;
				passTimedOut = false;
				synchronized (lock) {
					donePass = false;
					lock.notify();
					try {
						lock.wait(sleepTime);
					} catch (InterruptedException ex) {
						throw new RuntimeException(ex);
					}
				}
				passTimedOut = true;
				synchronized (lock) {
					while (!donePass) {
						try {
							lock.wait();
						} catch (InterruptedException ex) {
							throw new RuntimeException(ex);
						}
					}
				}
			}
		}
	};
	public Alarm(long sleepTime) {
		this.sleepTime = sleepTime;
	}
	public boolean passTimedOut() {
		return passTimedOut;
	}
	public void initAllPasses() {
		alarmThread.start();
	}
	public void initNewPass() {
		synchronized (lock) {
			donePass = true;
			lock.notify();
			while (donePass) {
				try {
					lock.wait();
				} catch (InterruptedException ex) {
					throw new RuntimeException(ex);
				}
			}
		}
	}
	public void doneAllPasses() {
		synchronized (lock) {
			donePass = true;
			lock.notify();
		}
		doneAllPasses = true;
	}
    public static void main(String[] args) {
        Alarm alarm = new Alarm(100);
        alarm.initAllPasses();
        for (int i = 0; i < 100; i++) {
            alarm.initNewPass();
            assert(!alarm.passTimedOut());
            assert(!alarm.donePass);
            System.out.print("PASS: " + i);
            for (int j = 0; j < 3000000; j++) Math.random();
            if (alarm.passTimedOut())
                System.out.println(" TIMED OUT!");
            else
                System.out.println();
            assert(!alarm.donePass);
        }
        alarm.doneAllPasses();
    }
}