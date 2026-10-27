package com.fayu.connection.task;

import com.fayu.api.client.MsgClient;
import com.fayu.api.domain.po.MessageOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RetryScheduleTask {

    private final MsgClient msgClient;

    @Scheduled(cron = "*/5 * * * * ?")
    public void retryScheduleTask() {
        List<MessageOutbox> retryLists = msgClient.getRetryLists();
        for (MessageOutbox outbox : retryLists) {

        }
    }
}
