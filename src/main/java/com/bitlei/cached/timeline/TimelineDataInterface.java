/**
 * 
 */
package com.bitlei.cached.timeline;

import java.util.List;

/**
 * @author lei
 * 
 */
public interface TimelineDataInterface<K, M> {

	List<TimelineData<M>> getEarlierData(K tid, long lastTime, int limit);

	/**
	 * 取得最早的时间.
	 * 
	 * @param tid
	 * @return -1:没数据；>0:最早时间；
	 */
	long getEarliestTime(K tid);

}
