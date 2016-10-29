/**
 * 
 */
package com.bitlei.cached.timeline;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

/**
 * 简单的timeline组件.
 * 
 * @author lei
 * 
 */
public class TimelineComponent<K, M> implements TimelineInterface<K, M> {
	private static final Logger logger = LoggerFactory.getLogger(TimelineComponent.class);
	private static final String TIMELINE_KEY = "tl_%s_%s"; // timeline key (%s=namespace %s=tid)
	private static final String TIMELINE_MEMBER_KEY = "tl_mem_%s_%s"; // timeline member key (%s=namespace %s=tid)
	private static final String EARLIEST_TIME_KEY_FORMAT = "%s_etime"; // 最早时间key

	private RedisTemplate<String, String> redisTemplate;

	public TimelineComponent(JedisConnectionFactory jedisConnectionFactory) {
		if (jedisConnectionFactory == null) {
			throw new IllegalArgumentException("jedisConnectionFactory is null");
		}

		this.redisTemplate = new RedisTemplate<String, String>();
		redisTemplate.setConnectionFactory(jedisConnectionFactory);
		redisTemplate.setKeySerializer(redisTemplate.getStringSerializer());
		redisTemplate.setValueSerializer(redisTemplate.getStringSerializer());
		redisTemplate.afterPropertiesSet();
	}

	private String namespace; // 命名空间，每个timeline隔离
	private TimelineDataInterface<K, M> dataInterface;
	private int batchLoadNum = 100; // 每次加载数据的数量

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public void setDataInterface(TimelineDataInterface<K, M> dataInterface) {
		this.dataInterface = dataInterface;
	}

	public void setBatchLoadNum(int batchLoadNum) {
		this.batchLoadNum = batchLoadNum;
	}

	private String key(K tid) {
		return String.format(TIMELINE_KEY, namespace, serialize(tid));
	}

	private String memberKey(K tid) {
		return String.format(TIMELINE_MEMBER_KEY, namespace, serialize(tid));
	}

	private String serialize(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof String) {
			return (String) value;
		}
		if (value instanceof Long) {
			return String.valueOf(value);
		}
		if (value instanceof Integer) {
			return String.valueOf(value);
		}

