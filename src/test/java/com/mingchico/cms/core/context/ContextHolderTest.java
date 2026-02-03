package com.mingchico.cms.core.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <h3>[ContextHolder 유틸리티 테스트]</h3>
 * <p>
 * 정적 메서드(Static Method)가 RequestContext 및 설정값(Properties)을
 * 올바르게 파싱하는지 <b>환경 격리(MockedStatic)</b>를 통해 검증합니다.
 * </p>
 */
class ContextHolderTest {

    private MockHttpServletRequest request;
    private MockedStatic<RequestContextHolder> requestContextHolderMock;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();

        // [Static Mocking] RequestContextHolder의 동작을 가로챕니다.
        requestContextHolderMock = Mockito.mockStatic(RequestContextHolder.class);
        requestContextHolderMock.when(RequestContextHolder::getRequestAttributes)
                .thenReturn(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        // [Resource Release] 다른 테스트에 영향을 주지 않도록 반드시 닫아줍니다.
        requestContextHolderMock.close();
    }

    @Test
    @DisplayName("Client IP: X-Forwarded-For 헤더가 여러 개일 경우 첫 번째 IP를 가져온다")
    void getClientIp_ProxyChain() {
        // Given
        // 실제 클라이언트 IP, Proxy1, Proxy2 순서
        request.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1, 10.0.0.2");

        // When
        String clientIp = ContextHolder.getClientIp();

        // Then
        assertThat(clientIp).isEqualTo("203.0.113.1");
    }

    @Test
    @DisplayName("Client IP: 프록시 헤더가 없으면 RemoteAddr을 반환한다")
    void getClientIp_DirectConnection() {
        // Given
        request.setRemoteAddr("127.0.0.1");

        // When
        String clientIp = ContextHolder.getClientIp();

        // Then
        assertThat(clientIp).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("Channel: URL 경로에 따라 올바른 채널 타입(ChannelType)을 반환한다")
    void getChannel_Routing() {
        // Given
        // 설정값 주입 (ContextProperties)
        ContextProperties properties = new ContextProperties();
        properties.getChannel().setAdminApiPrefix("/api/admin");
        properties.getChannel().setApiPrefix("/api");
        ContextHolder.setProperties(properties);

        // Case 1: 관리자 API
        request.setRequestURI("/api/admin/users");
        assertThat(ContextHolder.getChannel()).isEqualTo(ChannelType.ADMIN_API);

        // Case 2: 일반 API
        request.setRequestURI("/api/products");
        assertThat(ContextHolder.getChannel()).isEqualTo(ChannelType.API);

        // Case 3: 일반 웹
        request.setRequestURI("/home");
        assertThat(ContextHolder.getChannel()).isEqualTo(ChannelType.WEB);
    }
}