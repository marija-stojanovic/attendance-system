package com.attendance.course.attendance;

import com.attendance.course.ApiExceptionHandler;
import com.attendance.course.sessions.SessionQrController;
import com.attendance.course.sessions.SessionQrService;
import com.attendance.course.sessions.TeachingSessionService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AvailabilityTests {
    @Test
    void returnsServiceUnavailableWhenDatabaseFailsDuringAttendance() throws Exception {
        AttendanceService attendance = mock(AttendanceService.class);
        doThrow(new DataAccessResourceFailureException("connection lost")).when(attendance)
                .record(isNull(), eq("token"), isNull(), any(UUID.class), any(AttendanceClientDetails.class));
        var mvc = MockMvcBuilders.standaloneSetup(new AttendanceController(attendance))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(jwtArgumentResolver()).build();

        mvc.perform(post("/api/student/attendance").contentType("application/json")
                        .content("{\"token\":\"token\",\"requestId\":\"00000000-0000-0000-0000-000000000001\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("detail").value("Baza trenutno nije dostupna. Pokušajte ponovo."));
    }

    @Test
    void returnsServiceUnavailableWhenQrGenerationFails() throws Exception {
        SessionQrService qr = mock(SessionQrService.class);
        TeachingSessionService sessions = mock(TeachingSessionService.class);
        UUID id = UUID.randomUUID();
        when(qr.current(id)).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "QR kod trenutno nije dostupan."));
        var mvc = MockMvcBuilders.standaloneSetup(new SessionQrController(qr, sessions))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(jwtArgumentResolver()).build();

        mvc.perform(get("/api/sessions/{id}/qr", id))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("detail").value("QR kod trenutno nije dostupan."));
    }

    private HandlerMethodArgumentResolver jwtArgumentResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return Jwt.class.equals(parameter.getParameterType())
                        && parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                    NativeWebRequest request, org.springframework.web.bind.support.WebDataBinderFactory binder) {
                return null;
            }
        };
    }
}
