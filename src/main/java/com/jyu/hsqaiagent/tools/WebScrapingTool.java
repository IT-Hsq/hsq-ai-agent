package com.jyu.hsqaiagent.tools;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 网页抓取工具
 */
@Slf4j
public class WebScrapingTool {

    @Tool(description = "Scrape the content of web page")
    public String scrape(@ToolParam(description = "The url of the web page to scrape") String url) {
        try {
            Document document = Jsoup.connect(url).get();
            log.info(document.toString());
            return document.html();
        } catch (Exception e) {
            return "Error scraping web page:" + e.getMessage();
        }
    }
}
