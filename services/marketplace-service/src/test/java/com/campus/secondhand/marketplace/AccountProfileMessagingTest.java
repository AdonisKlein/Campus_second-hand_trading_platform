package com.campus.secondhand.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;

class AccountProfileMessagingTest {
    @Test
    void consumesRabbitJsonAsUtf8WithoutCorruptingChineseProfileFields() throws Exception {
        UserProjectionUpdater projections = mock(UserProjectionUpdater.class);
        AccountProfileListener listener = new AccountProfileListener(projections);
        byte[] payload = ("{\"eventId\":\"profile-1\",\"userId\":3,\"version\":1,"
                + "\"username\":\"e2e_seller\",\"nickname\":\"E2E 卖家\","
                + "\"region\":\"沙河校区\",\"creditScore\":110,\"status\":\"ACTIVE\","
                + "\"role\":\"STUDENT\"}").getBytes(StandardCharsets.UTF_8);

        listener.receive(new Message(payload));

        ArgumentCaptor<UserPublicProfileChanged> event = ArgumentCaptor.forClass(UserPublicProfileChanged.class);
        verify(projections).accept(event.capture());
        assertThat(event.getValue().nickname()).isEqualTo("E2E 卖家");
        assertThat(event.getValue().region()).isEqualTo("沙河校区");
    }
}
