package org.magemello.sys.node.controller;

import org.magemello.sys.node.repository.RecordRepository;
import org.magemello.sys.node.service.ProtocolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController()
@RequestMapping("/storage/")
public class StorageController {

    @Autowired
    ProtocolService protocolService;

    @Autowired
    RecordRepository recordRepository;

    @PostMapping("/{key}/{value}")
    public Mono<ResponseEntity> set(@PathVariable String key, @PathVariable String value) throws Exception {
        return protocolService.set(key, value);
    }

    @GetMapping("/{key}")
    public Mono<ResponseEntity> get(@PathVariable String key) {
        return protocolService.get(key);
    }

    @GetMapping("/dump")
    public ResponseEntity<?> getAll() {
        return new ResponseEntity<>(recordRepository.findAll(), HttpStatus.OK);
    }
}
