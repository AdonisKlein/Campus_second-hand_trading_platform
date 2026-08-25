package com.campus.secondhand.unit;

import com.campus.secondhand.user.InvalidVerificationCodeException;
import com.campus.secondhand.user.RegistrationService;
import com.campus.secondhand.user.User;
import com.campus.secondhand.user.UserRepository;
import com.campus.secondhand.user.VerificationPurpose;
import com.campus.secondhand.user.VerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RegistrationServiceTests {
    @Test
    void invalidVerificationStopsRegistrationBeforeUserWrite() {
        UserRepository users = mock(UserRepository.class);
        VerificationService verification = mock(VerificationService.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(verification.verifyCode("student@example.com", VerificationPurpose.REGISTER, "000000"))
            .thenReturn(false);
        RegistrationService service = new RegistrationService(users, verification, passwords);

        assertThrows(InvalidVerificationCodeException.class,
            () -> service.register("student@example.com", "student", "Student", "password", "000000"));
        verify(users, never()).saveAndFlush(any(User.class));
    }

    @Test
    void successfulRegistrationTrimsUsernameAndHashesPassword() {
        UserRepository users = mock(UserRepository.class);
        VerificationService verification = mock(VerificationService.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(verification.verifyCode("student@example.com", VerificationPurpose.REGISTER, "123456"))
            .thenReturn(true);
        when(passwords.encode("password")).thenReturn("encoded");
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RegistrationService service = new RegistrationService(users, verification, passwords);

        User registered = service.register("student@example.com", "  student  ", "Student", "password", "123456");

        assertEquals("student", registered.getUsername());
        assertEquals("encoded", registered.getPasswordHash());
        verify(users).saveAndFlush(registered);
    }

    @Test
    void persistenceFailureIsPropagatedSoTransactionCanRollback() {
        UserRepository users = mock(UserRepository.class);
        VerificationService verification = mock(VerificationService.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(verification.verifyCode(any(), any(), any())).thenReturn(true);
        when(passwords.encode(any())).thenReturn("encoded");
        doThrow(new RuntimeException("database unavailable")).when(users).saveAndFlush(any(User.class));
        RegistrationService service = new RegistrationService(users, verification, passwords);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> service.register("student@example.com", "student", "Student", "password", "123456"));

        assertEquals("database unavailable", error.getMessage());
        verify(users).saveAndFlush(any(User.class));
    }

    @Test
    void persistenceFailureRollsBackTheSpringTransactionBoundary() {
        UserRepository users = mock(UserRepository.class);
        VerificationService verification = mock(VerificationService.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(verification.verifyCode(any(), any(), any())).thenReturn(true);
        when(passwords.encode(any())).thenReturn("encoded");
        doThrow(new RuntimeException("write failed")).when(users).saveAndFlush(any(User.class));

        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactions.getTransaction(any())).thenReturn(status);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        interceptor.afterPropertiesSet();
        ProxyFactory proxy = new ProxyFactory(new RegistrationService(users, verification, passwords));
        proxy.addAdvice(interceptor);
        RegistrationService transactionalService = (RegistrationService) proxy.getProxy();

        assertThrows(RuntimeException.class, () -> transactionalService.register(
            "student@example.com", "student", "Student", "password", "123456"));

        verify(transactions).getTransaction(any());
        verify(transactions).rollback(status);
        verifyNoMoreInteractions(transactions);
    }
}
