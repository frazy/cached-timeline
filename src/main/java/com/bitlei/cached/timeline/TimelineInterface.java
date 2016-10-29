/**
 * 
 */
package com.bitlei.cached.timeline;

import java.util.List;

/**
 * 简单的timeline接口.<br>
 * K是指timeline里面key的类型.<br>
 * M是指timeline里面member.id的类型.<br>
 * tid是指timeline的标识.
 * 
 * @author lei
 * 
 */
public interface TimelineInterface<K, M> {

	/**
	 * 从指定tid的timeline中统计minTime和maxTime之间的数据个数。<br>
	 * 不包含minTime和maxTime.
	 * 
	 * @param tid
	 * @param minTime
	 * @param maxTime
	 * @return
	 */
	long count(K tid, long minTime, long maxTime);

	/**
	 * 将数据加入指定tid的timeline.
	 * 
	 * @param tid
	 * @param memberId
	 *            null时表示memberValue是简单对象
	 * @param memberValue
	 * @param time
	 * @return
	 */
	boolean add(K tid, M memberId, String memberValue, long time);

	/**
	 * 将数据从指定tid的timeline中移除.
	 * 
	 * @param tid
	 * @param memberId
	 * @param memberValue
	 * @param time
	 * @return
	 */
	long remove(K tid, M memberId, String memberValue, long time);

	/**
	 * 将数据从指定tid的timeline中全部移除.
	 * 
	 * @param tid
	 * @return
	 */
	long removeAll(K tid);

	/**
	 * 从指定tid的timeline中拉取minTime和maxTime之间的数据。<br>
	 * 不包含minTime和maxTime.
	 * 
	 * @param tid
	 * @param minTime
	 * @param maxTime
	 * @param limit
	 * @return
	 */
	List<String> pull(K tid, long minTime, long maxTime, int limit);

	/**
	 * 从指定tid的timeline中拉取比maxTime时间更早的数据。<br>
	 * 不包含maxTime.
	 * 
	 * @param tid
	 * @param maxTime
	 * @param limit
	 * @return
	 */
	List<String> pull(K tid, long maxTime, int limit);

	List<TimelineData<M>> pullWithTime(K tid, long minTime, long maxTime, int limit);

}
