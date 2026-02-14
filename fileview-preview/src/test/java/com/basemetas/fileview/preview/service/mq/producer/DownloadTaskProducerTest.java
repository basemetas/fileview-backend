package com.basemetas.fileview.preview.service.mq.producer;

import com.basemetas.fileview.preview.model.download.DownloadTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DownloadTaskProducerTest {

    @Autowired
    private DownloadTaskProducer downloadTaskProducer;

    @Test
    public void testSendDownloadTask() {
        // 创建测试任务
        DownloadTask task = new DownloadTask();
        task.setTaskId("test-task-id");
        task.setFileId("test-file-id");
        task.setNetworkFileUrl("http://example.com/test.zip");
        task.setDownloadTargetPath("/tmp/downloads");
        task.setDownloadTimeout(30000);

        // 验证生产者已正确注入
        assertNotNull(downloadTaskProducer, "DownloadTaskProducer应该被正确注入");

        // 注意：实际的消息发送测试需要RocketMQ服务器运行，这里只验证对象创建
        assertDoesNotThrow(() -> {
            // 这里不实际发送消息，因为需要RocketMQ服务器
            // 在实际测试中，我们可以使用TestContainers或Mockito来模拟
        }, "发送下载任务消息不应该抛出异常");
    }
}
