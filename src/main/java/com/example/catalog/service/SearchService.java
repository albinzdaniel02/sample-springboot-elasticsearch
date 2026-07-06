package com.example.catalog.service;

import com.example.catalog.dto.SearchResponse;
import com.example.catalog.model.Product;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.AggregationsContainer;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    public SearchResponse search(String q, String category, Double minPrice, Double maxPrice, int page, int size) {
        var queryBuilder = NativeQuery.builder()
                .withPageable(PageRequest.of(page, size));

        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        boolean hasQuery = false;

        if (q != null && !q.trim().isEmpty()) {
            boolQueryBuilder.must(Query.of(mq -> mq
                    .multiMatch(mm -> mm
                            .query(q)
                            .fields("name^2", "description")
                            .fuzziness("AUTO")
                    )
            ));
            hasQuery = true;
        }

        if (category != null && !category.trim().isEmpty()) {
            boolQueryBuilder.filter(Query.of(fq -> fq
                    .term(t -> t.field("category").value(category))
            ));
            hasQuery = true;
        }

        if (minPrice != null || maxPrice != null) {
            boolQueryBuilder.filter(Query.of(fq -> fq
                    .range(r -> {
                        r.field("price");
                        if (minPrice != null) {
                            r.gte(JsonData.of(minPrice));
                        }
                        if (maxPrice != null) {
                            r.lte(JsonData.of(maxPrice));
                        }
                        return r;
                    })
            ));
            hasQuery = true;
        }

        if (hasQuery) {
            queryBuilder.withQuery(Query.of(qb -> qb.bool(boolQueryBuilder.build())));
        } else {
            queryBuilder.withQuery(Query.of(qb -> qb.matchAll(m -> m)));
        }

        // Add category terms aggregation
        queryBuilder.withAggregation("category_facet", Aggregation.of(a -> a
                .terms(t -> t.field("category"))
        ));

        SearchHits<Product> searchHits = elasticsearchOperations.search(queryBuilder.build(), Product.class);

        List<Product> results = searchHits.stream()
                .map(SearchHit::getContent)
                .toList();

        Map<String, Map<String, Long>> facets = new HashMap<>();
        AggregationsContainer<?> aggregationsContainer = searchHits.getAggregations();
        if (aggregationsContainer instanceof ElasticsearchAggregations elasticsearchAggregations) {
            var elasticsearchAggregation = elasticsearchAggregations.get("category_facet");
            if (elasticsearchAggregation != null) {
                var aggregate = elasticsearchAggregation.aggregation().getAggregate();
                if (aggregate != null && aggregate.isSterms()) {
                    Map<String, Long> categoryFacet = new HashMap<>();
                    for (StringTermsBucket bucket : aggregate.sterms().buckets().array()) {
                        categoryFacet.put(bucket.key().stringValue(), bucket.docCount());
                    }
                    facets.put("category", categoryFacet);
                }
            }
        }

        return SearchResponse.builder()
                .results(results)
                .page(page)
                .size(size)
                .totalHits(searchHits.getTotalHits())
                .facets(facets)
                .build();
    }
}
