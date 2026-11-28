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
    void oversizedUploadReturns413WithUsefulMessage() {
        var response = handler.handleUploadTooLarge(
                new org.springframework.web.multipart.MaxUploadSizeExceededException(5 * 1024 * 1024));
        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody().getMessage()).contains("5MB").doesNotContain("Exception");
    }

    @Test
    void missingResourceIs404() {
        var response = handler.handleResourceNotFound(new ResourceNotFoundException("Không tìm thấy đơn hàng: BK-404"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Không tìm thấy đơn hàng: BK-404");
    }

    @Test
    void dispatchRuleIs400OrConflict409WithTheField() {
        var invalid = handler.handleDispatchRule(new DispatchRuleException("stops", "Lượt xe cần ít nhất hai điểm dừng."));
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody().getData()).containsEntry("stops", "Lượt xe cần ít nhất hai điểm dừng.");

        var conflict = handler.handleDispatchRule(new DispatchRuleException("vehicleId", "Xe đã có lượt chạy khác trùng giờ.", true));
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().getMessage()).isEqualTo("Xe đã có lượt chạy khác trùng giờ.");
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
    void wrongContentTypeIs415() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new JsonEndpoint()).setControllerAdvice(handler).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/json")
                        .contentType("text/plain").content("body"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnsupportedMediaType())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("error"));
    }

    @org.springframework.web.bind.annotation.RestController
    static class JsonEndpoint {
        @org.springframework.web.bind.annotation.PostMapping(path = "/json", consumes = "application/json")
        void accept(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> body) {
        }
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

    @Test
    void unexpectedErrorsAre500WithoutInternalDetails() {
        var response = handler.handleGeneralException(new NullPointerException("Cannot invoke \"Booking.getStatus()\" because \"b\" is null"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).doesNotContain("Booking.getStatus");
    }
}
