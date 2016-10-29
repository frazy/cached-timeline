/**
 * 
 */
package com.bitlei.cached.timeline;

import java.io.Serializable;

/**
 * @author lei
 * 
 */
public class TimelineData<M> implements Serializable {
	private static final long serialVersionUID = -4622167318649061754L;

	private long time;
	private M id;
	private String value;

	public TimelineData() {
		super();
	}

	public TimelineData(long time, M id, String value) {
		super();
		this.time = time;
		this.id = id;
		this.value = value;
	}

	@Override
	public String toString() {
		return "TimelineData [time=" + time + ", id=" + id + ", value=" + value + "]";
	}

	public long getTime() {
		return time;
	}

	public void setTime(long time) {
		this.time = time;
	}

	public M getId() {
		return id;
	}

	public void setId(M id) {
		this.id = id;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

}
