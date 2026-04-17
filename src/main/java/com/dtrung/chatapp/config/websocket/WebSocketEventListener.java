package com.dtrung.chatapp.config.websocket;

import com.dtrung.chatapp.service.OnlineOfflineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final OnlineOfflineService onlineOfflineService;
    private final Map<String, String> simpSessionIdToSubscriptionId = new ConcurrentHashMap<>();

    @EventListener
    public void handleConnectedEvent(SessionConnectedEvent event) {
        log.info("{} Connected", Objects.requireNonNull(event.getUser()).getName());
        onlineOfflineService.addOnlineUser(event.getUser());
    }

    @EventListener
    public void handleDisconnectEvent(SessionDisconnectEvent event) {
        log.info("{} Disconnected", Objects.requireNonNull(event.getUser()).getName());
        onlineOfflineService.removeOnlineUser(event.getUser());
    }

    @EventListener
//    @SendToUser
    public void handleSubscribeEvent(SessionSubscribeEvent event) {
        String subscribedChannel =
                (String) event.getMessage().getHeaders().get("simpDestination");
        String simpSessionId =
                (String) event.getMessage().getHeaders().get("simpSessionId");
        if (subscribedChannel == null) {
            log.error("SUBSCRIBED TO NULL?? WAT?!");
            return;
        }
        simpSessionIdToSubscriptionId.put(simpSessionId, subscribedChannel);
        log.info("Subscribed to channel {}", subscribedChannel);
        onlineOfflineService.addUserSubscribed(event.getUser(), subscribedChannel);
    }

    @EventListener
    public void handleUnsubscribeEvent(SessionUnsubscribeEvent event) {
        log.info("{} Unsubscribed", Objects.requireNonNull(event.getUser()).getName());
        String simpSessionId = (String) event
                .getMessage()
                .getHeaders()
                .get("simpSessionId");
        String unSubscribedChannel =
                simpSessionIdToSubscriptionId.get(simpSessionId);
        onlineOfflineService.removeUserSubscribed(event.getUser(), unSubscribedChannel);
    }
}
