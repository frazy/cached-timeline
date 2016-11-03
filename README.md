# cached-timeline
使用redis作为持久化对象实现的timeline。

用作简单的时间线消息存取。
类似朋友圈的数据获取。

##1. 使用说明
参考测试类

~~~ xml
    <bean id="testTimeline" class="com.bitlei.cached.timeline.TimelineComponent">
        <constructor-arg index="0" ref="jedisConnectionFactory" />
        <property name="namespace" value="test" />
        <property name="dataInterface" ref="testDao" />
    </bean>
~~~

指定redis的ConnectionFactory，设置namespace用于多个Timeline，dataInterface是数据拉取接口（用于将数据从数据库拉取到redis）

