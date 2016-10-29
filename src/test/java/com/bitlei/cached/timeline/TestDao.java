/**
 * 
 */
package com.bitlei.cached.timeline;

import java.util.List;

/**
 * @author lei
 * 
 */
public class TestDao implements TimelineDataInterface<Long, Long> {

	@Override
	public List<TimelineData<Long>> getEarlierData(Long tid, long lastTime, int limit) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getEarliestTime(Long tid) {
		// TODO Auto-generated method stub
		return 0;
	}

}
