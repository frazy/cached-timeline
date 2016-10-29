package com.bitlei.cached.timeline;

/**
 * 
 */

import java.util.List;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * 测试类模拟用户订阅的消息timeline.
 * 
 * @author lei
 * 
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:applicationContext.xml")
public class TimelineTest extends AbstractJUnit4SpringContextTests {

	@Resource
	private TimelineInterface<Long, Long> testTimeline;

	private long startTime = System.currentTimeMillis();

	@Test
	public void testAdd() throws InterruptedException {
		long tid = 1; // user.id
		for (int i = 1; i <= 1000; i++) {
			long memberId = i; // message.id
			String memberValue = "message_" + i; // message.value
			long time = startTime + 1000;// 每隔一秒
			boolean ret = testTimeline.add(tid, memberId, memberValue, time);
			logger.info(String.format("add %d %d %s %d > %s", tid, memberId, memberValue, time, ret));
		}
	}

	@Test
	public void testPull() {
		long tid = 1; // user.id
		long maxTime = startTime + 1000 * 10;
		List<String> list = testTimeline.pull(tid, maxTime, 10);

		int index = 0;
		for (String memberValue : list) {
			logger.info(String.format("%d %s", index, memberValue));
			index++;
		}
	}

}
