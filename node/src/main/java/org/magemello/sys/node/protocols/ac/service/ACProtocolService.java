package org.magemello.sys.node.protocols.ac.service;

import org.magemello.sys.node.domain.Record;
import org.magemello.sys.node.protocols.ac.clients.ACProtocolClient;
import org.magemello.sys.node.protocols.ac.domain.Transaction;
import org.magemello.sys.node.repository.RecordRepository;
import org.magemello.sys.node.service.ProtocolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Service("AC")
@SuppressWarnings("rawtypes")
public class ACProtocolService implements ProtocolService {

    private static final Logger log = LoggerFactory.getLogger(ACProtocolService.class);

    @Autowired
    private RecordRepository recordRepository;

    @Autowired
    private ACProtocolClient acProtocolClient;

    private Map<String, Transaction> writeAheadLog = new LinkedHashMap<>();

    @Override
    public Mono<ResponseEntity> get(String key) {
        log.info("AC Service - get for {}\n", key);

        return handleGet(key);
    }

    @Override
    public Mono<ResponseEntity> set(String key, String value) throws Exception {
        log.info("AC Service - Proposing to peers\n");

        Transaction transaction = new Transaction(key, value);
        return handleSet(transaction);
    }

    @Override
    public String protocolName() {
        return "AC";
    }

    @Override
    public void onCleanup() {
        writeAheadLog.clear();
    }

    public boolean propose(Transaction transaction) {

        if (!isAProposalPresentFor(transaction.getKey())) {
            log.info("- accepted proposal {} for key {}\n", transaction.get_ID(), transaction.getKey());
            writeAheadLog.put(transaction.get_ID(), transaction);
            return true;
        } else {
            log.info("- refused proposal {} for key {} (already present)\n", transaction.get_ID(), transaction.getKey());
            return false;
        }
    }

    public Record commit(String id) {
        Transaction transaction = writeAheadLog.get(id);

        if (transaction != null) {
            Record record = recordRepository.save(new Record(transaction.getKey(), transaction.getValue()));
            writeAheadLog.remove(id);
            log.info("- successfully committed proposal {}\n", id);
            return record;
        } else {
            log.info("- failed to find proposal {}\n", id);
            return null;
        }
    }

    public Transaction rollback(String id) {
        Transaction transaction = writeAheadLog.get(id);

        if (transaction != null) {
            transaction = writeAheadLog.remove(id);
            log.info("- successfully rolled back proposal {}\n", id);
        } else {
            log.info("- failed to find proposal {}\n", id);
        }

        return transaction;
    }

    private Mono<ResponseEntity> handleGet(String key) {
        return new Mono<ResponseEntity>() {
            @Override
            public void subscribe(CoreSubscriber<? super ResponseEntity> actual) {
                actual.onNext(ResponseEntity.ok().body(recordRepository.findByKey(key).toString()));
                actual.onComplete();
            }
        };
    }

    private Mono<ResponseEntity> handleSet(Transaction transaction) {
        return new Mono<ResponseEntity>() {

            CoreSubscriber<? super ResponseEntity> actual;

            @Override
            public void subscribe(CoreSubscriber<? super ResponseEntity> actual) {
                this.actual = actual;

                acProtocolClient.propose(transaction)
                        .subscribe(this::handleProposeResult
                                , this::handleError);
            }

            private void handleProposeResult(List<ClientResponse> clientResponses) {

                if (isAgreementReached(clientResponses)) {
                    log.info("Propose for {} succeed sending commit to peers\n", transaction);

                    acProtocolClient.commit(transaction.get_ID())
                            .subscribe(this::handleCommitResult
                                    , this::handleError);
                } else {
                    log.error("Propose for {} failed sending rollback to peers\n", transaction);

                    acProtocolClient
                            .rollback(transaction.get_ID(),
                                    clientResponses)
                            .subscribe(this::handleRollBackResult
                                    , this::handleError);
                }
            }

            private boolean isAgreementReached(List<ClientResponse> clientResponses) {
                return clientResponses.stream().allMatch(clientResponse -> !clientResponse.statusCode().isError());
            }

            private void handleCommitResult(Boolean resultCommit) {
                log.info("Peers Committed {}\n", transaction);

                recordRepository.save(new Record(transaction.getKey(), transaction.getValue()));

                actual.onNext(ResponseEntity
                        .status(HttpStatus.OK)
                        .body("Stored " + transaction.toString()));
                actual.onComplete();
            }

            private void handleRollBackResult(Boolean RollBack) {
                log.info("Peers Rolled Back {}\n", transaction);

                actual.onNext(ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body("Roll Backed " + transaction.toString()));
                actual.onComplete();
            }

            private void handleError(Throwable error) {
                actual.onNext(ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(error.getMessage()));
                actual.onComplete();
            }
        };
    }

    private boolean isAProposalPresentFor(String key) {
        return this.writeAheadLog
                .entrySet()
                .stream()
                .anyMatch(stringRecordEntry -> stringRecordEntry.getValue().getKey().equals(key));
    }

    @Override
    public void start() {
        log.info("AC mode (two-phase commit)\n\n");
    }

    @Override
    public void stop() {
    }
}
