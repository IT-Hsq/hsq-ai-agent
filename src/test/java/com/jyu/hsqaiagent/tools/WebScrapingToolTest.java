package com.jyu.hsqaiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

//@SpringBootTest
public class WebScrapingToolTest {

    @Test
    public void testScrapeWebPage() {
        WebScrapingTool tool = new WebScrapingTool();
        String url = "https://www.baidu.cn";
        String result = tool.scrape(url);
        System.out.println(result);
        assertNotNull(result);
    }
}