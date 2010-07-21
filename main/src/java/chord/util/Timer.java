/*
 * Copyright (c) 2008-2010, Intel Corporation.
 * Copyright (c) 2006-2007, The Trustees of Stanford University.
 * All rights reserved.
 * Licensed under the terms of the New BSD License.
 */
package chord.util;

import java.util.Date;
import java.text.DateFormat;

/**
 * Implementation of a timer with facility to pause and resume.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class Timer {
	public final String name;
	private long elapsedExclusiveTime = 0;
	private Date initDate = null;
	private Date lastResumeDate = null;
	private Date doneDate = null;
	private boolean isPaused = false;
	public Timer() {
		this.name = null;
	}
	public Timer(String name) {
		this.name = name;
	}
	public void init() {
		if (initDate != null)
			throw new RuntimeException("Timer '" + name + "' already started.");
		Date currDate = new Date();
		initDate = currDate;
		lastResumeDate = currDate;
	}
	public void pause() {
		checkInitDate();
		if (isPaused)
			throw new RuntimeException("Timer '" + name + "' already paused.");
		isPaused = true;
		Date currDate = new Date();
		elapsedExclusiveTime += currDate.getTime() - lastResumeDate.getTime();
	}
	public void resume() {
		if (!isPaused)
			throw new RuntimeException("Timer '" + name + "' not paused.");
		isPaused = false;
		lastResumeDate = new Date();
	}
	public void done() {
		if (doneDate != null)
			throw new RuntimeException("Timer '" + name + "' already stopped."); 
		if (isPaused) {
			throw new RuntimeException("Timer '" + name +
				"' stopped while paused.");
		}
		Date currDate = new Date();
		doneDate = currDate;
		elapsedExclusiveTime += currDate.getTime() - lastResumeDate.getTime();
	}
	private void checkInitDate() {
		if (initDate == null)
			throw new RuntimeException("Timer '" + name + "' not started.");
	}
	private void checkDoneDate() {
		if (doneDate == null)
			throw new RuntimeException("Timer '" + name + "' not stopped."); 
	}
	public String getInitTimeStr() {
		checkInitDate();
		return DateFormat.getDateTimeInstance().format(initDate);
    }
	public String getDoneTimeStr() {
		checkDoneDate();
		return DateFormat.getDateTimeInstance().format(doneDate);
	}
	public String getInclusiveTimeStr() {
		checkInitDate();
		checkDoneDate();
		long elapsedInclusiveTime = doneDate.getTime() - initDate.getTime();
		return getTimeStr(elapsedInclusiveTime);
	}
	public String getExclusiveTimeStr() {
		checkInitDate();
		checkDoneDate();
		return getTimeStr(elapsedExclusiveTime);
	}
	public static String getTimeStr(long time) {
        time /= 1000;
        String ss = String.valueOf(time % 60);
        if (ss.length() == 1)
        	ss = "0" + ss;
        time /= 60;
        String mm = String.valueOf(time % 60);
        if (mm.length() == 1)
        	mm = "0" + mm;
        time /= 60;
        String hh = String.valueOf(time % 24);
        if (hh.length() == 1)
        	hh = "0" + hh;
        time /= 24;
        String timeStr;
        if (time > 0)
        	timeStr = "&gt; 1 day";
        else
        	timeStr = hh + ":" + mm + ":" + ss + " hh:mm:ss";
        return timeStr;
	}
}