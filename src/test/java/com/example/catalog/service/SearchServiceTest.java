package com.example.catalog.service;

import com.example.catalog.dto.SearchResponse;
import com.example.catalog.model.Product;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private SearchHits<Product> searchHits;

    @Mock
    private ElasticsearchAggregations elasticsearchAggregations;

    @Mock
    private ElasticsearchAggregation elasticsearchAggregation;

    @Mock
    private org.springframework.data.elasticsearch.client.elc.Aggregation springAggregation;

    @InjectMocks
    private SearchService searchService;

    @Test
    void testSearchWithAllParameters() {
        // Arrange
        String queryText = "phone";
        String category = "electronics";
        Double minPrice = 100.0;
        Double maxPrice = 500.0;
        int page = 0;
        int size = 10;

        Product product = Product.builder()
                .id("1")
                .name("Smartphone")
                .description("A great phone")
                .price(299.99)
                .category("electronics")
                .build();

        SearchHit<Product> searchHit = mock(SearchHit.class);
        when(searchHit.getContent()).thenReturn(product);

        when(searchHits.stream()).thenReturn(Stream.of(searchHit));
        when(searchHits.getTotalHits()).thenReturn(1L);
        doReturn(elasticsearchAggregations).when(searchHits).getAggregations();
        when(elasticsearchAggregations.get("category_facet")).thenReturn(elasticsearchAggregation);
        when(elasticsearchAggregation.aggregation()).thenReturn(springAggregation);

        Aggregate aggregate = mock(Aggregate.class, RETURNS_DEEP_STUBS);
        when(springAggregation.getAggregate()).thenReturn(aggregate);
        when(aggregate.isSterms()).thenReturn(true);

        StringTermsBucket bucket = mock(StringTermsBucket.class, RETURNS_DEEP_STUBS);
        when(bucket.key().stringValue()).thenReturn("electronics");
        when(bucket.docCount()).thenReturn(1L);
        when(aggregate.sterms().buckets().array()).thenReturn(List.of(bucket));

        when(elasticsearchOperations.search(any(NativeQuery.class), eq(Product.class))).thenReturn(searchHits);

        // Act
        SearchResponse response = searchService.search(queryText, category, minPrice, maxPrice, page, size);

        // Assert
        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getName()).isEqualTo("Smartphone");
        assertThat(response.getPage()).isEqualTo(page);
        assertThat(response.getSize()).isEqualTo(size);
        assertThat(response.getTotalHits()).isEqualTo(1L);
        assertThat(response.getFacets()).containsKey("category");
        assertThat(response.getFacets().get("category")).containsEntry("electronics", 1L);

        // Verify Query DSL construction
        ArgumentCaptor<NativeQuery> queryCaptor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), eq(Product.class));

        NativeQuery capturedQuery = queryCaptor.getValue();
        Pageable pageable = capturedQuery.getPageable();
        assertThat(pageable.getPageNumber()).isEqualTo(page);
        assertThat(pageable.getPageSize()).isEqualTo(size);

        Query esQuery = capturedQuery.getQuery();
        assertThat(esQuery).isNotNull();
        assertThat(esQuery.isBool()).isTrue();

        BoolQuery boolQuery = esQuery.bool();
        
        // Assert multi_match text search exists and has boosting / fuzzy parameters
        assertThat(boolQuery.must()).hasSize(1);
        Query mustQuery = boolQuery.must().get(0);
        assertThat(mustQuery.isMultiMatch()).isTrue();
        var multiMatch = mustQuery.multiMatch();
        assertThat(multiMatch.query()).isEqualTo("phone");
        assertThat(multiMatch.fields()).containsExactlyInAnyOrder("name^2", "description");
        assertThat(multiMatch.fuzziness()).isEqualTo("AUTO");

        // Assert filter query has category and price range
        assertThat(boolQuery.filter()).hasSize(2);
        
        // Verify category term filter
        boolean hasCategoryFilter = boolQuery.filter().stream()
                .filter(Query::isTerm)
                .map(Query::term)
                .anyMatch(t -> t.field().equals("category") && t.value().stringValue().equals("electronics"));
        assertThat(hasCategoryFilter).isTrue();

        // Verify price range filter
        boolean hasPriceRangeFilter = boolQuery.filter().stream()
                .filter(Query::isRange)
                .map(Query::range)
                .anyMatch(r -> r.field().equals("price"));
        assertThat(hasPriceRangeFilter).isTrue();
    }

    @Test
    void testSearchWithEmptyParametersReturnsMatchAll() {
        // Arrange
        when(searchHits.stream()).thenReturn(Stream.empty());
        when(searchHits.getTotalHits()).thenReturn(0L);
        doReturn(null).when(searchHits).getAggregations();
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(Product.class))).thenReturn(searchHits);

        // Act
        SearchResponse response = searchService.search(null, null, null, null, 1, 5);

        // Assert
        assertThat(response.getResults()).isEmpty();
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(5);
        assertThat(response.getTotalHits()).isZero();

        ArgumentCaptor<NativeQuery> queryCaptor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), eq(Product.class));

        NativeQuery capturedQuery = queryCaptor.getValue();
        Query esQuery = capturedQuery.getQuery();
        assertThat(esQuery).isNotNull();
        assertThat(esQuery.isMatchAll()).isTrue();
    }
}
