package spring_steam_backend.spring_steam_backend.config;

import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.apache.http.HttpHost;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticSearchConfig {
    @Bean
    public RestHighLevelClient client() {
        return new RestHighLevelClient(
            RestClient.builder(
                new HttpHost("0d0c4b664b704151a4796acef6dba544.us-central1.gcp.cloud.es.io", 443, "https")
            )
        );
    }
}
