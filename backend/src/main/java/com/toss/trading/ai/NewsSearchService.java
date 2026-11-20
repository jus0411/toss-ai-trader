package com.toss.trading.ai;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NewsSearchService {

    private final WebClient webClient;

    public NewsSearchService(WebClient.Builder webClientBuilder) {
        // 구글 뉴스 베이스 URL 지정
        this.webClient = webClientBuilder.baseUrl("https://news.google.com").build();
    }

    /**
     * 특정 종목명과 연관된 실시간 구글 뉴스 헤드라인 최신 5개를 수집합니다.
     */
    public List<Map<String, String>> searchStockNews(String stockName) {
        List<Map<String, String>> newsList = new ArrayList<>();
        try {
            // 구글 뉴스 검색 쿼리 인코딩 (예: "삼성전자 주식")
            String query = URLEncoder.encode(stockName + " 주식", StandardCharsets.UTF_8);
            String uri = String.format("/rss/search?q=%s&hl=ko&gl=KR&ceid=KR:ko", query);

            System.out.println("[News Search] Requesting Google RSS for: " + stockName);

            // 1. XML 문자열 획득
            String xmlResponse = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(); // 동기 대기

            if (xmlResponse == null || xmlResponse.isEmpty()) {
                return newsList;
            }

            // 2. DOM XML 파싱
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 외부 XML Entity 주입 공격(XXE) 방지 설정
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlResponse.getBytes(StandardCharsets.UTF_8)));
            doc.getDocumentElement().normalize();

            // <item> 태그 목록 파싱
            NodeList items = doc.getElementsByTagName("item");
            int count = Math.min(items.getLength(), 5); // 최신 5개만 수집

            for (int i = 0; i < count; i++) {
                Element item = (Element) items.item(i);
                String title = getTagValue("title", item);
                String pubDate = getTagValue("pubDate", item);

                // 정제 작업 (구글 뉴스 뒤에 붙는 매체 배지 [중앙일보] 등 제거)
                if (title != null) {
                    title = title.replaceAll("\\s-\\s.*$", "").trim();
                }

                Map<String, String> news = new HashMap<>();
                news.put("title", title);
                news.put("date", pubDate);
                newsList.add(news);
            }

        } catch (Exception e) {
            System.err.println("[News Search] Google News RSS fetch failed: " + e.getMessage());
        }

        return newsList;
    }

    private String getTagValue(String tag, Element element) {
        NodeList nodeList = element.getElementsByTagName(tag);
        if (nodeList != null && nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return "";
    }
}
