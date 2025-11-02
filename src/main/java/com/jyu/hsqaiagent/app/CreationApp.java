package com.jyu.hsqaiagent.app;


import com.jyu.hsqaiagent.advisor.MyLoggerAdvisor;
import com.jyu.hsqaiagent.rag.MediaCreationAppRagCustomAdvisorFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Component
@Slf4j
public class CreationApp {
    private static final String SYSTEM_PROMPT = "你是拥有 5 年 + 一线自媒体实战经验的创作专家，曾操盘美妆、职场、母婴等领域账号从 0 到 1 起号" +
            "（单账号最高 100 万 + 粉丝、单条内容最高 500 万 + 播放量），擅长 “账号定位→内容创作→数据优化→变现落地” 全链路问题解决。现以 “创作咨" +
            "询顾问” 身份与用户互动，核心逻辑是：通过层层引导式提问挖掘用户真实需求与现状，再提供针对性可落地建议，具体执行要求如下：身份与语气：" +
            "以 “我之前帮 XX 领域账号解决过类似问题” 强化专家感，语气如同行交流（避免说教），每轮回复先提问引导用户补充信息，再基于已有信息做 2 句" +
            "话内的简要拆解，不单向输出。需求挖掘框架：第一轮必问账号基础盘（领域 / 细分方向、运营平台、所处阶段，选 1 个延伸追问定位 / 平台相关细节）" +
            "；第二轮引导用户明确当前最痛创作痛点（选题 / 文案 / 数据 / 剪辑等），并拆解痛点细节（如选题难则问 “过往找选题的方式”）；第三轮问痛点解决" +
            "的过往尝试与效果，补充确认用户是否有明确受众画像；第四轮问 1-2 周短期目标与日常创作时间，适配建议节奏。输出原则：用户至少回答 2 轮后再给建" +
            "议，建议需绑定用户场景（如 “结合你职场号 + 小红书冷启动的情况”），给出初步建议后需追问适配性（如 “这个方法你觉得能操作吗？”）；禁止一上来给" +
            "通用模板、堆砌专业术语（不说 “内容垂直度”，改说 “围绕细分方向发内容”）、回避用户问题";
    private final ChatClient chatClient;

    @Resource
    private VectorStore mediaCreationAppVectorStore;

    @Resource
    private Advisor mediaCreationAppRagCloudAdvisor;

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    @Resource
    private ToolCallback[] allTools;

    public CreationApp(ChatModel dashscopeChatModel) {
        //基于内存的对话存储
        ChatMemory chatMemory = new InMemoryChatMemory();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        //对话记忆增强顾问
                        new MessageChatMemoryAdvisor(chatMemory),
                        //日志增强顾问
                        new MyLoggerAdvisor()
                )
                .build();
    }

    public String doChat(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                //advisors 是框架提供的一个配置入口，用于传递对话记忆、上下文范围等辅助信息
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 5))
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        //log.info("content: {}", content);
        return content;
    }

    //java14之后的新特性，可以通过record快速定义一个类
    //自媒体数据报告类
    record MediaReport(String title, List<String> suggestion){}

    public MediaReport doChatWithReport(String message, String chatId) {
        MediaReport loveReprot = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成相应的结果，标题为{用户名}的自媒体数据报告，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 5))
                .call()
                .entity(MediaReport.class);
        log.info("loveReport: {}", loveReprot);
        return loveReprot;
    }

    /**
     * 基于本地知识库RAG增强检索
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRag(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 5))
                //问答拦截器
                .advisors(new QuestionAnswerAdvisor(mediaCreationAppVectorStore))
                .advisors(MediaCreationAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(mediaCreationAppVectorStore,"单身"))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * 基于云知识库的检索增强顾问RAG
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithCloudRag(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 5))
                .advisors(new MyLoggerAdvisor())
                //检索增强顾问
                .advisors(mediaCreationAppRagCloudAdvisor)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * 基于工具调用
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithTools(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 5))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .tools(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输）
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .stream()
                .content();
    }

    public String doChatWithMcp(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 5))
                .tools(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

}
