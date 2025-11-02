package com.jyu.hsqaiagent.controller;

import com.jyu.hsqaiagent.agent.MediaManus;
import com.jyu.hsqaiagent.app.CreationApp;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private CreationApp loveApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;


    /**
     * 同步调用 AI 自媒体创作助手应用（基于内存记忆对话增强顾问）
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/mediaCreation_app/chat/sync")
    public String doChatWithMediaCreationAppSync(String message, String chatId) {
        return loveApp.doChat(message, chatId);
    }

    /**
     * SSE 流式调用 AI 自媒体创作助手应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/mediaCreation_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithMediaCreationAppSSE(String message, String chatId) {
        return loveApp.doChatByStream(message, chatId);
    }

    /**
     * SSE 流式调用 AI 自媒体创作助手应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/mediaCreation_app/chat/sse_emitter")
    public SseEmitter doChatWithMediaCreationAppServerSseEmitter(String message, String chatId) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(180000L); // 3 分钟超时
        // 获取 Flux 响应式数据流并且直接通过订阅推送给 SseEmitter
        loveApp.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        // 返回
        return sseEmitter;
    }

    /**
     * 流式调用 Manus 超级智能体
     *
     * @param message
     * @return
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message) {
        MediaManus mediaManus = new MediaManus(allTools, dashscopeChatModel);
        return mediaManus.runStream(message);
    }
}