		throw new IllegalArgumentException("not support serialize class " + value.getClass());
	}

	@Override
	public long count(K tid, long minTime, long maxTime) {
		String key = key(tid);

		// 不包含minTime，所以+1
		minTime++;

		// maxTime<=0, 从当前时间开始
		if (maxTime <= 0) {
			maxTime = new Date().getTime();
		} else {
			// 不包含maxTime，所以-1
			maxTime--;
		}

		long count = count(key, minTime, maxTime);

		// if (count == 0) { // 为0需要看看是否是缓存挂了重启了
		// long earliestTime = readEarliestTime(tid);
		// if (earliestTime > 0) { // TODO 有数据并且在请求时间区间内，则需要拉取数据
		// pull(tid, 0, 0, 1000);
		// count = count(key, minTime, maxTime);
		// }
		// }

		return count;
	}

	private Long count(String key, long minTime, long maxTime) {
		Long count = null;
		try {
			count = redisTemplate.opsForZSet().count(key, minTime, maxTime);
		} finally {
			logger.debug("tl count {} {},{} > {}", new Object[] { key, minTime, maxTime, count });
		}
		return count == null ? 0 : count;
	}

	@Override
	public boolean add(K tid, M memberId, String memberValue, long time) {
		String key = key(tid);

		// memberId、memberValue关系单独放一份，用于更新和删除时候查老数据
		String memberKey = memberKey(tid);
		String mid = serialize(memberId);
		try {
			removeOld(key, memberKey, mid);
			redisTemplate.opsForHash().put(memberKey, mid, memberValue);
		} finally {
			logger.debug("tl put member {} {} {}", new Object[] { memberKey, mid, memberValue });
		}

		// 设置timeline
		Boolean ret = null;
		try {
			ret = redisTemplate.opsForZSet().add(key, memberValue, time);
		} finally {
			logger.debug("tl add {} {} > {}", new Object[] { key, memberValue, ret });
		}

		// 检查最早时间
		long earliestTime = readEarliestTime(tid);
		if (earliestTime == -1) {
			writeEarliestTime(tid, time);
		} else if (time > 0 && time < earliestTime) {
			removeEarliestTime(tid);
		}

		return ret;
	}

	private void removeOld(String key, String memberKey, String mid) {
		String oldMemberValue = (String) redisTemplate.opsForHash().get(memberKey, mid);
		if (oldMemberValue != null) {
			Long removedNum = redisTemplate.opsForZSet().remove(key, oldMemberValue);
			logger.debug("tl removeOld {} {} > {}", new Object[] { key, oldMemberValue, removedNum });
		}
	}

	@Override
	public long remove(K tid, M memberId, String memberValue, long time) {
		String key = key(tid);
		String value = memberValue;

		Long removedNum = 0L;
		try {
			// 根据memberId、memberValue关系取出老的memberValue
			String memberKey = memberKey(tid);
			String mid = serialize(memberId);
			removeOld(key, memberKey, mid);

			removedNum = redisTemplate.opsForZSet().remove(key, value);
			redisTemplate.opsForHash().delete(memberKey, mid);
		} finally {
			logger.debug("tl remove {} {} > {}", new Object[] { key, value, removedNum });
		}

		// 看看earliestTime是否需要清除
		long earliestTime = readEarliestTime(tid);
		if (earliestTime == -1) {
			removeEarliestTime(tid);
		}
		if (time > 0 && time <= earliestTime) {
			removeEarliestTime(tid);
		}

		return removedNum;
	}

	@Override
	public long removeAll(K tid) {
		String key = key(tid);

		Long removedNum = 0L;
		try {
			removedNum = redisTemplate.opsForZSet().removeRange(key, 0, -1);
			// 移除所有关系
			redisTemplate.delete(memberKey(tid));
		} finally {
			logger.debug("tl remove all {} > {}", new Object[] { key, removedNum });
		}

		removeEarliestTime(tid);
		// writeEarliestTime(tid, -1);

		return removedNum;
	}

	@Override
	public List<String> pull(K tid, long maxTime, int limit) {
		return pull(tid, 0, maxTime, limit);
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<String> pull(K tid, long minTime, long maxTime, int limit) {
		return (List<String>) pullData(tid, minTime, maxTime, limit, false);
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<TimelineData<M>> pullWithTime(K tid, long minTime, long maxTime, int limit) {
		return (List<TimelineData<M>>) pullData(tid, minTime, maxTime, limit, true);
	}

	private List<?> pullData(K tid, long minTime, long maxTime, int limit, boolean returnTime) {
		String key = key(tid);

		// 不包含minTime，所以+1
		minTime++;

		// maxTime<=0, 从当前时间开始
		if (maxTime <= 0) {
			maxTime = new Date().getTime();
		} else {
			// 不包含maxTime，所以-1
			maxTime--;
		}

		// 从timeline拉取数据
		Set<TypedTuple<String>> set = null;
		try {
			set = redisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, minTime, maxTime, 0, limit);
		} finally {
			logger.debug("tl pull key={} time={}~{} limit={} > {}", new Object[] { key, minTime, maxTime, limit, set == null ? 0 : set.size() });
		}

		// 转换为目标对象
		List<Object> result = new ArrayList<Object>();
		if (set != null) {
			for (TypedTuple<String> tuple : set) {
				if (returnTime) {
					result.add(new TimelineData<M>(tuple.getScore().longValue(), null, tuple.getValue()));
				} else {
					result.add(tuple.getValue());
				}
			}
		}

		// 如果不够limit数，看看是否已经超过timeline里最早的的时间，超过的话，不用从dataInterface里拉取数据
		if (result.size() < limit) {
			long cachedEarliestTime = readEarliestTime(tid); // -1表示没有任何数据；
			if (cachedEarliestTime >= 0) {
				// 看看timeline里面最早一条
				long timetag = System.currentTimeMillis();
				Set<TypedTuple<String>> earliestSet = redisTemplate.opsForZSet().rangeWithScores(key, 0, 0);
				if (earliestSet != null) {
					timetag = earliestSet.iterator().next().getScore().longValue();
				}
				if (timetag > cachedEarliestTime) {
					// 调用dataInterface拉取更早的数据，并塞入timeline
					int max = (limit - result.size() < batchLoadNum) ? batchLoadNum : (limit - result.size());
					List<TimelineData<M>> datas = dataInterface.getEarlierData(tid, timetag, max);
					int size = datas != null ? datas.size() : 0;
					logger.warn("not enough data from timeline, load {} {},{} > {}", new Object[] { tid, timetag, max, size });
					if (size > 0) {
						for (TimelineData<M> data : datas) {
							add(tid, data.getId(), data.getValue(), data.getTime());
							if (result.size() < limit && data.getTime() >= minTime && data.getTime() <= maxTime) {
								if (returnTime) {
									result.add(data);
								} else {
									result.add(data.getValue());
								}
							}
						}
					} else { // size == 0表示cachedEarliestTime不准
						removeEarliestTime(tid);
					}
				}
			}
		}

		logger.trace("tl pull key={} time={}~{} limit={} > {}", new Object[] { key, minTime, maxTime, limit, result });
		return result;
	}

	private long readEarliestTime(K tid) {
		String key = String.format(EARLIEST_TIME_KEY_FORMAT, key(tid));
		String value = redisTemplate.opsForValue().get(key);
		logger.debug("get cache {}={}", new Object[] { key, value });
		if (value == null) {
			long earliestTime = dataInterface.getEarliestTime(tid);
			if (earliestTime != 0) {
				writeEarliestTime(tid, earliestTime > 0 ? earliestTime : -1);
				return earliestTime;
			}
		}
		return value != null ? Long.valueOf(value) : 0;
	}

	private void writeEarliestTime(K tid, long earliestTime) {
		String key = String.format(EARLIEST_TIME_KEY_FORMAT, key(tid));
		String value = String.valueOf(earliestTime);
		redisTemplate.opsForValue().set(key, value);
		logger.debug("set cache {}={}", new Object[] { key, value });
	}

	private void removeEarliestTime(K tid) {
		String key = String.format(EARLIEST_TIME_KEY_FORMAT, key(tid));
		redisTemplate.delete(key);
		logger.debug("del cache {}", new Object[] { key });
	}

}
