package com.example.racehorse_transport.exception;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void missingResourceIs404() {
        var response = handler.handleResourceNotFound(new ResourceNotFoundException("Không tìm thấy đơn hàng: BK-404"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Không tìm thấy đơn hàng: BK-404");
    }

    @Test
    void malformedJsonIs400() {
        var ex = new HttpMessageNotReadableException("JSON parse error: Unexpected character", new MockHttpInputMessage(new byte[0]));

        var response = handler.handleUnreadableBody(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).doesNotContain("JSON parse error");
    }

    @Test
    void missingOrInvalidParameterIs400() {
        assertThat(handler.handleMissingParameter(new MissingServletRequestParameterException("status", "String")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        var mismatch = new MethodArgumentTypeMismatchException("abc", Integer.class, "id", mock(MethodParameter.class), null);
        assertThat(handler.handleTypeMismatch(mismatch).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void wrongHttpMethodIs405() {
        assertThat(handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("DELETE")).getStatusCode())
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void responseStatusExceptionKeepsItsStatus() {
        assertThat(handler.handleResponseStatus(new ResponseStatusException(HttpStatus.NOT_FOUND)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
