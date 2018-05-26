package org.magemello.sys.node.clients;

import org.magemello.sys.node.domain.Record;
import org.magemello.sys.node.service.P2PService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class ACProtocolClient {

    @Autowired
    private P2PService p2pService;

    public Mono<Boolean> propose(Record record) {
        return Flux.fromIterable(p2pService.getPeers())
                .flatMap(peer -> createWebClientPropose(record, peer), p2pService.getPeers().size())
                .timeout(Duration.ofSeconds(10))
                .all(response -> !response.statusCode().isError());
    }

    public Mono<Boolean> commit(String id) {
        return Flux.fromIterable(p2pService.getPeers())
                .flatMap(peer -> createWebClientCommit(id, peer), p2pService.getPeers().size())
                .timeout(Duration.ofSeconds(10))
                .all(response -> !response.statusCode().isError());
    }

    public Mono<Boolean> rollback(String id) {
        return Flux.fromIterable(p2pService.getPeers())
                .flatMap(peer -> createWebClientRollBack(id, peer), p2pService.getPeers().size())
                .timeout(Duration.ofSeconds(10))
                .all(response -> !response.statusCode().isError());
    }

    private Mono<ClientResponse> createWebClientPropose(Record record, String peer) {
        return WebClient.create()
                .post()
                .uri(peer + "ac/propose")
                .syncBody(record)
                .accept(MediaType.APPLICATION_JSON)
                .exchange();
    }

    private Mono<ClientResponse> createWebClientCommit(String id, String peer) {
        return WebClient.create()
                .post()
                .uri(peer + "ac/commit/" + id)
                .accept(MediaType.APPLICATION_JSON)
                .exchange();
    }

    private Mono<ClientResponse> createWebClientRollBack(String id, String peer) {
        return WebClient.create()
                .post()
                .uri(peer + "ac/rollback/" + id)
                .accept(MediaType.APPLICATION_JSON)
                .exchange();
    }
}