package edu.fpt.sba.scms.config;

import edu.fpt.sba.scms.common.trace.MdcTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/** Executor cho @Async: copy MDC traceId, không copy BranchContext (IMPLEMENTATION.md §5.4). */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private Executor executor;

    @Bean(name = "applicationTaskExecutor")
    public Executor applicationTaskExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(4);
        taskExecutor.setMaxPoolSize(16);
        taskExecutor.setQueueCapacity(500);
        taskExecutor.setThreadNamePrefix("scms-async-");
        taskExecutor.setTaskDecorator(new MdcTaskDecorator());
        taskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        taskExecutor.initialize();
        this.executor = taskExecutor;
        return taskExecutor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return executor;
    }
}
